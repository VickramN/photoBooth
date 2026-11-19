import { useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router";
import { errorMessage } from "../api/errorMessages";
import { useLogin, useProfile } from "../auth/useAuth";

export function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const { data: profile } = useProfile();
  const loginMutation = useLogin();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? "/map";

  if (profile) {
    return <Navigate to={from} replace />;
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    loginMutation.mutate(
      { username, password },
      { onSuccess: () => navigate(from, { replace: true }) },
    );
  }

  return (
    <main>
      <h1>Log in</h1>
      <form onSubmit={handleSubmit}>
        <label>
          Username
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            required
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        {loginMutation.isError && (
          <p role="alert">{errorMessage(loginMutation.error)}</p>
        )}
        <button type="submit" disabled={loginMutation.isPending}>
          {loginMutation.isPending ? "Logging in…" : "Log in"}
        </button>
      </form>
      <p>
        No account? <Link to="/register">Register</Link>
      </p>
    </main>
  );
}
