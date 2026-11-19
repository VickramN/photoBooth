import { QueryCache, QueryClient } from "@tanstack/react-query";
import { ApiError } from "./api/client";
import { PROFILE_QUERY_KEY } from "./auth/useAuth";

function shouldRetry(failureCount: number, error: Error): boolean {
  if (failureCount >= 2) {
    return false;
  }
  // Retry only problems that might fix themselves: the API being unreachable or a server error.
  // Never retry 400, 401, 403 or 429: the same request would get the same answer.
  return (
    error instanceof ApiError && (error.status === 0 || error.status >= 500)
  );
}

export const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error) => {
      // Any query that comes back 401 means the session ended (expired or logged out elsewhere).
      // Marking the profile as logged out makes RequireAuth send the user to /login.
      if (error instanceof ApiError && error.status === 401) {
        queryClient.setQueryData(PROFILE_QUERY_KEY, null);
      }
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 2 * 60 * 1000,
      retry: shouldRetry,
    },
    mutations: {
      retry: false,
    },
  },
});
