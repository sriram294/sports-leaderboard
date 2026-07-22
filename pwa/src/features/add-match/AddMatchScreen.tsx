import { useMemo, useState } from 'react';
import type { Member, RecordMatchRequest, User } from '../../models';
import { ApiError } from '../../data';
import { Avatar } from '../../components';
import { Icon } from '../../icons';
import {
  TEAM_SIZE,
  autoWinner,
  availablePlayers,
  buildRecordRequest,
  memberSlotLabel,
  setsValid,
  type SetInput,
} from '../../domain';

export type AddMatchPrefill = { team1: string[]; team2: string[]; sets: SetInput[]; winner?: number };

type Props = {
  user: User;
  roster: Member[];
  isEditing: boolean;
  playedAt: string;
  prefill?: AddMatchPrefill;
  onSubmit: (body: RecordMatchRequest) => Promise<void>;
};

/**
 * Add / Edit match form — see docs/pwa/requirements/04-add-match.md and Android `ui/add/*`.
 * Build two teams from the roster (guests collapse to a single "Guest"), score by set, pick
 * the winner (auto-derived from sets, tap to override), then record. In edit mode the form is
 * pre-filled and PATCHes a full replacement, round-tripping the original played time.
 */
export function AddMatchScreen({ user, roster, isEditing, playedAt, prefill, onSubmit }: Props) {
  const [team1, setTeam1] = useState<string[]>(prefill?.team1 ?? []);
  const [team2, setTeam2] = useState<string[]>(prefill?.team2 ?? []);
  const [sets, setSets] = useState<SetInput[]>(prefill?.sets?.length ? prefill.sets : [{ team1: '', team2: '' }]);
  const [winnerOverride, setWinnerOverride] = useState<number | undefined>(prefill?.winner);
  const [pickerTeam, setPickerTeam] = useState<number>();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>();

  const memberById = useMemo(() => new Map(roster.map(m => [m.userId, m])), [roster]);
  const assignedIds = useMemo(() => new Set([...team1, ...team2]), [team1, team2]);
  const teamsComplete = team1.length === TEAM_SIZE && team2.length === TEAM_SIZE;
  const winner = winnerOverride ?? autoWinner(sets) ?? undefined;
  const canRecord = teamsComplete && setsValid(sets) && winner != null && !submitting;

  const teamOf = (teamNo: number) => (teamNo === 1 ? team1 : team2);
  const setTeamOf = (teamNo: number) => (teamNo === 1 ? setTeam1 : setTeam2);

  const removePlayer = (userId: string) => {
    setTeam1(t => t.filter(id => id !== userId));
    setTeam2(t => t.filter(id => id !== userId));
    setError(undefined);
  };
  const pickPlayer = (userId: string) => {
    if (pickerTeam == null) return;
    const setter = setTeamOf(pickerTeam);
    setter(current => (current.length < TEAM_SIZE ? [...current, userId] : current));
    setPickerTeam(undefined);
    setError(undefined);
  };

  const updateSet = (index: number, side: 'team1' | 'team2', value: string) => {
    const digits = value.replace(/\D/g, '').slice(0, 2);
    setSets(current => current.map((set, i) => (i === index ? { ...set, [side]: digits } : set)));
    setError(undefined);
  };
  const addSet = () => setSets(current => [...current, { team1: '', team2: '' }]);
  const removeSet = (index: number) => setSets(current => (current.length <= 1 ? current : current.filter((_, i) => i !== index)));

  const teamLabel = (teamNo: number) =>
    Array.from({ length: TEAM_SIZE }, (_, i) => {
      const member = memberById.get(teamOf(teamNo)[i]);
      return member ? memberSlotLabel(member) : '?';
    }).join(' & ');

  const submit = async () => {
    if (!canRecord || winner == null) return;
    setSubmitting(true);
    setError(undefined);
    try {
      await onSubmit(buildRecordRequest(team1, team2, sets, winner, playedAt));
    } catch (cause) {
      setError(cause instanceof ApiError ? messageFor(cause) : 'Could not record the match. Check your connection and try again.');
      setSubmitting(false);
    }
  };

  return (
    <div className="add-match">
      <p className="recording-as">Recording as {user.displayName} · {user.email}</p>

      <p className="eyebrow">BUILD TEAMS</p>
      <div className="build-teams">
        <TeamColumn teamNo={1} ids={team1} memberById={memberById} onSlot={setPickerTeam} onRemove={removePlayer} />
        <span className="vs">VS</span>
        <TeamColumn teamNo={2} ids={team2} memberById={memberById} onSlot={setPickerTeam} onRemove={removePlayer} />
      </div>

      <p className="eyebrow score-eyebrow">SCORE BY SET</p>
      {sets.map((set, index) => (
        <div className="set-row" key={index}>
          <span className="set-label">Set {index + 1}</span>
          <input
            className="score-input" inputMode="numeric" placeholder="0" aria-label={`Team 1 set ${index + 1}`}
            value={set.team1} onChange={event => updateSet(index, 'team1', event.target.value)}
          />
          <span className="score-dash">–</span>
          <input
            className="score-input" inputMode="numeric" placeholder="0" aria-label={`Team 2 set ${index + 1}`}
            value={set.team2} onChange={event => updateSet(index, 'team2', event.target.value)}
          />
          {sets.length > 1 && (
            <button className="remove-set" onClick={() => removeSet(index)} aria-label={`Remove set ${index + 1}`}>
              <Icon name="close" size={16} />
            </button>
          )}
        </div>
      ))}
      <button className="add-set" onClick={addSet}>+ Add set</button>

      <p className="eyebrow">WHO WON?</p>
      <div className="who-won">
        {[1, 2].map(teamNo => (
          <button
            key={teamNo}
            className={`winner-card${winner === teamNo ? ' selected' : ''}`}
            onClick={() => { setWinnerOverride(teamNo); setError(undefined); }}
          >
            <strong>Team {teamNo}</strong>
            <span>{teamLabel(teamNo)}</span>
          </button>
        ))}
      </div>

      {error && <p className="form-error">{error}</p>}

      <button className="record-button" disabled={!canRecord} onClick={submit}>
        {submitting ? (isEditing ? 'Saving…' : 'Recording…') : isEditing ? 'Save changes' : 'Record Match'}
      </button>

      {pickerTeam != null && (
        <PlayerPicker
          teamNo={pickerTeam}
          players={availablePlayers(roster, assignedIds)}
          onPick={pickPlayer}
          onClose={() => setPickerTeam(undefined)}
        />
      )}
    </div>
  );
}

