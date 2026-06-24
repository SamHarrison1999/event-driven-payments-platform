import { useSystemInfo } from '../hooks/useSystemInfo'

export function SystemStatusPanel() {
  const {
    data,
    error,
    isError,
    isFetching,
    isPending,
    refetch,
  } = useSystemInfo()

  if (isPending) {
    return (
      <section
        aria-labelledby="system-status-title"
        className="status-panel"
        id="system-status"
      >
        <div className="section-heading">
          <p className="eyebrow">Backend connection</p>
          <h2 id="system-status-title">System status</h2>
        </div>

        <div
          aria-live="polite"
          className="status-message status-message--loading"
          role="status"
        >
          <span
            aria-hidden="true"
            className="status-spinner"
          />
          Checking platform availability…
        </div>
      </section>
    )
  }

  if (isError) {
    const message =
      error instanceof Error
        ? error.message
        : 'An unexpected connection error occurred.'

    return (
      <section
        aria-labelledby="system-status-title"
        className="status-panel"
        id="system-status"
      >
        <div className="section-heading">
          <p className="eyebrow">Backend connection</p>
          <h2 id="system-status-title">System status</h2>
        </div>

        <div
          className="status-message status-message--error"
          role="alert"
        >
          <div>
            <strong>Backend unavailable</strong>
            <p>{message}</p>
          </div>

          <button
            className="secondary-button"
            disabled={isFetching}
            onClick={() => {
              void refetch()
            }}
            type="button"
          >
            {isFetching ? 'Trying again…' : 'Try again'}
          </button>
        </div>
      </section>
    )
  }

  return (
    <section
      aria-labelledby="system-status-title"
      className="status-panel"
      id="system-status"
    >
      <div className="section-heading section-heading--with-status">
        <div>
          <p className="eyebrow">Backend connection</p>
          <h2 id="system-status-title">System status</h2>
        </div>

        <span
          aria-live="polite"
          className="connection-badge"
          role="status"
        >
          <span
            aria-hidden="true"
            className="connection-badge__dot"
          />
          {isFetching ? 'Refreshing' : 'Connected'}
        </span>
      </div>

      <div className="system-summary">
        <div>
          <p className="system-summary__label">
            Running service
          </p>

          <h3>{data.name}</h3>

          <p>{data.description}</p>
        </div>

        <dl className="system-metadata">
          <div>
            <dt>Version</dt>
            <dd>{data.version}</dd>
          </div>

          <div>
            <dt>Environment</dt>
            <dd>
              {data.educational
                ? 'Educational only'
                : 'Not specified'}
            </dd>
          </div>

          <div>
            <dt>Real-money processing</dt>
            <dd>
              {data.realMoneyProcessing
                ? 'Enabled'
                : 'Disabled'}
            </dd>
          </div>
        </dl>
      </div>
    </section>
  )
}
