import {
  useEffect,
  useId,
  useRef,
  useState,
} from 'react'
import type {
  ChangeEvent,
  FormEvent,
} from 'react'

import type { CustomerAccount } from '../../accounts/api/customerAccount'
import { useOwnedAccounts } from '../../accounts/hooks/useOwnedAccounts'
import { useCurrentSession } from '../../identity/hooks/useCurrentSession'
import {
  ApiProblemError,
} from '../../../shared/api/apiProblem'
import { formatGbpMinorUnits } from '../../../shared/money/gbp'
import {
  clearPaymentSubmissionEnvelope,
  discardPaymentSubmissionEnvelopeForDifferentUser,
} from '../idempotency/paymentSubmissionEnvelope'
import {
  isRetryablePaymentSubmissionError,
} from '../api/submitPaymentIdempotently'
import { useSubmitPayment } from '../hooks/useSubmitPayment'
import { PaymentAmountInput } from './PaymentAmountInput'
import {
  type PaymentDraft,
  type PaymentDraftErrors,
  type PaymentDraftFields,
  validatePaymentDraft,
} from '../model/paymentDraft'

const initialFields: PaymentDraftFields = {
  sourceAccountId: '',
  destinationAccountId: '',
  amount: '',
}

function accountReference(
  accountId: string,
): string {
  return accountId.slice(-4).toUpperCase()
}

function accountOptionLabel(
  account: CustomerAccount,
): string {
  return (
    `Account ending ${accountReference(account.id)}` +
    ` — ${formatGbpMinorUnits(account.balanceMinorUnits)}`
  )
}

function submissionProblemDetail(
  error: unknown,
): string {
  if (error instanceof ApiProblemError) {
    return error.problem.detail
  }

  return (
    'The browser could not confirm the result. ' +
    'Retry safely to reuse the same protected request key.'
  )
}

function submissionProblemTitle(
  error: unknown,
): string {
  if (
    error instanceof ApiProblemError &&
    error.problem.code ===
      'IDEMPOTENCY_REQUEST_IN_PROGRESS'
  ) {
    return 'Payment is still processing'
  }

  if (error instanceof ApiProblemError) {
    return error.problem.title
  }

  return 'Payment result not confirmed'
}

function PaymentReview({
  draft,
  accounts,
  onEdit,
}: {
  draft: PaymentDraft
  accounts: CustomerAccount[]
  onEdit: (resetFields: boolean) => void
}) {
  const submission = useSubmitPayment()
  const sourceAccount = accounts.find(
    (account) =>
      account.id === draft.sourceAccountId,
  )
  const destinationAccount = accounts.find(
    (account) =>
      account.id ===
      draft.destinationAccountId,
  )

  if (
    sourceAccount === undefined ||
    destinationAccount === undefined
  ) {
    return null
  }

  const retryableError =
    submission.isError &&
    isRetryablePaymentSubmissionError(
      submission.error,
    )

  return (
    <div className="payment-review">
      <div>
        <p className="workspace-card__label">
          Confirmation
        </p>

        <h5>Review payment draft</h5>

        <p>
          Check the exact account route and
          amount before this draft is submitted.
        </p>
      </div>

      <dl className="payment-review__details">
        <div>
          <dt>From</dt>
          <dd>
            Account ending{' '}
            {accountReference(
              sourceAccount.id,
            )}
          </dd>
        </div>

        <div>
          <dt>To</dt>
          <dd>
            Account ending{' '}
            {accountReference(
              destinationAccount.id,
            )}
          </dd>
        </div>

        <div>
          <dt>Amount</dt>
          <dd>
            {formatGbpMinorUnits(
              draft.amountMinorUnits,
            )}
          </dd>
        </div>
      </dl>

      {!submission.isSuccess && (
        <div
          aria-live="polite"
          className="payment-review__notice"
          role="status"
        >
          <strong>
            The payment has not been submitted
          </strong>

          <p>
            This review step has not moved or
            reserved any funds.
          </p>
        </div>
      )}

      {submission.isError && (
        <div
          className="status-message status-message--error"
          role="alert"
        >
          <div>
            <strong>
              {submissionProblemTitle(
                submission.error,
              )}
            </strong>

            <p>
              {submissionProblemDetail(
                submission.error,
              )}
            </p>
          </div>
        </div>
      )}

      {submission.isSuccess && (
        <div
          aria-live="polite"
          className="payment-submission-success"
          role="status"
        >
          <strong>Payment completed</strong>

          <p>
            The backend completed the payment
            and returned payment identifier{' '}
            <code>
              {submission.data.paymentId}
            </code>
            .
          </p>
        </div>
      )}

      <div className="payment-review__actions">
        {!submission.isSuccess &&
          (
            !submission.isError ||
            retryableError
          ) && (
          <button
            className="primary-button"
            disabled={
              submission.isPending ||
              !submission.canSubmit
            }
            onClick={() => {
              submission.mutate(draft)
            }}
            type="button"
          >
            {submission.isPending
              ? 'Submitting payment…'
              : retryableError
                ? 'Retry payment'
                : 'Submit payment'}
          </button>
        )}

        <button
          className="secondary-button"
          disabled={submission.isPending}
          onClick={() => {
            submission.reset()
            clearPaymentSubmissionEnvelope()
            onEdit(submission.isSuccess)
          }}
          type="button"
        >
          {submission.isSuccess
            ? 'Create another payment'
            : 'Edit payment details'}
        </button>
      </div>

      {retryableError && (
        <p className="payment-review__retry-note">
          Retrying reuses the same idempotency
          key for this exact draft.
        </p>
      )}
    </div>
  )
}

