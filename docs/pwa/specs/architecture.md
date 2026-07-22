# Architecture

`src/app` owns bootstrap and shell; `src/features` owns vertical slices; `src/components` owns reusable controls; `src/data` owns transport/auth/models; `src/domain` contains pure computations. Components render immutable state and emit events. URL state is reserved for durable deep links; forms stay local.
