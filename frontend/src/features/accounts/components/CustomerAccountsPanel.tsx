import type {
  AccountStatus,
  CustomerAccount,
} from '../api/customerAccount'
import { useOwnedAccounts } from '../hooks/useOwnedAccounts'
import { formatGbpMinorUnits } from '../../../shared/money/gbp'

const accountDateFormatter =
  new Intl.DateTimeFormat(
    'en-GB',
    {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'UTC',
    },
  )

const accountStatusLabels: Record<
  AccountStatus,
  string
> = {
  ACTIVE: 'Active',
  FROZEN: 'Frozen',
  CLOSED: 'Closed',
}

function accountReference(
  accountId: string,
): string {
  return accountId.slice(-4).toUpperCase()
}

function formatAccountTimestamp(
  timestamp: string,
): string {
  return accountDateFormatter.format(
    new Date(timestamp),
  )
}

function AccountCard({
  account,
}: {
  account: CustomerAccount
}) {
  const reference = accountReference(
    account.id,
  )
  const titleId =
    `account-${account.id}-title`
  const statusLabel =
    accountStatusLabels[account.status]

  return (
    <li className="account-list__item">
      <article
        aria-labelledby={titleId}
        className="account-card"
      >
        <header className="account-card__header">
          <div>
            <p className="account-card__label">
              GBP account
            </p>

            <h5 id={titleId}>
              Account ending {reference}
            </h5>
          </div>

          <span
            aria-label={`Status: ${statusLabel}`}
            className={
              'account-status ' +
              `account-status--${account.status.toLowerCase()}`
            }
          >
            {statusLabel}
          </span>
        </header>

        <div className="account-balance">
          <span>Balance</span>

          <strong>
            {formatGbpMinorUnits(
              account.balanceMinorUnits,
            )}
          </strong>
        </div>

        <dl className="account-metadata">
          <div>
            <dt>Account ID</dt>
            <dd>{account.id}</dd>
          </div>

          <div>
            <dt>Last updated (UTC)</dt>
            <dd>
              <time dateTime={account.updatedAt}>
                {formatAccountTimestamp(
                  account.updatedAt,
                )}
              </time>
            </dd>
          </div>
        </dl>
      </article>
    </li>
  )
}

export function CustomerAccountsPanel() {
  const accountsQuery = useOwnedAccounts()

  let statusText = 'Loading'

  if (accountsQuery.isError) {
    statusText = 'Unavailable'
  } else if (accountsQuery.isSuccess) {
    statusText =
      accountsQuery.data.length === 1
        ? '1 account'
        : `${accountsQuery.data.length} accounts`
  }

  return (
    <section
      aria-labelledby="customer-accounts-title"
      className="workspace-card workspace-card--primary"
      id="customer-accounts"
    >
      <div className="workspace-card__heading">
        <div>
          <p className="workspace-card__label">
            Accounts
          </p>

          <h4 id="customer-accounts-title">
            Your GBP accounts
          </h4>
        </div>

        <span
          aria-live="polite"
          className="workspace-status-pill"
        >
          {statusText}
        </span>
      </div>

      <p>
        Balances and account availability are
        retrieved securely for the signed-in
        customer.
      </p>

      {accountsQuery.isPending && (
        <div
          aria-live="polite"
          className="accounts-message"
          role="status"
        >
          <span
            aria-hidden="true"
            className="status-spinner"
          />

          <div>
            <strong>Loading accounts</strong>
            <p>
              Retrieving your latest account
              balances.
            </p>
          </div>
        </div>
      )}

      {accountsQuery.isError && (
        <div
          className="status-message status-message--error"
          role="alert"
        >
          <div>
            <strong>
              Accounts unavailable
            </strong>

            <p>
              Your account information could not
              be retrieved safely.
            </p>
          </div>

          <button
            className="secondary-button"
            disabled={accountsQuery.isFetching}
            onClick={() => {
              void accountsQuery.refetch()
            }}
            type="button"
          >
            {accountsQuery.isFetching
              ? 'Trying again…'
              : 'Try again'}
          </button>
        </div>
      )}

      {accountsQuery.isSuccess &&
        accountsQuery.data.length === 0 && (
          <div className="accounts-empty-state">
            <span
              aria-hidden="true"
              className="accounts-empty-state__icon"
            >
              GBP
            </span>

            <div>
              <strong>
                No accounts available
              </strong>

              <p>
                No GBP accounts are currently
                assigned to this customer.
              </p>
            </div>
          </div>
        )}

      {accountsQuery.isSuccess &&
        accountsQuery.data.length > 0 && (
          <ul
            aria-label="Owned GBP accounts"
            className="account-list"
          >
            {accountsQuery.data.map(
              (account) => (
                <AccountCard
                  account={account}
                  key={account.id}
                />
              ),
            )}
          </ul>
        )}
    </section>
  )
}
