import {
  isJsonObject,
  isNonEmptyString,
} from '../../../shared/api/apiValidation'
import { isUuid } from '../../../shared/identifiers/uuid'

export const settlementResultOutcomes = [
  'MATCHED',
  'DISCREPANCY',
] as const

export type SettlementResultOutcome =
  (typeof settlementResultOutcomes)[number]

export const settlementDiscrepancyCodes = [
  'PAYMENT_NOT_FOUND',
  'PAYMENT_NOT_COMPLETED',
  'CURRENCY_MISMATCH',
  'AMOUNT_MISMATCH',
  'SETTLED_BEFORE_COMPLETION',
  'DUPLICATE_PAYMENT_SETTLEMENT',
] as const

export type SettlementDiscrepancyCode =
  (typeof settlementDiscrepancyCodes)[number]

export const settlementDiscrepancyStatuses = [
  'OPEN',
  'RESOLVED',
] as const

export type SettlementDiscrepancyStatus =
  (typeof settlementDiscrepancyStatuses)[number]

export const settlementResolutionDecisions = [
  'ACCEPTED',
  'INTERNAL_CORRECTION_REQUIRED',
  'EXTERNAL_CORRECTION_REQUIRED',
] as const

export type SettlementResolutionDecision =
  (typeof settlementResolutionDecisions)[number]

export interface SettlementImport {
  importId: string
  existingImport: boolean
  status: 'COMPLETED'
  originalFilename: string
  rawFileSha256: string
  rawFileSizeBytes: number
  rowCount: number
  matchedCount: number
  discrepancyCount: number
  createdAt: string
  completedAt: string
}

export interface SettlementResult {
  rowNumber: number
  settlementRecordId: string
  paymentId: string
  amountMinorUnits: number
  currency: 'GBP'
  settledAt: string
  outcome: SettlementResultOutcome
  discrepancyCode:
    | SettlementDiscrepancyCode
    | null
  reconciledAt: string
}

export interface SettlementResultPage {
  results: SettlementResult[]
  nextAfterRowNumber: number | null
}

export interface SettlementResolution {
  resolutionId: string
  actorIdentityUserId: string
  decision: SettlementResolutionDecision
  reason: string
  discrepancyVersion: number
  decidedAt: string
}

export interface SettlementDiscrepancy {
  discrepancyId: string
  importId: string
  rowNumber: number
  settlementRecordId: string
  paymentId: string
  amountMinorUnits: number
  currency: 'GBP'
  settledAt: string
  code: SettlementDiscrepancyCode
  status: SettlementDiscrepancyStatus
  createdAt: string
  version: number
  resolution: SettlementResolution | null
}

export interface SettlementDiscrepancyPage {
  discrepancies: SettlementDiscrepancy[]
  nextAfterCreatedAt: string | null
  nextAfterId: string | null
}

