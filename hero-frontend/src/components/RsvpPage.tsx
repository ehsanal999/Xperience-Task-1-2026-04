import { useEffect, useState } from 'react';
import * as api from '../services/api';
import type { InviteeView, RsvpResponse } from '../types';

interface Props {
  inviteeToken: string;
}

/**
 * Invitee Response Page (DESIGN.md - Actors and Workflows: Submit RSVP,
 * Change RSVP). Talks to RsvpEngineService alone via this one token - it
 * has no way to reach Event Service (Proposed Architecture: structural
 * host/invitee separation).
 */
export function RsvpPage({ inviteeToken }: Props) {
  const [view, setView] = useState<InviteeView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    load();
  }, [inviteeToken]);

  function load() {
    api
      .getRsvpView(inviteeToken)
      .then(setView)
      .catch((e) => setError(e.message));
  }

  function respond(response: RsvpResponse) {
    setSubmitting(true);
    setError(null);
    api
      .submitRsvp(inviteeToken, response)
      .then(setView)
      .catch((e) => setError(e.message))
      .finally(() => setSubmitting(false));
  }

  if (error) {
    return (
      <div className="panel">
        <p className="error">{error}</p>
      </div>
    );
  }

  if (!view) {
    return (
      <div className="panel">
        <p>Loading...</p>
      </div>
    );
  }

  const { event, status, locked } = view;

  return (
    <div className="panel">
      <h1>{event.title}</h1>
      <p className="muted">
        {new Date(event.startTime).toLocaleString()} &middot; {event.location}
      </p>
      {event.description && <p>{event.description}</p>}

      <p>
        Your current response: <strong>{status}</strong>
      </p>

      {locked ? (
        <p className="callout">
          Responses are locked for this event (it's closed, cancelled, or already started).
        </p>
      ) : (
        <div className="actions">
          <button onClick={() => respond('YES')} disabled={submitting}>
            Yes
          </button>
          <button onClick={() => respond('MAYBE')} disabled={submitting}>
            Maybe
          </button>
          <button onClick={() => respond('NO')} disabled={submitting}>
            No
          </button>
        </div>
      )}
    </div>
  );
}
