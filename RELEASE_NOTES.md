## Overview

FitLens is now public. This is the first release anyone can download, and it's the same app as 1.0.3: a
companion to FitNotes that pairs your progress photos with your workouts and body measurements, day by day. If you
already have FitLens installed, this release installs over it and keeps all your data.

## Highlights
- **Imports your FitNotes backups:** workouts, sets, PRs, comments, workout times and every Body Tracker
  measurement, with automatic sync from your backup folder.
- **Dates your progress photos automatically** from their camera data, even when you import hundreds at once.
- **Every day in one place:** photos, measurements and workout together, with Log, Day, Calendar, Body, Training
  and Photos views.
- **Custom metrics.** Track anything, by hand or filled in from matching FitNotes measurements.
- **Progress videos** with up to five metrics shown over your photos, as values or animated charts, exported as MP4.
- **Before-and-after comparisons** you can save or share.
- **A luxury black, imperial purple and gold design.**

## What's new since 1.0.3
- **Open to everyone.** Download releases without a GitHub account, and report bugs or request features with the
  new issue forms.
- **Open to contributions.** Added a contribution guide. Every pull request now gets an automatic test build.
- **Secure release signing.** The official signing key now lives only in encrypted build secrets, so only official
  releases can update your installed app.

No app features changed in this release.

## Known limitations
- Android doesn't let one app read another app's data, so FitLens can't trigger FitNotes backups itself. The
  backup-folder sync is the closest to automatic that Android allows.
- Photos are copied into FitLens. Deleting a photo from your gallery doesn't remove it from FitLens, and the other
  way round.
