## Overview

This build completes FitLens's data tools and welcomes new users properly. A fresh install now opens a short guided
setup. Settings gains **Data tools**, with CSV export and a way to delete workout history safely, and backups can be
shared straight from the app. The home screen is tidier too. None of your existing data changes.

## What's new

- **Guided setup.** The first time FitLens opens, a short setup walks you through:
  - restoring a FitLens backup if you're moving phones;
  - choosing kilograms or pounds;
  - picking a folder for automatic backups;
  - importing a FitNotes backup;
  - adding your progress photos;
  - adding the starter exercise library.

  Every step can be skipped, and **Settings → Run setup again** brings it back at any time. If you already use
  FitLens, you won't see it unless you open it.
- **CSV export.** **Settings → Data tools → Export as CSV** writes your workouts or body data for any date range, with
  weights in kilograms or pounds, to a file or straight to the share sheet. It's ready for Excel or Google Sheets. The
  page lists the columns. CSV files are for spreadsheets only: FitLens restores from backups, not CSVs.
- **Delete workout history.** **Settings → Data tools → Delete workout history** removes logged sets by date range,
  by exercise or both. You see how many sets and workouts will go before you confirm. Your exercises, categories,
  workout comments, photos and body data are kept, and personal records are worked out again afterwards. FitLens
  takes a safety copy first, so **Settings → Backups → Undo** can put everything back for 7 days.
- **Share a backup.** **Settings → Backups → Share backup** makes a full backup and opens the share sheet, so you can
  send it to email, Drive, Dropbox or any app you already use. FitLens itself still never goes online.
- **Backup file names.** A new switch in Settings → Backups lets you leave the date and time out of the names of
  backups you save or share. It's on by default. Automatic backups always include it.

## Improved

- **A cleaner home screen.** The line about your last FitNotes import no longer sits at the top of the Log tab. It
  lives in **Settings → FitNotes import**, next to the import itself, along with the workout and set counts.
- **FitNotes history stays deleted.** If you delete sets that came from FitNotes, importing the same FitNotes backup
  again won't bring them back.

## Known limitations

- Week start, and choosing a default set of body measurements, will join the setup when those settings arrive.
- Deleting history removes sets only. A workout's comment and times stay on that day.
- CSV export covers workouts and body data. Photos are included only in `.fitlens` backups.
