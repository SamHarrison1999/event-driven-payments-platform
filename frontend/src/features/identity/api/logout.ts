import { isApiErrorWithStatus } from '../../../shared/api/apiError'
import { apiRequestNoContent } from '../../../shared/api/apiClient'
import {
  clearCsrfToken,
  getCsrfHeaders,
} from '../../../shared/api/csrfToken'

export async function logout(): Promise<void> {
  try {
    const csrfHeaders = await getCsrfHeaders()

    await apiRequestNoContent(
      '/api/v1/identity/session',
      {
        method: 'DELETE',
        headers: csrfHeaders,
        contractName: 'Sign out',
      },
    )
  } catch (error) {
    if (!isApiErrorWithStatus(error, 401)) {
      throw error
    }
  } finally {
    clearCsrfToken()
  }
}
