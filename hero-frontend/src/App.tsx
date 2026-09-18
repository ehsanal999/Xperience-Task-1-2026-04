import './App.css';
import { HostConsole } from './components/HostConsole';
import { RsvpPage } from './components/RsvpPage';

/**
 * No router library is added here - just two URL shapes to distinguish,
 * matching the two structurally-separate surfaces in Proposed Architecture:
 *   /rsvp/:inviteeToken  -> Invitee Response Page
 *   /host/:hostToken     -> Host Console (existing event)
 *   anything else        -> Host Console (create event)
 */
function App() {
  const path = window.location.pathname;
  const rsvpMatch = path.match(/^\/rsvp\/([^/]+)/);
  const hostMatch = path.match(/^\/host\/([^/]+)/);

  if (rsvpMatch) {
    return <RsvpPage inviteeToken={rsvpMatch[1]} />;
  }

  return <HostConsole initialHostToken={hostMatch ? hostMatch[1] : null} />;
}

export default App;
