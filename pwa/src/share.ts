import type { Group, Ranking } from './models';

export function leaderboardText(group: Group | undefined, rankings: Ranking[]) {
  return `${group?.name || 'Playboard'} leaderboard — ${rankings.slice(0, 3).map(r => `${r.rank}. ${r.displayName}`).join(', ')}`;
}

export async function leaderboardImage(group: Group | undefined, rankings: Ranking[]): Promise<File> {
  const canvas = document.createElement('canvas');
  canvas.width = 1200;
  canvas.height = 760;
  const context = canvas.getContext('2d');
  if (!context) throw new Error('Image sharing is unavailable in this browser.');
  context.fillStyle = '#0a0a0a'; context.fillRect(0, 0, canvas.width, canvas.height);
  context.fillStyle = '#9ade28'; context.font = '800 30px system-ui'; context.fillText('PLAYBOARD', 72, 82);
  context.fillStyle = '#f5f5f5'; context.font = '800 64px system-ui'; context.fillText(group?.name || 'Leaderboard', 72, 165);
  context.fillStyle = '#9e9e9e'; context.font = '24px system-ui'; context.fillText('BADMINTON · LEADERBOARD', 74, 210);
  rankings.slice(0, 5).forEach((ranking, index) => {
    const y = 285 + index * 82;
    context.fillStyle = index === 0 ? '#9ade28' : '#666'; context.font = '800 28px system-ui'; context.fillText(`${ranking.rank}`, 80, y);
    context.fillStyle = '#f5f5f5'; context.font = '600 30px system-ui'; context.fillText(ranking.displayName, 150, y);
    context.fillStyle = '#9ade28'; context.font = '800 28px system-ui'; context.textAlign = 'right'; context.fillText(`${Math.round(ranking.winRate * 100)}%`, 1110, y); context.textAlign = 'left';
    context.fillStyle = '#292929'; context.fillRect(72, y + 26, 1038, 1);
  });
  const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/png'));
  if (!blob) throw new Error('Could not create the share image.');
  return new File([blob], 'playboard-leaderboard.png', { type: 'image/png' });
}

export async function shareLeaderboard(group: Group | undefined, rankings: Ranking[]) {
  const text = leaderboardText(group, rankings);
  try {
    const image = await leaderboardImage(group, rankings);
    if (navigator.share && (!navigator.canShare || navigator.canShare({ files: [image] }))) { await navigator.share({ title: 'Playboard leaderboard', text, files: [image] }); return 'shared' as const; }
  } catch (error) { if (error instanceof DOMException && error.name === 'AbortError') return 'cancelled' as const; }
  if (navigator.share) { await navigator.share({ title: 'Playboard leaderboard', text }); return 'shared' as const; }
  if (navigator.clipboard) { await navigator.clipboard.writeText(text); return 'copied' as const; }
  const link = document.createElement('a'); link.href = URL.createObjectURL(await leaderboardImage(group, rankings)); link.download = 'playboard-leaderboard.png'; link.click(); URL.revokeObjectURL(link.href); return 'downloaded' as const;
}
