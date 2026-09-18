# Event RSVP Manager — Design File

This is a Phase 1 (design-only) document: no implementation exists yet. Every section below reflects the state of the design as of this writing — including its unresolved questions, rejected alternatives, and accepted tradeoffs. Nothing here should be read as settled just because it's written in prose; where something is genuinely open, it's marked as open (see Open Questions) rather than assumed away.

## Table of Contents

1. [Problem Statement](#problem-statement)
2. [Goals](#goals)
3. [Non-Goals](#non-goals)
4. [Context and Constraints](#context-and-constraints)
5. [Facts, Assumptions, and Open Questions](#facts-assumptions-and-open-questions)
6. [Actors and Workflows](#actors-and-workflows)
7. [Invariants](#invariants)
8. [Proposed Architecture](#proposed-architecture)
9. [Data Ownership and State Model](#data-ownership-and-state-model)
10. [Trust Boundaries and Security Notes](#trust-boundaries-and-security-notes)
11. [Concurrency and Correctness Notes](#concurrency-and-correctness-notes)
12. [Scalability and Multi-Tenancy Notes](#scalability-and-multi-tenancy-notes)
13. [Risks and Failure Notes](#risks-and-failure-notes)
14. [Alternatives Considered](#alternatives-considered)
15. [Tradeoffs](#tradeoffs)
16. [Rollout / Migration Notes](#rollout--migration-notes)

## Problem Statement

Hosts have no reliable way to track who's actually coming once there's a capacity limit — manual tracking breaks down, overbooking happens, and waitlists get managed by hand with no automatic promotion. There's also no clear moment when the guest list becomes final. This design turns RSVPs and capacity into live, enforced state instead of informal, manually-reconciled communication.

## Goals

1. One authoritative RSVP (Yes/No/Maybe) per invitee, current until the event starts.
2. Max-capacity enforced automatically — confirmed attendees never exceed it; overflow goes to a waitlist.
3. Waitlist promotion happens automatically when a confirmed spot frees.
4. Invitees respond and change their response themselves, without the host.
5. Host gets a live, accurate view of counts and attendee status.
6. RSVPs lock automatically at event start.
7. Host can end responses early (close or cancel) at any time.

## Non-Goals

1. No public event discovery — invite-only via the host's link.
2. No co-hosts — single owner per event.
3. No plus-ones — one invitee, one response.
4. No ticketing/payment.
5. No recurring events.
6. No invitee accounts/login — identified only by their unique link.
7. No cross-event reporting.
8. No alternate response channels (email-reply, SMS) — link only.

## Context and Constraints

- **Technical:** Stack is fixed (Java/Spring Boot/JPA + React/Vite + PostgreSQL), single instance, no auth framework wired in. Schema managed via Hibernate `ddl-auto: update` with no migration tool, so changes must be additive.
- **Product:** Scope is fixed by the brief and the Non-Goals above. A WhatsApp integration (WaSenderAPI) is already wired in the scaffold, conflicting with the brief's "invite by email" — resolved as email (Q1); the WhatsApp wiring is to be removed.
- **Operational:** Local single-developer Windows setup (Postgres + `start.ps1`), hardcoded dev credentials, no CI/CD or observability — none of that is in scope to design for.
- **Organizational:** Single owner acting as host, developer, and reviewer; no external dependents; design is expected to precede implementation.

## Facts, Assumptions, and Open Questions

### Facts

1. An event has title, description, date/time, location, and an optional max-capacity; its creator is the host.
2. Invitees respond via a unique link: Yes/No/Maybe.
3. A Yes beyond capacity goes to a waitlist, not rejected.
4. A freed confirmed spot auto-promotes the next waitlisted invitee.
5. RSVPs are changeable until event start, then lock.
6. The host can close or cancel at any time.
7. No auth framework or invitee accounts exist.
8. WhatsApp delivery (WaSenderAPI) is wired but unused; the brief says email.

### Assumptions

1. Invite delivery is by email (per the brief), treating the WhatsApp config as unresolved scaffold noise. *(Confirmed as a decision — see Resolved Decisions, Q1.)*
2. Only "Yes" counts toward capacity/waitlist; "Maybe" doesn't.
3. The waitlist is FIFO.
4. Invite count is unbounded, independent of capacity.
5. "Close" ≠ "cancel" — a closed event still happens, a cancelled one doesn't.
6. Each invitee's link is durable/reusable, not single-use.

### Resolved Decisions

These were listed as Open Questions earlier; they're resolved now, before moving into implementation. Numbering (Q1–Q4) is kept stable since it's referenced throughout the rest of this document. Q6 was identified only during implementation, not part of the original five — added here for the same reason: a real decision that needs to be visible, not left implicit in code.

**Q1 — Email or WhatsApp for invite delivery?**
**Decision:** Email, per the brief. The WaSenderAPI/WhatsApp wiring already in the scaffold is to be *removed*, not just left unused — it directly conflicts with the brief and stays a live liability (Risk #4, Risk #5) for as long as it remains in the codebase.
**Residual risk:** this decision doesn't retire Risk #5 on its own — the vendor key already committed in `application.yml` is in git history regardless of whether the code using it is removed. Rotation needs to happen at the vendor, but that account isn't owned by whoever implements this design (see Rollout notes) — it belongs to whoever created the shared scaffold this project forked from.

**Q2 — What resolves the last-spot race when two Yes's arrive simultaneously?**
**Decision:** Already resolved by the design, not a new decision — see Concurrency and Correctness Notes #1: a per-Event row lock, serializing the capacity check-and-write inside one transaction. Listed here only to correct its earlier mislabeling as open.

**Q3 — What mechanism enforces the event-start lock — a scheduled job vs. a check on access?**
**Decision:** Check-on-access only; no scheduled job. Every mutation already has to evaluate the database's own `now()` against the event's start time inside its own write transaction regardless (Concurrency Note #3 — this is required for TOCTOU-correctness even if a job also existed). A scheduled job would add a new always-running process, compounding Risk #8, for no correctness benefit — nothing in Goals or Non-Goals needs a side effect to fire at the exact moment of lock. Locked state stays fully derived, never stored, consistent with Data Ownership and State Model.

**Q4 — How does a host re-access their own event, given there's no login?**
**Decision:** The same link-as-capability model already used for invitees (Non-Goal #6): a single durable management link, returned once at Create Event, which the host is responsible for saving. No new auth infrastructure.
**Residual risk:** if the host loses this link, there is no recovery path — full, permanent loss of control over that event. Accepted given the single-developer/single-host scope (Organizational constraint); would need revisiting before this ever serves a real, less technical host population.

**Q6 — How is a link token actually generated? (surfaced during implementation review, not one of the original five)**
**Decision:** A cryptographically random UUID v4 (`UUID.randomUUID()`, backed by `SecureRandom`), never a sequential ID — implemented identically for both the host's management token and each invitee's token (`TokenGenerator.newToken()` in the codebase). This is what the Invariants section's "unguessable link token" requirement actually resolves to.
**Residual risk:** UUID v4 has 122 bits of randomness, unguessable at any realistic rate — but no rate-limiting exists on token lookups (Trust Boundaries doesn't cover this either). Accepted as an existing gap at the current single-developer scope, not a new one this decision introduces.

### Open Questions

5. **What happens if a host lowers max-capacity below the current confirmed count?** Still genuinely open. Deferred, not blocking: the design already holds that the downward capacity-edit endpoint shouldn't ship until this is resolved (Sensitive Data note, Rollout notes) — everything else can be built without an answer here.

## Actors and Workflows

**Actors:** Host (owns the event, invites, monitors, closes/cancels) · Invitee (holds a link, responds/changes until lock) · System (enforces capacity, promotion, lock).

### Create Event
- **Actor:** Host. **Precondition:** none — no account required (Non-Goal #6).
- **Flow:** Host submits title, description, date/time, location, optional max-capacity → Event Service creates the Event row and returns the host their management link.
- **System checks:** none at creation — capacity is only enforced later, against RSVPs.
- **Result:** Event exists, host holds its only access link — this link *is* the host's re-access mechanism, per Q4's resolution; there's no recovery if it's lost.

### Invite People
- **Actor:** Host. **Precondition:** Event exists; host holds its management link.
- **Flow:** Host submits a list of contacts → Event Service creates one Invitee row per contact, each with a unique link token → hands each to Invite Delivery for dispatch by email (Q1).
- **System checks:** none on roster size (Assumption #4 — unbounded).
- **Result:** each invitee starts Pending with an outstanding delivery.
- **Failure modes:** delivery failure is currently invisible to the host (Risk #7); a retried "add invitee" call must not duplicate the Invitee (Concurrency Note #8).

### Submit RSVP (first response)
- **Actor:** Invitee, via their unique link. **Precondition:** Event not Closed/Cancelled/past start; the link resolves to exactly one Invitee scoped to exactly one Event.
- **Flow:** Invitee submits Yes/No/Maybe → RSVP Engine runs the Lock Check, then for Yes runs the Capacity Gate (Confirmed if under capacity, else Waitlisted) → writes status.
- **System checks:** Lock Check; Capacity Gate (Yes only, per Assumption #2).
- **Failure modes:** last-spot race (Q2); an invalid/foreign link gets a generic rejection with no information leak about the Event.

### Change RSVP
- **Actor:** Invitee, via the same link. **Precondition:** same as Submit — not yet locked.
- **Flow:** Invitee resubmits a different status → Lock Check re-runs → the transition either frees a Confirmed slot (triggering Waitlist Promotion) or consumes one (re-running the Capacity Gate).
- **Failure modes:** two tabs racing on the same invitee (Concurrency Note #6); a concurrent free picking the wrong "earliest" waitlisted invitee (Concurrency Note #2).

### View Dashboard
- **Actor:** Host, via their management link. **Precondition:** host-token (the management link, per Q4) resolves to this Event.
- **Flow:** Event Service computes live counts and the attendee list from current Invitee rows; no cache, always fresh.
- **System checks:** host-ownership check — as privileged as a write (Trust Boundaries).

### Close Early / Cancel
- **Actor:** Host. **Precondition:** Event not already Closed/Cancelled.
- **Flow:** Host issues Close (stop new responses, event still happens) or Cancel (event doesn't happen) → Event Service updates Event state; any RSVP write arriving afterward is rejected by the Lock Check.
- **Result:** irreversible, affects every invitee at once (Sensitive Data note).
- **Failure modes:** races against a concurrent Submit RSVP consuming a slot on an event just cancelled (Concurrency Note #4).

## Invariants

- **Business:** Confirmed count never exceeds max-capacity *(Capacity Gate, atomic with the write)*. Each invitee has exactly one current RSVP status *(single mutable field, not a log)*. A freed confirmed slot always triggers promotion if the waitlist is non-empty *(same transaction as the freeing change)*. An event's start time is always in the future at creation time *(checked at Create Event against the database's own clock, same principle as Q3 — rejecting a past-dated event before it can ever collect RSVPs it has no way to honor; identified during manual testing, not in the original invariant list)*.
- **Data integrity:** Every RSVP/Invitee references a valid Event *(DB foreign keys)*. An invitee's link token is globally unique *(DB unique constraint)*. Confirmed count always matches actual Confirmed rows *(computed, not cached)*.
- **Authorization:** Only the owning host can manage their event *(ownership checked on every host endpoint)*. An invitee can act only on their own RSVP, scoped strictly by their own unguessable link token, never a sequential ID.
- **Concurrency:** Capacity holds under simultaneous Yes submissions *(DB constraint or row-locked check-and-increment)*. Exactly one invitee is promoted per freed slot *(same locked/transactional scope as the freeing check)*.
- **Tenant isolation** *(= per-event data boundary)*: one event's data is never reachable through another event's link or dashboard *(every query scoped by Event ID)*; a link resolves only against the event it was issued for, not by token match alone.

## Proposed Architecture

- **Event Service** — owns Event lifecycle, the invitee roster, and the host dashboard read. Only the owning host can act on it.
- **RSVP Engine** — owns RSVP status, the Capacity Gate, and Waitlist Promotion; applies the lock check before every write. The one place capacity is ever counted or compared.
- **Invite Delivery** — dispatches the link by email (Q1), behind an interface so a future channel change doesn't leak into Event Service or RSVP Engine.
- **Host Console / Invitee Response Page (frontend)** — two separate surfaces calling Event Service and RSVP Engine respectively; neither can reach the other's backend component.
- **Persistence** — single PostgreSQL schema, Hibernate `ddl-auto: update`; backs the integrity invariants via DB-level constraints.

**Interaction summary:** Host actions flow through Event Service, which hands new invitees to Invite Delivery for dispatch. Invitee actions flow through RSVP Engine alone, entered only via their own link. The two never write each other's data, so host/invitee separation is structural rather than a role check. Both persist to the same schema; since the app runs as a single instance, correctness under concurrency is entirely RSVP Engine's responsibility within one process and one database.

## Data Ownership and State Model

- **Event** — source of truth: Event row. Mutated by: Event Service only. Read by: Event Service, RSVP Engine. Locked state is derived (status + now vs. start), not stored — per Q3's resolution, there's no "transition" to assign mutation authority over. *Risk: stale-read if app-clock and DB-clock disagree — always check via DB `now()`, never app time.*
- **Invitee** — source of truth: Invitee row, split ownership (Event Service writes identity fields, RSVP Engine alone writes `status`). Read by: RSVP Engine, Event Service, Invitee page (via token). *Risk: unclear mutation authority (one row, two owners — enforce at code layer); conflicting-update on concurrent status changes (needs a version/row lock); duplicate-write if a retried submit isn't idempotent; stale-read on the invitee's own page after a background promotion.*
- **Confirmed Count** — derived (`COUNT` of Confirmed rows), no stored column. Read by: RSVP Engine (Capacity Gate), Event Service (dashboard). *Risk: stale-read = conflicting-update — count-then-write across concurrent requests is the last-spot race; needs an atomic conditional update or an Event-scoped lock, not a cached counter.*
- **Waitlist Order** — derived from response timestamps, not stored. Read by: RSVP Engine's promotion logic. *Risk: conflicting-update — concurrent promotions can pick the same "earliest" invitee; selection needs a locking read, not read-then-write.*
- **Dashboard View** — fully derived, read-only, no owner. *Risk: stale-read only if caching is ever introduced — none exists today, so always read fresh.*

**Resolved:** Q3 (no transition — the lock is derived, checked on access) and Q4 (the host's bearer management link is the ownership credential) — see Resolved Decisions. Both protections above can now be implemented as specified.

## Trust Boundaries and Security Notes

### Trust Entry Points

- Host Console → Event Service and Invitee Response Page → RSVP Engine are both bearer-token-in-URL boundaries — no login exists, so holding a link grants full capability for that role.
- Invite Delivery hands an invitee's token to a third party (the email provider, per Q1) before the invitee sees it — that vendor briefly holds a working capability token.

### Authorization Enforcement

- Every Event Service endpoint (create/edit/close/cancel, dashboard read) must check the host-token against the specific Event — per Q4, that token is the management link issued once at Create Event.
- Every RSVP Engine endpoint must resolve the invitee-token to exactly one Invitee scoped to exactly one Event, not just confirm the token exists anywhere.
- The dashboard read is as privileged as a write — it exposes the full attendee list — and needs the same host-ownership check as Close/Cancel.

### Tenant Isolation

- Invitee has split ownership (Event Service writes identity, RSVP Engine writes status) — the Event-ID scope check must be enforced identically in both, or a bug in one leaks one event's invitees into another event's dashboard.
- Waitlist Promotion writes to a *different* Invitee than the requester — that write must be scoped to the same Event and select the promoted invitee server-side, never from a request parameter.

### Sensitive Data / Privileged Operations

- Invitee contact info (email, per Q1) is PII with no separate access tier from the rest of the schema.
- Link tokens are the credential — no login to fall back on, so a leak is a full compromise with no revoke/reissue path (no invitee accounts, Non-Goal #6). Store hashed at rest, like a password.
- Cancel/Close affects every invitee at once, irreversibly. A downward capacity edit (Q5) is worse — its effect on already-Confirmed attendees is undefined, so that endpoint shouldn't ship before Q5 is resolved.

## Concurrency and Correctness Notes

*Format per item: Workflow/state — Risk. What can go wrong. Control note.*

1. **Submit RSVP (Yes) / Capacity Gate — concurrent update.** Two requests both read "capacity available" before either writes; both get Confirmed, capacity breached. *Control: transaction + row lock — serialize check-and-write per Event; a plain unique constraint can't express count ≤ N.*
2. **Waitlist Promotion on a freed slot — concurrent update.** Two promotions select the same "earliest" waitlisted invitee before either commits → double-promotion or a skipped invitee. *Control: as implemented, the same per-Event row lock as #1 (not a separate `SKIP LOCKED` read) — since every RSVP write already locks the Event row for its whole transaction, no other write can be touching this event's invitees concurrently, so the promotion pick needs no lock of its own.*
3. **Submit/Change RSVP near event start, or Close/Cancel — stale read (TOCTOU).** A mutation is accepted as "not yet locked" but the boundary is crossed before the write commits. *Control: transaction — evaluate lock state via the database's own `now()`/row state inside the same transaction as the write.*
4. **Close/Cancel racing a concurrent Submit RSVP — concurrent update across workflows.** An invitee's Yes commits and consumes a capacity slot on an event the host just cancelled. *Control: the same Event row lock as #1 — status changes and capacity-affecting writes must serialize against each other.*
5. **Submit RSVP retried by the client — duplicate request.** A naive retry could insert a duplicate record instead of updating the existing one. *Control: idempotency — submit is an upsert keyed by the invitee's token, never a create.*
6. **Change RSVP from two tabs / a stale form — concurrent update.** Last-write-wins silently discards the user's actual final intent. *Control: version check — an optimistic version/updated-at column rejects a write based on a stale read.*
7. **Status change plus its Capacity Gate/Promotion side effects — partial application.** The status write commits but the side effect doesn't (or vice versa), leaving state inconsistent. *Control: transaction — the write and its side effects commit as one unit or not at all.*
8. **Invite Delivery retry — worker retry / duplicate request.** A retried dispatch re-creates the Invitee instead of just resending. *Control: idempotency — retries act on the existing Invitee by ID via a delivery-status field, never by re-invoking "add invitee."*
9. **Any future move to async/queued capacity processing — out-of-order events.** Not applicable today (everything is synchronous within RSVP Engine), but a "slot freed" event applied after a "new Yes" event meant to precede it would corrupt capacity math. *Control: queue serialization — partition/key strictly by Event ID if this is ever introduced.*

## Scalability and Multi-Tenancy Notes

### Growth Axes

Number of events in the system; invitees per event (guest-list size); how bursty RSVP submissions are for a given event (e.g. a reminder going out and everyone responding within minutes); how many events approach their lock boundary around the same time.

### Likely First Bottlenecks

- The per-Event row lock behind the Capacity Gate serializes every Yes submission for *that* event through a single lock — a popular event's own RSVP burst queues up behind it (other events are unaffected, since it's row-level).
- Invite dispatch is inline with "Invite People" — a host adding a large batch of invitees pays for N outbound calls to the delivery vendor inside that one request; latency scales with batch size.
- The dashboard aggregate is computed live on every read, not cached — fine for one host checking their own event, but multiplies under frequent polling across many simultaneously-live events.

### Current Sufficiency

Single Postgres instance, single schema; inline synchronous invite dispatch; whole-Event row lock for capacity; live-computed dashboard aggregates. All of this is sufficient given the confirmed scope — realistic human-scale guest lists, a single developer/host, no fact indicating high concurrency or many simultaneous hosts.

### Future Redesign Triggers

- If a single event's RSVP burst gets large enough to make lock contention visible → replace the whole-Event row lock with a narrower, dedicated capacity-counter lock.
- If invite batch sizes grow → decouple dispatch into an async delivery worker/queue instead of sending inline within "Invite People."
- If polling volume across many live events becomes measurable load → move to caching with invalidation or push updates (websocket/SSE) instead of live-computing the dashboard on every read.
- If this is ever deployed to serve multiple hosts concurrently on one instance → introduce per-tenant resource isolation (see below).

### Tenant / Noisy-Neighbor Notes

Everything shares one Postgres instance, one connection pool, and one app process, with no per-host or per-event resource isolation. A single event with an unusually large or bursty guest list — or a host who invites an unbounded number of people (Assumption #4, no cap) — can consume a disproportionate share of DB connections and slow down RSVP processing for every *other* event on the same instance. Given the current single-developer/single-host scope (Organizational constraint), this is a latent risk rather than an active one — it becomes real the moment this is deployed to serve multiple hosts concurrently on one instance.

## Risks and Failure Notes

*Format per item: Risk — Failure shape — Cause — Note.*

### Correctness Risks

1. **Resolved decisions are load-bearing.** Failure shape: invariants that look enforced quietly stop holding. Cause: Q3 (lock mechanism) and Q4 (host auth) are now resolved (see Resolved Decisions), but nearly every invariant/security control depends on them being implemented exactly as decided — e.g. skipping the host-token check because "it's just a link anyway" silently reopens this risk. Note: implementation must match the resolved decisions, not deviate from them for convenience.
2. **Invitee's split ownership relies on code discipline, not the database.** Failure shape: the authorization model breaks silently, with no compiler/DB error. Cause: a future change (e.g. a "reset invite" feature) writes `status` from Event Service instead of RSVP Engine. Note: worth enforcing at the repository/method layer, not just by convention.
3. **Waitlist order is implicit, not stored.** Failure shape: promotion order becomes silently non-deterministic or unfair. Cause: FIFO is inferred from timestamps with no explicit rank column; ties or a future reordering feature expose this. Note: fine at current scale, but fragile if the ordering rule ever needs to change.

### Dependency Risks

4. **Invite channel depends on one unverified third party.** Failure shape: invitees never learn they were invited, with no fallback. Cause: Q1 is resolved as email, but no fallback/retry behavior is defined for a failed send — a single provider with no fallback is still a single point of failure. Note: this wasn't part of what Q1 decided; still open as a follow-up, though non-blocking.
5. **A live vendor key is committed in config.** Failure shape: an external account (WaSenderAPI) is exposed to anyone with repo access. Cause: the key sits in `application.yml` rather than a secret store. Note: already flagged under Trust Boundaries — this is a present fact, not a hypothetical. **This predates this fork:** the key is committed in the shared upstream template repo, which is already public with pull requests open from multiple other people forking the same assignment — the exposure isn't something any one fork created, and rotating the key requires access to a vendor account this project's implementer doesn't own. The available action isn't rotation; it's removing the key from this fork's own tracked files (done) and reporting it to whoever controls the scaffold/vendor account so they can rotate it centrally.
6. **Single Postgres instance, no backup story.** Failure shape: total loss of all Event/Invitee/RSVP state. Cause: no CI/CD, no observability, no documented recovery path exists in the current constraints. Note: acceptable for a local dev exercise, not for anything beyond it.

### Operational Risks

7. **No monitoring or alerting.** Failure shape: a silent Invite Delivery failure (Failure Flow #4) goes undetected indefinitely. Cause: no observability beyond console SQL logging. Note: the host has no way to know an invitee never got their link.
8. **Unsupervised process launch.** Failure shape: RSVP Engine crashes mid-event and silently stops accepting responses. Cause: `start.ps1` starts two processes with no restart-on-crash. Note: a human has to notice and rerun the script.
9. **Hardcoded dev credentials.** Failure shape: an exposed database if this ever runs beyond localhost unchanged. Cause: `postgres`/`1234` is a fact of the current config, not a placeholder. Note: fine for local dev; a real risk the moment the deployment target changes.

### Assumption Failures

10. **The Q1 decision ("email") turns out wrong.** Failure shape: the Invitee data model and Invite Delivery both need rework, not a tweak. Cause: WhatsApp (or another channel) turns out to be the actually required one after all, despite the brief.
11. **"Only Yes counts toward capacity" is wrong.** Failure shape: the Capacity Gate's core logic changes, not just a filter condition. Cause: Maybe turns out to need a provisional slot reservation.
12. **"Waitlist is FIFO" is wrong.** Failure shape: the promotion query needs a real ranking model instead of a timestamp read. Cause: a host-curated or priority order turns out to be required.
13. **"Invite count is unbounded" is wrong.** Failure shape: Event Service and Invite Delivery both need new validation that doesn't exist today. Cause: a real cap on invitees per event turns out to be required.
14. **"Links are durable/reusable" is wrong.** Failure shape: the entire link-as-capability trust model needs an expiry/reissue mechanism it currently has no place for. Cause: links turn out to need to expire or be single-use.

## Alternatives Considered

### A. Optimistic concurrency (`SERIALIZABLE` + retry) instead of row locks

Run the Capacity Gate and Promotion transactions at Postgres `SERIALIZABLE` isolation, retrying on serialization failure, instead of explicit `SELECT ... FOR UPDATE`. Equivalent correctness — the DB detects the last-spot race and double-promotion instead of the app managing lock order — with less lock-ordering code to get wrong. Trades predictable lock-queue latency for a new failure mode (serialization aborts) that can thrash under heavy contention on one event, and is harder for a reviewer to verify correct by inspection than an explicit lock. **Rejected:** the explicit row lock is more legible and matches the project's actual scale; worth revisiting under the lock-contention trigger already listed in Future Redesign Triggers.

### B. Event-sourced RSVP log + stored waitlist rank instead of a mutable status field

An append-only `RsvpEvent` log as source of truth, with `Invitee.status` as a materialized projection, plus an explicit `position` column for waitlist order instead of inferring FIFO from timestamps. Directly fixes Risk #3 (implicit, tie-fragile waitlist order) and gives a real audit trail for disputed outcomes. Costs a second table and a dual write on every status change, for capabilities (reporting, re-ordering) that aren't in Goals or Non-Goals today. **Rejected:** over-engineered for a system with no stated reporting or reordering requirement; the derived-state model is the better trade at current scope.

### C. Outbox pattern for invite delivery + short-lived signed host session tokens, instead of inline dispatch + a permanent bearer link

Write an `Outbox` row transactionally with "add invitee," with a background poller performing delivery instead of an inline vendor call — paired with issuing hosts an expiring, revocable session token instead of treating the raw event-management link as a permanent capability. The outbox half is close to a free win: it directly fixes the documented delivery-visibility gap (Risk #7) and the inline-dispatch latency bottleneck, at the cost of one more always-running process to babysit on top of the already-unsupervised `start.ps1` launch (Risk #8). The session-token half was one candidate answer to Q4, which has since been resolved with the simpler bearer-link model instead (see Resolved Decisions) — so this alternative is now rejected against that decision directly, not against an open question. **Rejected as a bundle:** the outbox half is a strong near-term candidate on its own; coupling it to a heavier auth model it doesn't need would have tied two independent changes together unnecessarily.

## Tradeoffs

- Pessimistic per-Event row locking is chosen over serializable-isolation retries — correctness is easy to verify by inspection, at the cost of requests queuing behind lock contention on a single popular event.
- RSVP status is a single mutable field with confirmed count and waitlist order both derived at read time, rather than an event log or a stored rank — less code and no dual-write to keep in sync, but waitlist ordering is fragile under ties or a future reordering requirement (Risk #3), and there's no audit trail if a host disputes an outcome.
- Invite dispatch stays synchronous and inline rather than moving to an outbox/worker — no extra process to run or monitor, but a host inviting a large batch pays for it in request latency, and a delivery failure is currently invisible to the host (Risk #7).
- Host and invitee identity stay a raw bearer token in the URL rather than an expiring signed session — no auth system to build, but a leaked link is a full, permanent, non-revocable compromise (Trust Boundaries, Sensitive Data note).
- Concurrency correctness is scoped entirely to a single Postgres instance and process rather than designed for multi-instance or queue-based processing — the simplest possible model for the current single-host, single-developer scope, but would need real rework (partitioned locking or a distributed queue) before ever running as more than one instance.

## Rollout / Migration Notes

Everything below except the first bullet is conditional on Phase 2 (implementation) actually being attempted — per the brief, that's a stretch goal, not a guaranteed deliverable. This section is deliberately short for that reason: there is no deployed prior version to migrate from, so most of what a rollout section would normally cover (backward compatibility, phased traffic shifting, coexistence with an old release) doesn't apply to a greenfield build.

- **Unconditional, and due now regardless of Phase 2 — but not fully actionable by this fork alone:** the WaSenderAPI key already committed in `application.yml` (Risk #5) belongs to the shared upstream scaffold, not to this fork — rotating it requires vendor-account access this fork's owner doesn't have. What is within this fork's control, and has been done: the key is removed from the current `application.yml`, so no new code here depends on or re-exposes it. What remains is reporting the exposed key to whoever controls the scaffold/vendor account (e.g. the course/assignment maintainer), since only they can actually rotate it — the submission being a public fork doesn't change who is able to fix this, only that it stays visible in this fork's git history too, same as in the upstream template it was forked from.
- **Build order, if Phase 2 is attempted:** build and exercise the Capacity Gate and Waitlist Promotion in isolation (e.g. a script firing concurrent Yes submissions at one test event) before wiring them to the frontend — it's the highest-risk code in the design (Concurrency Notes #1/#2) and the one place a bug stays silent until a real event overbooks. Event-start lock enforcement can be built directly per Q3's resolution (check-on-access, no scheduled job). Don't ship the downward capacity-edit endpoint until Q5 is resolved (Sensitive Data note already holds this) — it's the only remaining open question, and the only piece that should stay unbuilt.
- **Schema changes:** `ddl-auto: update` is additive-only with no migration tool (Technical Constraints) — every new column must be nullable or defaulted, since there's no way to backfill existing rows automatically and no rollback path beyond a manual forward fix. The Invitee contact field can be modeled directly as an email column now that Q1 is resolved — no need to hedge with a channel-agnostic design.
- **No feature-flag system exists** in the stack — gating the one remaining unfinished capability (the Q5 endpoint) means not merging it, not toggling it off later. Any leftover WaSenderAPI code from the scaffold should be removed outright per Q1's resolution, not merged-but-disabled — an unused path with a live-shaped vendor config is exactly Risk #5's exposure surface.
- **Don't restart the single app instance mid RSVP-burst** (e.g. right after a host sends invites) — there's no graceful shutdown or failover (Risk #8), so an in-flight Capacity Gate transaction is simply dropped.
