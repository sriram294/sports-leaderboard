import { useId, useMemo, useRef, useState } from 'react';
import type { MatchSet, MonthlyFinish, Partner, PlayerStats } from '../../models';
import { Avatar } from '../../components';
import { Icon } from '../../icons';
import { usePartners } from '../../queries';
import {
  WEEKDAYS,
  dateLabel,
  monthShortLabel,
  percent,
  recentMatchRow,
  streakLabel,
  weekColumns,
  type HeatMonth,
  type RecentMatchRow,
} from '../../domain';
import { deriveFinishChart, finishDescription, finishesDescription, finishMonthLabel } from './monthly-finishes';

/** The bundled default avatars available in the picker (`public/avatars/avatar0..15.png`). */
const AVATAR_IDS = Array.from({ length: 16 }, (_, i) => `avatar${i}`);

export type ProfileIdentity = { displayName: string; photoUrl?: string | null; avatarId?: string | null; avatarColor: string };
export type ProfileAttendance = { months: HeatMonth[]; activeDays: Set<string> };

type Props = {
  groupId: string;
  stats: PlayerStats;
  isOwn: boolean;
  identity: ProfileIdentity;
  attendance?: ProfileAttendance;
  onRename?: (name: string) => Promise<void>;
  onSelectAvatar?: (avatarId: string) => Promise<void>;
  onUploadPhoto?: (file: File) => Promise<void>;
  onOpenSettings?: () => void;
  onOpenGroups?: () => void;
  onBack?: () => void;
};

/**
 * Profile / player stats — see docs/pwa/requirements/05-profile.md and Android `ui/profile/*`.
 * Own profile shows the top settings/manage-groups icons and the edit affordances (rename
 * pencil, avatar "+" badge → picker/upload); a viewed player (Board drill-down) is read-only
 * with a back button. Below the hero: 2×3 stat tiles, activity heatmap, best partner, recent matches.
 */
export function ProfileScreen({ groupId, stats, isOwn, identity, attendance, onRename, onSelectAvatar, onUploadPhoto, onOpenSettings, onOpenGroups, onBack }: Props) {
  const [avatarSheet, setAvatarSheet] = useState(false);
  const [renaming, setRenaming] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string>();

  const rows = useMemo(() => stats.recentMatches.map(m => recentMatchRow(m, stats.userId)), [stats]);

  const uploadPhoto = async (file: File) => {
    if (!onUploadPhoto) return;
    setAvatarSheet(false);
    setUploading(true);
    setError(undefined);
    try { await onUploadPhoto(file); } catch { setError('Could not upload the photo.'); } finally { setUploading(false); }
  };
  const selectAvatar = async (avatarId: string) => {
    if (!onSelectAvatar) return;
    setAvatarSheet(false);
    setError(undefined);
    try { await onSelectAvatar(avatarId); } catch { setError('Could not update your avatar.'); }
  };

  return (
    <div className="profile">
      {isOwn ? (
        <div className="profile-top-actions">
          <button className="icon-button" onClick={onOpenSettings} aria-label="Settings"><Icon name="settings" size={22} /></button>
          <button className="icon-button" onClick={onOpenGroups} aria-label="Manage groups"><Icon name="group" size={22} /></button>
        </div>
      ) : (
        <button className="back" onClick={onBack}><Icon name="back" size={16} /> Back</button>
      )}

      <div className="profile-hero">
        <HeroAvatar identity={identity} editable={isOwn} uploading={uploading} onEdit={() => setAvatarSheet(true)} />
        <div className="hero-name">
          {isOwn && <span className="hero-name-spacer" />}
          <h2>{identity.displayName}</h2>
          {isOwn && (
            <button className="edit-badge pencil" onClick={() => setRenaming(true)} aria-label="Edit name">
              <Icon name="edit" size={14} />
            </button>
          )}
        </div>
        <p className="hero-meta">
          <span className="hero-winrate">{percent(stats.winRate)}%</span> win rate
          <span className="meta-dot" />
          <Icon name="matches" size={14} className="meta-icon" /> {stats.matchesPlayed} {stats.matchesPlayed === 1 ? 'match' : 'matches'}
        </p>
      </div>

      {error && <p className="form-error">{error}</p>}

      <div className="stat-tiles">
        <StatTile label="WINS" value={stats.wins} />
        <StatTile label="LOSSES" value={stats.losses} />
        <StatTile label="PTS FOR" value={stats.pointsFor} />
        <StatTile label="CURRENT STREAK" value={streakLabel(stats.currentStreak)} accent />
        <StatTile label="BEST STREAK" value={stats.bestStreak} />
        <StatTile label="PTS AGNST" value={stats.pointsAgainst} />
      </div>

      <MonthlyFinishesChart finishes={stats.monthlyFinishes ?? []} />

      {attendance && attendance.months.length > 0 && <ActivityHeatmap attendance={attendance} />}

      <PartnersCard groupId={groupId} userId={stats.userId} />

      {rows.length > 0 && (
        <>
          <p className="section-label">RECENT MATCHES</p>
          <div className="recent-list">
            {rows.map(row => <RecentMatchCard key={row.matchId} row={row} />)}
          </div>
        </>
      )}

      {avatarSheet && (
        <AvatarSheet onClose={() => setAvatarSheet(false)} onSelect={selectAvatar} onUpload={uploadPhoto} />
      )}
      {renaming && onRename && (
        <RenameSheet current={identity.displayName} onClose={() => setRenaming(false)} onRename={onRename} />
      )}
    </div>
  );
}

