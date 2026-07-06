import {
  isJsonObject,
} from '../../../shared/api/apiValidation'
import { isUuid } from '../../../shared/identifiers/uuid'

export const paymentStatuses = [
  'PENDING',
  'PROCESSING',
  'COMPLETED',
  'REJECTED',
  'FAILED',
] as const

export type PaymentStatus =
  (typeof paymentStatuses)[number]

export const paymentRejectionReasons = [
  'PAYMENT_SOURCE_NOT_OWNED',
  'PAYMENT_SOURCE_NOT_FOUND',
  'PAYMENT_DESTINATION_NOT_FOUND',
  'PAYMENT_SOURCE_NOT_ACTIVE',
  'PAYMENT_DESTINATION_NOT_ACTIVE',
  'PAYMENT_CURRENCY_MISMATCH',
  'PAYMENT_INSUFFICIENT_FUNDS',
] as const

export type PaymentRejectionReason =
  (typeof paymentRejectionReasons)[number]

export const paymentFailureReasons = [
  'PAYMENT_PROCESSING_FAILED',
  'PAYMENT_CONCURRENT_MODIFICATION',
] as const

export type PaymentFailureReason =
  (typeof paymentFailureReasons)[number]

export interface PaymentReceiptData {
  paymentId: string
  sourceAccountId: string
  destinationAccountId: string
  amountMinorUnits: number
  currency: 'GBP'
  status: PaymentStatus
  ledgerTransactionId?: string
  rejectionReason?: PaymentRejectionReason
  failureReason?: PaymentFailureReason
  createdAt?: string
  updatedAt?: string
  version?: number
}

export interface PaymentDetails
  extends PaymentReceiptData {
  createdAt: string
  updatedAt: string
  version: number
}

function isOneOf<T extends string>(
  value: unknown,
  values: readonly T[],
): value is T {
  return (
    typeof value === 'string' &&
    values.some(
      (candidate) => candidate === value,
    )
  )
}

function isPositiveSafeInteger(
  value: unknown,
): value is number {
  return (
    typeof value === 'number' &&
    Number.isSafeInteger(value) &&
    value > 0
  )
}

function isNonNegativeSafeInteger(
  value: unknown,
): value is number {
  return (
    typeof value === 'number' &&
    Number.isSafeInteger(value) &&
    value >= 0
  )
}

function isInstant(
  value: unknown,
): value is string {
  return (
    typeof value === 'string' &&
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(
      value,
    ) &&
    !Number.isNaN(Date.parse(value))
  )
}

function hasExpectedOutcome(
  value: Record<string, unknown>,
): boolean {
  switch (value.status) {
    case 'COMPLETED':
      return (
        isUuid(value.ledgerTransactionId) &&
        value.rejectionReason === undefined &&
        value.failureReason === undefined
      )

    case 'REJECTED':
      return (
        value.ledgerTransactionId === undefined &&
        isOneOf(
          value.rejectionReason,
          paymentRejectionReasons,
        ) &&
        value.failureReason === undefined
      )

    case 'FAILED':
      return (
        value.ledgerTransactionId === undefined &&
        value.rejectionReason === undefined &&
        isOneOf(
          value.failureReason,
          paymentFailureReasons,
        )
      )

    case 'PENDING':
    case 'PROCESSING':
      return (
        value.ledgerTransactionId === undefined &&
        value.rejectionReason === undefined &&
        value.failureReason === undefined
      )

    default:
      return false
  }
}

export function isPaymentDetails(
  value: unknown,
): value is PaymentDetails {
  if (!isJsonObject(value)) {
    return false
  }

  if (
    !isUuid(value.paymentId) ||
    !isUuid(value.sourceAccountId) ||
    !isUuid(value.destinationAccountId) ||
    value.sourceAccountId ===
      value.destinationAccountId ||
    !isPositiveSafeInteger(
      value.amountMinorUnits,
    ) ||
    value.currency !== 'GBP' ||
    !isOneOf(
      value.status,
      paymentStatuses,
    ) ||
    !isInstant(value.createdAt) ||
    !isInstant(value.updatedAt) ||
    !isNonNegativeSafeInteger(
      value.version,
    ) ||
    Date.parse(value.updatedAt) <
      Date.parse(value.createdAt)
  ) {
    return false
  }

  return hasExpectedOutcome(value)
}
