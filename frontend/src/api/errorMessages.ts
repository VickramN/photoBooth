import { ApiError } from "./client";

const MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: "Wrong username or password.",
  USERNAME_TAKEN: "That username is already taken.",
  VALIDATION_FAILED: "Please check the highlighted fields.",
  NETWORK_ERROR: "Can't reach the server. Check your connection and try again.",
  CSRF_TOKEN_INVALID: "Your session needs a refresh. Please try again.",
};

export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "Something went wrong. Please try again.";
  }
  if (error.code === "RATE_LIMITED") {
    const minutes = error.retryAfterSeconds
      ? Math.ceil(error.retryAfterSeconds / 60)
      : null;
    return minutes
      ? `Too many attempts. Try again in ${minutes} minute${minutes === 1 ? "" : "s"}.`
      : "Too many attempts. Try again later.";
  }
  return MESSAGES[error.code] ?? "Something went wrong. Please try again.";
}
