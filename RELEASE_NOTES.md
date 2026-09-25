## Overview

This build tidies the app around one Settings screen. The Sync tab has gone: importing from FitNotes now sits in
Settings with backups, and photo import is on the Photos tab. A new **Workout & logging** page lets you choose how
the set entry screen behaves. Full-screen graphs can now change their range and options without closing. None of
your workouts, photos or measurements change.

## What's new

- **Workout & logging settings.** A new page in Settings (under the gear on every tab) with three options:
  - **Keep the screen on** while you log sets. This is on by default, as before, and you can now turn it off.
  - **Fill new sets from** your last workout (as before), or **Leave empty** to start every new set blank.
  - **Select the next set** after updating one. This helps with a copied workout: adjust and update each set in turn
    without tapping the next one.

  These preferences travel with your `.fitlens` backups.
- **Range and options in full screen.** Full-screen graphs now show the 1M / 3M / 6M / 1Y / All range chips, plus
  **Trend** and **From zero**, so you can change the view while you zoom and pan.

## Improved

- **One place for your data.** Settings has a **Data, backup & import** group holding **Backups** and
  **FitNotes import**. FitNotes import works exactly as before: you see what will be added first, and it merges
  without changing anything you logged in FitLens.
- **Simpler bottom bar.** The Sync tab is gone, leaving five tabs: Log, Calendar, Body, Training and Photos. Photo
  import (choose photos, or import a whole folder) is on the Photos tab's **+** button and on each day's screen.
- **Shared FitNotes backups** now open Settings → FitNotes import, which shows what the backup adds before anything
  is imported.
- **Clearer for TalkBack.** The button that leaves a full-screen graph now reads "Close full screen".
- **Leaner backups.** Old phone-only settings that 1.0.21 moved out of the database are removed from it, so they no
  longer ride along in new backups. Your folders, schedules and other settings on this phone are kept.

## Known limitations

- Full-screen zoom works on the time axis. The vertical scale fits whatever stretch you're looking at, but you can't
  zoom it on its own yet.
- "Fill new sets from" will add routine plans as a third choice once routines arrive.
- CSV export, deleting history by date range, sharing a backup, and re-running first-run setup will join the
  Data, backup & import group in later builds.
