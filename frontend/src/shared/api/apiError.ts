import { ApiHttpError } from './apiClient'
import { ApiProblemError } from './apiProblem'

export function isApiErrorWithStatus(
  error: unknown,
  status: number,
): boolean {
  return (
    (
      error instanceof ApiHttpError ||
      error instanceof ApiProblemError
    ) &&
    error.status === status
  )
}
