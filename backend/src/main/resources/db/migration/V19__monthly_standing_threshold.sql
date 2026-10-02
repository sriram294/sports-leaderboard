-- Freeze the qualification threshold alongside each monthly standings verdict.
alter table monthly_trophy add column min_games_to_rank int not null default 1;
