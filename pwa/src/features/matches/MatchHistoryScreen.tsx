import { useState } from 'react';
import type { Group, Match } from '../../models';
import { api } from '../../data';
import { Button, Card } from '../../components';

export function MatchHistoryScreen({ group, matches, onReload }: { group?: Group; matches: Match[]; onReload: () => void }) {
  const [expanded, setExpanded] = useState<string>();
  const [error, setError] = useState('');
  const remove = async (id: string) => {
    if (!confirm('Delete this match? This cannot be undone.')) return;
    try { await api.deleteMatch(group!.id, id); onReload(); } catch (cause) { setError(cause instanceof Error ? cause.message : 'Delete failed'); }
  };
  return <><div className="title-row"><div><p className="eyebrow">{group?.matchCount || matches.length} GAMES PLAYED</p><h2>Matches</h2></div><button className="icon-button" onClick={onReload}>↻</button></div>{error && <p className="form-error">{error}</p>}{matches.length === 0 ? <div className="empty"><h3>No matches recorded</h3><p>Tap + to add the first one.</p></div> : matches.map(match => <MatchCard key={match.id} match={match} expanded={expanded === match.id} onToggle={() => setExpanded(expanded === match.id ? undefined : match.id)} onDelete={() => remove(match.id)} />)}</>;
}

function MatchCard({ match, expanded, onToggle, onDelete }: { match: Match; expanded: boolean; onToggle: () => void; onDelete: () => void }) {
  return <Card className="match-card"><button className="match-summary" onClick={onToggle}><span className="date">{new Date(match.playedAt).toLocaleDateString(undefined, { day: '2-digit', month: 'short' })}</span><span className="match-teams">{match.teams.map(team => team.players.map(player => player.displayName.split(' ')[0]).join(' & ')).join('  ×  ')}</span><span className="score">{match.sets[0]?.team1Score}–{match.sets[0]?.team2Score}</span><span>⌄</span></button>{expanded && <div className="match-detail"><div className="set-scores">{match.sets.map(set => <span key={set.setNo}>{set.team1Score} — {set.team2Score}</span>)}</div><p className="muted">Recorded {match.recordedBy?.displayName ? `by ${match.recordedBy.displayName}` : ''}</p><Button variant="danger" onClick={onDelete}>Delete match</Button></div>}</Card>;
}
