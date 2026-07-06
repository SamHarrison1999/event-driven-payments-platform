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
import {
  clearCsrfToken,
  getCsrfToken,
} from '../../../shared/api/csrfToken'
import { server } from '../../../test/server'
import { getCurrentSession } from './getCurrentSession'
import type { IdentitySession } from './identitySession'
import { login } from './login'
import { logout } from './logout'

const sessionEndpoint =
  'http://localhost:5173/api/v1/identity/session'

const csrfEndpoint =
  'http://localhost:5173/api/v1/identity/csrf'

const session: IdentitySession = {
  userId:
    '2f1f55da-5793-4a75-aeb5-c20f69f16949',
  email: 'sam.customer@example.com',
  roles: ['CUSTOMER'],
}

function unauthorizedProblem() {
  return HttpResponse.json(
    {
      type:
        'urn:problem:security:authentication-required',
      title: 'Authentication required',
      status: 401,
      detail:
        'Authentication is required to access this resource.',
      code:
        'SECURITY_AUTHENTICATION_REQUIRED',
    },
    {
      status: 401,
      headers: {
        'Content-Type':
          'application/problem+json',
      },
    },
  )
}

beforeEach(() => {
  clearCsrfToken()
})

describe('identity session API', () => {
  it(
    'returns the authenticated current session',
    async () => {
      server.use(
        http.get(sessionEndpoint, () => {
          return HttpResponse.json(session)
        }),
      )

      await expect(
        getCurrentSession(),
      ).resolves.toEqual(session)
    },
  )

  it(
    'maps an unauthenticated current-session response to null',
    async () => {
      server.use(
        http.get(sessionEndpoint, () => {
          return unauthorizedProblem()
        }),
      )

      await expect(
        getCurrentSession(),
      ).resolves.toBeNull()
    },
  )

  it(
    'rejects a malformed session response',
    async () => {
      server.use(
        http.get(sessionEndpoint, () => {
          return HttpResponse.json({
            userId: 'not-a-uuid',
            email: session.email,
            roles: session.roles,
          })
        }),
      )

      await expect(
        getCurrentSession(),
      ).rejects.toBeInstanceOf(
        ApiContractError,
      )
    },
  )

  it(
    'signs in with CSRF and clears the cached token after authentication',
    async () => {
      let csrfRequests = 0
      let receivedBody: unknown
      let receivedToken: string | null = null

      server.use(
        http.get(csrfEndpoint, () => {
          csrfRequests += 1

          return HttpResponse.json({
            headerName: 'X-CSRF-TOKEN',
            parameterName: '_csrf',
            token: `token-${csrfRequests}`,
          })
        }),

        http.post(
          sessionEndpoint,
          async ({ request }) => {
            receivedToken =
              request.headers.get(
                'X-CSRF-TOKEN',
              )
            receivedBody =
              await request.json()

            return HttpResponse.json(session)
          },
        ),
      )

      await expect(
        login({
          email: session.email,
          password:
            'this is a secure customer passphrase',
        }),
      ).resolves.toEqual(session)

      expect(receivedToken).toBe('token-1')
      expect(receivedBody).toEqual({
        email: session.email,
        password:
          'this is a secure customer passphrase',
      })

      await expect(
        getCsrfToken(),
      ).resolves.toMatchObject({
        token: 'token-2',
      })

      expect(csrfRequests).toBe(2)
    },
  )

  it(
    'signs out with the configured CSRF header',
    async () => {
      let receivedToken: string | null = null

      server.use(
        http.get(csrfEndpoint, () => {
          return HttpResponse.json({
            headerName: 'X-CSRF-TOKEN',
            parameterName: '_csrf',
            token: 'logout-token',
          })
        }),

        http.delete(
          sessionEndpoint,
          ({ request }) => {
            receivedToken =
              request.headers.get(
                'X-CSRF-TOKEN',
              )

            return new HttpResponse(null, {
              status: 204,
            })
          },
        ),
      )

      await expect(
        logout(),
      ).resolves.toBeUndefined()

      expect(receivedToken).toBe(
        'logout-token',
      )
    },
  )

  it(
    'treats an already expired session as signed out during logout',
    async () => {
      server.use(
        http.get(csrfEndpoint, () => {
          return HttpResponse.json({
            headerName: 'X-CSRF-TOKEN',
            parameterName: '_csrf',
            token: 'expired-token',
          })
        }),

        http.delete(sessionEndpoint, () => {
          return unauthorizedProblem()
        }),
      )

      await expect(
        logout(),
      ).resolves.toBeUndefined()
    },
  )
})
