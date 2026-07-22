import { useState } from 'react';
import type { Group, Match, MatchDetail, MatchTeam } from '../../models';
import { Avatar } from '../../components';
import { useMatchDetail } from '../../queries';
import {
  actionLabel,
  groupMatchesByDate,
  matchTeam,
  teamName,
  timeLabel,
  winningTeamNo,
} from '../../domain';

type Props = {
  group: Group;
  groupId: string;
  matches: Match[];
  currentUserId?: string;
  canModerate: boolean;
  mine: boolean;
  onToggleMine: () => void;
  canLoadMore: boolean;
  isLoadingMore: boolean;
  onLoadMore: () => void;
  onDelete: (matchId: string) => Promise<void>;
  onEdit: (matchId: string) => void;
};

/**
 * Matches log — see docs/pwa/requirements/03-matches.md and Android `ui/matches/*`.
 * Matches are grouped into collapsible per-day sections (newest day expanded by default);
 * each card expands in place to fetch full detail (game breakdown, winner, audit history)
 * with edit/delete for the recorder or a group moderator.
 */
export function MatchHistoryScreen(props: Props) {
  const { matches, mine, onToggleMine, canLoadMore, isLoadingMore, onLoadMore } = props;
  const [expandedDates, setExpandedDates] = useState<Record<string, boolean>>({});
  const [expandedId, setExpandedId] = useState<string>();

  const sections = groupMatchesByDate(matches);
  const newestDate = sections[0]?.date;
  const isDateExpanded = (date: string) => expandedDates[date] ?? date === newestDate;
  const toggleDate = (date: string) => setExpandedDates(prev => ({ ...prev, [date]: !isDateExpanded(date) }));

  return (
    <div className="matches">
      <div className="matches-filter">
        <span className="matches-count">{matches.length} {matches.length === 1 ? 'match' : 'matches'}</span>
        <button
          className={`mine-pill${mine ? ' active' : ''}`}
          onClick={onToggleMine}
          aria-pressed={mine}
        >
          My matches
        </button>
      </div>

      {matches.length === 0 ? (
        <p className="matches-empty">
          {mine ? "You haven't played any matches in this group yet." : 'No matches recorded yet. Tap + to add the first one.'}
        </p>
      ) : (
        <>
          {sections.map(section => {
            const open = isDateExpanded(section.date);
            return (
              <div className="date-section" key={section.date}>
                <button className="date-header" onClick={() => toggleDate(section.date)} aria-expanded={open}>
                  <span>{section.label} · {section.matches.length} {section.matches.length === 1 ? 'match' : 'matches'}</span>
                  <span className="caret" aria-hidden="true">{open ? '▴' : '▾'}</span>
                </button>
                {open && section.matches.map(match => (
                  <MatchCard
                    key={match.id}
                    match={match}
                    groupId={props.groupId}
                    expanded={expandedId === match.id}
                    currentUserId={props.currentUserId}
                    canModerate={props.canModerate}
                    onToggle={() => setExpandedId(id => (id === match.id ? undefined : match.id))}
                    onEdit={() => props.onEdit(match.id)}
                    onDelete={props.onDelete}
                  />
                ))}
              </div>
            );
          })}

          {canLoadMore && (
            <button className="load-older" onClick={onLoadMore} disabled={isLoadingMore}>
              {isLoadingMore ? 'Loading…' : 'Load older matches'}
            </button>
          )}
        </>
      )}
    </div>
  );
}

