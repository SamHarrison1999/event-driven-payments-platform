import {
  isJsonObject,
  isNonEmptyString,
} from '../../../shared/api/apiValidation'
import { isUuid } from '../../../shared/identifiers/uuid'

export const identityRoles = [
  'CUSTOMER',
  'OPERATIONS',
  'RECONCILIATION_ANALYST',
  'ADMIN',
] as const

export type IdentityRole =
  (typeof identityRoles)[number]

export interface IdentitySession {
  userId: string
  email: string
  roles: IdentityRole[]
}

function isIdentityRole(
  value: unknown,
): value is IdentityRole {
  return identityRoles.some(
    (role) => role === value,
  )
}

function isSessionEmail(
  value: unknown,
): value is string {
  return (
    isNonEmptyString(value) &&
    value.length <= 320 &&
    !/\s/.test(value) &&
    value.includes('@')
  )
}

export function isIdentitySession(
  value: unknown,
): value is IdentitySession {
  if (!isJsonObject(value)) {
    return false
  }

  const roles = value.roles

  if (
    !Array.isArray(roles) ||
    !roles.every(isIdentityRole)
  ) {
    return false
  }

  return (
    isUuid(value.userId) &&
    isSessionEmail(value.email) &&
    new Set(roles).size === roles.length
  )
}