function MonthlyFinishesChart({ finishes }: { finishes: MonthlyFinish[] }) {
  const titleId = useId();
  const descriptionId = useId();
  const chart = useMemo(() => deriveFinishChart(finishes), [finishes]);
  const width = 640;
  const height = 210;
  const left = 28;
  const right = width - 28;
  const top = 28;
  const bottom = 168;
  const position = (point: { x: number; y: number }) => ({
    x: left + (right - left) * point.x,
    y: top + (bottom - top) * point.y,
  });

  return (
    <>
      <p className="section-label">MONTHLY FINISHES</p>
      <div
        className="card monthly-finishes-card"
        role={chart.points.length === 0 ? 'img' : undefined}
        aria-label={chart.points.length === 0 ? finishesDescription(chart.finishes) : undefined}
      >
        {chart.points.length === 0 ? (
          <p className="muted">Finishing positions are recorded after each month closes.</p>
        ) : (
          <svg className="monthly-finishes-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-labelledby={`${titleId} ${descriptionId}`}>
            <title id={titleId}>Monthly finishing positions</title>
            <desc id={descriptionId}>{chart.finishes.map(finishDescription).join('. ')}</desc>
            {[0, .5, 1].map(fraction => {
              const y = top + (bottom - top) * fraction;
              return <line key={fraction} className="finish-grid" x1={left} x2={right} y1={y} y2={y} />;
            })}
            {chart.segments.map(segment => {
              const from = position(segment.from);
              const to = position(segment.to);
              return <line key={`${segment.from.index}-${segment.to.index}`} className="finish-line" x1={from.x} y1={from.y} x2={to.x} y2={to.y} />;
            })}
            {chart.points.map(point => {
              const p = position(point);
              return <g key={point.index}>
                <circle className="finish-point-ring" cx={p.x} cy={p.y} r="7" />
                <circle className="finish-point" cx={p.x} cy={p.y} r="4" />
                <text className="finish-rank" x={p.x} y={p.y - 11}>#{point.rank}</text>
              </g>;
            })}
            {chart.finishes.map((finish, index) => {
              const x = left + (right - left) * index / Math.max(1, chart.finishes.length - 1);
              return <text className="finish-month" key={finish.month} x={x} y={198}>{finishMonthLabel(finish.month)}</text>;
            })}
          </svg>
        )}
      </div>
    </>
  );
}

function HeroAvatar({ identity, editable, uploading, onEdit }: { identity: ProfileIdentity; editable: boolean; uploading: boolean; onEdit: () => void }) {
  return (
    <div className="hero-avatar">
      <span className="hero-avatar-ring">
        <Avatar person={identity} size={96} />
      </span>
      {uploading && <span className="hero-avatar-spinner" aria-label="Uploading" />}
      {editable && (
        <button className="edit-badge" onClick={onEdit} aria-label="Change avatar"><Icon name="add" size={16} /></button>
      )}
    </div>
  );
}

function StatTile({ label, value, accent = false }: { label: string; value: string | number; accent?: boolean }) {
  return (
    <div className="stat-tile">
      <strong className={accent ? 'accent' : undefined}>{value}</strong>
      <span>{label}</span>
    </div>
  );
}

