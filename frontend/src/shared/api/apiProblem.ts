import {
  isInteger,
  isJsonObject,
} from './apiValidation'

export interface ApiFieldViolation {
  field: string
  message: string
}

export interface ApiProblem {
  type?: string
  title: string
  status: number
  detail: string
  instance?: string
  code?: string
  violations?: ApiFieldViolation[]
}

function isOptionalString(
  value: unknown,
): value is string | undefined {
  return (
    value === undefined ||
    typeof value === 'string'
  )
}

function isFieldViolation(
  value: unknown,
): value is ApiFieldViolation {
  return (
    isJsonObject(value) &&
    typeof value.field === 'string' &&
    typeof value.message === 'string'
  )
}

export function isApiProblem(
  value: unknown,
): value is ApiProblem {
  if (!isJsonObject(value)) {
    return false
  }

  return (
    isOptionalString(value.type) &&
    typeof value.title === 'string' &&
    isInteger(value.status) &&
    value.status >= 100 &&
    value.status <= 599 &&
    typeof value.detail === 'string' &&
    isOptionalString(value.instance) &&
    isOptionalString(value.code) &&
    (
      value.violations === undefined ||
      (
        Array.isArray(value.violations) &&
        value.violations.every(isFieldViolation)
      )
    )
  )
}

export class ApiProblemError extends Error {
  readonly problem: ApiProblem
  readonly status: number
  readonly retryAfterSeconds: number | null

  constructor(
    problem: ApiProblem,
    retryAfterSeconds: number | null = null,
  ) {
    super(problem.detail)

    this.name = 'ApiProblemError'
    this.problem = problem
    this.status = problem.status
    this.retryAfterSeconds = retryAfterSeconds
  }
}
