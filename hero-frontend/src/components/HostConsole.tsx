import { useEffect, useState } from 'react';
import * as api from '../services/api';
import type { EventDashboardResponse } from '../types';

interface Props {
  initialHostToken: string | null;
}

/**
 * Local "now" formatted for an <input type="datetime-local"> min attribute.
 * UI nicety only, using the browser's clock - never authoritative. The
 * backend re-validates against the database's own clock (createEvent in
 * EventService), which is what actually enforces this rule.
 */
function nowForDateTimeLocal(): string {
  const now = new Date(Date.now() - new Date().getTimezoneOffset() * 60000);
  return now.toISOString().slice(0, 16);
}

/**
 * Host Console (DESIGN.md - Actors and Workflows: Create Event, Invite People,
 * View Dashboard, Close Early / Cancel). The hostToken in the URL IS the
 * authorization credential (Q4) - there is no login screen here by design.
 */
export function HostConsole({ initialHostToken }: Props) {
  const [hostToken, setHostToken] = useState<string | null>(initialHostToken);
  const [dashboard, setDashboard] = useState<EventDashboardResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (hostToken) {
      refreshDashboard(hostToken);
    }
  }, [hostToken]);

  function refreshDashboard(token: string) {
    api
      .getDashboard(token)
      .then(setDashboard)
      .catch((e) => setError(e.message));
  }

  function handleCreated(token: string) {
    setError(null);
    window.history.pushState({}, '', `/host/${token}`);
    setHostToken(token);
  }

  if (!hostToken) {
    return <CreateEventForm onCreated={handleCreated} error={error} setError={setError} />;
  }

  if (error) {
    return <p className="error">{error}</p>;
  }

  if (!dashboard) {
    return <p>Loading...</p>;
  }

  return (
    <Dashboard
      hostToken={hostToken}
      dashboard={dashboard}
      onChanged={() => refreshDashboard(hostToken)}
      onError={setError}
    />
  );
}

function CreateEventForm({
  onCreated,
  error,
  setError,
}: {
  onCreated: (hostToken: string) => void;
  error: string | null;
  setError: (e: string | null) => void;
}) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [startTime, setStartTime] = useState('');
  const [location, setLocation] = useState('');
  const [maxCapacity, setMaxCapacity] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    api
      .createEvent({
        title,
        description: description || null,
        startTime,
        location,
        maxCapacity: maxCapacity ? Number(maxCapacity) : null,
      })
      .then((event) => onCreated(event.hostToken))
      .catch((err) => setError(err.message))
      .finally(() => setSubmitting(false));
  }

  return (
    <div className="panel">
      <h1>Create Event</h1>
      <form onSubmit={handleSubmit} className="form">
        <label>
          Title
          <input value={title} onChange={(e) => setTitle(e.target.value)} required />
        </label>
        <label>
          Description
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
        <label>
          Start time
          <input
            type="datetime-local"
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
            min={nowForDateTimeLocal()}
            required
          />
        </label>
        <label>
          Location
          <input value={location} onChange={(e) => setLocation(e.target.value)} required />
        </label>
        <label>
          Max capacity (optional)
          <input
            type="number"
            min={1}
            value={maxCapacity}
            onChange={(e) => setMaxCapacity(e.target.value)}
          />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Creating...' : 'Create Event'}
        </button>
      </form>
    </div>
  );
}

function Dashboard({
  hostToken,
  dashboard,
  onChanged,
  onError,
}: {
  hostToken: string;
  dashboard: EventDashboardResponse;
  onChanged: () => void;
  onError: (e: string | null) => void;
}) {
  const { event, confirmedCount, waitlistedCount, invitees } = dashboard;
  const [emailsInput, setEmailsInput] = useState('');
  const [inviting, setInviting] = useState(false);
  const rsvpBaseUrl = `${window.location.origin}/rsvp/`;

  function handleInvite(e: React.FormEvent) {
    e.preventDefault();
    const emails = emailsInput
      .split(/[\n,]/)
      .map((s) => s.trim())
      .filter(Boolean);
    if (emails.length === 0) return;
    setInviting(true);
    onError(null);
    api
      .invitePeople(hostToken, emails)
      .then(() => {
        setEmailsInput('');
        onChanged();
      })
      .catch((err) => onError(err.message))
      .finally(() => setInviting(false));
  }

  function handleClose() {
    if (!confirm('Close this event to new responses? This cannot be undone.')) return;
    api.closeEvent(hostToken).then(onChanged).catch((err) => onError(err.message));
  }

  function handleCancel() {
    if (!confirm('Cancel this event entirely? This cannot be undone.')) return;
    api.cancelEvent(hostToken).then(onChanged).catch((err) => onError(err.message));
  }

  return (
    <div className="panel">
      <h1>{event.title}</h1>
      <p className="muted">
        {new Date(event.startTime).toLocaleString()} &middot; {event.location} &middot; status:{' '}
        {event.status}
      </p>
      {event.description && <p>{event.description}</p>}

      <div className="callout">
        This link is your only way to manage this event - there's no login to recover it if lost:
        <br />
        <code>{window.location.href}</code>
      </div>

      <div className="stats">
        <span>Confirmed: {confirmedCount}</span>
        <span>Waitlisted: {waitlistedCount}</span>
        <span>Capacity: {event.maxCapacity ?? 'unlimited'}</span>
      </div>

      {event.status === 'OPEN' && (
        <div className="actions">
          <button onClick={handleClose}>Close to new responses</button>
          <button onClick={handleCancel} className="danger">
            Cancel event
          </button>
        </div>
      )}

      <h2>Invite people</h2>
      <form onSubmit={handleInvite} className="form">
        <label>
          Emails (comma or newline separated)
          <textarea value={emailsInput} onChange={(e) => setEmailsInput(e.target.value)} />
        </label>
        <button type="submit" disabled={inviting || event.status !== 'OPEN'}>
          {inviting ? 'Sending...' : 'Send invites'}
        </button>
      </form>

      <h2>Invitees ({invitees.length})</h2>
      <table>
        <thead>
          <tr>
            <th>Email</th>
            <th>Status</th>
            <th>RSVP link</th>
          </tr>
        </thead>
        <tbody>
          {invitees.map((inv) => (
            <tr key={inv.token}>
              <td>{inv.email}</td>
              <td>{inv.status}</td>
              <td>
                <code>{rsvpBaseUrl}{inv.token}</code>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
