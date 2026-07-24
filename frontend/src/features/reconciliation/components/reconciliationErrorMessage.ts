import { ApiProblemError } from '../../../shared/api/apiProblem'

export function reconciliationErrorMessage(
  error: unknown,
  fallback: string,
): string {
  if (error instanceof ApiProblemError) {
    return error.problem.detail
  }

  return fallback
}
