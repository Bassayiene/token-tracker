import { Role, Session } from '../core/auth.service';

/** Stores a session as the app would after logging in; call before the AuthService is first injected. */
export function storeSession(role: Role, expiresInMs = 3600_000): Session {
  const session: Session = {
    token: `test-token-${role}`,
    username: role === 'ADMIN' ? 'admin' : 'alice',
    role,
    expiresAt: new Date(Date.now() + expiresInMs).toISOString(),
  };
  localStorage.setItem('token-tracker.session', JSON.stringify(session));
  return session;
}
