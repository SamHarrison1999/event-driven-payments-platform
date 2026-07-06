export type JsonObject = Record<string, unknown>

export type JsonValidator<T> = (
  value: unknown,
) => value is T

export function isJsonObject(
  value: unknown,
): value is JsonObject {
  return (
    typeof value === 'object' &&
    value !== null &&
    !Array.isArray(value)
  )
}

export function isNonEmptyString(
  value: unknown,
): value is string {
  return (
    typeof value === 'string' &&
    value.trim().length > 0
  )
}

export function isInteger(
  value: unknown,
): value is number {
  return (
    typeof value === 'number' &&
    Number.isInteger(value)
  )
}
