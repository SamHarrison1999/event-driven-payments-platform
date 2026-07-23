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

import { clearCsrfToken } from '../../../shared/api/csrfToken'
import { renderWithQueryClient } from '../../../test/renderWithQueryClient'
import { server } from '../../../test/server'
import type {
  OutboxDeadLetter,
  OutboxReplayResult,
} from '../api/deadLetter'
import { DeadLetterPanel } from './DeadLetterPanel'

const csrfEndpoint =
  'http://localhost:5173/api/v1/identity/csrf'
const endpoint =
  'http://localhost:5173/api/v1/admin/outbox/dead-letters'

const deadLetter: OutboxDeadLetter = {
  eventId:
    '11111111-1111-4111-8111-11111111bc28',
  aggregateType: 'payment',
  aggregateId:
    '22222222-2222-4222-8222-222222222222',
  eventType: 'payment.completed.v1',
  schemaVersion: 1,
  payload:
    '{"paymentId":"22222222-2222-4222-8222-222222222222"}',
  correlationIdentifier: 'correlation-1',
  causationIdentifier: null,
  createdAt: '2026-07-23T14:59:00Z',
  updatedAt: '2026-07-23T15:00:00Z',
  status: 'DEAD_LETTER',
  attemptCount: 5,
  lastErrorCategory:
    'PermanentOutboxPublicationException',
  lastErrorMessage:
    'Simulated invalid event',
  replayCount: 0,
  lastReplayedAt: null,
  version: 0,
}

const replayResult: OutboxReplayResult = {
  event: {
    ...deadLetter,
    status: 'PENDING',
    attemptCount: 0,
    lastErrorCategory: null,
    lastErrorMessage: null,
    replayCount: 1,
    lastReplayedAt:
      '2026-07-23T15:01:00Z',
    updatedAt: '2026-07-23T15:01:00Z',
    version: 1,
  },
  replayAuditId:
    '33333333-3333-4333-8333-333333333333',
  replayedAt: '2026-07-23T15:01:00Z',
}

beforeEach(() => {
  clearCsrfToken()
})

describe('DeadLetterPanel', () => {
  it(
    'inspects and replays one dead-letter event',
    async () => {
      let replayed = false
      let receivedBody: unknown

      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json(
            replayed ? [] : [deadLetter],
          )
        }),

        http.get(csrfEndpoint, () => {
          return HttpResponse.json({
            headerName: 'X-CSRF-TOKEN',
            parameterName: '_csrf',
            token: 'replay-csrf-token',
          })
        }),

        http.post(
          `${endpoint}/${deadLetter.eventId}/replay`,
          async ({ request }) => {
            receivedBody = await request.json()
            replayed = true
            return HttpResponse.json(
              replayResult,
            )
          },
        ),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <DeadLetterPanel />,
      )

      expect(
        await screen.findByRole('list', {
          name: 'Outbox dead-letter events',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('heading', {
          level: 5,
          name: 'payment.completed.v1',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          'Simulated invalid event',
        ),
      ).toBeInTheDocument()

      await user.type(
        screen.getByRole('textbox', {
          name:
            'Replay reason for event 1111BC28',
        }),
        'Retry after simulated sink repair.',
      )

      await user.click(
        screen.getByRole('button', {
          name: 'Replay event',
        }),
      )

      expect(
        await screen.findByText(
          'Replay queued',
        ),
      ).toBeInTheDocument()

      expect(
        await screen.findByText(
          'No dead-letter events',
        ),
      ).toBeInTheDocument()

      expect(receivedBody).toEqual({
        reason:
          'Retry after simulated sink repair.',
        expectedVersion: 0,
      })
    },
  )

  it(
    'shows the empty recovery state',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json([])
        }),
      )

      renderWithQueryClient(
        <DeadLetterPanel />,
      )

      expect(
        await screen.findByText(
          'No dead-letter events',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText('0 events'),
      ).toBeInTheDocument()
    },
  )

  it(
    'explains an optimistic replay conflict',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json([
            deadLetter,
          ])
        }),

        http.get(csrfEndpoint, () => {
          return HttpResponse.json({
            headerName: 'X-CSRF-TOKEN',
            parameterName: '_csrf',
            token: 'replay-csrf-token',
          })
        }),

        http.post(
          `${endpoint}/${deadLetter.eventId}/replay`,
          () => {
            return HttpResponse.json(
              {
                type:
                  'urn:problem:outbox:replay-conflict',
                title:
                  'Outbox replay conflict',
                status: 409,
                detail:
                  'The outbox event version changed before replay.',
                code:
                  'OUTBOX_REPLAY_CONFLICT',
              },
              {
                status: 409,
                headers: {
                  'Content-Type':
                    'application/problem+json',
                },
              },
            )
          },
        ),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <DeadLetterPanel />,
      )

      await user.type(
        await screen.findByRole('textbox', {
          name:
            'Replay reason for event 1111BC28',
        }),
        'Retry stale event.',
      )

      await user.click(
        screen.getByRole('button', {
          name: 'Replay event',
        }),
      )

      expect(
        await screen.findByRole('alert'),
      ).toHaveTextContent(
        'The event changed before replay. Refresh the list and try again.',
      )
    },
  )
})
