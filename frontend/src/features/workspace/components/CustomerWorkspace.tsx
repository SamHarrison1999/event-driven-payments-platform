import { CustomerAccountsPanel } from '../../accounts/components/CustomerAccountsPanel'
import { useCurrentSession } from '../../identity/hooks/useCurrentSession'
import { NotificationPanel } from '../../notifications/components/NotificationPanel'
import { DeadLetterPanel } from '../../operations/components/DeadLetterPanel'
import { PaymentCreationForm } from '../../payments/components/PaymentCreationForm'
import { PaymentLookup } from '../../payments/components/PaymentLookup'
import { ReconciliationWorkspace } from '../../reconciliation/components/ReconciliationWorkspace'
import { AuditReportingWorkspace } from '../../reporting/components/AuditReportingWorkspace'

const workspaceLinks = [
  {
    href: '#customer-accounts',
    label: 'Accounts',
    description:
      'Review owned GBP accounts and balances.',
  },
  {
    href: '#payment-notifications',
    label: 'Notifications',
    description:
      'Review simulated payment delivery records.',
  },
  {
    href: '#create-payment',
    label: 'Create payment',
    description:
      'Prepare an internal account transfer.',
  },
  {
    href: '#payment-lookup',
    label: 'Payment lookup',
    description:
      'Retrieve a payment by its identifier.',
  },
  {
    href: '#outbox-dead-letters',
    label: 'Dead letters',
    description:
      'Inspect and replay failed outbox events.',
    adminOnly: true,
  },
  {
    href: '#settlement-import',
    label: 'Settlement import',
    description:
      'Upload and inspect synthetic settlement results.',
    reconciliationOnly: true,
  },
  {
    href: '#settlement-discrepancies',
    label: 'Discrepancies',
    description:
      'Review and resolve reconciliation differences.',
    reconciliationOnly: true,
  },
  {
    href: '#audit-reporting',
    label: 'Audit and reports',
    description:
      'Search evidence and export bounded operational reports.',
    reportingOnly: true,
  },
]

export function CustomerWorkspace() {
  const currentSession = useCurrentSession()
  const isAdministrator =
    currentSession.data?.roles.includes(
      'ADMIN',
    ) === true
  const isCustomer =
    currentSession.data?.roles.includes(
      'CUSTOMER',
    ) === true
  const isReconciliationUser =
    isAdministrator ||
    currentSession.data?.roles.includes(
      'RECONCILIATION_ANALYST',
    ) === true
  const isReportingUser =
    isAdministrator ||
    currentSession.data?.roles.includes(
      'OPERATIONS',
    ) === true ||
    currentSession.data?.roles.includes(
      'RECONCILIATION_ANALYST',
    ) === true
  const visibleWorkspaceLinks =
    workspaceLinks.filter(
      (link) =>
        (
          link.adminOnly !== true ||
          isAdministrator
        ) &&
        (
          link.reconciliationOnly !== true ||
          isReconciliationUser
        ) &&
        (
          link.reportingOnly !== true ||
          isReportingUser
        ) &&
        (
          link.adminOnly === true ||
          link.reconciliationOnly === true ||
          link.reportingOnly === true ||
          isCustomer
        ),
    )

  return (
    <div
      className="customer-workspace"
      id="customer-workspace"
    >
      <aside className="workspace-sidebar">
        <p className="workspace-sidebar__label">
          Workspace
        </p>

        <nav aria-label="Customer workspace">
          <ul className="workspace-navigation">
            {visibleWorkspaceLinks.map((link) => (
              <li key={link.href}>
                <a
                  aria-label={link.label}
                  href={link.href}
                >
                  <strong>{link.label}</strong>
                  <span>{link.description}</span>
                </a>
              </li>
            ))}
          </ul>
        </nav>

        <div className="workspace-safety-note">
          <strong>Simulation only</strong>

          <p>
            This workspace uses synthetic data
            and never moves real funds.
          </p>
        </div>
      </aside>

      <div className="workspace-content">
        <header className="workspace-header">
          <p className="eyebrow">
            Customer workspace
          </p>

          <h3>
            {isCustomer
              ? 'Manage simulated payments'
              : isReconciliationUser
                ? 'Review settlement operations'
                : 'Review payment operations'}
          </h3>

          <p>
            {isCustomer
              ? 'Review account information, simulated payment notifications, payment creation and previous results from one protected browser workspace.'
              : isReconciliationUser
                ? 'Import synthetic settlement data, inspect deterministic results and record attributable discrepancy decisions.'
                : 'Search immutable operational evidence and generate bounded payment reports.'}
          </p>
        </header>

        <div className="workspace-grid">
          {isCustomer && (
            <>
              <CustomerAccountsPanel />

              <NotificationPanel />

              <PaymentCreationForm />

              <PaymentLookup />
            </>
          )}

          {isAdministrator && (
            <DeadLetterPanel />
          )}

          {isReconciliationUser &&
            currentSession.data !== null &&
            currentSession.data !==
              undefined && (
              <ReconciliationWorkspace
                userId={
                  currentSession.data.userId
                }
              />
            )}

          {isReportingUser &&
            currentSession.data !== null &&
            currentSession.data !==
              undefined && (
              <AuditReportingWorkspace
                roles={
                  currentSession.data.roles
                }
                userId={
                  currentSession.data.userId
                }
              />
            )}
        </div>
      </div>
    </div>
  )
}
