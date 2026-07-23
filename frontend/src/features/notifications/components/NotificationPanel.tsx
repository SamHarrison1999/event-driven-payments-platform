import { formatGbpMinorUnits } from '../../../shared/money/gbp'
import type {
  NotificationStatus,
  PaymentNotification,
} from '../api/notification'
import { useOwnedNotifications } from '../hooks/useOwnedNotifications'

const notificationDateFormatter =
  new Intl.DateTimeFormat(
    'en-GB',
    {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'UTC',
    },
  )

const notificationStatusLabels: Record<
  NotificationStatus,
  string
> = {
  PENDING: 'Pending',
  DELIVERING: 'Delivering',
  DELIVERED: 'Delivered',
  DEAD_LETTER: 'Delivery failed',
}

function paymentReference(
  paymentId: string,
): string {
  return paymentId.slice(-8).toUpperCase()
}

function formatTimestamp(
  timestamp: string,
): string {
  return notificationDateFormatter.format(
    new Date(timestamp),
  )
}

function NotificationCard({
  notification,
}: {
  notification: PaymentNotification
}) {
  const titleId =
    `notification-${notification.notificationId}-title`
  const statusLabel =
    notificationStatusLabels[notification.status]

  return (
    <li className="notification-list__item">
      <article
        aria-labelledby={titleId}
        className="notification-card"
      >
        <header className="notification-card__header">
          <div>
            <p className="notification-card__label">
              Payment completed
            </p>

            <h5 id={titleId}>
              Payment {paymentReference(
                notification.paymentId,
              )}
            </h5>
          </div>

          <span
            aria-label={`Status: ${statusLabel}`}
            className={
              'notification-status ' +
              `notification-status--${notification.status.toLowerCase()}`
            }
          >
            {statusLabel}
          </span>
        </header>

        <strong className="notification-card__amount">
          {formatGbpMinorUnits(
            notification.amountMinorUnits,
          )}
        </strong>

        <dl className="notification-metadata">
          <div>
            <dt>Completed (UTC)</dt>
            <dd>
              <time
                dateTime={
                  notification.paymentCompletedAt
                }
              >
                {formatTimestamp(
                  notification.paymentCompletedAt,
                )}
              </time>
            </dd>
          </div>

          <div>
            <dt>Notification created (UTC)</dt>
            <dd>
              <time dateTime={notification.createdAt}>
                {formatTimestamp(
                  notification.createdAt,
                )}
              </time>
            </dd>
          </div>
        </dl>
      </article>
    </li>
  )
}

export function NotificationPanel() {
  const notificationsQuery =
    useOwnedNotifications()

  let statusText = 'Loading'

  if (notificationsQuery.isError) {
    statusText = 'Unavailable'
  } else if (notificationsQuery.isSuccess) {
    statusText =
      notificationsQuery.data.length === 1
        ? '1 notification'
        : `${notificationsQuery.data.length} notifications`
  }

  return (
    <section
      aria-labelledby="payment-notifications-title"
      className="workspace-card workspace-card--primary"
      id="payment-notifications"
    >
      <div className="workspace-card__heading">
        <div>
          <p className="workspace-card__label">
            Notifications
          </p>

          <h4 id="payment-notifications-title">
            Payment notifications
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
        These are simulated delivery records for
        completed payments addressed to your
        signed-in identity.
      </p>

      {notificationsQuery.isPending && (
        <div
          aria-live="polite"
          className="notifications-message"
          role="status"
        >
          <span
            aria-hidden="true"
            className="status-spinner"
          />

          <div>
            <strong>
              Loading notifications
            </strong>

            <p>
              Retrieving your latest simulated
              payment messages.
            </p>
          </div>
        </div>
      )}

      {notificationsQuery.isError && (
        <div
          className="status-message status-message--error"
          role="alert"
        >
          <div>
            <strong>
              Notifications unavailable
            </strong>

            <p>
              Your notification history could not
              be retrieved safely.
            </p>
          </div>

          <button
            className="secondary-button"
            disabled={
              notificationsQuery.isFetching
            }
            onClick={() => {
              void notificationsQuery.refetch()
            }}
            type="button"
          >
            {notificationsQuery.isFetching
              ? 'Trying again…'
              : 'Try again'}
          </button>
        </div>
      )}

      {notificationsQuery.isSuccess &&
        notificationsQuery.data.length === 0 && (
          <div className="notifications-empty-state">
            <span
              aria-hidden="true"
              className="notifications-empty-state__icon"
            >
              MSG
            </span>

            <div>
              <strong>
                No notifications yet
              </strong>

              <p>
                Completed-payment notifications
                will appear here after simulated
                delivery processing.
              </p>
            </div>
          </div>
        )}

      {notificationsQuery.isSuccess &&
        notificationsQuery.data.length > 0 && (
          <ul
            aria-label="Payment notifications"
            className="notification-list"
          >
            {notificationsQuery.data.map(
              (notification) => (
                <NotificationCard
                  key={
                    notification.notificationId
                  }
                  notification={notification}
                />
              ),
            )}
          </ul>
        )}
    </section>
  )
}
