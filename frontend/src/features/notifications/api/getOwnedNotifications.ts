import { apiRequestJson } from '../../../shared/api/apiClient'
import {
  isPaymentNotificationList,
  type PaymentNotification,
} from './notification'

export function getOwnedNotifications(
  signal?: AbortSignal,
): Promise<PaymentNotification[]> {
  return apiRequestJson(
    '/api/v1/notifications?limit=50',
    {
      contractName: 'Owned notifications',
      validate: isPaymentNotificationList,
      signal,
    },
  )
}