export function PaymentCreationForm() {
  const accountsQuery = useOwnedAccounts()
  const currentSession = useCurrentSession()
  const idPrefix = useId()
  const errorSummaryRef =
    useRef<HTMLDivElement>(null)
  const [fields, setFields] =
    useState(initialFields)
  const [
    validationRequested,
    setValidationRequested,
  ] = useState(false)
  const [
    errorFocusRequest,
    setErrorFocusRequest,
  ] = useState(0)
  const [reviewDraft, setReviewDraft] =
    useState<PaymentDraft | null>(null)

  const identityUserId =
    currentSession.data?.userId

  useEffect(() => {
    if (identityUserId !== undefined) {
      discardPaymentSubmissionEnvelopeForDifferentUser(
        identityUserId,
      )
    }
  }, [identityUserId])

  const accounts =
    accountsQuery.data ?? []
  const activeAccounts = accounts.filter(
    (account) =>
      account.status === 'ACTIVE',
  )

  const validation =
    validatePaymentDraft(fields, accounts)
  const errors: PaymentDraftErrors =
    validationRequested && !validation.ok
      ? validation.errors
      : {}

  const updateField = (
    field: keyof PaymentDraftFields,
    value: string,
  ) => {
    setFields((current) => ({
      ...current,
      [field]: value,
      ...(field === 'sourceAccountId' &&
      value === current.destinationAccountId
        ? {
            destinationAccountId: '',
          }
        : {}),
    }))
    setReviewDraft(null)
  }

  const handleSourceChange = (
    event: ChangeEvent<HTMLSelectElement>,
  ) => {
    updateField(
      'sourceAccountId',
      event.target.value,
    )
  }

  const handleDestinationChange = (
    event: ChangeEvent<HTMLSelectElement>,
  ) => {
    updateField(
      'destinationAccountId',
      event.target.value,
    )
  }

  const handleReview = (
    event: FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault()

    const currentValidation =
      validatePaymentDraft(fields, accounts)

    if (!currentValidation.ok) {
      setReviewDraft(null)
      setValidationRequested(true)
      setErrorFocusRequest(
        (current) => current + 1,
      )
      return
    }

    setValidationRequested(false)
    setReviewDraft(
      currentValidation.draft,
    )
  }

  const destinationAccounts =
    activeAccounts.filter(
      (account) =>
        account.id !==
        fields.sourceAccountId,
    )

  useEffect(() => {
    if (errorFocusRequest > 0) {
      errorSummaryRef.current?.focus()
    }
  }, [errorFocusRequest])

  const sourceErrorId =
    `${idPrefix}-source-error`
  const destinationErrorId =
    `${idPrefix}-destination-error`
  const sourceHintId =
    `${idPrefix}-source-hint`
  const destinationHintId =
    `${idPrefix}-destination-hint`

  return (
    <section
      aria-labelledby="create-payment-title"
      className="workspace-card"
      id="create-payment"
    >
      <p className="workspace-card__label">
        Payment
      </p>

      <h4 id="create-payment-title">
        Create an internal payment
      </h4>

      <p>
        Choose two active owned accounts and
        prepare an exact positive GBP payment
        draft.
      </p>

      {accountsQuery.isPending && (
        <div
          aria-live="polite"
          className="payment-form-state"
          role="status"
        >
          <span
            aria-hidden="true"
            className="status-spinner"
          />

          <div>
            <strong>
              Preparing payment accounts
            </strong>
            <p>
              Checking which accounts can be
              selected.
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
              Payment setup unavailable
            </strong>

            <p>
              Restore the account information
              above before preparing a payment.
            </p>
          </div>
        </div>
      )}

      {accountsQuery.isSuccess &&
        activeAccounts.length < 2 && (
          <div className="payment-form-state">
            <div>
              <strong>
                Two active accounts are required
              </strong>

              <p>
                A payment needs one active source
                account and a different active
                destination account.
              </p>
            </div>
          </div>
        )}

      {accountsQuery.isSuccess &&
        activeAccounts.length >= 2 &&
        reviewDraft !== null && (
          <PaymentReview
            accounts={accounts}
            draft={reviewDraft}
            onEdit={(resetFields) => {
              if (resetFields) {
                setFields(initialFields)
              }

              setReviewDraft(null)
            }}
          />
        )}

      {accountsQuery.isSuccess &&
        activeAccounts.length >= 2 &&
        reviewDraft === null && (
          <form
            className="payment-form"
            noValidate
            onSubmit={handleReview}
          >
            {validationRequested &&
              !validation.ok && (
                <div
                  className="form-error-summary"
                  ref={errorSummaryRef}
                  role="alert"
                  tabIndex={-1}
                >
                  <strong>
                    Review the payment details
                  </strong>

                  <p>
                    Correct the highlighted fields
                    before continuing.
                  </p>
                </div>
              )}

            <div className="payment-form__accounts">
              <div className="form-field">
                <label
                  htmlFor={`${idPrefix}-source`}
                >
                  Source account
                </label>

                <select
                  aria-describedby={[
                    sourceHintId,
                    errors.sourceAccountId
                      ? sourceErrorId
                      : null,
                  ]
                    .filter(Boolean)
                    .join(' ')}
                  aria-invalid={
                    errors.sourceAccountId
                      ? true
                      : undefined
                  }
                  id={`${idPrefix}-source`}
                  name="sourceAccountId"
                  onChange={handleSourceChange}
                  value={fields.sourceAccountId}
                >
                  <option value="">
                    Choose source account
                  </option>

                  {activeAccounts.map(
                    (account) => (
                      <option
                        key={account.id}
                        value={account.id}
                      >
                        {accountOptionLabel(
                          account,
                        )}
                      </option>
                    ),
                  )}
                </select>

                <p
                  className="form-field__hint"
                  id={sourceHintId}
                >
                  Funds are taken from this
                  active account.
                </p>

                {errors.sourceAccountId && (
                  <p
                    className="form-field__error"
                    id={sourceErrorId}
                  >
                    {errors.sourceAccountId}
                  </p>
                )}
              </div>

              <div className="form-field">
                <label
                  htmlFor={`${idPrefix}-destination`}
                >
                  Destination account
                </label>

                <select
                  aria-describedby={[
                    destinationHintId,
                    errors.destinationAccountId
                      ? destinationErrorId
                      : null,
                  ]
                    .filter(Boolean)
                    .join(' ')}
                  aria-invalid={
                    errors.destinationAccountId
                      ? true
                      : undefined
                  }
                  id={`${idPrefix}-destination`}
                  name="destinationAccountId"
                  onChange={
                    handleDestinationChange
                  }
                  value={
                    fields.destinationAccountId
                  }
                >
                  <option value="">
                    Choose destination account
                  </option>

                  {destinationAccounts.map(
                    (account) => (
                      <option
                        key={account.id}
                        value={account.id}
                      >
                        {accountOptionLabel(
                          account,
                        )}
                      </option>
                    ),
                  )}
                </select>

                <p
                  className="form-field__hint"
                  id={destinationHintId}
                >
                  The source account cannot also
                  be the destination.
                </p>

                {errors.destinationAccountId && (
                  <p
                    className="form-field__error"
                    id={destinationErrorId}
                  >
                    {
                      errors.destinationAccountId
                    }
                  </p>
                )}
              </div>
            </div>

            <PaymentAmountInput
              externalError={
                errors.amount ?? null
              }
              onChange={(value) => {
                updateField('amount', value)
              }}
              validationRequested={
                validationRequested
              }
              value={fields.amount}
            />

            <div className="payment-form__actions">
              <button
                className="primary-button"
                type="submit"
              >
                Review payment
              </button>

              <p>
                Reviewing does not submit the
                payment or move funds.
              </p>
            </div>
          </form>
        )}
    </section>
  )
}
