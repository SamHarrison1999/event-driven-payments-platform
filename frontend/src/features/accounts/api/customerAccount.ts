import {
  isJsonObject,
} from '../../../shared/api/apiValidation'
import { isUuid } from '../../../shared/identifiers/uuid'

export const accountStatuses = [
  'ACTIVE',
  'FROZEN',
  'CLOSED',
] as const

export type AccountStatus =
  (typeof accountStatuses)[number]

export interface CustomerAccount {
  id: string
  customerId: string
  currency: 'GBP'
  balanceMinorUnits: number
  status: AccountStatus
  createdAt: string
  updatedAt: string
  version: number
}

function isAccountStatus(
  value: unknown,
): value is AccountStatus {
  return accountStatuses.some(
    (status) => status === value,
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

export function isCustomerAccount(
  value: unknown,
): value is CustomerAccount {
  return (
    isJsonObject(value) &&
    isUuid(value.id) &&
    isUuid(value.customerId) &&
    value.currency === 'GBP' &&
    isNonNegativeSafeInteger(
      value.balanceMinorUnits,
    ) &&
    isAccountStatus(value.status) &&
    isInstant(value.createdAt) &&
    isInstant(value.updatedAt) &&
    isNonNegativeSafeInteger(value.version)
  )
}

export function isCustomerAccountList(
  value: unknown,
): value is CustomerAccount[] {
  return (
    Array.isArray(value) &&
    value.every(isCustomerAccount)
  )
}
