import { apiRequestJson } from '../../../shared/api/apiClient'
import {
  clearCsrfToken,
  getCsrfHeaders,
} from '../../../shared/api/csrfToken'
import {
  isIdentitySession,
  type IdentitySession,
} from './identitySession'

export interface LoginCredentials {
  email: string
  password: string
}

export async function login(
  credentials: LoginCredentials,
): Promise<IdentitySession> {
  const csrfHeaders = await getCsrfHeaders()

  const session = await apiRequestJson(
    '/api/v1/identity/session',
    {
      method: 'POST',
      headers: csrfHeaders,
      body: credentials,
      contractName: 'Sign in',
      validate: isIdentitySession,
    },
  )

  clearCsrfToken()

  return session
}
