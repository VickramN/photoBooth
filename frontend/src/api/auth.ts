import { ApiError, apiFetch } from "./client";

export interface Profile {
  id: string;
  username: string;
  roles: string[];
}

export async function getProfile(): Promise<Profile | null> {
  try {
    return await apiFetch<Profile>("/profile");
  } catch (error) {
    // 401 here is not a failure: it is the answer "nobody is logged in".
    if (error instanceof ApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}

export function login(username: string, password: string): Promise<void> {
  return apiFetch<void>("/auth/login", {
    method: "POST",
    body: { username, password },
  });
}

export function register(username: string, password: string): Promise<void> {
  return apiFetch<void>("/auth/register", {
    method: "POST",
    body: { username, password },
  });
}

export function logout(): Promise<void> {
  return apiFetch<void>("/auth/logout", { method: "POST" });
}
