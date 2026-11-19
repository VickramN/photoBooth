import { Navigate, Outlet, useLocation } from "react-router";
import { useProfile } from "./useAuth";

export function RequireAuth() {
  const { data: profile, isPending, isError } = useProfile();
  const location = useLocation();

  if (isPending) {
    return <p>Loading…</p>;
  }
  if (isError) {
    return <p>Can't reach the server. Try again in a moment.</p>;
  }
  if (!profile) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
}
