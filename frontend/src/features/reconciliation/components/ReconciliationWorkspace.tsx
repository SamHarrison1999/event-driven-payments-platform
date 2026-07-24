import { SettlementDiscrepancyPanel } from './SettlementDiscrepancyPanel'
import { SettlementImportPanel } from './SettlementImportPanel'

interface ReconciliationWorkspaceProps {
  userId: string
}

export function ReconciliationWorkspace({
  userId,
}: ReconciliationWorkspaceProps) {
  return (
    <>
      <SettlementImportPanel userId={userId} />
      <SettlementDiscrepancyPanel
        userId={userId}
      />
    </>
  )
}
