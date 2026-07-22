import type { Ranking } from '../../models';
import { Avatar, Card } from '../../components';
import { formatRate } from '../../domain';

export function PlayerScreen({ ranking, onBack }: { ranking: Ranking; onBack: () => void }) {
  return <><button className="back" onClick={onBack}>← Board</button><div className="player-hero"><Avatar person={ranking} size="lg" /><h2>{ranking.displayName}</h2><p>Player profile</p></div><div className="stat-grid"><Card><strong>{ranking.gamesPlayed}</strong><span>Games</span></Card><Card><strong>{ranking.wins}</strong><span>Wins</span></Card><Card><strong>{formatRate(ranking.winRate)}</strong><span>Win rate</span></Card><Card><strong>{ranking.bestStreak}</strong><span>Best streak</span></Card></div></>;
}
