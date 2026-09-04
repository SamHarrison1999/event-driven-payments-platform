import { useMutation } from '@tanstack/react-query'

import { registerCustomer } from '../api/register'

export function useRegister() {
  return useMutation({
    mutationFn: registerCustomer,
  })
}