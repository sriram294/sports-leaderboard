# State and navigation

The authenticated shell owns user, groups, active group, destination, and shared data revision. Switching groups reloads dependent data and clears transient drill-down state. A future router may encode board/player/match deep links, but transient forms must not be serialized into URLs.
