import type {
  CreateEventRequest,
  EventDashboardResponse,
  EventSummary,
  InviteeSummary,
  InviteeView,
  RsvpResponse,
} from '../types';

// Dev-time only: vite.config.ts proxies /api to the backend (port 8280),
// so there's no CORS setup to build - see DESIGN.md Context and Constraints
// (no auth framework, minimal infra).
const BASE = '/api';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(body.error ?? `Request failed (${res.status})`);
  }
  return res.json() as Promise<T>;
}

export function createEvent(payload: CreateEventRequest): Promise<EventSummary> {
  return request('/events', { method: 'POST', body: JSON.stringify(payload) });
}

export function getDashboard(hostToken: string): Promise<EventDashboardResponse> {
  return request(`/events/${hostToken}`);
}

export function invitePeople(hostToken: string, emails: string[]): Promise<InviteeSummary[]> {
  return request(`/events/${hostToken}/invitees`, {
    method: 'POST',
    body: JSON.stringify({ emails }),
  });
}

export function closeEvent(hostToken: string): Promise<EventDashboardResponse> {
  return request(`/events/${hostToken}/close`, { method: 'POST' });
}

export function cancelEvent(hostToken: string): Promise<EventDashboardResponse> {
  return request(`/events/${hostToken}/cancel`, { method: 'POST' });
}

export function getRsvpView(inviteeToken: string): Promise<InviteeView> {
  return request(`/rsvp/${inviteeToken}`);
}

export function submitRsvp(inviteeToken: string, response: RsvpResponse): Promise<InviteeView> {
  return request(`/rsvp/${inviteeToken}`, {
    method: 'POST',
    body: JSON.stringify({ response }),
  });
}
