import {
  isInteger,
  isJsonObject,
  isNonEmptyString,
} from '../../../shared/api/apiValidation'
import { isUuid } from '../../../shared/identifiers/uuid'

export const auditCategories = [
  'CUSTOMER',
  'ACCOUNT',
  'PAYMENT',
  'SETTLEMENT',
  'RECONCILIATION',
  'IDENTITY_SECURITY',
  'ADMIN_RECOVERY',
] as const

export type AuditCategory =
  (typeof auditCategories)[number]

export const auditSources = [
  'BUSINESS_AUDIT',
  'IDENTITY_SECURITY',
  'OUTBOX_REPLAY',
  'SETTLEMENT_RESOLUTION',
] as const

export type AuditSource =
  (typeof auditSources)[number]

export type AuditDetailValue =
  | string
  | number
  | boolean
  | null

export interface AuditEvent {
  eventId: string
  source: AuditSource
  category: AuditCategory
  eventType: string
  schemaVersion: number
  occurredAt: string
  actorKind: 'IDENTITY_USER' | 'SYSTEM'
  actorIdentityUserId: string | null
  subjectType: string
  subjectIdentifier: string
  correlationIdentifier: string | null
  details: Record<string, AuditDetailValue>
}

export interface AuditEventPage {
  events: AuditEvent[]
  nextCursor: string | null
}

export interface AuditSearchParameters {
  from: string
  to: string
  category?: AuditCategory
  eventType?: string
  actorIdentityUserId?: string
  subjectType?: string
  subjectIdentifier?: string
  correlationIdentifier?: string
  source?: AuditSource
  cursor?: string
  limit: number
}

function isEnumValue<
  T extends readonly string[],
>(
  values: T,
  value: unknown,
): value is T[number] {
  return values.some(
    (candidate) => candidate === value,
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

function isOptionalUuid(
  value: unknown,
): value is string | null {
  return value === null || isUuid(value)
}

function isOptionalString(
  value: unknown,
): value is string | null {
  return (
    value === null ||
    isNonEmptyString(value)
  )
}

function isDetailValue(
  value: unknown,
): value is AuditDetailValue {
  return (
    value === null ||
    typeof value === 'string' ||
    typeof value === 'boolean' ||
    (
      typeof value === 'number' &&
      Number.isSafeInteger(value)
    )
  )
}

function isAuditDetails(
  value: unknown,
): value is Record<
  string,
  AuditDetailValue
> {
  return (
    isJsonObject(value) &&
    Object.entries(value).every(
      ([key, detail]) =>
        /^[A-Za-z][A-Za-z0-9]{0,63}$/.test(
          key,
        ) &&
        isDetailValue(detail),
    )
  )
}

export function isAuditEvent(
  value: unknown,
): value is AuditEvent {
  if (!isJsonObject(value)) {
    return false
  }

  const eventIdParts =
    typeof value.eventId === 'string'
      ? value.eventId.split(':')
      : []

  return (
    eventIdParts.length === 2 &&
    isEnumValue(
      auditSources,
      eventIdParts[0],
    ) &&
    isUuid(eventIdParts[1]) &&
    isEnumValue(
      auditSources,
      value.source,
    ) &&
    value.source === eventIdParts[0] &&
    isEnumValue(
      auditCategories,
      value.category,
    ) &&
    isNonEmptyString(value.eventType) &&
    isInteger(value.schemaVersion) &&
    value.schemaVersion > 0 &&
    isInstant(value.occurredAt) &&
    (
      value.actorKind ===
        'IDENTITY_USER' ||
      value.actorKind === 'SYSTEM'
    ) &&
    isOptionalUuid(
      value.actorIdentityUserId,
    ) &&
    (
      value.actorKind !== 'SYSTEM' ||
      value.actorIdentityUserId === null
    ) &&
    isNonEmptyString(value.subjectType) &&
    isNonEmptyString(
      value.subjectIdentifier,
    ) &&
    isOptionalString(
      value.correlationIdentifier,
    ) &&
    isAuditDetails(value.details)
  )
}

export function isAuditEventPage(
  value: unknown,
): value is AuditEventPage {
  return (
    isJsonObject(value) &&
    Array.isArray(value.events) &&
    value.events.every(isAuditEvent) &&
    (
      value.nextCursor === null ||
      (
        isNonEmptyString(
          value.nextCursor,
        ) &&
        value.nextCursor.length <= 2048
      )
    )
  )
}
