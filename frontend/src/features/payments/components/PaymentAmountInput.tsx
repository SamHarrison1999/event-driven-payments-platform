import {
  useId,
  useState,
} from 'react'

import {
  formatGbpMinorUnits,
  parsePositiveGbpAmount,
} from '../../../shared/money/gbp'

interface PaymentAmountInputProps {
  value: string
  onChange: (value: string) => void
  validationRequested?: boolean
  externalError?: string | null
}

export function PaymentAmountInput({
  value,
  onChange,
  validationRequested = false,
  externalError = null,
}: PaymentAmountInputProps) {
  const inputId = useId()
  const hintId = `${inputId}-hint`
  const errorId = `${inputId}-error`
  const previewId = `${inputId}-preview`
  const [touched, setTouched] =
    useState(false)

  const parsedAmount =
    parsePositiveGbpAmount(value)
  const parserError =
    !parsedAmount.ok
      ? parsedAmount.message
      : null
  const error =
    externalError ??
    (touched || validationRequested
      ? parserError
      : null)
  const formattedAmount =
    parsedAmount.ok
      ? formatGbpMinorUnits(
          parsedAmount.minorUnits,
        )
      : null

  const describedBy = [
    hintId,
    error !== null ? errorId : null,
    formattedAmount !== null
      ? previewId
      : null,
  ]
    .filter(
      (id): id is string => id !== null,
    )
    .join(' ')

  return (
    <div className="payment-amount-input">
      <div className="form-field">
        <label htmlFor={inputId}>
          Payment amount
        </label>

        <div className="currency-input">
          <span aria-hidden="true">£</span>

          <input
            aria-describedby={describedBy}
            aria-invalid={
              error === null
                ? undefined
                : true
            }
            autoComplete="off"
            id={inputId}
            inputMode="decimal"
            name="paymentAmount"
            onBlur={() => {
              setTouched(true)
            }}
            onChange={(event) => {
              onChange(event.target.value)
            }}
            placeholder="0.00"
            type="text"
            value={value}
          />
        </div>

        <p
          className="form-field__hint"
          id={hintId}
        >
          Enter pounds and pence, for example
          10.50.
        </p>

        {error !== null && (
          <p
            className="form-field__error"
            id={errorId}
            role="alert"
          >
            {error}
          </p>
        )}
      </div>

      <div
        aria-live="polite"
        className="payment-amount-preview"
        id={previewId}
      >
        <span>Exact amount</span>

        <strong>
          {formattedAmount ?? '£0.00'}
        </strong>

        <p>
          {parsedAmount.ok
            ? `${parsedAmount.minorUnits} minor units`
            : 'Validated without floating-point conversion.'}
        </p>
      </div>
    </div>
  )
}
