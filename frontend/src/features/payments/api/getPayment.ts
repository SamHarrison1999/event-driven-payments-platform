import { apiRequestJson } from '../../../shared/api/apiClient'
import {
  isPaymentDetails,
  type PaymentDetails,
} from '../model/payment'

export function getPayment(
  paymentId: string,
  signal?: AbortSignal,
): Promise<PaymentDetails> {
  return apiRequestJson(
    `/api/v1/payments/${encodeURIComponent(paymentId)}`,
    {
      contractName: 'Payment lookup',
      expectedStatus: 200,
      validate: isPaymentDetails,
      signal,
    },
  )
}
