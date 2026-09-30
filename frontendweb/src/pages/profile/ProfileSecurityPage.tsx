import { FormEvent, useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { changePassword, getOwnSessions, getProfile, revokeOwnSession, updateProfile } from '../../entities/user/api/profile.api';

export function ProfileSecurityPage() {
  const queryClient = useQueryClient();
  const profile = useQuery({ queryKey: ['profile'], queryFn: getProfile });
  const sessions = useQuery({ queryKey: ['profile', 'sessions'], queryFn: getOwnSessions });
  const [displayName, setDisplayName] = useState('');
  const [locale, setLocale] = useState('');
  const [timezone, setTimezone] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');

  useEffect(() => {
    if (profile.data) {
      setDisplayName(profile.data.displayName ?? '');
      setLocale(profile.data.locale ?? '');
      setTimezone(profile.data.timezone ?? '');
    }
  }, [profile.data]);

  const save = useMutation({
    mutationFn: () => updateProfile({ displayName, locale: locale || null, timezone: timezone || null }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['profile'] }),
  });
  const password = useMutation({
    mutationFn: () => changePassword({ currentPassword, newPassword }),
    onSuccess: () => {
      setCurrentPassword('');
      setNewPassword('');
    },
  });
  const revoke = useMutation({
    mutationFn: revokeOwnSession,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['profile', 'sessions'] }),
  });

  function submitProfile(event: FormEvent) {
    event.preventDefault();
    save.mutate();
  }

  function submitPassword(event: FormEvent) {
    event.preventDefault();
    password.mutate();
  }

  if (profile.isLoading) return <p>Loading profile…</p>;
  if (profile.isError) return <p role="alert">Failed to load profile.</p>;

  return (
    <main>
      <h1>Profile & security</h1>
      <section>
        <h2>Profile</h2>
        <p>{profile.data?.email}</p>
        <form onSubmit={submitProfile}>
          <label>Display name<input value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={200} required /></label>
          <label>Locale<input value={locale} onChange={(e) => setLocale(e.target.value)} maxLength={10} /></label>
          <label>Timezone<input value={timezone} onChange={(e) => setTimezone(e.target.value)} maxLength={60} /></label>
          <button type="submit" disabled={save.isPending}>Save profile</button>
        </form>
      </section>

      <section>
        <h2>Change password</h2>
        <form onSubmit={submitPassword}>
          <label>Current password<input type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} required /></label>
          <label>New password<input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} minLength={12} maxLength={72} required /></label>
          <button type="submit" disabled={password.isPending}>Change password</button>
        </form>
      </section>

      <section>
        <h2>Sessions</h2>
        {sessions.isError && <p role="alert">Failed to load sessions.</p>}
        <table>
          <thead><tr><th>Created</th><th>Last used</th><th>Expires</th><th>Source</th><th>State</th><th>Action</th></tr></thead>
          <tbody>
            {(sessions.data ?? []).map((session) => (
              <tr key={session.id}>
                <td>{session.createdAt}</td><td>{session.lastUsedAt ?? '—'}</td><td>{session.expiresAt}</td>
                <td>{session.sourceIp ?? '—'} {session.userAgent ?? ''}</td>
                <td>{session.revokedAt ? 'Revoked' : 'Active'}</td>
                <td><button type="button" disabled={Boolean(session.revokedAt) || revoke.isPending} onClick={() => revoke.mutate(session.id)}>{session.revokedAt ? 'Revoked' : 'Revoke'}</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </main>
  );
}
