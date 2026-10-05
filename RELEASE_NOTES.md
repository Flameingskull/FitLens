## Overview

This update moves the messages FitLens shows after backing up, restoring and importing into the app's own string
resources, alongside the rest of its wording. Backup and restore results, the FitNotes import summary, photo import
results and the automatic-backup notification now read consistently, and their counts read properly for one or many,
for example "Backup saved with 1 photo", "1 new workout day" and "1 needs its date checked".

There's no database change. Everything you've logged, your photos, poses and settings are kept, and existing backups
restore exactly as before. Install it over the current app as usual.

## Improved

- **Backups.** Saving, sharing and restoring a backup, the safety copy taken before a restore or import, and Undo all
  report their results in the same wording, including the clear explanations when something can't be done (not
  enough room for a safety copy, a backup from a newer FitLens, or a damaged file).
- **Automatic backups.** The result of each automatic backup, the messages when the backup folder can't be reached,
  and the notification FitLens posts when a backup doesn't finish.
- **FitNotes import.** The summary shown before importing ("Will be added" and "Already in FitLens"), the result
  afterwards, the folder sync's messages, and the Body Tracker CSV import. Counts no longer read "1 sets" or
  "1 exercises".
- **Photo import.** The message after importing photos, with how each photo was dated and how many need their date
  checked.

## Known limitations

- Messages from saving workouts and exercises (for example "There's already an exercise called …") and the starter
  library's summary still keep their own wording. They move over in the next update.
- Your own names (exercises, measurements, custom metrics) are shown as you entered them.
