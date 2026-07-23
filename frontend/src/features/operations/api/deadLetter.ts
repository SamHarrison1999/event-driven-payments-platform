import {
  isJsonObject,
  isNonEmptyString,
} from '../../../shared/api/apiValidation'
import { isUuid } from '../../../shared/identifiers/uuid'

export const outboxEventStatuses = [
  'PENDING',
  'PUBLISHING',
  'PUBLISHED',
  'DEAD_LETTER',
] as const

export type OutboxEventStatus =
  (typeof outboxEventStatuses)[number]

export interface OutboxDeadLetter {
  eventId: string
  aggregateType: string
  aggregateId: string
  eventType: string
  schemaVersion: number
  payload: string
  correlationIdentifier: string
  causationIdentifier: string | null
  createdAt: string
  updatedAt: string
  status: OutboxEventStatus
  attemptCount: number
  lastErrorCategory: string | null
  lastErrorMessage: string | null
  replayCount: number
  lastReplayedAt: string | null
  version: number
}

export interface OutboxReplayResult {
  event: OutboxDeadLetter
  replayAuditId: string
  replayedAt: string
}

function isOutboxEventStatus(
  value: unknown,
): value is OutboxEventStatus {
  return outboxEventStatuses.some(
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

function isNullableString(
  value: unknown,
): value is string | null {
  return (
    value === null ||
    isNonEmptyString(value)
  )
}

function isNullableInstant(
  value: unknown,
): value is string | null {
  return (
    value === null ||
    isInstant(value)
  )
}

export function isOutboxDeadLetter(
  value: unknown,
): value is OutboxDeadLetter {
  return (
    isJsonObject(value) &&
    isUuid(value.eventId) &&
    isNonEmptyString(value.aggregateType) &&
    isUuid(value.aggregateId) &&
    isNonEmptyString(value.eventType) &&
    isPositiveSafeInteger(
      value.schemaVersion,
    ) &&
    isNonEmptyString(value.payload) &&
    isNonEmptyString(
      value.correlationIdentifier,
    ) &&
    isNullableString(
      value.causationIdentifier,
    ) &&
    isInstant(value.createdAt) &&
    isInstant(value.updatedAt) &&
    isOutboxEventStatus(value.status) &&
    isNonNegativeSafeInteger(
      value.attemptCount,
    ) &&
    isNullableString(
      value.lastErrorCategory,
    ) &&
    isNullableString(
      value.lastErrorMessage,
    ) &&
    isNonNegativeSafeInteger(
      value.replayCount,
    ) &&
    isNullableInstant(
      value.lastReplayedAt,
    ) &&
    isNonNegativeSafeInteger(value.version)
  )
}

export function isOutboxDeadLetterList(
  value: unknown,
): value is OutboxDeadLetter[] {
  return (
    Array.isArray(value) &&
    value.every(isOutboxDeadLetter)
  )
}

export function isOutboxReplayResult(
  value: unknown,
): value is OutboxReplayResult {
  return (
    isJsonObject(value) &&
    isOutboxDeadLetter(value.event) &&
    isUuid(value.replayAuditId) &&
    isInstant(value.replayedAt)
  )
}
