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
import { customerSessionStorageKeys } from '../../../shared/storage/customerSessionStorage'
import { renderWithQueryClient } from '../../../test/renderWithQueryClient'
import { server } from '../../../test/server'
import type { IdentitySession } from '../api/identitySession'
import { SessionBoundary } from './SessionBoundary'

const sessionEndpoint =
  'http://localhost:5173/api/v1/identity/session'

const csrfEndpoint =
  'http://localhost:5173/api/v1/identity/csrf'

const registrationEndpoint =
  'http://localhost:5173/api/v1/identity/registrations'

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

function csrfResponse(
  token = 'csrf-token',
) {
  return HttpResponse.json({
    headerName: 'X-CSRF-TOKEN',
    parameterName: '_csrf',
    token,
  })
}

beforeEach(() => {
  clearCsrfToken()
  window.sessionStorage.clear()
})

describe('SessionBoundary', () => {
  it(
    'restores an authenticated customer session',
    async () => {
      server.use(
        http.get(sessionEndpoint, () => {
          return HttpResponse.json(session)
        }),
      )

      renderWithQueryClient(
        <SessionBoundary>
          <p>Protected customer content</p>
        </SessionBoundary>,
      )

      expect(
        await screen.findByText(
          session.email,
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          'Protected customer content',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('button', {
          name: 'Sign out',
        }),
      ).toBeEnabled()
    },
  )

  it(
    'shows the login form when no session exists',
    async () => {
      server.use(
        http.get(sessionEndpoint, () => {
          return unauthorizedProblem()
        }),
      )

      window.sessionStorage.setItem(
        customerSessionStorageKeys
          .paymentSubmission,
        'unresolved-payment',
      )

      renderWithQueryClient(
        <SessionBoundary />,
      )

      expect(
        await screen.findByRole('heading', {
          level: 3,
          name: 'Sign in',
        }),
      ).toBeInTheDocument()

      expect(
        window.sessionStorage.getItem(
          customerSessionStorageKeys
            .paymentSubmission,
        ),
      ).toBe('unresolved-payment')

      expect(
        screen.getByLabelText(
          'Email address',
          {
            selector: '#login-email',
          },
        )
      ).toBeInTheDocument()

      expect(
        screen.getByLabelText(
          'Password',
          {
            selector: '#login-password',
          },
        )
      ).toBeInTheDocument()
    },
  )

  it(
    'signs in with the CSRF header and exact credentials',
    async () => {
      let receivedToken: string | null = null
      let receivedBody: unknown

      server.use(
        http.get(sessionEndpoint, () => {
          return unauthorizedProblem()
        }),

        http.get(csrfEndpoint, () => {
          return csrfResponse(
            'login-token',
          )
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

      const user = userEvent.setup()

      renderWithQueryClient(
        <SessionBoundary>
          <p>Protected customer content</p>
        </SessionBoundary>,
      )

      await user.type(
        await screen.findByLabelText(
          'Email address',
          {
            selector: '#login-email',
          },
        ),
        session.email,
      )

      await user.type(
        screen.getByLabelText(
          'Password',
          {
            selector: '#login-password',
          },
        ),
        'this is a secure customer passphrase',
      )

      await user.click(
        screen.getByRole('button', {
          name: 'Sign in',
        }),
      )

      expect(
        await screen.findByText(
          session.email,
        ),
      ).toBeInTheDocument()

      expect(receivedToken).toBe(
        'login-token',
      )

      expect(receivedBody).toEqual({
        email: session.email,
        password:
          'this is a secure customer passphrase',
      })
    },
  )

  it(
    'shows a generic message after rejected credentials',
    async () => {
      server.use(
        http.get(sessionEndpoint, () => {
          return unauthorizedProblem()
        }),

        http.get(csrfEndpoint, () => {
          return csrfResponse()
        }),

        http.post(sessionEndpoint, () => {
          return new HttpResponse(null, {
            status: 401,
          })
        }),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <SessionBoundary />,
      )

      await user.type(
        await screen.findByLabelText(
          'Email address',
          {
            selector: '#login-email',
          },
        ),
        session.email,
      )

      const passwordInput =
        screen.getByLabelText(
          'Password',
          {
            selector: '#login-password',
          },
        )

      await user.type(
        passwordInput,
        'incorrect private password',
      )

      await user.click(
        screen.getByRole('button', {
          name: 'Sign in',
        }),
      )

      expect(
        await screen.findByRole('alert'),
      ).toHaveTextContent(
        'Email or password was not accepted.',
      )

      await waitFor(() => {
        expect(passwordInput).toHaveValue('')
      })
    },
  )

  it(
    'signs out and returns to the login form',
    async () => {
      let receivedToken: string | null = null

      server.use(
        http.get(sessionEndpoint, () => {
          return HttpResponse.json(session)
        }),

        http.get(csrfEndpoint, () => {
          return csrfResponse(
            'logout-token',
          )
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

      const user = userEvent.setup()

      window.sessionStorage.setItem(
        customerSessionStorageKeys
          .paymentSubmission,
        'unresolved-payment',
      )

      renderWithQueryClient(
        <SessionBoundary />,
      )

      await user.click(
        await screen.findByRole(
          'button',
          {
            name: 'Sign out',
          },
        ),
      )

      expect(
        await screen.findByRole('heading', {
          level: 3,
          name: 'Sign in',
        }),
      ).toBeInTheDocument()

      expect(receivedToken).toBe(
        'logout-token',
      )

      expect(
        window.sessionStorage.getItem(
          customerSessionStorageKeys
            .paymentSubmission,
        ),
      ).toBeNull()
    },
  )

  it(
    'automatically retries a transient session bootstrap failure',
    async () => {
      let attempts = 0

      server.use(
        http.get(sessionEndpoint, () => {
          attempts += 1

          if (attempts === 1) {
            return new HttpResponse(null, {
              status: 503,
            })
          }

          return HttpResponse.json(session)
        }),
      )

      window.sessionStorage.setItem(
        customerSessionStorageKeys
          .paymentSubmission,
        'unresolved-payment',
      )

      renderWithQueryClient(
        <SessionBoundary />,
      )

      expect(
        await screen.findByText(
          session.email,
          {},
          {
            timeout: 3_000,
          },
        ),
      ).toBeInTheDocument()

      expect(attempts).toBe(2)
    },
  )

  it(
    'allows a non-transient session bootstrap failure to be manually retried',
    async () => {
      let attempts = 0

      server.use(
        http.get(sessionEndpoint, () => {
          attempts += 1

          if (attempts === 1) {
            return new HttpResponse(null, {
              status: 500,
            })
          }

          return HttpResponse.json(session)
        }),
      )

      const user = userEvent.setup()

      window.sessionStorage.setItem(
        customerSessionStorageKeys
          .paymentSubmission,
        'unresolved-payment',
      )

      renderWithQueryClient(
        <SessionBoundary />,
      )

      await user.click(
        await screen.findByRole(
          'button',
          {
            name: 'Try again',
          },
        ),
      )

      expect(
        await screen.findByText(
          session.email,
        ),
      ).toBeInTheDocument()

      expect(attempts).toBe(2)
    },
  )

  it(
    'creates a customer account from the unauthenticated workspace',
    async () => {
      let registrationBody: unknown

      server.use(
        http.get(sessionEndpoint, () => {
          return unauthorizedProblem()
        }),

        http.get(csrfEndpoint, () => {
          return csrfResponse(
            'registration-token',
          )
        }),

        http.post(
          registrationEndpoint,
          async ({ request }) => {
            registrationBody =
              await request.json()

            return HttpResponse.json(
              {
                id: '9ee15533-c884-45fa-b1e6-caf9af1d2eba',
                email:
                  'new.customer@example.com',
                status: 'ACTIVE',
                roles: ['CUSTOMER'],
                createdAt:
                  '2026-09-04T16:00:00Z',
              },
              {
                status: 201,
              },
            )
          },
        ),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <SessionBoundary />,
      )

      await screen.findByRole(
        'heading',
        {
          name: 'Create account',
        },
      )

      await user.type(
        screen.getByLabelText(
          'Email address',
          {
            selector: '#register-email',
          },
        ),
        'new.customer@example.com',
      )

      await user.type(
        screen.getByLabelText(
          'Password',
          {
            selector: '#register-password',
          },
        ),
        'LongSecureDemoPassword2026!',
      )

      await user.type(
        screen.getByLabelText(
          'Confirm password',
        ),
        'LongSecureDemoPassword2026!',
      )

      await user.click(
        screen.getByRole(
          'button',
          {
            name: 'Create account',
          },
        ),
      )

      expect(
        await screen.findByText(
          'Account created',
        ),
      ).toBeInTheDocument()

      expect(registrationBody).toEqual({
        email: 'new.customer@example.com',
        password: 'LongSecureDemoPassword2026!',
      })
    },
  )

  it(
    'rejects mismatched registration passwords before submitting',
    async () => {
      server.use(
        http.get(sessionEndpoint, () => {
          return unauthorizedProblem()
        }),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <SessionBoundary />,
      )

      await screen.findByRole(
        'heading',
        {
          name: 'Create account',
        },
      )

      await user.type(
        screen.getByLabelText(
          'Email address',
          {
            selector: '#register-email',
          },
        ),
        'new.customer@example.com',
      )

      await user.type(
        screen.getByLabelText(
          'Password',
          {
            selector: '#register-password',
          },
        ),
        'LongSecureDemoPassword2026!',
      )

      await user.type(
        screen.getByLabelText(
          'Confirm password',
        ),
        'DifferentSecurePassword2026!',
      )

      await user.click(
        screen.getByRole(
          'button',
          {
            name: 'Create account',
          },
        ),
      )

      expect(
        await screen.findByText(
          'Passwords do not match.',
        ),
      ).toBeInTheDocument()
    },
  )

  it(
    'explains when a registration email already exists',
    async () => {
      server.use(
        http.get(sessionEndpoint, () => {
          return unauthorizedProblem()
        }),

        http.get(csrfEndpoint, () => {
          return csrfResponse(
            'registration-token',
          )
        }),

        http.post(
          registrationEndpoint,
          () =>
            HttpResponse.json(
              {
                type:
                  'urn:problem:identity:duplicate-email',
                title:
                  'Email address already registered',
                status: 409,
                detail:
                  'Email already exists.',
                code:
                  'IDENTITY_DUPLICATE_EMAIL',
              },
              {
                status: 409,
                headers: {
                  'Content-Type':
                    'application/problem+json',
                },
              },
            ),
        ),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <SessionBoundary />,
      )

      await screen.findByRole(
        'heading',
        {
          name: 'Create account',
        },
      )

      await user.type(
        screen.getByLabelText(
          'Email address',
          {
            selector: '#register-email',
          },
        ),
        'existing@example.com',
      )

      await user.type(
        screen.getByLabelText(
          'Password',
          {
            selector: '#register-password',
          },
        ),
        'LongSecureDemoPassword2026!',
      )

      await user.type(
        screen.getByLabelText(
          'Confirm password',
        ),
        'LongSecureDemoPassword2026!',
      )

      await user.click(
        screen.getByRole(
          'button',
          {
            name: 'Create account',
          },
        ),
      )

      expect(
        await screen.findByText(
          /account with this email already exists/i,
        ),
      ).toBeInTheDocument()
    },
  )
})
