import {
  isJsonObject,
} from '../../../shared/api/apiValidation'
import { isUuid } from '../../../shared/identifiers/uuid'

export const notificationStatuses = [
  'PENDING',
  'DELIVERING',
  'DELIVERED',
  'DEAD_LETTER',
] as const

export type NotificationStatus =
  (typeof notificationStatuses)[number]

export interface PaymentNotification {
  notificationId: string
  paymentId: string
  amountMinorUnits: number
  currency: 'GBP'
  paymentCompletedAt: string
  status: NotificationStatus
  createdAt: string
  deliveredAt: string | null
}

function isNotificationStatus(
  value: unknown,
): value is NotificationStatus {
  return notificationStatuses.some(
    (status) => status === value,
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

export function isPaymentNotification(
  value: unknown,
): value is PaymentNotification {
  return (
    isJsonObject(value) &&
    isUuid(value.notificationId) &&
    isUuid(value.paymentId) &&
    isPositiveSafeInteger(
      value.amountMinorUnits,
    ) &&
    value.currency === 'GBP' &&
    isInstant(value.paymentCompletedAt) &&
    isNotificationStatus(value.status) &&
    isInstant(value.createdAt) &&
    (
      value.deliveredAt === null ||
      isInstant(value.deliveredAt)
    )
  )
}

export function isPaymentNotificationList(
  value: unknown,
): value is PaymentNotification[] {
  return (
    Array.isArray(value) &&
    value.every(isPaymentNotification)
  )
}
