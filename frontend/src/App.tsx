import './App.css'

import { SessionBoundary } from './features/identity/components/SessionBoundary'
import { SystemStatusPanel } from './features/system/components/SystemStatusPanel'
import { CustomerWorkspace } from './features/workspace/components/CustomerWorkspace'

const foundations = [
  {
    title: 'Event-driven workflows',
    description:
      'Model payment state changes as explicit, traceable domain events.',
  },
  {
    title: 'Double-entry ledger',
    description:
      'Preserve financial consistency through balanced debit and credit entries.',
  },
  {
    title: 'Settlement reconciliation',
    description:
      'Compare internal payment records against simulated external settlement data.',
  },
]

function App() {
  return (
    <div className="application">
      <header className="site-header">
        <a
          aria-label="Payments Platform home"
          className="brand"
          href="#overview"
        >
          <span
            aria-hidden="true"
            className="brand__mark"
          >
            EP
          </span>

          <span>
            <strong>Payments Platform</strong>
            <small>Engineering sandbox</small>
          </span>
        </a>

        <nav aria-label="Primary navigation">
          <a href="#overview">Overview</a>
          <a href="#customer-session">
            Customer workspace
          </a>
          <a href="#foundations">Foundations</a>
          <a href="#system-status">
            System status
          </a>
        </nav>
      </header>

      <main>
        <section
          className="hero"
          id="overview"
        >
          <div className="hero__content">
            <p className="eyebrow">
              Event-driven payments and reconciliation
            </p>

            <h1>Payments operations workspace</h1>

            <p className="hero__summary">
              A full-stack engineering project demonstrating
              payment orchestration, ledger integrity,
              event processing and settlement reconciliation.
            </p>

            <div className="hero__notices">
              <span className="notice-pill">
                Simulation environment
              </span>

              <span className="notice-pill">
                No real funds
              </span>

              <span className="notice-pill">
                Synthetic data only
              </span>
            </div>
          </div>

          <aside
            aria-label="Project scope"
            className="hero__scope-card"
          >
            <p className="scope-card__label">
              Current milestone
            </p>

            <strong>
              Durable notifications and recovery
            </strong>

            <p>
              Demonstrating duplicate-safe event
              consumption, simulated notification
              delivery and audited dead-letter replay.
            </p>

            <div className="scope-card__progress">
              <span>Delivery progress</span>
              <strong>Phase 8</strong>
            </div>
          </aside>
        </section>

        <SessionBoundary>
          <CustomerWorkspace />
        </SessionBoundary>

        <section
          aria-labelledby="foundations-title"
          className="foundations"
          id="foundations"
        >
          <div className="section-heading">
            <p className="eyebrow">Architecture</p>

            <h2 id="foundations-title">
              Core platform foundations
            </h2>

            <p>
              The project is being built incrementally around
              explicit financial and operational guarantees.
            </p>
          </div>

          <div className="foundation-grid">
            {foundations.map((foundation, index) => (
              <article
                className="foundation-card"
                key={foundation.title}
              >
                <span
                  aria-hidden="true"
                  className="foundation-card__number"
                >
                  {String(index + 1).padStart(2, '0')}
                </span>

                <h3>{foundation.title}</h3>
                <p>{foundation.description}</p>
              </article>
            ))}
          </div>
        </section>

        <SystemStatusPanel />
      </main>

      <footer className="site-footer">
        <p>
          Educational software engineering project. This
          application does not process real money.
        </p>
      </footer>
    </div>
  )
}

export default App
