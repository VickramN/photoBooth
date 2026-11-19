const API_URL = import.meta.env.VITE_API_URL;

const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";
const MUTATING_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

interface RequestOptions {
  method?: HttpMethod;
  body?: unknown;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fields?: Record<string, string>;
  readonly retryAfterSeconds?: number;

  constructor(
    status: number,
    code: string,
    fields?: Record<string, string>,
    retryAfterSeconds?: number,
  ) {
    super(code);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fields = fields;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

function readCookie(name: string): string | null {
  const prefix = `${name}=`;
  const entry = document.cookie.split("; ").find((c) => c.startsWith(prefix));
  return entry ? decodeURIComponent(entry.slice(prefix.length)) : null;
}

async function fetchCsrfToken(): Promise<string> {
  await fetch(`${API_URL}/auth/csrf`, { credentials: "include" });
  const token = readCookie(CSRF_COOKIE);
  if (!token) {
    throw new ApiError(0, "CSRF_TOKEN_UNAVAILABLE");
  }
  return token;
}

async function csrfToken(): Promise<string> {
  return readCookie(CSRF_COOKIE) ?? fetchCsrfToken();
}

async function toApiError(response: Response): Promise<ApiError> {
  const retryAfter = response.headers.get("Retry-After");
  const retryAfterSeconds = retryAfter ? Number(retryAfter) : undefined;
  try {
    const body = await response.json();
    return new ApiError(
      response.status,
      body.error ?? "UNKNOWN",
      body.fields,
      retryAfterSeconds,
    );
  } catch {
    return new ApiError(
      response.status,
      "UNKNOWN",
      undefined,
      retryAfterSeconds,
    );
  }
}

export async function apiFetch<T>(
  path: string,
  { method = "GET", body }: RequestOptions = {},
  isRetry = false,
): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (MUTATING_METHODS.has(method)) {
    headers[CSRF_HEADER] = isRetry ? await fetchCsrfToken() : await csrfToken();
  }

  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      method,
      headers,
      credentials: "include",
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "NETWORK_ERROR");
  }

  if (response.ok) {
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }

  const error = await toApiError(response);
  if (error.code === "CSRF_TOKEN_INVALID" && !isRetry) {
    return apiFetch<T>(path, { method, body }, true);
  }
  throw error;
}