export interface SettlementDiscrepancyDetail {
  discrepancy: SettlementDiscrepancy
  etag: string
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
    isNonNegativeSafeInteger(value) &&
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

function isSettlementRecordId(
  value: unknown,
): value is string {
  return (
    typeof value === 'string' &&
    /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/.test(
      value,
    )
  )
}

function isResolutionReason(
  value: unknown,
): value is string {
  return (
    isNonEmptyString(value) &&
    value === value.trim() &&
    value.length <= 500 &&
    Array.from(value).every(
      (character) => {
        const codePoint =
          character.codePointAt(0) ?? 0

        return (
          codePoint >= 32 &&
          codePoint !== 127
        )
      },
    )
  )
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

function isSettlementDiscrepancyCode(
  value: unknown,
): value is SettlementDiscrepancyCode {
  return isEnumValue(
    settlementDiscrepancyCodes,
    value,
  )
}

function isSettlementResolution(
  value: unknown,
): value is SettlementResolution {
  return (
    isJsonObject(value) &&
    isUuid(value.resolutionId) &&
    isUuid(value.actorIdentityUserId) &&
    isEnumValue(
      settlementResolutionDecisions,
      value.decision,
    ) &&
    isResolutionReason(value.reason) &&
    isNonNegativeSafeInteger(
      value.discrepancyVersion,
    ) &&
    isInstant(value.decidedAt)
  )
}

export function isSettlementImport(
  value: unknown,
): value is SettlementImport {
  return (
    isJsonObject(value) &&
    isUuid(value.importId) &&
    typeof value.existingImport ===
      'boolean' &&
    value.status === 'COMPLETED' &&
    isNonEmptyString(
      value.originalFilename,
    ) &&
    typeof value.rawFileSha256 ===
      'string' &&
    /^[a-f0-9]{64}$/.test(
      value.rawFileSha256,
    ) &&
    isPositiveSafeInteger(
      value.rawFileSizeBytes,
    ) &&
    isPositiveSafeInteger(value.rowCount) &&
    isNonNegativeSafeInteger(
      value.matchedCount,
    ) &&
    isNonNegativeSafeInteger(
      value.discrepancyCount,
    ) &&
    value.matchedCount +
      value.discrepancyCount ===
      value.rowCount &&
    isInstant(value.createdAt) &&
    isInstant(value.completedAt)
  )
}

export function isSettlementResult(
  value: unknown,
): value is SettlementResult {
  if (
    !isJsonObject(value) ||
    !isPositiveSafeInteger(value.rowNumber) ||
    !isSettlementRecordId(
      value.settlementRecordId,
    ) ||
    !isUuid(value.paymentId) ||
    !isPositiveSafeInteger(
      value.amountMinorUnits,
    ) ||
    value.currency !== 'GBP' ||
    !isInstant(value.settledAt) ||
    !isEnumValue(
      settlementResultOutcomes,
      value.outcome,
    ) ||
    !isInstant(value.reconciledAt)
  ) {
    return false
  }

  return value.outcome === 'MATCHED'
    ? value.discrepancyCode === null
    : isSettlementDiscrepancyCode(
        value.discrepancyCode,
      )
}

export function isSettlementResultPage(
  value: unknown,
): value is SettlementResultPage {
  return (
    isJsonObject(value) &&
    Array.isArray(value.results) &&
    value.results.every(isSettlementResult) &&
    (
      value.nextAfterRowNumber === null ||
      isPositiveSafeInteger(
        value.nextAfterRowNumber,
      )
    )
  )
}

export function isSettlementDiscrepancy(
  value: unknown,
): value is SettlementDiscrepancy {
  if (
    !isJsonObject(value) ||
    !isUuid(value.discrepancyId) ||
    !isUuid(value.importId) ||
    !isPositiveSafeInteger(value.rowNumber) ||
    !isSettlementRecordId(
      value.settlementRecordId,
    ) ||
    !isUuid(value.paymentId) ||
    !isPositiveSafeInteger(
      value.amountMinorUnits,
    ) ||
    value.currency !== 'GBP' ||
    !isInstant(value.settledAt) ||
    !isSettlementDiscrepancyCode(
      value.code,
    ) ||
    !isEnumValue(
      settlementDiscrepancyStatuses,
      value.status,
    ) ||
    !isInstant(value.createdAt) ||
    !isNonNegativeSafeInteger(value.version)
  ) {
    return false
  }

  if (value.status === 'OPEN') {
    return value.resolution === null
  }

  return (
    isSettlementResolution(
      value.resolution,
    ) &&
    value.version ===
      value.resolution.discrepancyVersion + 1
  )
}

export function isSettlementDiscrepancyPage(
  value: unknown,
): value is SettlementDiscrepancyPage {
  if (
    !isJsonObject(value) ||
    !Array.isArray(value.discrepancies) ||
    !value.discrepancies.every(
      isSettlementDiscrepancy,
    )
  ) {
    return false
  }

  const hasNextCreatedAt =
    value.nextAfterCreatedAt !== null
  const hasNextId = value.nextAfterId !== null

  return (
    hasNextCreatedAt === hasNextId &&
    (
      !hasNextCreatedAt ||
      (
        isInstant(value.nextAfterCreatedAt) &&
        isUuid(value.nextAfterId)
      )
    )
  )
}
