import {
  isJsonObject,
} from '../../../shared/api/apiValidation'
import { isUuid } from '../../../shared/identifiers/uuid'
import {
  customerSessionStorageKeys,
} from '../../../shared/storage/customerSessionStorage'
import type { PaymentDraft } from '../model/paymentDraft'

const schemaVersion = 1
const maximumEnvelopeAgeMs =
  24 * 60 * 60 * 1000

export interface PaymentSubmissionEnvelope {
  schemaVersion: 1
  identityUserId: string
  idempotencyKey: string
  draft: PaymentDraft
  createdAtEpochMs: number
}

export class PaymentSubmissionStorageError
  extends Error {
  constructor(
    message: string,
    options?: ErrorOptions,
  ) {
    super(message, options)
    this.name =
      'PaymentSubmissionStorageError'
  }
}

function isPaymentDraft(
  value: unknown,
): value is PaymentDraft {
  return (
    isJsonObject(value) &&
    isUuid(value.sourceAccountId) &&
    isUuid(value.destinationAccountId) &&
    value.sourceAccountId !==
      value.destinationAccountId &&
    typeof value.amountMinorUnits ===
      'number' &&
    Number.isSafeInteger(
      value.amountMinorUnits,
    ) &&
    value.amountMinorUnits > 0
  )
}

function isPaymentSubmissionEnvelope(
  value: unknown,
): value is PaymentSubmissionEnvelope {
  return (
    isJsonObject(value) &&
    value.schemaVersion === schemaVersion &&
    isUuid(value.identityUserId) &&
    typeof value.idempotencyKey ===
      'string' &&
    /^[\x21-\x7e]{1,128}$/.test(
      value.idempotencyKey,
    ) &&
    isPaymentDraft(value.draft) &&
    typeof value.createdAtEpochMs ===
      'number' &&
    Number.isSafeInteger(
      value.createdAtEpochMs,
    ) &&
    value.createdAtEpochMs >= 0
  )
}

function sameDraft(
  left: PaymentDraft,
  right: PaymentDraft,
): boolean {
  return (
    left.sourceAccountId ===
      right.sourceAccountId &&
    left.destinationAccountId ===
      right.destinationAccountId &&
    left.amountMinorUnits ===
      right.amountMinorUnits
  )
}

function readRawEnvelope(): string | null {
  try {
    return window.sessionStorage.getItem(
      customerSessionStorageKeys
        .paymentSubmission,
    )
  } catch (error) {
    throw new PaymentSubmissionStorageError(
      'Payment retry state could not be read from this browser session.',
      {
        cause: error,
      },
    )
  }
}

function writeEnvelope(
  envelope: PaymentSubmissionEnvelope,
): void {
  try {
    window.sessionStorage.setItem(
      customerSessionStorageKeys
        .paymentSubmission,
      JSON.stringify(envelope),
    )
  } catch (error) {
    throw new PaymentSubmissionStorageError(
      'Payment retry state could not be stored in this browser session.',
      {
        cause: error,
      },
    )
  }
}

function removeEnvelope(): void {
  try {
    window.sessionStorage.removeItem(
      customerSessionStorageKeys
        .paymentSubmission,
    )
  } catch {
    // A stale envelope can only cause a safe replay.
  }
}

function readEnvelope(
  nowEpochMs: number,
): PaymentSubmissionEnvelope | null {
  const rawEnvelope = readRawEnvelope()

  if (rawEnvelope === null) {
    return null
  }

  let parsedEnvelope: unknown

  try {
    parsedEnvelope =
      JSON.parse(rawEnvelope) as unknown
  } catch {
    removeEnvelope()
    return null
  }

  if (
    !isPaymentSubmissionEnvelope(
      parsedEnvelope,
    ) ||
    parsedEnvelope.createdAtEpochMs >
      nowEpochMs ||
    nowEpochMs -
      parsedEnvelope.createdAtEpochMs >
      maximumEnvelopeAgeMs
  ) {
    removeEnvelope()
    return null
  }

  return parsedEnvelope
}

export function clearPaymentSubmissionEnvelope(): void {
  removeEnvelope()
}

export function discardPaymentSubmissionEnvelopeForDifferentUser(
  identityUserId: string,
  nowEpochMs = Date.now(),
): void {
  const envelope = readEnvelope(nowEpochMs)

  if (
    envelope !== null &&
    envelope.identityUserId !==
      identityUserId
  ) {
    removeEnvelope()
  }
}

export function getOrCreatePaymentSubmissionEnvelope(
  identityUserId: string,
  draft: PaymentDraft,
  nowEpochMs = Date.now(),
): PaymentSubmissionEnvelope {
  if (!isUuid(identityUserId)) {
    throw new TypeError(
      'Payment submission identity must be a UUID.',
    )
  }

  if (!isPaymentDraft(draft)) {
    throw new TypeError(
      'Payment submission draft is invalid.',
    )
  }

  const existingEnvelope =
    readEnvelope(nowEpochMs)

  if (
    existingEnvelope !== null &&
    existingEnvelope.identityUserId ===
      identityUserId &&
    sameDraft(
      existingEnvelope.draft,
      draft,
    )
  ) {
    return existingEnvelope
  }

  if (existingEnvelope !== null) {
    removeEnvelope()
  }

  const envelope: PaymentSubmissionEnvelope = {
    schemaVersion,
    identityUserId,
    idempotencyKey:
      globalThis.crypto.randomUUID(),
    draft: {
      ...draft,
    },
    createdAtEpochMs: nowEpochMs,
  }

  writeEnvelope(envelope)

  return envelope
}

export function resolvePaymentSubmissionEnvelope(
  idempotencyKey: string,
  nowEpochMs = Date.now(),
): void {
  const envelope = readEnvelope(nowEpochMs)

  if (
    envelope !== null &&
    envelope.idempotencyKey ===
      idempotencyKey
  ) {
    removeEnvelope()
  }
}
