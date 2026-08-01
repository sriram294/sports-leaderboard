import { useEffect, useMemo, useRef, useState } from 'react';
import type { Match, MonthlyTrophy, Partner, Ranking } from '../../models';
import { Avatar } from '../../components';
import { usePartners } from '../../queries';
import {
  computeBiggestWin,
  computeRecords,
  matchTeam,
  monthlyTrophyLabel,
  percent,
  teamName,
  winRatePercent,
  winningTeamNo,
  type BiggestWin,
  type Records,
} from '../../domain';

type Props = {
  groupId: string;
  currentUserId?: string;
  rankings: Ranking[];
  matchCount: number;
  matches: Match[];
  trophies: MonthlyTrophy[];
};

/**
 * Stats / Insights — see docs/pwa/requirements/06-stats.md and Android `ui/stats/*`.
 * A group-level dashboard: all-time RECORDS from the leaderboard + group match count,
 * MONTHLY WINNERS (served), a PARTNERS card (pick any player from the leaderboard, fetch
 * just their partner list on demand), and BIGGEST WIN derived client-side from the first
 * page of matches — no new endpoints beyond the per-player partners one Profile also uses.
 */
export function StatsScreen({ groupId, currentUserId, rankings, matchCount, matches, trophies }: Props) {
  const records = useMemo(() => computeRecords(rankings, matchCount), [rankings, matchCount]);
  const biggestWin = useMemo(() => computeBiggestWin(matches), [matches]);

  if (matchCount === 0 && matches.length === 0) {
    return <p className="stats-empty">Play some matches to see insights.</p>;
  }

  return (
    <div className="stats">
      <RecordsCard records={records} />
      {trophies.length > 0 && <MonthlyWinnersCard winners={trophies} />}
      <PartnersCard groupId={groupId} rankings={rankings} currentUserId={currentUserId} />
      {biggestWin && <BiggestWinCard biggestWin={biggestWin} />}
    </div>
  );
}

function RecordsCard({ records }: { records: Records }) {
  return (
    <section className="card insight-card">
      <p className="section-label">RECORDS</p>
      <p className="records-total"><strong>{records.totalMatches}</strong> {records.totalMatches === 1 ? 'match played' : 'matches played'}</p>
      {records.winLeader && <LeaderRow label="WIN LEADER" player={records.winLeader} value={`${winRatePercent(records.winLeader)}%`} />}
      {records.mostPoints && <LeaderRow label="MOST POINTS" player={records.mostPoints} value={`${records.mostPoints.pointsFor}`} />}
      {records.mostActive && <LeaderRow label="MOST ACTIVE" player={records.mostActive} value={`${records.mostActive.gamesPlayed} games`} />}
      {records.longestStreak && <LeaderRow label="LONGEST STREAK" player={records.longestStreak} value={`${records.longestStreak.bestStreak} in a row`} />}
      {records.currentStreak && <LeaderRow label="HOT STREAK" player={records.currentStreak} value={`${records.currentStreak.currentStreak} in a row`} />}
    </section>
  );
}

function LeaderRow({ label, player, value }: { label: string; player: Ranking; value: string }) {
  return (
    <div className="leader-row">
      <span className="leader-label">{label}</span>
      <Avatar person={player} size={30} />
      <span className="leader-name">{player.displayName}</span>
      <span className="leader-value">{value}</span>
    </div>
  );
}

function MonthlyWinnersCard({ winners }: { winners: MonthlyTrophy[] }) {
  return (
    <section className="card insight-card">
      <p className="section-label">MONTHLY WINNERS</p>
      <div className="winners-row">
        {winners.map(winner => (
          <div className="winner-tile" key={winner.month}>
            <span className="winner-avatar">
              <Avatar person={winner} size={60} />
              <span className="winner-crown" aria-hidden="true">👑</span>
            </span>
            <span className="winner-name">{winner.displayName}</span>
            <span className="winner-month">{monthlyTrophyLabel(winner.month)}</span>
          </div>
        ))}
      </div>
    </section>
  );
}

/**
 * Collapsed by default. Expanding reveals a player picker (defaulting to the signed-in
 * user) and fetches just that player's partner list — not eagerly with the rest of the
 * page, and not every pair in the group at once.
 */
