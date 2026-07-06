import type { ReactNode } from 'react'

import { useCurrentSession } from '../hooks/useCurrentSession'
import { LoginForm } from './LoginForm'
import { UserMenu } from './UserMenu'

interface SessionBoundaryProps {
  children?: ReactNode
}

export function SessionBoundary({
  children,
}: SessionBoundaryProps) {
  const currentSession =
    useCurrentSession()

  return (
    <section
      aria-labelledby="customer-session-title"
      className="session-panel"
      id="customer-session"
    >
      <div className="section-heading">
        <p className="eyebrow">
          Customer access
        </p>

        <h2 id="customer-session-title">
          Authenticated payment workspace
        </h2>

        <p>
          Browser access uses a server-side
          session and CSRF-protected mutations.
        </p>
      </div>

      {currentSession.isPending && (
        <div
          aria-live="polite"
          className="session-card session-card--loading"
          role="status"
        >
          <span
            aria-hidden="true"
            className="status-spinner"
          />

          <div>
            <strong>
              Restoring your session
            </strong>

            <p>
              Checking whether this browser is
              already signed in.
            </p>
          </div>
        </div>
      )}

      {currentSession.isError && (
        <div
          className="status-message status-message--error"
          role="alert"
        >
          <div>
            <strong>
              Session unavailable
            </strong>

            <p>
              The platform could not safely
              restore the browser session.
            </p>
          </div>

          <button
            className="secondary-button"
            disabled={
              currentSession.isFetching
            }
            onClick={() => {
              void currentSession.refetch()
            }}
            type="button"
          >
            {currentSession.isFetching
              ? 'Trying again…'
              : 'Try again'}
          </button>
        </div>
      )}

      {currentSession.isSuccess &&
        currentSession.data === null && (
          <LoginForm />
        )}

      {currentSession.isSuccess &&
        currentSession.data !== null && (
          <>
            <UserMenu
              session={currentSession.data}
            />

            {children}
          </>
        )}
    </section>
  )
}
