import http from 'k6/http'
import { check } from 'k6'
import { Trend } from 'k6/metrics'

const paymentSubmissionDuration = new Trend(
  'payment_submission_duration',
  true,
)

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080')
  .replace(/\/$/, '')
const sessionCookie = required('PAYMENTS_SESSION')
const csrfToken = required('CSRF_TOKEN')
const sourceAccountId = required('SOURCE_ACCOUNT_ID')
const destinationAccountId = required(
  'DESTINATION_ACCOUNT_ID',
)
const amountMinorUnits = Number(
  __ENV.PAYMENT_AMOUNT_MINOR_UNITS || '1',
)

if (!Number.isSafeInteger(amountMinorUnits) || amountMinorUnits <= 0) {
  throw new Error(
    'PAYMENT_AMOUNT_MINOR_UNITS must be a positive safe integer.',
  )
}

export const options = {
  scenarios: {
    payment_submission: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.PAYMENT_RATE || '5'),
      timeUnit: '1s',
      duration: __ENV.PAYMENT_DURATION || '30s',
      preAllocatedVUs: Number(
        __ENV.PAYMENT_PRE_ALLOCATED_VUS || '5',
      ),
      maxVUs: Number(__ENV.PAYMENT_MAX_VUS || '20'),
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:payment_submission}': [
      'p(95)<2000',
    ],
    payment_submission_duration: ['p(95)<2000'],
  },
}

export default function () {
  const idempotencyKey = [
    'phase11-payment',
    __VU,
    __ITER,
    Date.now(),
  ].join('-')

  const response = http.post(
    `${baseUrl}/api/v1/payments`,
    JSON.stringify({
      sourceAccountId,
      destinationAccountId,
      amountMinorUnits,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'Cookie': `PAYMENTS_SESSION=${sessionCookie}`,
        'X-CSRF-TOKEN': csrfToken,
        'Idempotency-Key': idempotencyKey,
      },
      tags: {
        endpoint: 'payment_submission',
      },
    },
  )

  paymentSubmissionDuration.add(
    response.timings.duration,
    {
      endpoint: 'payment_submission',
    },
  )

  let body = null

  try {
    body = response.json()
  } catch (_error) {
    body = null
  }

  check(response, {
    'payment returns HTTP 201': (result) =>
      result.status === 201,
    'payment response is completed': () =>
      body !== null && body.status === 'COMPLETED',
  })
}

function required(name) {
  const value = __ENV[name]

  if (!value) {
    throw new Error(`${name} must be supplied to the load test.`)
  }

  return value
}
