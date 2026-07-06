import type { QueryClient } from '@tanstack/react-query'

const customerQueryRoots =
  new Set(['accounts', 'payments'])

export function clearCustomerQueries(
  queryClient: QueryClient,
): void {
  queryClient.removeQueries({
    predicate: (query) => {
      const root = query.queryKey[0]

      return (
        typeof root === 'string' &&
        customerQueryRoots.has(root)
      )
    },
  })
}
