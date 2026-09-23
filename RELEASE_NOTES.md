## Overview

This is a documentation and project-housekeeping release. **The app itself is unchanged from 1.0.6.** It's published
as usual so every push has a matching release. Installing it over 1.0.6 is safe and keeps all your data, but it isn't
needed.

## Improved

- **README rewritten** to match the app as it is today and where it's heading:
  - FitLens's purpose: a private, local-only workout logger with progress photos, growing out of its FitNotes
    companion roots.
  - What works now and what's in progress, with links to the foundation work (#6) and the planned Settings screen
    (#38).
  - Features that were missing from the README: custom metrics, choosing the pose while importing, grouping photos
    by pose, and the backup status and alerts.
  - A clear permissions and privacy summary, an updated first-time setup (including automatic backups), and the tech
    stack for anyone building from source.
- **Project workflow:** improvements the project agents find while working (missing features, gaps, or more stable
  ways of doing things) are now always filed as feature requests. As a result, new requests #36–#40 cover a move of the
  database to Room, standard screen navigation, a proper settings layer, automatic dependency updates and automated
  tests for imports, backups and database upgrades.

## Known limitations

- Workouts can't be logged directly in FitLens yet. Log in FitNotes and import until #6 ships.
- Settings are still on the **Sync** tab until the dedicated Settings screen (#38) arrives.
