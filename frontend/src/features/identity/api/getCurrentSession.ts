import { apiRequestJson } from '../../../shared/api/apiClient'
import { isApiErrorWithStatus } from '../../../shared/api/apiError'
import {
  isIdentitySession,
  type IdentitySession,
} from './identitySession'

export async function getCurrentSession(
  signal?: AbortSignal,
): Promise<IdentitySession | null> {
  try {
    return await apiRequestJson(
      '/api/v1/identity/session',
      {
        contractName: 'Current session',
        validate: isIdentitySession,
        signal,
      },
    )
  } catch (error) {
    if (isApiErrorWithStatus(error, 401)) {
      return null
    }

    throw error
  }
}
