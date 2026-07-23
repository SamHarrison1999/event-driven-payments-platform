import {
  HttpResponse,
  http,
} from 'msw'
import {
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest'

import { ApiContractError } from '../../../shared/api/apiClient'
import { clearCsrfToken } from '../../../shared/api/csrfToken'
import { server } from '../../../test/server'
import type {
  OutboxDeadLetter,
  OutboxReplayResult,
} from './deadLetter'
import { getDeadLetters } from './getDeadLetters'
import { replayDeadLetter } from './replayDeadLetter'

const csrfEndpoint =
  'http://localhost:5173/api/v1/identity/csrf'
const deadLetterEndpoint =
  'http://localhost:5173/api/v1/admin/outbox/dead-letters'

const deadLetter: OutboxDeadLetter = {
  eventId:
    '11111111-1111-4111-8111-111111111111',
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

describe('dead-letter API', () => {
  it(
    'returns the bounded administrator dead-letter list',
    async () => {
      server.use(
        http.get(
          deadLetterEndpoint,
          ({ request }) => {
            expect(
              new URL(request.url)
                .searchParams.get('limit'),
            ).toBe('50')

            return HttpResponse.json([
              deadLetter,
            ])
          },
        ),
      )

      await expect(
        getDeadLetters(),
      ).resolves.toEqual([deadLetter])
    },
  )

  it.each([
    {
      name: 'unknown status',
      override: {
        status: 'FAILED',
      },
    },
    {
      name: 'negative version',
      override: {
        version: -1,
      },
    },
    {
      name: 'invalid timestamp',
      override: {
        updatedAt: '23 July 2026',
      },
    },
  ])(
    'rejects a dead-letter response with $name',
    async ({ override }) => {
      server.use(
        http.get(deadLetterEndpoint, () => {
          return HttpResponse.json([
            {
              ...deadLetter,
              ...override,
            },
          ])
        }),
      )

      await expect(
        getDeadLetters(),
      ).rejects.toBeInstanceOf(
        ApiContractError,
      )
    },
  )

  it(
    'replays with CSRF and optimistic version evidence',
    async () => {
      let receivedBody: unknown
      let receivedCsrf: string | null = null

      server.use(
        http.get(csrfEndpoint, () => {
          return HttpResponse.json({
            headerName: 'X-CSRF-TOKEN',
            parameterName: '_csrf',
            token: 'replay-csrf-token',
          })
        }),

        http.post(
          `${deadLetterEndpoint}/${deadLetter.eventId}/replay`,
          async ({ request }) => {
            receivedBody = await request.json()
            receivedCsrf =
              request.headers.get(
                'X-CSRF-TOKEN',
              )

            return HttpResponse.json(
              replayResult,
            )
          },
        ),
      )

      await expect(
        replayDeadLetter({
          eventId: deadLetter.eventId,
          reason:
            'Retry after simulated sink repair.',
          expectedVersion: 0,
        }),
      ).resolves.toEqual(replayResult)

      expect(receivedCsrf).toBe(
        'replay-csrf-token',
      )

      expect(receivedBody).toEqual({
        reason:
          'Retry after simulated sink repair.',
        expectedVersion: 0,
      })
    },
  )
})
