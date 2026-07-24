import {
  HttpResponse,
  http,
} from 'msw'
import {
  screen,
  waitFor,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest'

import { clearCsrfToken } from '../../../shared/api/csrfToken'
import { renderWithQueryClient } from '../../../test/renderWithQueryClient'
import { server } from '../../../test/server'
import type {
  SettlementDiscrepancy,
  SettlementImport,
  SettlementResultPage,
} from '../api/settlement'
import { ReconciliationWorkspace } from './ReconciliationWorkspace'

const userId =
  'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const importId =
  '11111111-1111-4111-8111-111111111111'
const discrepancyId =
  '22222222-2222-4222-8222-222222222222'
const importEndpoint =
  'http://localhost:5173/api/v1/settlement-imports'
const discrepancyEndpoint =
  'http://localhost:5173/api/v1/settlement-discrepancies'
const csrfEndpoint =
  'http://localhost:5173/api/v1/identity/csrf'

const imported: SettlementImport = {
  importId,
  existingImport: false,
  status: 'COMPLETED',
  originalFilename: 'daily.csv',
  rawFileSha256: 'b'.repeat(64),
  rawFileSizeBytes: 240,
  rowCount: 2,
  matchedCount: 1,
  discrepancyCount: 1,
  createdAt: '2026-07-24T10:00:00Z',
  completedAt: '2026-07-24T10:00:01Z',
}

const resultPage: SettlementResultPage = {
  results: [
    {
      rowNumber: 1,
      settlementRecordId:
        'settlement-matched',
      paymentId:
        '33333333-3333-4333-8333-333333333333',
      amountMinorUnits: 2500,
      currency: 'GBP',
      settledAt:
        '2026-07-24T10:00:00Z',
      outcome: 'MATCHED',
      discrepancyCode: null,
      reconciledAt:
        '2026-07-24T10:00:01Z',
    },
    {
      rowNumber: 2,
      settlementRecordId:
        'settlement-mismatch',
      paymentId:
        '44444444-4444-4444-8444-444444444444',
      amountMinorUnits: 3000,
      currency: 'GBP',
      settledAt:
        '2026-07-24T10:00:00Z',
      outcome: 'DISCREPANCY',
      discrepancyCode:
        'AMOUNT_MISMATCH',
      reconciledAt:
        '2026-07-24T10:00:01Z',
    },
  ],
  nextAfterRowNumber: null,
}

const openDiscrepancy:
  SettlementDiscrepancy = {
    discrepancyId,
    importId,
    rowNumber: 2,
    settlementRecordId:
      'settlement-mismatch',
    paymentId:
      '44444444-4444-4444-8444-444444444444',
    amountMinorUnits: 3000,
    currency: 'GBP',
    settledAt: '2026-07-24T10:00:00Z',
    code: 'AMOUNT_MISMATCH',
    status: 'OPEN',
    createdAt: '2026-07-24T10:00:01Z',
    version: 0,
    resolution: null,
  }

beforeEach(() => {
  clearCsrfToken()

  server.use(
    http.get(csrfEndpoint, () => {
      return HttpResponse.json({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'reconciliation-csrf-token',
      })
    }),

    http.get(
      discrepancyEndpoint,
      () => {
        return HttpResponse.json({
          discrepancies: [],
          nextAfterCreatedAt: null,
          nextAfterId: null,
        })
      },
    ),
  )
})

describe('ReconciliationWorkspace', () => {
  it(
    'uploads a CSV and presents its atomic results',
    async () => {
      server.use(
        http.post(importEndpoint, () => {
          return HttpResponse.json(
            imported,
            {
              status: 201,
            },
          )
        }),

        http.get(
          `${importEndpoint}/${importId}/results`,
          ({ request }) => {
            expect(
              new URL(request.url)
                .searchParams.get(
                  'afterRowNumber',
                ),
            ).toBe('0')

            return HttpResponse.json(
              resultPage,
            )
          },
        ),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <ReconciliationWorkspace
          userId={userId}
        />,
      )

      await user.upload(
        screen.getByLabelText(
          'Settlement CSV',
        ),
        new File(
          ['header\r\nrow\r\n'],
          'daily.csv',
          {
            type: 'text/csv',
          },
        ),
      )

      await user.click(
        screen.getByRole('button', {
          name: 'Import settlement',
        }),
      )

      expect(
        await screen.findByText(
          'Settlement imported',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText('Total rows')
          .nextElementSibling,
      ).toHaveTextContent('2')

      expect(
        await screen.findByText(
          'settlement-matched',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          'AMOUNT_MISMATCH',
        ),
      ).toBeInTheDocument()
    },
  )

  it(
    'recovers from a stale ETag before resolving',
    async () => {
      let detailReads = 0
      let resolutionAttempts = 0
      const submittedEtags: string[] = []

      server.use(
        http.get(
          discrepancyEndpoint,
          () => {
            return HttpResponse.json({
              discrepancies: [
                openDiscrepancy,
              ],
              nextAfterCreatedAt: null,
              nextAfterId: null,
            })
          },
        ),

        http.get(
          `${discrepancyEndpoint}/${discrepancyId}`,
          () => {
            detailReads += 1
            const version =
              detailReads === 1 ? 0 : 1

            return HttpResponse.json(
              {
                ...openDiscrepancy,
                version,
              },
              {
                headers: {
                  ETag: `"${version}"`,
                },
              },
            )
          },
        ),

        http.put(
          `${discrepancyEndpoint}/${discrepancyId}/resolution`,
          async ({ request }) => {
            resolutionAttempts += 1
            submittedEtags.push(
              request.headers.get(
                'If-Match',
              ) ?? '',
            )

            if (resolutionAttempts === 1) {
              return HttpResponse.json(
                {
                  type:
                    'urn:problem:reconciliation:discrepancy-version-conflict',
                  title:
                    'Settlement discrepancy version conflict',
                  status: 412,
                  detail:
                    'The settlement discrepancy has version 1.',
                  code:
                    'SETTLEMENT_DISCREPANCY_VERSION_CONFLICT',
                },
                {
                  status: 412,
                  headers: {
                    'Content-Type':
                      'application/problem+json',
                  },
                },
              )
            }

            return HttpResponse.json({
              ...openDiscrepancy,
              status: 'RESOLVED',
              version: 2,
              resolution: {
                resolutionId:
                  '55555555-5555-4555-8555-555555555555',
                actorIdentityUserId: userId,
                decision: 'ACCEPTED',
                reason:
                  'External settlement evidence accepted.',
                discrepancyVersion: 1,
                decidedAt:
                  '2026-07-24T10:05:00Z',
              },
            })
          },
        ),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <ReconciliationWorkspace
          userId={userId}
        />,
      )

      await user.click(
        await screen.findByRole(
          'button',
          {
            name: /AMOUNT_MISMATCH/,
          },
        ),
      )

      const reason =
        'External settlement evidence accepted.'

      await user.type(
        await screen.findByRole(
          'textbox',
          {
            name: 'Resolution reason',
          },
        ),
        reason,
      )

      await user.click(
        screen.getByRole('button', {
          name: 'Resolve discrepancy',
        }),
      )

      expect(
        await screen.findByRole('alert'),
      ).toHaveTextContent(
        'This discrepancy changed after it was loaded.',
      )

      await waitFor(() => {
        expect(detailReads).toBeGreaterThanOrEqual(
          2,
        )
      })

      await user.click(
        screen.getByRole('button', {
          name: 'Resolve discrepancy',
        }),
      )

      expect(
        await screen.findByText(reason),
      ).toBeInTheDocument()

      expect(submittedEtags).toEqual([
        '"0"',
        '"1"',
      ])
    },
  )
})
