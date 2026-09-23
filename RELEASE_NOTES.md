## Overview

This release starts FitLens's move to being your main workout logger. FitNotes imports now **merge** into FitLens
instead of replacing its data, and they show exactly what will be added before anything changes. Automatic backups
now run in the background, even when FitLens is closed, and they stay on your phone. Progress photos are easier to
organise by pose: you choose the pose while importing, filter and group by it, and change many photos at once.

## What's new

### FitNotes imports that merge
- **Imports only add, never replace.** Importing a FitNotes backup adds its new workouts, sets, comments, workout
  times and body tracker records. Anything already in FitLens is skipped. Nothing you created or edited in FitLens is
  deleted or overwritten, and importing the same backup twice changes nothing.
- **See what an import adds first.** Before a FitNotes backup is imported, FitLens shows what it will add and what it
  will skip, with an option to **save a FitLens backup first**. This applies to picked files, backups shared from
  FitNotes, and **Sync now**.
- Exercises and categories are matched by name, so imported history and history logged in FitLens join up.
- Renames and deletions you make in FitLens are remembered, so a later import doesn't bring back what you removed.

### Automatic backups in the background
- Daily or weekly automatic backups now run **in the background**, even if FitLens isn't opened, whenever the
  battery isn't low. They're saved only to the folder you chose and never go online.
- **Back up after changes** (optional): when you leave FitLens after changing something, a backup is saved in the
  background, at most once an hour.
- If the backup folder can't be reached (for example, the SD card was removed or access was lost), a
  **notification** explains how to fix it. Tapping it opens the backup settings.

### Photo poses
- **Choose a pose when importing:** every bulk import (chosen photos, a whole folder, **Add photos** on a day, or
  photos shared from your gallery) asks whether the photos are Front, Side, Back, Other or Not set, and applies that
  pose to all of them.
- **Filter and group by pose:** the pose filters on the Photos tab show how many photos have each pose. A new
  **Group by** option shows photos by month or by pose.
- **Bulk pose editing:** each month or pose section has a **Select** button, and selection mode has **Select all
  shown**. You can re-tag a whole month, a pose group or everything in the current filter in a few taps.

## Improved
- The backup settings now show the **last successful backup**, the **next scheduled backup**, the **free space** in
  the backup folder, and the last failed attempt if there was one.
- **Automatic sync from the FitNotes backup folder is now off by default.** If you already use it, it stays on after
  this update.
- The photo viewer has a labelled pose row, with **Not set** to clear a pose.
- The photo grid fits more columns on larger screens and in landscape.
- Groundwork for logging in FitLens: every exercise, category, set, comment and workout time now records whether it
  came from FitNotes or was created in FitLens. Your existing data is upgraded automatically and kept in full.

## Known limitations
- The screens for logging workouts directly in FitLens are coming in later updates.
- Background backups follow Android's battery rules, so a scheduled backup can run a few hours after it's due.
- On Android 13 and newer, FitLens asks for the notification permission when automatic backups are first set up. If
  you decline it, problems still show in the backup settings but no notification appears.
- If a set you already imported is later edited or deleted in FitNotes, the next import adds the changed version as
  a new set.
