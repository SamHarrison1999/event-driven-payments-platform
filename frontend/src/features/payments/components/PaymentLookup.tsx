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

import {
  ApiProblemError,
} from '../../../shared/api/apiProblem'
import { isUuid } from '../../../shared/identifiers/uuid'
import { usePaymentLookup } from '../hooks/usePaymentLookup'
import { PaymentReceipt } from './PaymentReceipt'

function lookupErrorTitle(
  error: unknown,
): string {
  if (
    error instanceof ApiProblemError &&
    error.problem.code ===
      'PAYMENT_NOT_FOUND'
  ) {
    return 'Payment not found'
  }

  if (
    error instanceof ApiProblemError &&
    error.status === 401
  ) {
    return 'Session expired'
  }

  if (error instanceof ApiProblemError) {
    return error.problem.title
  }

  return 'Payment lookup unavailable'
}

function lookupErrorDetail(
  error: unknown,
): string {
  if (
    error instanceof ApiProblemError &&
    error.problem.code ===
      'PAYMENT_NOT_FOUND'
  ) {
    return (
      'No customer-owned payment is available ' +
      'for this identifier.'
    )
  }

  if (
    error instanceof ApiProblemError &&
    error.status === 401
  ) {
    return (
      'Sign in again before retrieving a payment.'
    )
  }

  if (error instanceof ApiProblemError) {
    return error.problem.detail
  }

  return (
    'The browser could not retrieve the payment. ' +
    'Check the connection and try again.'
  )
}

export function PaymentLookup() {
  const lookup = usePaymentLookup()
  const idPrefix = useId()
  const inputRef =
    useRef<HTMLInputElement>(null)
  const errorRef =
    useRef<HTMLDivElement>(null)
  const [
    paymentId,
    setPaymentId,
  ] = useState('')
  const [
    validationRequested,
    setValidationRequested,
  ] = useState(false)
  const [
    focusRequest,
    setFocusRequest,
  ] = useState(0)

  const normalizedPaymentId =
    paymentId.trim()

  const validationError =
    validationRequested &&
    normalizedPaymentId.length === 0
      ? 'Enter a payment identifier.'
      : validationRequested &&
          !isUuid(normalizedPaymentId)
        ? 'Enter a valid payment UUID.'
        : null

  useEffect(() => {
    if (focusRequest > 0) {
      inputRef.current?.focus()
    }
  }, [focusRequest])

  useEffect(() => {
    if (lookup.isError) {
      errorRef.current?.focus()
    }
  }, [lookup.isError])

  const handleChange = (
    event: ChangeEvent<HTMLInputElement>,
  ) => {
    setPaymentId(event.target.value)
    lookup.reset()
  }

  const handleSubmit = (
    event: FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault()
    setValidationRequested(true)

    if (!isUuid(normalizedPaymentId)) {
      setFocusRequest(
        (current) => current + 1,
      )
      return
    }

    lookup.mutate(normalizedPaymentId)
  }

  const hintId = `${idPrefix}-hint`
  const errorId = `${idPrefix}-error`

  return (
    <section
      aria-busy={lookup.isPending}
      aria-labelledby="payment-lookup-title"
      className="workspace-card"
      id="payment-lookup"
    >
      <p className="workspace-card__label">
        Retrieval
      </p>

      <h4 id="payment-lookup-title">
        Find a payment
      </h4>

      <p>
        Retrieve a customer-owned payment by
        UUID and review its terminal or
        in-progress status.
      </p>

      <form
        className="payment-lookup-form"
        noValidate
        onSubmit={handleSubmit}
      >
        <div className="form-field">
          <label htmlFor={`${idPrefix}-id`}>
            Payment identifier
          </label>

          <input
            aria-describedby={[
              hintId,
              validationError
                ? errorId
                : null,
            ]
              .filter(Boolean)
              .join(' ')}
            aria-invalid={
              validationError
                ? true
                : undefined
            }
            autoComplete="off"
            disabled={lookup.isPending}
            id={`${idPrefix}-id`}
            name="paymentId"
            onChange={handleChange}
            placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            ref={inputRef}
            spellCheck={false}
            value={paymentId}
          />

          <p
            className="form-field__hint"
            id={hintId}
          >
            Use the full UUID returned with the
            payment receipt.
          </p>

          {validationError && (
            <p
              className="form-field__error"
              id={errorId}
            >
              {validationError}
            </p>
          )}
        </div>

        <button
          className="primary-button"
          disabled={lookup.isPending}
          type="submit"
        >
          {lookup.isPending
            ? 'Finding payment…'
            : 'Find payment'}
        </button>
      </form>

      {lookup.isError && (
        <div
          className="status-message status-message--error"
          ref={errorRef}
          role="alert"
          tabIndex={-1}
        >
          <div>
            <strong>
              {lookupErrorTitle(
                lookup.error,
              )}
            </strong>

            <p>
              {lookupErrorDetail(
                lookup.error,
              )}
            </p>
          </div>
        </div>
      )}

      {lookup.isSuccess && (
        <PaymentReceipt
          focusOnMount
          payment={lookup.data}
        />
      )}
    </section>
  )
}
