import {
  isInteger,
  isJsonObject,
} from '../../../shared/api/apiValidation'

export interface PaymentOperationalSummary {
  submittedCount: number
  terminalCount: number
  completedCount: number
  rejectedCount: number
  failedCount: number
  completedAmountMinorUnits: number
  rejectionCodeCounts: Record<string, number>
  failureCodeCounts: Record<string, number>
}

export interface SettlementOperationalSummary {
  acceptedImportCount: number
  acceptedRowCount: number
  matchedCount: number
  discrepancyCount: number
  importOutcomeCounts: Record<string, number>
}

export interface ReconciliationOperationalSummary {
  discrepancyCodeCounts:
    Record<string, number>
  lifecycleStateCounts:
    Record<string, number>
  resolutionDecisionCounts:
    Record<string, number>
  openAgeBandCounts:
    Record<string, number>
}

export interface OperationalSummary {
  from: string
  to: string
  payment: PaymentOperationalSummary | null
  settlement:
    | SettlementOperationalSummary
    | null
  reconciliation:
    | ReconciliationOperationalSummary
    | null
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

function isCount(
  value: unknown,
): value is number {
  return (
    isInteger(value) &&
    Number.isSafeInteger(value) &&
    value >= 0
  )
}

function isCountMap(
  value: unknown,
): value is Record<string, number> {
  return (
    isJsonObject(value) &&
    Object.entries(value).every(
      ([key, count]) =>
        key.length > 0 &&
        isCount(count),
    )
  )
}

function isPaymentSummary(
  value: unknown,
): value is PaymentOperationalSummary {
  return (
    isJsonObject(value) &&
    isCount(value.submittedCount) &&
    isCount(value.terminalCount) &&
    isCount(value.completedCount) &&
    isCount(value.rejectedCount) &&
    isCount(value.failedCount) &&
    isCount(
      value.completedAmountMinorUnits,
    ) &&
    isCountMap(
      value.rejectionCodeCounts,
    ) &&
    isCountMap(value.failureCodeCounts)
  )
}

function isSettlementSummary(
  value: unknown,
): value is SettlementOperationalSummary {
  return (
    isJsonObject(value) &&
    isCount(value.acceptedImportCount) &&
    isCount(value.acceptedRowCount) &&
    isCount(value.matchedCount) &&
    isCount(value.discrepancyCount) &&
    isCountMap(value.importOutcomeCounts)
  )
}

function isReconciliationSummary(
  value: unknown,
): value is ReconciliationOperationalSummary {
  return (
    isJsonObject(value) &&
    isCountMap(
      value.discrepancyCodeCounts,
    ) &&
    isCountMap(
      value.lifecycleStateCounts,
    ) &&
    isCountMap(
      value.resolutionDecisionCounts,
    ) &&
    isCountMap(value.openAgeBandCounts)
  )
}

export function isOperationalSummary(
  value: unknown,
): value is OperationalSummary {
  return (
    isJsonObject(value) &&
    isInstant(value.from) &&
    isInstant(value.to) &&
    (
      value.payment === null ||
      isPaymentSummary(value.payment)
    ) &&
    (
      value.settlement === null ||
      isSettlementSummary(
        value.settlement,
      )
    ) &&
    (
      value.reconciliation === null ||
      isReconciliationSummary(
        value.reconciliation,
      )
    )
  )
}
