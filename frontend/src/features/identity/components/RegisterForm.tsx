import {
  type FormEvent,
  useEffect,
  useRef,
  useState,
} from 'react'

import { isApiErrorWithStatus } from '../../../shared/api/apiError'
import { ApiNetworkError } from '../../../shared/api/apiClient'
import { useRegister } from '../hooks/useRegister'

interface RegistrationValidationErrors {
  email?: string
  password?: string
  confirmPassword?: string
}

function validateRegistration(
  email: string,
  password: string,
  confirmPassword: string,
): RegistrationValidationErrors {
  const errors: RegistrationValidationErrors = {}

  if (email.length === 0) {
    errors.email = 'Enter your email address.'
  } else if (email.length > 320) {
    errors.email =
      'Email address must not exceed 320 characters.'
  } else if (
    !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
  ) {
    errors.email = 'Enter a valid email address.'
  }

  if (password.length === 0) {
    errors.password = 'Enter a password.'
  } else if (password.length < 15) {
    errors.password =
      'Password must contain at least 15 characters.'
  } else if (password.length > 128) {
    errors.password =
      'Password must not exceed 128 characters.'
  }

  if (confirmPassword.length === 0) {
    errors.confirmPassword =
      'Confirm your password.'
  } else if (password !== confirmPassword) {
    errors.confirmPassword =
      'Passwords do not match.'
  }

  return errors
}

function getRegistrationErrorMessage(
  error: unknown,
): string {
  if (isApiErrorWithStatus(error, 409)) {
    return (
      'An account with this email already exists. ' +
      'Use the sign-in form instead.'
    )
  }

  if (isApiErrorWithStatus(error, 400)) {
    return (
      'The account details were not accepted. ' +
      'Check the fields and try again.'
    )
  }

  if (error instanceof ApiNetworkError) {
    return (
      'Account creation could not reach the platform. ' +
      'Check the connection and try again.'
    )
  }

  return (
    'Account creation could not be completed safely. ' +
    'Try again.'
  )
}

export function RegisterForm() {
  const registerMutation = useRegister()

  const [email, setEmail] = useState('')
  const [password, setPassword] =
    useState('')
  const [
    confirmPassword,
    setConfirmPassword,
  ] = useState('')

  const [
    validationErrors,
    setValidationErrors,
  ] =
    useState<RegistrationValidationErrors>({})

  const messageRef =
    useRef<HTMLDivElement>(null)

  const hasValidationErrors =
    Object.keys(validationErrors).length > 0

  useEffect(() => {
    if (
      hasValidationErrors ||
      registerMutation.isError ||
      registerMutation.isSuccess
    ) {
      messageRef.current?.focus()
    }
  }, [
    hasValidationErrors,
    registerMutation.isError,
    registerMutation.isSuccess,
  ])

  function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ): void {
    event.preventDefault()

    registerMutation.reset()

    const normalizedEmail = email.trim()

    const errors = validateRegistration(
      normalizedEmail,
      password,
      confirmPassword,
    )

    setValidationErrors(errors)

    if (Object.keys(errors).length > 0) {
      return
    }

    registerMutation.mutate(
      {
        email: normalizedEmail,
        password,
      },
      {
        onSuccess: () => {
          setPassword('')
          setConfirmPassword('')
        },
      },
    )
  }

  return (
    <div className="session-card">
      <div className="session-card__heading">
        <p className="eyebrow">
          New customer
        </p>

        <h3>Create account</h3>

        <p>
          Create a customer account for this
          educational payment simulation.
        </p>
      </div>

      {registerMutation.isSuccess && (
        <div
          aria-live="polite"
          className="form-success-summary"
          ref={messageRef}
          role="status"
          tabIndex={-1}
        >
          <strong>Account created</strong>

          <p>
            Your account is ready. Use the
            sign-in form to access the payment
            workspace.
          </p>
        </div>
      )}

      {(hasValidationErrors ||
        registerMutation.isError) && (
        <div
          aria-live="assertive"
          className="form-error-summary"
          ref={messageRef}
          role="alert"
          tabIndex={-1}
        >
          <strong>
            Account creation needs your attention
          </strong>

          {registerMutation.isError && (
            <p>
              {getRegistrationErrorMessage(
                registerMutation.error,
              )}
            </p>
          )}

          {hasValidationErrors && (
            <p>
              Correct the highlighted fields
              and try again.
            </p>
          )}
        </div>
      )}

      <form
        className="login-form"
        noValidate
        onSubmit={handleSubmit}
      >
        <div className="form-field">
          <label htmlFor="register-email">
            Email address
          </label>

          <input
            aria-describedby={
              validationErrors.email
                ? 'register-email-error'
                : undefined
            }
            aria-invalid={
              validationErrors.email
                ? 'true'
                : undefined
            }
            autoComplete="email"
            id="register-email"
            maxLength={320}
            name="email"
            onChange={(event) => {
              setEmail(event.target.value)
            }}
            type="email"
            value={email}
          />

          {validationErrors.email && (
            <p
              className="form-field__error"
              id="register-email-error"
            >
              {validationErrors.email}
            </p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="register-password">
            Password
          </label>

          <input
            aria-describedby={
              validationErrors.password
                ? 'register-password-error'
                : 'register-password-help'
            }
            aria-invalid={
              validationErrors.password
                ? 'true'
                : undefined
            }
            autoComplete="new-password"
            id="register-password"
            maxLength={128}
            name="password"
            onChange={(event) => {
              setPassword(event.target.value)
            }}
            type="password"
            value={password}
          />

          <p
            className="form-field__help"
            id="register-password-help"
          >
            Use between 15 and 128 characters.
          </p>

          {validationErrors.password && (
            <p
              className="form-field__error"
              id="register-password-error"
            >
              {validationErrors.password}
            </p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="register-confirm-password">
            Confirm password
          </label>

          <input
            aria-describedby={
              validationErrors.confirmPassword
                ? 'register-confirm-password-error'
                : undefined
            }
            aria-invalid={
              validationErrors.confirmPassword
                ? 'true'
                : undefined
            }
            autoComplete="new-password"
            id="register-confirm-password"
            maxLength={128}
            name="confirmPassword"
            onChange={(event) => {
              setConfirmPassword(
                event.target.value,
              )
            }}
            type="password"
            value={confirmPassword}
          />

          {validationErrors.confirmPassword && (
            <p
              className="form-field__error"
              id="register-confirm-password-error"
            >
              {
                validationErrors.confirmPassword
              }
            </p>
          )}
        </div>

        <button
          className="primary-button"
          disabled={registerMutation.isPending}
          type="submit"
        >
          {registerMutation.isPending
            ? 'Creating account…'
            : 'Create account'}
        </button>
      </form>
    </div>
  )
}