import { useNavigate } from "react-router";
import { useLogout, useProfile } from "../auth/useAuth";

export function MapPage() {
  const { data: profile } = useProfile();
  const logoutMutation = useLogout();
  const navigate = useNavigate();

  return (
    <main>
      <p>Logged in as {profile?.username}</p>
      <p>The map goes here.</p>
      <button
        onClick={() =>
          logoutMutation.mutate(undefined, {
            onSettled: () => navigate("/login"),
          })
        }
        disabled={logoutMutation.isPending}
      >
        Log out
      </button>
    </main>
  );
}
