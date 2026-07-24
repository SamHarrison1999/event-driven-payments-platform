import {
  type FormEvent,
  useState,
} from 'react'

import { formatGbpMinorUnits } from '../../../shared/money/gbp'
import { useSettlementResults } from '../hooks/useSettlementResults'
import { useUploadSettlementFile } from '../hooks/useUploadSettlementFile'
import { reconciliationErrorMessage } from './reconciliationErrorMessage'

const maximumFileSizeBytes = 1_048_576

interface SettlementImportPanelProps {
  userId: string
}

export function SettlementImportPanel({
  userId,
}: SettlementImportPanelProps) {
  const [file, setFile] =
    useState<File | null>(null)
  const [selectionError, setSelectionError] =
    useState<string | null>(null)
  const uploadMutation =
    useUploadSettlementFile(userId)
  const imported =
    uploadMutation.data ?? null
  const resultsQuery = useSettlementResults(
    userId,
    imported?.importId ?? null,
  )
  const results =
    resultsQuery.data?.pages.flatMap(
      (page) => page.results,
    ) ?? []

  function submit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    if (file === null) {
      setSelectionError(
        'Select one settlement CSV file.',
      )
      return
    }

    if (
      file.size === 0 ||
      file.size > maximumFileSizeBytes
    ) {
      setSelectionError(
        'The CSV must contain data and be no larger than 1 MiB.',
      )
      return
    }

    setSelectionError(null)
    uploadMutation.mutate(file)
  }

  return (
    <section
      aria-labelledby="settlement-import-title"
      className="workspace-card workspace-card--primary settlement-panel"
      id="settlement-import"
    >
      <div className="workspace-card__heading">
        <div>
          <p className="workspace-card__label">
            Settlement ingestion
          </p>

          <h4 id="settlement-import-title">
            Import and reconcile CSV
          </h4>
        </div>

        <span className="workspace-status-pill">
          {imported === null
            ? 'Ready'
            : `${imported.rowCount} rows`}
        </span>
      </div>

      <p>
        Upload one strict synthetic GBP settlement
        file. The backend validates every row before
        atomically storing any reconciliation data.
      </p>

      <form
        className="settlement-upload-form"
        onSubmit={submit}
      >
        <div className="form-field">
          <label htmlFor="settlement-file">
            Settlement CSV
          </label>

          <input
            accept=".csv,text/csv"
            disabled={uploadMutation.isPending}
            id="settlement-file"
            onChange={(event) => {
              const selected =
                event.target.files?.[0] ?? null

              setFile(selected)
              setSelectionError(null)
              uploadMutation.reset()
            }}
            type="file"
          />

          <p className="form-field__hint">
            UTF-8 CSV, 1–1,000 rows, maximum 1 MiB.
            Synthetic data only.
          </p>
        </div>

        {(selectionError !== null ||
          uploadMutation.isError) && (
          <div
            className="form-error-summary"
            role="alert"
          >
            <strong>
              Settlement file not imported
            </strong>

            <p>
              {selectionError ??
                reconciliationErrorMessage(
                  uploadMutation.error,
                  'The settlement file could not be imported safely.',
                )}
            </p>
          </div>
        )}

        <button
          className="primary-button"
          disabled={
            file === null ||
            uploadMutation.isPending
          }
          type="submit"
        >
          {uploadMutation.isPending
            ? 'Validating and reconciling…'
            : 'Import settlement'}
        </button>
      </form>

      {imported !== null && (
        <div className="settlement-import-summary">
          <div
            aria-live="polite"
            className="settlement-success"
            role="status"
          >
            <strong>
              {imported.existingImport
                ? 'Existing import restored'
                : 'Settlement imported'}
            </strong>

            <p>
              {imported.originalFilename} completed
              with an atomic result for every row.
            </p>
          </div>

          <dl className="settlement-counts">
            <div>
              <dt>Total rows</dt>
              <dd>{imported.rowCount}</dd>
            </div>

            <div>
              <dt>Matched</dt>
              <dd>{imported.matchedCount}</dd>
            </div>

            <div>
              <dt>Discrepancies</dt>
              <dd>
                {imported.discrepancyCount}
              </dd>
            </div>
          </dl>

          <details className="settlement-evidence">
            <summary>
              Inspect immutable import evidence
            </summary>

            <dl>
              <div>
                <dt>Import identifier</dt>
                <dd>{imported.importId}</dd>
              </div>

              <div>
                <dt>Raw SHA-256</dt>
                <dd>{imported.rawFileSha256}</dd>
              </div>
            </dl>
          </details>
        </div>
      )}

      {imported !== null && (
        <div className="settlement-results">
          <h5>Reconciliation results</h5>

          {resultsQuery.isPending && (
            <div
              className="settlement-message"
              role="status"
            >
              <span
                aria-hidden="true"
                className="status-spinner"
              />
              <p>Loading immutable row results…</p>
            </div>
          )}

          {resultsQuery.isError && (
            <div
              className="status-message status-message--error"
              role="alert"
            >
              <div>
                <strong>
                  Results unavailable
                </strong>
                <p>
                  The imported row results could not
                  be retrieved safely.
                </p>
              </div>

              <button
                className="secondary-button"
                onClick={() => {
                  void resultsQuery.refetch()
                }}
                type="button"
              >
                Try again
              </button>
            </div>
          )}

          {results.length > 0 && (
            <div className="settlement-table-scroll">
              <table>
                <caption className="visually-hidden">
                  Settlement reconciliation results
                </caption>
                <thead>
                  <tr>
                    <th scope="col">Row</th>
                    <th scope="col">
                      External record
                    </th>
                    <th scope="col">Amount</th>
                    <th scope="col">Outcome</th>
                    <th scope="col">Reason</th>
                  </tr>
                </thead>
                <tbody>
                  {results.map((result) => (
                    <tr key={result.rowNumber}>
                      <td>{result.rowNumber}</td>
                      <td>
                        {result.settlementRecordId}
                      </td>
                      <td>
                        {formatGbpMinorUnits(
                          result.amountMinorUnits,
                        )}
                      </td>
                      <td>{result.outcome}</td>
                      <td>
                        {result.discrepancyCode ??
                          '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {resultsQuery.hasNextPage && (
            <button
              className="secondary-button"
              disabled={
                resultsQuery.isFetchingNextPage
              }
              onClick={() => {
                void resultsQuery.fetchNextPage()
              }}
              type="button"
            >
              {resultsQuery.isFetchingNextPage
                ? 'Loading more…'
                : 'Load more results'}
            </button>
          )}
        </div>
      )}
    </section>
  )
}
