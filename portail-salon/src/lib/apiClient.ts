export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors?: Record<string, string>;

  constructor(
    status: number,
    message: string,
    fieldErrors?: Record<string, string>,
  ) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

interface ProblemDetail {
  title?: string;
  detail?: string;
  fieldErrors?: Record<string, string>;
}

interface RefreshResponse {
  accountId: string;
  accessToken: string;
}

interface AuthClient {
  getAccessToken: () => string | null;
  setAccessToken: (accessToken: string) => void;
  clearSession: () => void;
}

let authClient: AuthClient | null = null;

let refreshPromise: Promise<string | null> | null = null;

export function configureAuthClient(client: AuthClient): void {
  authClient = client;
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));

  return match ? decodeURIComponent(match[1]) : null;
}

async function executeRequest(
  path: string,
  options: RequestInit = {},
): Promise<Response> {
  const headers = new Headers(options.headers);

  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  return fetch(path, {
    credentials: "include",
    ...options,
    headers,
  });
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }

  const isJson = response.headers
    .get("content-type")
    ?.includes("application/json");

  const body = isJson ? await response.json() : undefined;

  if (!response.ok) {
    const problem = body as ProblemDetail | undefined;

    throw new ApiError(
      response.status,
      problem?.detail ?? "Une erreur est survenue.",
      problem?.fieldErrors,
    );
  }

  return body as T;
}

async function refreshAccessToken(): Promise<string | null> {
  if (!authClient) {
    return null;
  }

  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = (async () => {
    const csrfToken = readCookie("XSRF-TOKEN");

    const response = await executeRequest("/api/auth/refresh", {
      method: "POST",
      headers: csrfToken ? { "X-XSRF-TOKEN": csrfToken } : {},
    });

    if (!response.ok) {
      authClient?.clearSession();
      return null;
    }

    const result = await parseResponse<RefreshResponse>(response);

    authClient?.setAccessToken(result.accessToken);

    return result.accessToken;
  })()
    .catch(() => {
      authClient?.clearSession();
      return null;
    })
    .finally(() => {
      refreshPromise = null;
    });

  return refreshPromise;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await executeRequest(path, options);

  const authorization =
    options.headers instanceof Headers
      ? options.headers.get("Authorization")
      : Array.isArray(options.headers)
        ? options.headers.find(([name]) => name === "Authorization")?.[1]
        : options.headers?.["Authorization"];

  if (
    response.status === 401 &&
    authorization &&
    !path.startsWith("/api/auth/refresh")
  ) {
    const newAccessToken = await refreshAccessToken();

    if (newAccessToken) {
      const headers = new Headers(options.headers);
      headers.set("Authorization", `Bearer ${newAccessToken}`);

      const retryResponse = await executeRequest(path, {
        ...options,
        headers,
      });

      return parseResponse<T>(retryResponse);
    }
  }

  return parseResponse<T>(response);
}

export function apiGet<T>(path: string, accessToken?: string): Promise<T> {
  return request<T>(path, {
    method: "GET",
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });
}

export function apiPost<T>(
  path: string,
  payload?: unknown,
  accessToken?: string,
): Promise<T> {
  return request<T>(path, {
    method: "POST",
    body: payload !== undefined ? JSON.stringify(payload) : undefined,
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });
}

export function apiPostWithCsrf<T>(
  path: string,
  payload?: unknown,
): Promise<T> {
  const csrfToken = readCookie("XSRF-TOKEN");

  return request<T>(path, {
    method: "POST",
    body: payload !== undefined ? JSON.stringify(payload) : undefined,
    headers: csrfToken ? { "X-XSRF-TOKEN": csrfToken } : {},
  });
}

export function apiPatch<T>(
  path: string,
  payload?: unknown,
  accessToken?: string,
): Promise<T> {
  return request<T>(path, {
    method: "PATCH",
    body: payload !== undefined ? JSON.stringify(payload) : undefined,
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });
}

export function apiDelete<T>(path: string, accessToken?: string): Promise<T> {
  return request<T>(path, {
    method: "DELETE",
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });
}
