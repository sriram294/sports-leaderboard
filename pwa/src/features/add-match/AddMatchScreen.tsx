import { useState } from 'react';
import type { Group, User } from '../../models';
import { api, ApiError } from '../../data';
import { Button, Card } from '../../components';

export function AddMatchScreen({ group, user, onDone }: { group?: Group; user: User; onDone: () => void }) {
  const [scores, setScores] = useState([[21, 15]]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); setBusy(true); setError('');
    try { await api.createMatch(group!.id, { playedAt: new Date().toISOString(), teams: [{ teamNo: 1, playerIds: [user.id] }, { teamNo: 2, playerIds: ['guest'] }], sets: scores.map(([team1Score, team2Score], index) => ({ setNo: index + 1, team1Score, team2Score })), winningTeamNo: scores[0][0] > scores[0][1] ? 1 : 2 }); onDone(); }
    catch (cause) { setError(cause instanceof ApiError ? cause.message : 'Could not record match.'); }
    finally { setBusy(false); }
  };
  return <><p className="eyebrow">NEW GAME</p><h2>Add match</h2><form className="form" onSubmit={submit}><Card><h3>Teams</h3><div className="team-box winner"><span className="team-label">TEAM 1 <b>WINNER</b></span><strong>{user.displayName}</strong><button type="button" className="add-player">+ Add player</button></div><div className="versus">VS</div><div className="team-box"><span className="team-label">TEAM 2</span><button type="button" className="add-player">+ Add player</button></div></Card><Card><div className="card-heading"><h3>Set scores</h3><button type="button" className="text-button" onClick={() => setScores([...scores, [21, 19]])}>+ Add set</button></div>{scores.map((score, index) => <div className="score-inputs" key={index}><span>Set {index + 1}</span><input aria-label={`Team 1 set ${index + 1}`} type="number" min="0" value={score[0]} onChange={event => setScores(scores.map((item, itemIndex) => itemIndex === index ? [+event.target.value, item[1]] : item))} /><b>–</b><input aria-label={`Team 2 set ${index + 1}`} type="number" min="0" value={score[1]} onChange={event => setScores(scores.map((item, itemIndex) => itemIndex === index ? [item[0], +event.target.value] : item))} /></div>)}</Card>{error && <p className="form-error">{error}</p>}<Button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Record match'}</Button></form></>;
}
