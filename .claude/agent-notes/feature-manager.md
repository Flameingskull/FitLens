# feature-manager notes

What earlier runs learned that isn't obvious from the code or `docs/CODEMAP.md`. One dated line each; delete lines
that stop being true. The repository is public: no personal data, secrets or `FIL.n` keys.

- 2026-09-25: No local Android SDK. CI is the compiler, so check imports, named arguments and `@OptIn`s by hand.
  1.0.17 failed on a missing experimental opt-in for the Material 3 top bar.
- 2026-09-25: Every workout write goes through `Workouts.write`, which reloads the whole `Snapshot`. Don't reload again
  from the UI.
- 2026-09-25: 1RM and PR logic lives only in `data/Records.kt` (#23). 1.0.19 and 1.0.20 shipped everything except
  the PR celebration, which shipped in 1.0.21 (#23 closed).
- 2026-09-25: PR rule: a set is a PR when it's strictly heavier than every earlier set of at least as many reps
  (ties aren't PRs). New sets get it on save (`Workouts.addSet`); `Workouts.recalculatePrs` replays history.
- 2026-09-25: Settings (1.0.21, #38): never call `Db.getMeta` / `setMeta` for a setting; use `data/Settings.kt`.
  The phone-only `meta` rows left for downgrades are cleaned up by #98. The importer's `weight_unit` write is the
  one deliberate exception.
- 2026-09-25: The name `Settings` clashes with `androidx.compose.material.icons.filled.Settings`. Don't import both
  in one file; `FitTopBar(onSettings = …)` already draws the gear.
- 2026-09-25: Charts (1.0.22, #50): every graph uses `ui/Charts.kt` / `ui/ChartViews.kt`, with colours from
  `LocalChartColors.palette` via `seriesColor(i)`. `FrameRenderer` has its own unrelated `ChartSeries` (video), so
  don't reuse that name. #96 still needs Y-axis zoom and the buttons on the redesign's future graph screens.
- 2026-09-25: Cleaning `meta` rows that DataStore's one-time migration copies must happen *after* DataStore loads
  (1.0.23, #98), never in `Db.onUpgrade`: the upgrade runs first, so a user jumping from 1.0.20 would lose them.
  Data-only clean-ups like this don't need a `Db.VERSION` bump. The Sync tab is gone (#35); FitNotes import lives in
  Settings → FitNotes import (`ui/FitNotesCards.kt`), and a new full-screen graph passes `GraphOptionChips` as
  `controls`.
