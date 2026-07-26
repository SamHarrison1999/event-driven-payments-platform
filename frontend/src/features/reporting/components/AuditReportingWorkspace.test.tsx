import {
  HttpResponse,
  http,
} from 'msw'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest'

import { renderWithQueryClient } from '../../../test/renderWithQueryClient'
import { server } from '../../../test/server'
import type { AuditEvent } from '../api/auditEvent'
import { AuditReportingWorkspace } from './AuditReportingWorkspace'

const auditEndpoint =
  'http://localhost:5173/api/v1/audit-events'
const summaryEndpoint =
  'http://localhost:5173/api/v1/reports/operational-summary'

const firstEvent: AuditEvent = {
  eventId:
    'BUSINESS_AUDIT:11111111-1111-4111-8111-111111111111',
  source: 'BUSINESS_AUDIT',
  category: 'PAYMENT',
  eventType: 'payment.completed',
  schemaVersion: 1,
  occurredAt: '2026-07-24T10:00:00Z',
  actorKind: 'IDENTITY_USER',
  actorIdentityUserId:
    '22222222-2222-4222-8222-222222222222',
  subjectType: 'payment',
  subjectIdentifier:
    '33333333-3333-4333-8333-333333333333',
  correlationIdentifier:
    'payment-correlation',
  details: {
    amountMinorUnits: 1250,
    currency: 'GBP',
  },
}

const secondEvent: AuditEvent = {
  ...firstEvent,
  eventId:
    'BUSINESS_AUDIT:44444444-4444-4444-8444-444444444444',
  eventType: 'payment.rejected',
  subjectIdentifier:
    '55555555-5555-4555-8555-555555555555',
  details: {
    rejectionCode:
      'INSUFFICIENT_FUNDS',
  },
}

function summary(
  includePayment: boolean,
  includeReconciliation: boolean,
) {
  return {
    from: '2026-07-18T10:00:00Z',
    to: '2026-07-25T10:00:00Z',
    payment: includePayment
      ? {
          submittedCount: 3,
          terminalCount: 3,
          completedCount: 1,
          rejectedCount: 1,
          failedCount: 1,
          completedAmountMinorUnits: 1250,
          rejectionCodeCounts: {
            INSUFFICIENT_FUNDS: 1,
          },
          failureCodeCounts: {
            PROCESSING_FAILED: 1,
          },
        }
      : null,
    settlement: includeReconciliation
      ? {
          acceptedImportCount: 1,
          acceptedRowCount: 2,
          matchedCount: 1,
          discrepancyCount: 1,
          importOutcomeCounts: {
            WITH_DISCREPANCIES: 1,
          },
        }
      : null,
    reconciliation: includeReconciliation
      ? {
          discrepancyCodeCounts: {
            PAYMENT_NOT_FOUND: 1,
          },
          lifecycleStateCounts: {
            RESOLVED: 1,
          },
          resolutionDecisionCounts: {
            ACCEPTED: 1,
          },
          openAgeBandCounts: {
            UNDER_24_HOURS: 0,
          },
        }
      : null,
  }
}

beforeEach(() => {
  server.use(
    http.get(auditEndpoint, () => {
      return HttpResponse.json({
        events: [firstEvent],
        nextCursor: null,
      })
    }),

    http.get(summaryEndpoint, () => {
      return HttpResponse.json(
        summary(true, true),
      )
    }),
  )
})

describe('AuditReportingWorkspace', () => {
  it(
    'shows all role-scoped sections and downloads to an administrator',
    async () => {
      renderWithQueryClient(
        <AuditReportingWorkspace
          roles={['ADMIN']}
          userId="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        />,
      )

      expect(
        screen.getByRole('heading', {
          level: 4,
          name: 'Bounded UTC window',
        }),
      ).toBeInTheDocument()

      expect(
        await screen.findByRole('heading', {
          level: 5,
          name: 'payment.completed',
        }),
      ).toBeInTheDocument()

      expect(
        await screen.findByRole('heading', {
          level: 5,
          name: 'Payment operations',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('heading', {
          level: 5,
          name: 'Settlement imports',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('heading', {
          level: 5,
          name: 'Reconciliation review',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('button', {
          name: /Audit events/,
        }),
      ).toBeInTheDocument()
      expect(
        screen.getByRole('button', {
          name: /Payments/,
        }),
      ).toBeInTheDocument()
      expect(
        screen.getByRole('button', {
          name: /Settlements/,
        }),
      ).toBeInTheDocument()
      expect(
        screen.getByRole('button', {
          name: /Reconciliation/,
        }),
      ).toBeInTheDocument()
    },
  )

  it(
    'limits an operations user to payment reporting controls',
    async () => {
      server.use(
        http.get(summaryEndpoint, () => {
          return HttpResponse.json(
            summary(true, false),
          )
        }),
      )

      renderWithQueryClient(
        <AuditReportingWorkspace
          roles={['OPERATIONS']}
          userId="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        />,
      )

      expect(
        await screen.findByRole('heading', {
          level: 5,
          name: 'Payment operations',
        }),
      ).toBeInTheDocument()

      expect(
        screen.queryByRole('heading', {
          level: 5,
          name: 'Settlement imports',
        }),
      ).not.toBeInTheDocument()

      expect(
        screen.getByRole('button', {
          name: /Audit events/,
        }),
      ).toBeInTheDocument()
      expect(
        screen.getByRole('button', {
          name: /Payments/,
        }),
      ).toBeInTheDocument()
      expect(
        screen.queryByRole('button', {
          name: /Settlements/,
        }),
      ).not.toBeInTheDocument()
      expect(
        screen.queryByRole('button', {
          name: /Reconciliation/,
        }),
      ).not.toBeInTheDocument()
    },
  )

  it(
    'uses the opaque cursor only for the next audit page',
    async () => {
      let receivedCursor: string | null =
        null

      server.use(
        http.get(
          auditEndpoint,
          ({ request }) => {
            const cursor = new URL(
              request.url,
            ).searchParams.get('cursor')
            receivedCursor = cursor

            return HttpResponse.json(
              cursor === null
                ? {
                    events: [firstEvent],
                    nextCursor:
                      'opaque-next-cursor',
                  }
                : {
                    events: [secondEvent],
                    nextCursor: null,
                  },
            )
          },
        ),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <AuditReportingWorkspace
          roles={[
            'RECONCILIATION_ANALYST',
          ]}
          userId="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        />,
      )

      await user.click(
        await screen.findByRole('button', {
          name: 'Next page',
        }),
      )

      expect(
        await screen.findByRole('heading', {
          level: 5,
          name: 'payment.rejected',
        }),
      ).toBeInTheDocument()
      expect(receivedCursor).toBe(
        'opaque-next-cursor',
      )
      expect(
        screen.getByRole('button', {
          name: 'Previous page',
        }),
      ).toBeEnabled()
    },
  )

  it(
    'rejects an overlong window before applying it',
    async () => {
      const user = userEvent.setup()

      renderWithQueryClient(
        <AuditReportingWorkspace
          roles={['ADMIN']}
          userId="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        />,
      )

      const from = screen.getByLabelText(
        'From (UTC)',
      )
      const to = screen.getByLabelText(
        'To (UTC)',
      )

      await user.clear(from)
      await user.type(
        from,
        '2026-06-01T00:00',
      )
      await user.clear(to)
      await user.type(
        to,
        '2026-07-25T00:00',
      )
      await user.click(
        screen.getByRole('button', {
          name: 'Apply UTC window',
        }),
      )

      expect(
        screen.getByRole('alert'),
      ).toHaveTextContent(
        'no more than 31 days',
      )
    },
  )
})