function ActivityHeatmap({ attendance }: { attendance: ProfileAttendance }) {
  return (
    <>
      <p className="section-label">ACTIVITY</p>
      <div className="card heatmap">
        <div className="heatmap-axis">
          <span className="heatmap-header-spacer" />
          {WEEKDAYS.map(day => <span key={day} className="heatmap-weekday">{day}</span>)}
        </div>
        <div className="heatmap-months">
          {attendance.months.map(month => (
            <div className="heatmap-month" key={`${month.year}-${month.month}`}>
              <span className="heatmap-month-label">{monthShortLabel(month)}</span>
              <div className="heatmap-weeks">
                {weekColumns(month).map((column, ci) => (
                  <div className="heatmap-col" key={ci}>
                    {column.map((day, di) => (
                      day == null
                        ? <span key={di} className="heatmap-cell blank" />
                        : <span key={di} className={`heatmap-cell${attendance.activeDays.has(day) ? ' active' : ''}`} />
                    ))}
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}

/**
 * Collapsed by default — the partner list is only fetched from the server once the user
 * taps to expand, not eagerly with the rest of the profile (mirrors the Matches screen's
 * expand-to-fetch card detail).
 */
function PartnersCard({ groupId, userId }: { groupId: string; userId: string }) {
  const [expanded, setExpanded] = useState(false);
  const partners = usePartners(groupId, expanded ? userId : undefined);
  return (
    <>
      <button className="section-label-toggle" onClick={() => setExpanded(v => !v)} aria-expanded={expanded}>
        <span className="section-label">PARTNERS</span>
        <span className="caret" aria-hidden="true">{expanded ? '▴' : '▾'}</span>
      </button>
      <div className="card">
        {!expanded && <p className="muted">Tap to see who you've partnered with</p>}
        {expanded && partners.isLoading && <p className="muted">Loading…</p>}
        {expanded && partners.error && <p className="muted">Couldn't load partners. Tap to retry.</p>}
        {expanded && partners.data && partners.data.length === 0 && <p className="muted">No completed matches with a teammate yet.</p>}
        {expanded && partners.data && partners.data.length > 0 && (
          <div className="partner-rows">
            {partners.data.map(partner => <PartnerRow key={partner.userId} partner={partner} />)}
          </div>
        )}
      </div>
    </>
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

function RecentMatchCard({ row }: { row: RecentMatchRow }) {
  return (
    <div className={`recent-card ${row.isWin ? 'win' : 'loss'}`}>
      <div className="recent-head">
        <span className={`result-badge ${row.isWin ? 'win' : 'loss'}`}>{row.isWin ? 'WIN' : 'LOSS'}</span>
        <span className="recent-date">{dateLabel(new Date(row.playedAt))}</span>
      </div>
      <p className="recent-teams">
        {row.partnerNames && <>w/ {row.partnerNames} </>}vs {row.opponentNames}
      </p>
      {row.sets.length > 0 && <p className="recent-score">{scoreLine(row.sets)}</p>}
    </div>
  );
}

const scoreLine = (sets: MatchSet[]) => sets.map(s => `${s.team1Score}-${s.team2Score}`).join(', ');

function AvatarSheet({ onClose, onSelect, onUpload }: { onClose: () => void; onSelect: (avatarId: string) => void; onUpload: (file: File) => void }) {
  const fileInput = useRef<HTMLInputElement>(null);
  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet" role="dialog" aria-modal="true" onClick={event => event.stopPropagation()}>
        <div className="sheet-head">
          <span>Choose avatar</span>
          <button className="icon-button" onClick={onClose} aria-label="Close"><Icon name="close" size={20} /></button>
        </div>
        <button className="upload-photo" onClick={() => fileInput.current?.click()}>
          <Icon name="add" size={18} /> Upload a photo
        </button>
        <input
          ref={fileInput} type="file" accept="image/*" hidden
          onChange={event => { const file = event.target.files?.[0]; if (file) onUpload(file); event.target.value = ''; }}
        />
        <div className="avatar-grid">
          {AVATAR_IDS.map(avatarId => (
            <button key={avatarId} className="avatar-choice" onClick={() => onSelect(avatarId)} aria-label={`Choose ${avatarId}`}>
              <img src={`/avatars/${avatarId}.png`} alt="" loading="lazy" />
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

function RenameSheet({ current, onClose, onRename }: { current: string; onClose: () => void; onRename: (name: string) => Promise<void> }) {
  const [name, setName] = useState(current);
  const [saving, setSaving] = useState(false);
  const [failed, setFailed] = useState(false);
  const submit = async () => {
    if (!name.trim() || saving) return;
    setSaving(true);
    setFailed(false);
    try { await onRename(name.trim()); onClose(); } catch { setFailed(true); setSaving(false); }
  };
  return (
    <div className="sheet-backdrop" onClick={() => !saving && onClose()}>
      <div className="sheet" role="dialog" aria-modal="true" onClick={event => event.stopPropagation()}>
        <div className="sheet-head">
          <span>Edit name</span>
          <button className="icon-button" onClick={onClose} aria-label="Close"><Icon name="close" size={20} /></button>
        </div>
        <input
          className="rename-input" value={name} autoFocus aria-label="Display name"
          onChange={event => setName(event.target.value)}
          onKeyDown={event => { if (event.key === 'Enter') submit(); }}
        />
        {failed && <p className="form-error">Couldn't save. Try again.</p>}
        <button className="record-button" disabled={!name.trim() || saving} onClick={submit}>{saving ? 'Saving…' : 'Save'}</button>
      </div>
    </div>
  );
}
