import type { SettlementDiscrepancyStatus } from '../api/settlement'

export const reconciliationQueryKeys = {
  all: (userId: string) =>
    ['reconciliation', userId] as const,
  results: (
    userId: string,
    importId: string,
  ) =>
    [
      'reconciliation',
      userId,
      'imports',
      importId,
      'results',
    ] as const,
  discrepancyQueues: (userId: string) =>
    [
      'reconciliation',
      userId,
      'discrepancies',
    ] as const,
  discrepancies: (
    userId: string,
    status: SettlementDiscrepancyStatus,
  ) =>
    [
      'reconciliation',
      userId,
      'discrepancies',
      status,
    ] as const,
  discrepancy: (
    userId: string,
    discrepancyId: string,
  ) =>
    [
      'reconciliation',
      userId,
      'discrepancy',
      discrepancyId,
    ] as const,
}
