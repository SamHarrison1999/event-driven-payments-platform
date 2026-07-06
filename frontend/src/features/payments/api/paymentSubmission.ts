import { apiRequestJson } from '../../../shared/api/apiClient'
import {
  isJsonObject,
} from '../../../shared/api/apiValidation'
import { getCsrfHeaders } from '../../../shared/api/csrfToken'
import { isUuid } from '../../../shared/identifiers/uuid'
import type { PaymentDraft } from '../model/paymentDraft'

export interface CompletedPaymentSubmission {
  paymentId: string
  status: 'COMPLETED'
  ledgerTransactionId: string
}

function isCompletedPaymentSubmission(
  value: unknown,
): value is CompletedPaymentSubmission {
  return (
    isJsonObject(value) &&
    isUuid(value.paymentId) &&
    value.status === 'COMPLETED' &&
    isUuid(value.ledgerTransactionId)
  )
}

export async function submitPayment(
  draft: PaymentDraft,
  idempotencyKey: string,
  signal?: AbortSignal,
): Promise<CompletedPaymentSubmission> {
  const csrfHeaders =
    await getCsrfHeaders()

  return apiRequestJson(
    '/api/v1/payments',
    {
      method: 'POST',
      headers: {
        ...csrfHeaders,
        'Idempotency-Key':
          idempotencyKey,
      },
      body: draft,
      signal,
      contractName: 'Payment submission',
      expectedStatus: 201,
      validate:
        isCompletedPaymentSubmission,
    },
  )
}