function PartnersCard({ groupId, rankings, currentUserId }: { groupId: string; rankings: Ranking[]; currentUserId?: string }) {
  const [expanded, setExpanded] = useState(false);
  const [selectedId, setSelectedId] = useState<string>();

  // Default the picker to the signed-in user (or the first player) the first time the
  // card is expanded; a player with zero games can't have partners either, so the
  // leaderboard roster already excludes anyone who couldn't appear here anyway.
  useEffect(() => {
    if (expanded && !selectedId) {
      setSelectedId(rankings.find(r => r.userId === currentUserId)?.userId ?? rankings[0]?.userId);
    }
  }, [expanded, selectedId, rankings, currentUserId]);

  const partners = usePartners(groupId, expanded ? selectedId : undefined);
  const selected = rankings.find(r => r.userId === selectedId);

  return (
    <section className="card insight-card">
      <button className="section-label-toggle" onClick={() => setExpanded(v => !v)} aria-expanded={expanded}>
        <span className="section-label">PARTNERS</span>
        <span className="caret" aria-hidden="true">{expanded ? '▴' : '▾'}</span>
      </button>
      {!expanded ? (
        <p className="muted">Tap to see a player's partners</p>
      ) : (
        <>
          <PlayerPicker players={rankings} selected={selected} onSelect={setSelectedId} />
          {partners.isLoading && <p className="muted">Loading…</p>}
          {partners.error && <p className="muted">Couldn't load partners. Tap a player to retry.</p>}
          {partners.data && partners.data.length === 0 && <p className="muted">No completed matches with a teammate yet.</p>}
          {partners.data && partners.data.length > 0 && (
            <div className="partner-rows">
              {partners.data.map(partner => <PartnerRow key={partner.userId} partner={partner} />)}
            </div>
          )}
        </>
      )}
    </section>
  );
}

/** Avatar + name pill that opens a dropdown of every player on the leaderboard, same pattern as Board's range selector. */
function PlayerPicker({ players, selected, onSelect }: { players: Ranking[]; selected?: Ranking; onSelect: (userId: string) => void }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const onDown = (event: MouseEvent) => { if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false); };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);
  return (
    <div className="player-picker" ref={ref}>
      <button className="player-picker-trigger" onClick={() => setOpen(v => !v)} aria-haspopup="menu" aria-expanded={open}>
        {selected && <Avatar person={selected} size={28} />}
        <span>{selected?.displayName ?? 'Select a player'}</span>
        <span aria-hidden="true">{open ? '▴' : '▾'}</span>
      </button>
      {open && (
        <div className="player-picker-menu" role="menu">
          {players.map(player => (
            <button
              key={player.userId}
              role="menuitem"
              className={player.userId === selected?.userId ? 'selected' : ''}
              onClick={() => { onSelect(player.userId); setOpen(false); }}
            >
              <Avatar person={player} size={26} />
              <span>{player.displayName}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function PartnerRow({ partner }: { partner: Partner }) {
  return (
    <div className="partner-row">
      <Avatar person={partner} size={44} />
      <div className="partner-info">
        <strong>{partner.displayName}</strong>
        <span>{partner.winsTogether}W / {partner.gamesTogether} games together</span>
      </div>
      <span className="partner-rate" style={{ color: partner.avatarColor }}>{percent(partner.winRate)}%</span>
    </div>
  );
}

function BiggestWinCard({ biggestWin }: { biggestWin: BiggestWin }) {
  const { match, margin } = biggestWin;
  const winner = winningTeamNo(match);
  return (
    <section className="card insight-card">
      <div className="biggest-head">
        <p className="section-label">BIGGEST WIN · recent</p>
        <span className="margin-badge">+{margin} pts</span>
      </div>
      <TeamLine team={1} match={match} isWinner={winner === 1} />
      <TeamLine team={2} match={match} isWinner={winner === 2} />
      {match.sets.length > 0 && <p className="biggest-score">{match.sets.map(s => `${s.team1Score}-${s.team2Score}`).join(', ')}</p>}
    </section>
  );
}

function TeamLine({ team, match, isWinner }: { team: number; match: Match; isWinner: boolean }) {
  return (
    <p className={`team-line${isWinner ? ' winner' : ''}`}>
      <span className="team-w">{isWinner ? 'W' : ''}</span>
      {teamName(matchTeam(match, team))}
    </p>
  );
}
