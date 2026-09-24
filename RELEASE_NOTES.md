## Overview

This build is about not losing data. Before FitLens restores a backup or imports from FitNotes, it now keeps a safety
copy of your current data that you can go back to. Failures no longer disappear in a few seconds. Moving a workout
onto a day that already has one now merges them cleanly instead of duplicating entries.

## What's new

- **Safety copy, with Undo.** Before restoring a backup or importing from FitNotes, FitLens saves a copy of your
  current data on the phone. For 7 days, **Sync → Backups → Safety copy → Undo** puts that data back. A restore keeps
  a full copy, photos included; if there isn't room for it, the restore won't start. A FitNotes import keeps a copy of
  your data without photos, because imports never touch photos.

## Improved

- **Failures stay on screen until you've read them.** A failed restore, import or backup now opens a dialog that
  stays until you close it, instead of a message that vanished after about four seconds. Successes still show as a
  short message.
- **The last important result is kept.** Sync → Backups → Last result shows it, even after FitLens is closed and
  reopened. Clear it when you're done.
- **A restore that fails part-way now points you to Undo** to get your previous data back.
- **Release numbers no longer skip.** Pull-request checks used to share the release build counter, which is why 1.0.8
  was followed by 1.0.13. They now run separately, so each release gets the next number.

## Fixed

- **Moving a workout onto a day that already has one** now combines the two days' comments into one, destination
  first, and keeps a single start and finish time covering both. Before, the day ended up with duplicate comments
  and times, and a duplicate comment was lost the next time the comment was edited.
- **Undoing the deletion of an imported set restores it fully.** Before, the next FitNotes import still treated the
  set as deleted and wouldn't bring it back.

## Known limitations

- Only one safety copy is kept. Each new restore or import replaces it, including an automatic FitNotes folder sync
  that finds a newer backup.
- Undoing a FitNotes import doesn't remove photos added since then. Their files stay on the phone, but FitLens no
  longer lists them.
- A FitNotes import doesn't offer Undo in its own message; use Sync → Backups instead.
- Personal records still aren't calculated for sets logged in FitLens, and Settings still live on the Sync tab.

## Upgrading

Install over your existing FitLens. Your data is kept, and this build adds no new database fields.
