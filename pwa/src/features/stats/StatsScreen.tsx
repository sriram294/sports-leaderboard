import type { Ranking } from '../../models';
import { Avatar, Card } from '../../components';
import { formatRate } from '../../domain';

export function StatsScreen({ rankings }: { rankings: Ranking[] }) {
  const games = rankings.reduce((total, ranking) => total + ranking.gamesPlayed, 0) / 4;
  const wins = rankings.reduce((total, ranking) => total + ranking.wins, 0) / 2;
  const best = rankings.reduce<Ranking | undefined>((current, ranking) => !current || ranking.winRate > current.winRate ? ranking : current, undefined);
  return <><p className="eyebrow">YOUR GROUP INSIGHTS</p><h2>Stats</h2><div className="stat-grid"><Card><strong>{Math.round(games)}</strong><span>Matches</span></Card><Card><strong>{Math.round(wins)}</strong><span>Wins</span></Card><Card><strong>{best ? formatRate(best.winRate) : '—'}</strong><span>Top win rate</span></Card><Card><strong>{Math.max(0, ...rankings.map(ranking => ranking.bestStreak))}</strong><span>Best streak</span></Card></div>{best && <Card><div className="card-heading"><span>IN FORM</span><span>TOP PLAYER</span></div><div className="insight"><Avatar person={best} /><div><strong>{best.displayName}</strong><p className="muted">{best.wins} wins from {best.gamesPlayed} games</p></div><span className="lime">{formatRate(best.winRate)}</span></div></Card>}</>;
}
