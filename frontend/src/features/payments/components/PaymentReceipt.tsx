import { useId } from 'react'

import { formatGbpMinorUnits } from '../../../shared/money/gbp'
import type {
  PaymentFailureReason,
  PaymentReceiptData,
  PaymentRejectionReason,
  PaymentStatus,
} from '../model/payment'

const statusDetails: Record<
  PaymentStatus,
  {
    title: string
    label: string
    message: string
  }
> = {
  PENDING: {
    title: 'Payment pending',
    label: 'Pending',
    message:
      'The payment has been accepted but processing has not started.',
  },
  PROCESSING: {
    title: 'Payment processing',
    label: 'Processing',
    message:
      'The payment is currently being processed. Look it up again for the latest result.',
  },
  COMPLETED: {
    title: 'Payment completed',
    label: 'Completed',
    message:
      'The simulated transfer completed successfully.',
  },
  REJECTED: {
    title: 'Payment rejected',
    label: 'Rejected',
    message:
      'The payment was rejected and no simulated funds were moved.',
  },
  FAILED: {
    title: 'Payment failed',
    label: 'Failed',
    message:
      'The payment could not be completed.',
  },
}

const rejectionLabels: Record<
  PaymentRejectionReason,
  string
> = {
  PAYMENT_SOURCE_NOT_OWNED:
    'Source account is not owned by this customer',
  PAYMENT_SOURCE_NOT_FOUND:
    'Source account was not found',
  PAYMENT_DESTINATION_NOT_FOUND:
    'Destination account was not found',
  PAYMENT_SOURCE_NOT_ACTIVE:
    'Source account is not active',
  PAYMENT_DESTINATION_NOT_ACTIVE:
    'Destination account is not active',
  PAYMENT_CURRENCY_MISMATCH:
    'Both accounts must use GBP',
  PAYMENT_INSUFFICIENT_FUNDS:
    'Source account has insufficient funds',
}

const failureLabels: Record<
  PaymentFailureReason,
  string
> = {
  PAYMENT_PROCESSING_FAILED:
    'Payment processing failed',
  PAYMENT_CONCURRENT_MODIFICATION:
    'Accounts changed concurrently',
}

function accountReference(
  accountId: string,
): string {
  return accountId.slice(-4).toUpperCase()
}

function formatInstant(
  value: string,
): string {
  return (
    new Intl.DateTimeFormat(
      'en-GB',
      {
        dateStyle: 'medium',
        timeStyle: 'short',
        timeZone: 'UTC',
      },
    ).format(new Date(value)) +
    ' UTC'
  )
}

export function PaymentReceipt({
  payment,
}: {
  payment: PaymentReceiptData
}) {
  const titleId = useId()
  const details =
    statusDetails[payment.status]

  return (
    <article
      aria-labelledby={titleId}
      aria-live="polite"
      className={
        `payment-receipt ` +
        `payment-receipt--${payment.status.toLowerCase()}`
      }
    >
      <header className="payment-receipt__header">
        <div>
          <p className="workspace-card__label">
            Receipt
          </p>

          <h5 id={titleId}>
            {details.title}
          </h5>
        </div>

        <span className="payment-receipt__status">
          {details.label}
        </span>
      </header>

      <p className="payment-receipt__message">
        {details.message}
      </p>

      <dl className="payment-receipt__details">
        <div>
          <dt>Amount</dt>
          <dd>
            {formatGbpMinorUnits(
              payment.amountMinorUnits,
            )}
          </dd>
        </div>

        <div>
          <dt>From</dt>
          <dd>
            Account ending{' '}
            {accountReference(
              payment.sourceAccountId,
            )}
          </dd>
        </div>

        <div>
          <dt>To</dt>
          <dd>
            Account ending{' '}
            {accountReference(
              payment.destinationAccountId,
            )}
          </dd>
        </div>

        <div>
          <dt>Payment ID</dt>
          <dd>
            <code>{payment.paymentId}</code>
          </dd>
        </div>

        {payment.ledgerTransactionId && (
          <div>
            <dt>Ledger transaction</dt>
            <dd>
              <code>
                {payment.ledgerTransactionId}
              </code>
            </dd>
          </div>
        )}

        {payment.rejectionReason && (
          <div>
            <dt>Rejection reason</dt>
            <dd>
              {
                rejectionLabels[
                  payment.rejectionReason
                ]
              }
            </dd>
          </div>
        )}

        {payment.failureReason && (
          <div>
            <dt>Failure reason</dt>
            <dd>
              {
                failureLabels[
                  payment.failureReason
                ]
              }
            </dd>
          </div>
        )}

        {payment.createdAt && (
          <div>
            <dt>Created</dt>
            <dd>
              <time
                dateTime={payment.createdAt}
              >
                {formatInstant(
                  payment.createdAt,
                )}
              </time>
            </dd>
          </div>
        )}

        {payment.updatedAt && (
          <div>
            <dt>Last updated</dt>
            <dd>
              <time
                dateTime={payment.updatedAt}
              >
                {formatInstant(
                  payment.updatedAt,
                )}
              </time>
            </dd>
          </div>
        )}

        {payment.version !== undefined && (
          <div>
            <dt>Record version</dt>
            <dd>{payment.version}</dd>
          </div>
        )}
      </dl>
    </article>
  )
}