function MatchCard({ match, groupId, expanded, currentUserId, canModerate, onToggle, onEdit, onDelete }: {
  match: Match;
  groupId: string;
  expanded: boolean;
  currentUserId?: string;
  canModerate: boolean;
  onToggle: () => void;
  onEdit: () => void;
  onDelete: (matchId: string) => Promise<void>;
}) {
  const winner = winningTeamNo(match);
  const detail = useMatchDetail(groupId, expanded ? match.id : undefined);

  return (
    <div className="match-card">
      <button className="match-row" onClick={onToggle} aria-expanded={expanded}>
        <TeamBlock team={matchTeam(match, 1)} isWinner={winner === 1} />
        <div className="match-score">
          {match.sets.map(set => (
            <span key={set.setNo}>{set.team1Score} – {set.team2Score}</span>
          ))}
        </div>
        <TeamBlock team={matchTeam(match, 2)} isWinner={winner === 2} alignEnd />
        <span className="caret" aria-hidden="true">{expanded ? '▴' : '▾'}</span>
      </button>

      {expanded && (
        <div className="match-detail">
          {detail.isLoading && <p className="muted">Loading…</p>}
          {detail.error && <p className="muted">Couldn't load match details.</p>}
          {detail.data && (
            <ExpandedDetail
              detail={detail.data}
              canModify={canModerate || detail.data.recordedBy.userId === currentUserId}
              onEdit={onEdit}
              onDelete={onDelete}
            />
          )}
        </div>
      )}
    </div>
  );
}

/** One side of a match: a row of avatars (with a "W" badge on the inner edge when winning)
 *  over the players' names. The right team aligns to the end, badge toward the score. */
function TeamBlock({ team, isWinner, alignEnd = false }: { team?: MatchTeam; isWinner: boolean; alignEnd?: boolean }) {
  const players = team?.players ?? [];
  return (
    <div className={`team-block${alignEnd ? ' end' : ''}`}>
      <div className="team-avatars">
        {alignEnd && isWinner && <WinnerBadge />}
        {players.map(player => <Avatar key={player.userId} person={player} size={30} />)}
        {!alignEnd && isWinner && <WinnerBadge />}
      </div>
      <span className={`team-names${isWinner ? ' winner' : ''}`}>{teamName(team)}</span>
    </div>
  );
}

function WinnerBadge() {
  return <span className="winner-badge" aria-label="Winner">W</span>;
}

function ExpandedDetail({ detail, canModify, onEdit, onDelete }: {
  detail: MatchDetail;
  canModify: boolean;
  onEdit: () => void;
  onDelete: (matchId: string) => Promise<void>;
}) {
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const winner = winningTeamNo(detail);
  const winnerName = winner != null ? teamName(matchTeam(detail, winner)) : null;

  const confirmDelete = async () => {
    setDeleting(true);
    try { await onDelete(detail.id); } finally { setDeleting(false); setConfirming(false); }
  };

  return (
    <>
      <p className="detail-label">GAME BREAKDOWN</p>
      {detail.sets.map(set => (
        <p className="detail-line" key={set.setNo}>Set {set.setNo}:  {set.team1Score} – {set.team2Score}</p>
      ))}
      {winnerName && <p className="detail-winner">Winner: {winnerName}</p>}

      <p className="detail-label history">HISTORY</p>
      {detail.events.map((event, index) => (
        <p className="detail-history" key={index}>{event.displayName} · {actionLabel(event.action)} · {timeLabel(event.createdAt)}</p>
      ))}

      {canModify && (
        <div className="detail-actions">
          <button className="outline-action edit" onClick={onEdit}>Edit match</button>
          <button className="outline-action delete" onClick={() => setConfirming(true)}>Delete match</button>
        </div>
      )}

      {confirming && (
        <div className="confirm-backdrop" onClick={() => !deleting && setConfirming(false)}>
          <div className="confirm-dialog" role="alertdialog" aria-modal="true" onClick={event => event.stopPropagation()}>
            <h3>Delete match?</h3>
            <p className="muted">This permanently removes the match and updates the leaderboard.</p>
            <div className="confirm-actions">
              <button className="confirm-cancel" onClick={() => setConfirming(false)} disabled={deleting}>Cancel</button>
              <button className="confirm-delete" onClick={confirmDelete} disabled={deleting}>{deleting ? 'Deleting…' : 'Delete'}</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
