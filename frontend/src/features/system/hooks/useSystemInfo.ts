import { useQuery } from '@tanstack/react-query'

import { getSystemInfo } from '../api/getSystemInfo'

export const systemInfoQueryKey = ['system-info'] as const

export function useSystemInfo() {
  return useQuery({
    queryKey: systemInfoQueryKey,
    queryFn: ({ signal }) => getSystemInfo(signal),
  })
}
