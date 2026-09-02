export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors?: Record<string, string>

  constructor(status: number, message: string, fieldErrors?: Record<string, string>) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

interface ProblemDetail {
  title?: string
  detail?: string
  fieldErrors?: Record<string, string>
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    credentials: 'include',
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  })

  if (response.status === 204) {
    return undefined as T
  }

  const isJson = response.headers.get('content-type')?.includes('application/json')
  const body = isJson ? await response.json() : undefined

  if (!response.ok) {
    const problem = body as ProblemDetail | undefined
    throw new ApiError(response.status, problem?.detail ?? 'Une erreur est survenue.', problem?.fieldErrors)
  }

  return body as T
}

/** Pour les endpoints publics, exemptés de CSRF côté backend (register/login/forgot-password/...). */
export function apiPost<T>(path: string, payload?: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    body: payload !== undefined ? JSON.stringify(payload) : undefined,
  })
}

/** Pour les endpoints protégés par le double-submit CSRF (refresh/logout). */
export function apiPostWithCsrf<T>(path: string, payload?: unknown): Promise<T> {
  const csrfToken = readCookie('XSRF-TOKEN')
  return request<T>(path, {
    method: 'POST',
    body: payload !== undefined ? JSON.stringify(payload) : undefined,
    headers: csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {},
  })
}
