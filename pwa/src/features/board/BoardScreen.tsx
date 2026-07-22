import { useMemo, useState } from 'react';
import type { Ranking, User } from '../../models';
import { Avatar, Card } from '../../components';
import { formatRate, sortRankings, type SortKey } from '../../domain';

type Props = { rankings: Ranking[]; user: User; onPlayer: (ranking: Ranking) => void; onShare: () => void };
const sortOptions: [SortKey, string][] = [['rank', 'POS'], ['gamesPlayed', 'GP'], ['wins', 'W'], ['losses', 'L'], ['pointsFor', 'PF'], ['winRate', 'WIN%']];

export function BoardScreen({ rankings, user, onPlayer, onShare }: Props) {
  const [sort, setSort] = useState<SortKey>('rank');
  const sorted = useMemo(() => sortRankings(rankings, sort), [rankings, sort]);
  return <>
    <div className="title-row"><div><p className="eyebrow">YOUR LEADERBOARD</p><h2>Board <span className="online-dot" /></h2></div><button className="icon-button" onClick={onShare} aria-label="Share leaderboard">↗</button></div>
    {rankings.length === 0 ? <div className="empty"><h3>No matches yet</h3><p>Record your first game to start the board.</p></div> : <>
      <Podium rankings={rankings} onPlayer={onPlayer} />
      <Card className="rankings"><div className="card-heading"><span>RANKINGS</span><span>{rankings.length} PLAYERS</span></div><div className="sorts">{sortOptions.map(([key, label]) => <button key={key} className={sort === key ? 'selected' : ''} onClick={() => setSort(key)}>{label}</button>)}</div>{sorted.map((ranking, index) => <button className="ranking-row" key={ranking.userId} onClick={() => onPlayer(ranking)}><span className="rank">{sort === 'rank' ? ranking.rank : index + 1}</span><Avatar person={ranking} /><span className="player-name">{ranking.displayName}{ranking.userId === user.id && <small>YOU</small>}</span><span className="row-stat">{sort === 'winRate' ? formatRate(ranking.winRate) : ranking[sort]}</span></button>)}</Card>
    </>}
  </>;
}

function Podium({ rankings, onPlayer }: { rankings: Ranking[]; onPlayer: (ranking: Ranking) => void }) {
  return <div className="podium">{rankings.slice(0, 3).map((ranking, index) => <button key={ranking.userId} className={`podium-player p${index + 1}`} onClick={() => onPlayer(ranking)}><span className="medal">{index === 0 ? '♛' : index + 1}</span><Avatar person={ranking} size="lg" /><strong>{ranking.displayName.split(' ')[0]}</strong><small>{formatRate(ranking.winRate)} win rate</small></button>)}</div>;
}
