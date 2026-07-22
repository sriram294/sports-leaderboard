import { useMemo } from 'react';
import type { Match, MonthlyTrophy, Ranking } from '../../models';
import { Avatar, FormPill } from '../../components';
import {
  computeBestPartnership,
  computeBiggestWin,
  computeRecentForm,
  computeRecords,
  matchTeam,
  monthlyTrophyLabel,
  percent,
  teamName,
  winRatePercent,
  winningTeamNo,
  type BestPartnership,
  type BiggestWin,
  type PlayerForm,
  type Records,
} from '../../domain';

type Props = {
  rankings: Ranking[];
  matchCount: number;
  matches: Match[];
  trophies: MonthlyTrophy[];
};

/**
 * Stats / Insights — see docs/pwa/requirements/06-stats.md and Android `ui/stats/*`.
 * A group-level dashboard: all-time RECORDS from the leaderboard + group match count, then
 * MONTHLY WINNERS (served) and the recent-window sections (BEST PARTNERSHIP / FORM / BIGGEST
 * WIN) derived client-side from the first page of matches — no new endpoints.
 */
export function StatsScreen({ rankings, matchCount, matches, trophies }: Props) {
  const records = useMemo(() => computeRecords(rankings, matchCount), [rankings, matchCount]);
  const partnership = useMemo(() => computeBestPartnership(matches), [matches]);
  const form = useMemo(() => computeRecentForm(matches, rankings), [matches, rankings]);
  const biggestWin = useMemo(() => computeBiggestWin(matches), [matches]);

  if (matchCount === 0 && matches.length === 0) {
    return <p className="stats-empty">Play some matches to see insights.</p>;
  }

  return (
    <div className="stats">
      <RecordsCard records={records} />
      {trophies.length > 0 && <MonthlyWinnersCard winners={trophies} />}
      {partnership && <BestPartnershipCard partnership={partnership} />}
      {form.length > 0 && <RecentFormCard form={form} />}
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

function BestPartnershipCard({ partnership }: { partnership: BestPartnership }) {
  return (
    <section className="card insight-card partnership-card">
      <p className="section-label">BEST PARTNERSHIP · recent</p>
      <div className="partnership-row">
        <span className="partnership-avatars">
          <Avatar person={partnership.player1} size={44} />
          <Avatar person={partnership.player2} size={44} />
        </span>
        <div className="partnership-info">
          <strong>{partnership.player1.displayName} & {partnership.player2.displayName}</strong>
          <span>{partnership.winsTogether}W / {partnership.gamesTogether} games together</span>
        </div>
        <span className="partnership-rate">{percent(partnership.winRate)}%</span>
      </div>
    </section>
  );
}

function RecentFormCard({ form }: { form: PlayerForm[] }) {
  return (
    <section className="card insight-card">
      <p className="section-label">FORM · recent</p>
      <div className="form-rows">
        {form.map(row => (
          <div className="form-row" key={row.player.userId}>
            <Avatar person={row.player} size={34} />
            <span className="form-player">{row.player.displayName}</span>
            <span className="form-pills">
              {row.results.map((win, index) => <FormPill key={index} win={win} />)}
            </span>
          </div>
        ))}
      </div>
    </section>
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
