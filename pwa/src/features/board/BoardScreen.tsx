import { useEffect, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import type { Ranking, User } from '../../models';
import { Avatar, FormPill } from '../../components';
import { Icon } from '../../icons';
import {
  METRIC_LABEL,
  RANGE_LABEL,
  metricColor,
  metricValue,
  nextMetric,
  podium as podiumOf,
  rankColor,
  ratingLabel,
  secondaryLine,
  tableRows,
  winRatePercent,
  type RankingSortMetric,
  type TimeRange,
} from '../../domain';
import crown from '../../assets/brand/crown-3d.png';

type Props = {
  rankings: Ranking[];
  minGamesToRank: number;
  groupId: string;
  user: User;
  range: TimeRange;
  onRangeChange: (range: TimeRange) => void;
  recentForm: boolean[];
  onPlayer: (userId: string) => void;
  onShare: () => void;
};

/**
 * Board (home) tab — see docs/pwa/requirements/02-board-leaderboard.md and the Android
 * `ui/board/*`. The "TOP PLAYERS" header (with the calendar-window selector) is always
 * shown, so an empty window still lets the user switch ranges; below it sit the podium,
 * the RANKINGS card (whose header cycles the sort metric), and a pinned "YOUR FORM" bar.
 */
export function BoardScreen({ rankings, minGamesToRank, groupId, range, onRangeChange, recentForm, onPlayer, onShare }: Props) {
  const [metric, setMetric] = useState<RankingSortMetric>('rating');
  // A different group is a different board, so the metric resets to the default.
  useEffect(() => setMetric('rating'), [groupId]);

  const podium = podiumOf(rankings);
  const rows = tableRows(rankings, metric);
  const showForm = recentForm.length > 0 && rankings.length > 0;

  return (
    <div className={`board${showForm ? ' has-form' : ''}`}>
      <div className="board-head">
        <div className="board-head-title">
          <span className="eyebrow">TOP PLAYERS</span>
          <RangeSelector range={range} onChange={onRangeChange} />
        </div>
        <button className="icon-button board-share" onClick={onShare} aria-label="Share leaderboard">
          <Icon name="share" size={18} />
        </button>
      </div>

      {rankings.length === 0 ? (
        <p className="board-empty">
          {range === 'month'
            ? 'No matches this month yet. Record one to rank this month.'
            : 'No matches recorded yet. Rankings appear after the first match.'}
        </p>
      ) : (
        <>
          <div className="podium">
            <PodiumSlot entry={podium[1]} onPlayer={onPlayer} />
            <PodiumSlot entry={podium[0]} champion onPlayer={onPlayer} />
            <PodiumSlot entry={podium[2]} onPlayer={onPlayer} />
          </div>

          <section className="card rankings">
            <div className="rankings-title">RANKINGS</div>
            <div className="rankings-header">
              <span className="col-rank">#</span>
              <span className="col-player">PLAYER</span>
              <button className="col-metric" onClick={() => setMetric(nextMetric(metric))}>
                {METRIC_LABEL[metric]} <span aria-hidden="true">▾</span>
              </button>
            </div>
            {rows.map((row, index) => {
              const prevRanked = index === 0 || !rows[index - 1].provisional;
              const boundary = prevRanked && !!row.provisional;
              return (
                <button
                  key={row.userId}
                  className={`ranking-row${boundary ? ' boundary' : ''}${row.provisional ? ' provisional' : ''}`}
                  onClick={() => onPlayer(row.userId)}
                >
                  <span className="col-rank" style={{ color: row.provisional ? 'var(--muted)' : rankColor(row.rank) }}>
                    {row.provisional ? '—' : row.rank}
                  </span>
                  <Avatar person={row} size={32} />
                  <span className="col-player">
                    <span className="ranking-name">{row.displayName}</span>
                    <span className="ranking-sub">{secondaryLine(row, minGamesToRank)}</span>
                  </span>
                  <span className="col-metric value" style={{ color: metricColor(row, metric) }}>
                    {metricValue(row, metric)}
                  </span>
                </button>
              );
            })}
          </section>
        </>
      )}

      {showForm && (
        <div className="form-bar-wrap">
          <div className="form-bar">
            <div className="form-bar-label">
              <span className="eyebrow">YOUR FORM</span>
              <span className="form-bar-sub">Last {recentForm.length} {recentForm.length === 1 ? 'match' : 'matches'}</span>
            </div>
            <div className="form-bar-pills">
              {recentForm.map((win, index) => <FormPill key={index} win={win} />)}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * The muted calendar-window selector: a label + caret that opens a small menu. Deliberately
 * low-key so it reads as a refinement of "TOP PLAYERS" rather than a primary control.
 */
function RangeSelector({ range, onChange }: { range: TimeRange; onChange: (range: TimeRange) => void }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const onDown = (event: MouseEvent) => { if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false); };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);
  return (
    <div className="range-selector" ref={ref}>
      <button className="range-trigger" onClick={() => setOpen(value => !value)} aria-haspopup="menu" aria-expanded={open}>
        {RANGE_LABEL[range]} <span aria-hidden="true">▾</span>
      </button>
      {open && (
        <div className="range-menu" role="menu">
          {(['month', 'all'] as TimeRange[]).map(option => (
            <button
              key={option}
              role="menuitem"
              className={option === range ? 'selected' : ''}
              onClick={() => { onChange(option); setOpen(false); }}
            >
              {RANGE_LABEL[option]}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * One podium column; a missing entry (fewer than 3 ranked players) leaves the slot empty.
 * The champion sits center, larger and crowned; runners-up flank it a step lower. Each
 * avatar wears a colored ring + glow with a numbered badge tucked at its bottom edge.
 */
function PodiumSlot({ entry, champion = false, onPlayer }: { entry?: Ranking; champion?: boolean; onPlayer: (userId: string) => void }) {
  if (!entry) return <div className="podium-slot empty" />;
  const size = champion ? 94 : 64;
  return (
    <button
      className={`podium-slot${champion ? ' champion' : ''}`}
      style={{ '--ring': entry.avatarColor } as CSSProperties}
      onClick={() => onPlayer(entry.userId)}
    >
      <div className="podium-avatar">
        {champion && <img className="podium-crown" src={crown} alt="" aria-hidden="true" />}
        <Avatar person={entry} size={size} />
        <span className="podium-badge">{entry.rank}</span>
      </div>
      <span className="podium-name">{entry.displayName}</span>
      <span className="podium-metric" style={champion ? { color: entry.avatarColor } : undefined}>
        {entry.rating != null ? `${ratingLabel(entry)} rating` : `${winRatePercent(entry)}% win rate`}
      </span>
    </button>
  );
}
