import {
  type FormEvent,
  useEffect,
  useRef,
  useState,
} from 'react'

import { isApiErrorWithStatus } from '../../../shared/api/apiError'
import { ApiNetworkError } from '../../../shared/api/apiClient'
import type { LoginCredentials } from '../api/login'
import { useLogin } from '../hooks/useLogin'

interface LoginValidationErrors {
  email?: string
  password?: string
}

function validateCredentials(
  email: string,
  password: string,
): LoginValidationErrors {
  const errors: LoginValidationErrors = {}

  if (email.length === 0) {
    errors.email = 'Enter your email address.'
  } else if (email.length > 320) {
    errors.email =
      'Email address must not exceed 320 characters.'
  } else if (
    !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
  ) {
    errors.email =
      'Enter a valid email address.'
  }

  if (password.length === 0) {
    errors.password = 'Enter your password.'
  } else if (password.length > 128) {
    errors.password =
      'Password must not exceed 128 characters.'
  }

  return errors
}

function getLoginErrorMessage(
  error: unknown,
): string {
  if (isApiErrorWithStatus(error, 401)) {
    return 'Email or password was not accepted.'
  }

  if (error instanceof ApiNetworkError) {
    return (
      'Sign in could not reach the platform. ' +
      'Check the connection and try again.'
    )
  }

  return (
    'Sign in could not be completed safely. ' +
    'Try again.'
  )
}

export function LoginForm() {
  const loginMutation = useLogin()
  const [email, setEmail] = useState('')
  const [password, setPassword] =
    useState('')
  const [
    validationErrors,
    setValidationErrors,
  ] = useState<LoginValidationErrors>({})
  const errorSummaryRef =
    useRef<HTMLDivElement>(null)

  const hasValidationErrors =
    Object.keys(validationErrors).length > 0

  useEffect(() => {
    if (
      hasValidationErrors ||
      loginMutation.isError
    ) {
      errorSummaryRef.current?.focus()
    }
  }, [
    hasValidationErrors,
    loginMutation.isError,
  ])

  function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ): void {
    event.preventDefault()
    loginMutation.reset()

    const normalizedEmail = email.trim()
    const errors = validateCredentials(
      normalizedEmail,
      password,
    )

    setValidationErrors(errors)

    if (Object.keys(errors).length > 0) {
      return
    }

    const credentials: LoginCredentials = {
      email: normalizedEmail,
      password,
    }

    loginMutation.mutate(
      credentials,
      {
        onSettled: () => {
          setPassword('')
        },
      },
    )
  }

  return (
    <div className="session-card">
      <div className="session-card__heading">
        <p className="eyebrow">
          Secure customer session
        </p>

        <h3>Sign in</h3>

        <p>
          Use a customer account for this
          educational payment simulation.
        </p>
      </div>

      {(hasValidationErrors ||
        loginMutation.isError) && (
        <div
          aria-live="assertive"
          className="form-error-summary"
          ref={errorSummaryRef}
          role="alert"
          tabIndex={-1}
        >
          <strong>
            Sign in needs your attention
          </strong>

          {loginMutation.isError && (
            <p>
              {getLoginErrorMessage(
                loginMutation.error,
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
          <label htmlFor="login-email">
            Email address
          </label>

          <input
            aria-describedby={
              validationErrors.email
                ? 'login-email-error'
                : undefined
            }
            aria-invalid={
              validationErrors.email
                ? 'true'
                : undefined
            }
            autoComplete="username"
            id="login-email"
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
              id="login-email-error"
            >
              {validationErrors.email}
            </p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="login-password">
            Password
          </label>

          <input
            aria-describedby={
              validationErrors.password
                ? 'login-password-error'
                : undefined
            }
            aria-invalid={
              validationErrors.password
                ? 'true'
                : undefined
            }
            autoComplete="current-password"
            id="login-password"
            maxLength={128}
            name="password"
            onChange={(event) => {
              setPassword(event.target.value)
            }}
            type="password"
            value={password}
          />

          {validationErrors.password && (
            <p
              className="form-field__error"
              id="login-password-error"
            >
              {validationErrors.password}
            </p>
          )}
        </div>

        <button
          className="primary-button"
          disabled={loginMutation.isPending}
          type="submit"
        >
          {loginMutation.isPending
            ? 'Signing in…'
            : 'Sign in'}
        </button>
      </form>
    </div>
  )
}
