import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getProfile, login, logout, register } from "../api/auth";

export const PROFILE_QUERY_KEY = ["profile"] as const;

export function useProfile() {
  return useQuery({
    queryKey: PROFILE_QUERY_KEY,
    queryFn: getProfile,
  });
}

interface Credentials {
  username: string;
  password: string;
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ username, password }: Credentials) =>
      login(username, password),
    // The cookie is set now, so ask the server who we are.
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: PROFILE_QUERY_KEY }),
  });
}

export function useRegister() {
  return useMutation({
    mutationFn: ({ username, password }: Credentials) =>
      register(username, password),
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: logout,
    onSettled: () => {
      // Drop every cached response so the next person on this browser sees nothing of the last one.
      queryClient.removeQueries();
      queryClient.setQueryData(PROFILE_QUERY_KEY, null);
    },
  });
}
