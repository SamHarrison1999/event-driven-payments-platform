import { CustomerAccountsPanel } from '../../accounts/components/CustomerAccountsPanel'
import { PaymentCreationForm } from '../../payments/components/PaymentCreationForm'
import { PaymentLookup } from '../../payments/components/PaymentLookup'

const workspaceLinks = [
  {
    href: '#customer-accounts',
    label: 'Accounts',
    description:
      'Review owned GBP accounts and balances.',
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
]

export function CustomerWorkspace() {
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
            {workspaceLinks.map((link) => (
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

          <h3>Manage simulated payments</h3>

          <p>
            Review account information, create
            an internal payment and retrieve a
            previous result from one protected
            browser workspace.
          </p>
        </header>

        <div className="workspace-grid">
          <CustomerAccountsPanel />

          <PaymentCreationForm />

          <PaymentLookup />
        </div>
      </div>
    </div>
  )
}
