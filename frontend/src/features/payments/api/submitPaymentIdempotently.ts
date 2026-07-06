import {
  ApiProblemError,
} from '../../../shared/api/apiProblem'
import type { PaymentDraft } from '../model/paymentDraft'
import {
  getOrCreatePaymentSubmissionEnvelope,
  resolvePaymentSubmissionEnvelope,
} from '../idempotency/paymentSubmissionEnvelope'
import {
  type CompletedPaymentSubmission,
  submitPayment,
} from './paymentSubmission'

const terminalProblemCodes = new Set([
  'IDEMPOTENCY_KEY_REUSED',
  'PAYMENT_SOURCE_NOT_OWNED',
  'PAYMENT_SOURCE_NOT_FOUND',
  'PAYMENT_DESTINATION_NOT_FOUND',
  'PAYMENT_SOURCE_NOT_ACTIVE',
  'PAYMENT_DESTINATION_NOT_ACTIVE',
  'PAYMENT_CURRENCY_MISMATCH',
  'PAYMENT_INSUFFICIENT_FUNDS',
  'PAYMENT_PROCESSING_FAILED',
  'PAYMENT_CONCURRENT_MODIFICATION',
])

function isTerminalPaymentSubmissionProblem(
  error: unknown,
): boolean {
  return (
    error instanceof ApiProblemError &&
    typeof error.problem.code ===
      'string' &&
    terminalProblemCodes.has(
      error.problem.code,
    )
  )
}

function resolveEnvelopeWithoutMasking(
  idempotencyKey: string,
): void {
  try {
    resolvePaymentSubmissionEnvelope(
      idempotencyKey,
    )
  } catch {
    // Cleanup failure must not replace a confirmed outcome.
  }
}

export function isRetryablePaymentSubmissionError(
  error: unknown,
): boolean {
  return !isTerminalPaymentSubmissionProblem(
    error,
  )
}

export async function submitPaymentIdempotently(
  identityUserId: string,
  draft: PaymentDraft,
): Promise<CompletedPaymentSubmission> {
  const envelope =
    getOrCreatePaymentSubmissionEnvelope(
      identityUserId,
      draft,
    )

  try {
    const response = await submitPayment(
      envelope.draft,
      envelope.idempotencyKey,
    )

    resolveEnvelopeWithoutMasking(
      envelope.idempotencyKey,
    )

    return response
  } catch (error) {
    if (
      isTerminalPaymentSubmissionProblem(
        error,
      )
    ) {
      resolveEnvelopeWithoutMasking(
        envelope.idempotencyKey,
      )
    }

    throw error
  }
}
