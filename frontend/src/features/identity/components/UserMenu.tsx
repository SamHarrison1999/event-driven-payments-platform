import type { IdentitySession } from '../api/identitySession'
import { useLogout } from '../hooks/useLogout'

interface UserMenuProps {
  session: IdentitySession
}

export function UserMenu({
  session,
}: UserMenuProps) {
  const logoutMutation = useLogout()

  return (
    <div className="session-card session-card--authenticated">
      <div className="session-user">
        <div>
          <p className="eyebrow">
            Authenticated customer session
          </p>

          <h3>Signed in</h3>

          <p className="session-user__email">
            {session.email}
          </p>
        </div>

        <button
          className="secondary-button"
          disabled={logoutMutation.isPending}
          onClick={() => {
            logoutMutation.mutate()
          }}
          type="button"
        >
          {logoutMutation.isPending
            ? 'Signing out…'
            : 'Sign out'}
        </button>
      </div>

      <dl className="session-metadata">
        <div>
          <dt>User identifier</dt>
          <dd>{session.userId}</dd>
        </div>

        <div>
          <dt>Roles</dt>
          <dd>{session.roles.join(', ')}</dd>
        </div>
      </dl>

      {logoutMutation.isError && (
        <div
          aria-live="assertive"
          className="form-error-summary"
          role="alert"
        >
          <strong>
            Sign out could not be completed
          </strong>

          <p>
            Your session may still be active.
            Try signing out again.
          </p>
        </div>
      )}
    </div>
  )
}