function TeamColumn({ teamNo, ids, memberById, onSlot, onRemove }: {
  teamNo: number;
  ids: string[];
  memberById: Map<string, Member>;
  onSlot: (teamNo: number) => void;
  onRemove: (userId: string) => void;
}) {
  return (
    <div className="team-column">
      <span className="team-title">Team {teamNo}</span>
      <div className="team-slots">
        {Array.from({ length: TEAM_SIZE }, (_, i) => {
          const member = memberById.get(ids[i]);
          return member ? (
            <button key={i} className="slot filled" onClick={() => onRemove(member.userId)} title={`Remove ${memberSlotLabel(member)}`}>
              <Avatar person={member} size={52} />
              <span className="slot-name">{memberSlotLabel(member)}</span>
            </button>
          ) : (
            <button key={i} className="slot empty" onClick={() => onSlot(teamNo)} aria-label={`Add player to team ${teamNo}`}>
              <Icon name="add" size={22} />
            </button>
          );
        })}
      </div>
    </div>
  );
}

function PlayerPicker({ teamNo, players, onPick, onClose }: {
  teamNo: number;
  players: Member[];
  onPick: (userId: string) => void;
  onClose: () => void;
}) {
  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet" role="dialog" aria-modal="true" onClick={event => event.stopPropagation()}>
        <div className="sheet-head">
          <span>Add to Team {teamNo}</span>
          <button className="icon-button" onClick={onClose} aria-label="Close"><Icon name="close" size={20} /></button>
        </div>
        {players.length === 0 ? (
          <p className="muted sheet-empty">Everyone's already assigned.</p>
        ) : (
          <div className="sheet-list">
            {players.map(member => (
              <button key={member.userId} className="sheet-row" onClick={() => onPick(member.userId)}>
                <Avatar person={member} size={36} />
                <span className="sheet-name">{memberSlotLabel(member)}</span>
                {member.role === 'guest' && <span className="guest-tag">GUEST</span>}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

/** Map the backend's 422 validation codes to a friendly message. */
function messageFor(error: ApiError): string {
  switch (error.code) {
    case 'MATCH_INVALID_TEAMS': return 'Each team needs two distinct players.';
    case 'MATCH_INVALID_SCORES': return 'Those set scores look invalid — check for ties or blanks.';
    case 'MATCH_EDIT_FORBIDDEN': return "You don't have permission to edit this match.";
    default: return error.message || 'Could not record the match.';
  }
}
