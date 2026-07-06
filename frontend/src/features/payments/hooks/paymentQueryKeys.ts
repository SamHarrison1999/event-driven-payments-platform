export const paymentQueryKeys = {
  all: ['payments'] as const,
  detail: (paymentId: string) =>
    [
      'payments',
      'detail',
      paymentId,
    ] as const,
}
