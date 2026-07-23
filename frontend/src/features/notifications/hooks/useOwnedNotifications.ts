import { useQuery } from '@tanstack/react-query'

import { getOwnedNotifications } from '../api/getOwnedNotifications'
import { notificationQueryKeys } from './notificationQueryKeys'

export function useOwnedNotifications() {
  return useQuery({
    queryKey: notificationQueryKeys.owned,
    queryFn: ({ signal }) =>
      getOwnedNotifications(signal),
    retry: false,
    staleTime: 15_000,
  })
}
