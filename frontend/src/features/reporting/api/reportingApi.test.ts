import {
  HttpResponse,
  http,
} from 'msw'
import {
  describe,
  expect,
  it,
} from 'vitest'

import { ApiContractError } from '../../../shared/api/apiClient'
import { server } from '../../../test/server'
import type { AuditEventPage } from './auditEvent'
import { downloadReport } from './downloadReport'
import { getAuditEvents } from './getAuditEvents'
import { getOperationalSummary } from './getOperationalSummary'
import type { OperationalSummary } from './operationalSummary'

const auditEndpoint =
  'http://localhost:5173/api/v1/audit-events'
const summaryEndpoint =
  'http://localhost:5173/api/v1/reports/operational-summary'
const paymentsEndpoint =
  'http://localhost:5173/api/v1/reports/payments.csv'

const window = {
  from: '2026-07-18T10:00:00Z',
  to: '2026-07-25T10:00:00Z',
}

const auditPage: AuditEventPage = {
  events: [
    {
      eventId:
        'BUSINESS_AUDIT:11111111-1111-4111-8111-111111111111',
      source: 'BUSINESS_AUDIT',
      category: 'PAYMENT',
      eventType: 'payment.completed',
      schemaVersion: 1,
      occurredAt:
        '2026-07-24T10:00:00Z',
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
    },
  ],
  nextCursor: 'opaque-cursor',
}

const summary: OperationalSummary = {
  ...window,
  payment: {
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
  },
  settlement: null,
  reconciliation: null,
}

describe('reporting API', () => {
  it(
    'sends conjunctive audit filters and validates the normalized page',
    async () => {
      let received:
        URLSearchParams | undefined

      server.use(
        http.get(
          auditEndpoint,
          ({ request }) => {
            received = new URL(
              request.url,
            ).searchParams

            return HttpResponse.json(
              auditPage,
            )
          },
        ),
      )

      await expect(
        getAuditEvents({
          ...window,
          category: 'PAYMENT',
          eventType:
            'payment.completed',
          subjectType: 'payment',
          subjectIdentifier:
            '33333333-3333-4333-8333-333333333333',
          cursor: 'opaque-cursor',
          limit: 25,
        }),
      ).resolves.toEqual(auditPage)

      expect(
        received?.get('category'),
      ).toBe('PAYMENT')
      expect(
        received?.get('eventType'),
      ).toBe('payment.completed')
      expect(
        received?.get('subjectType'),
      ).toBe('payment')
      expect(
        received?.get('cursor'),
      ).toBe('opaque-cursor')
      expect(received?.get('limit')).toBe(
        '25',
      )
    },
  )

  it(
    'rejects malformed audit and summary contracts',
    async () => {
      server.use(
        http.get(auditEndpoint, () => {
          return HttpResponse.json({
            events: [
              {
                ...auditPage.events[0],
                details: {
                  unsafe: {
                    nested: true,
                  },
                },
              },
            ],
            nextCursor: null,
          })
        }),
      )

      await expect(
        getAuditEvents({
          ...window,
          limit: 25,
        }),
      ).rejects.toBeInstanceOf(
        ApiContractError,
      )

      server.use(
        http.get(summaryEndpoint, () => {
          return HttpResponse.json({
            ...summary,
            payment: {
              ...summary.payment,
              completedCount: 1.5,
            },
          })
        }),
      )

      await expect(
        getOperationalSummary(window),
      ).rejects.toBeInstanceOf(
        ApiContractError,
      )
    },
  )

  it(
    'accepts role-scoped nullable summary sections',
    async () => {
      server.use(
        http.get(summaryEndpoint, () => {
          return HttpResponse.json(summary)
        }),
      )

      await expect(
        getOperationalSummary(window),
      ).resolves.toEqual(summary)
    },
  )

  it(
    'treats CSV as a fixed safe download',
    async () => {
      server.use(
        http.get(paymentsEndpoint, () => {
          return new HttpResponse(
            '"payment_id"\r\n"payment-1"\r\n',
            {
              headers: {
                'Content-Type':
                  'text/csv;charset=UTF-8',
                'Content-Disposition':
                  'attachment; filename="payments.csv"',
              },
            },
          )
        }),
      )

      const download =
        await downloadReport(
          'payments',
          window,
        )

      expect(download.filename).toBe(
        'payments.csv',
      )
      await expect(
        download.blob.text(),
      ).resolves.toContain(
        '"payment_id"',
      )
    },
  )

  it(
    'rejects a server-selected download filename',
    async () => {
      server.use(
        http.get(paymentsEndpoint, () => {
          return new HttpResponse('csv', {
            headers: {
              'Content-Type': 'text/csv',
              'Content-Disposition':
                'attachment; filename="unsafe.csv"',
            },
          })
        }),
      )

      await expect(
        downloadReport(
          'payments',
          window,
        ),
      ).rejects.toBeInstanceOf(
        ApiContractError,
      )
    },
  )
})
