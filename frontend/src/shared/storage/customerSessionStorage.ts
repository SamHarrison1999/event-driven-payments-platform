export const customerSessionStorageKeys = {
  paymentSubmission:
    'event-driven-payments.payment-submission.v1',
} as const

export function clearCustomerSessionStorage(): void {
  if (typeof window === 'undefined') {
    return
  }

  try {
    Object.values(
      customerSessionStorageKeys,
    ).forEach((key) => {
      window.sessionStorage.removeItem(key)
    })
  } catch {
    // Session cleanup must not block authentication changes.
  }
}
