export type EventStatus = 'OPEN' | 'CLOSED' | 'CANCELLED';
export type RsvpStatus = 'PENDING' | 'CONFIRMED' | 'WAITLISTED' | 'DECLINED' | 'MAYBE';
export type RsvpResponse = 'YES' | 'NO' | 'MAYBE';

export interface EventSummary {
  title: string;
  description: string | null;
  startTime: string;
  location: string;
  maxCapacity: number | null;
  status: EventStatus;
  hostToken: string;
}

export interface EventPublicSummary {
  title: string;
  description: string | null;
  startTime: string;
  location: string;
  status: EventStatus;
}

export interface InviteeSummary {
  email: string;
  status: RsvpStatus;
  token: string;
}

export interface EventDashboardResponse {
  event: EventSummary;
  confirmedCount: number;
  waitlistedCount: number;
  invitees: InviteeSummary[];
}

export interface InviteeView {
  status: RsvpStatus;
  locked: boolean;
  event: EventPublicSummary;
}

export interface CreateEventRequest {
  title: string;
  description: string | null;
  startTime: string;
  location: string;
  maxCapacity: number | null;
}
