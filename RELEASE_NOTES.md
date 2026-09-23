## Overview

This release adds a complete backup system that works entirely on your phone. Save everything to a single file,
back up automatically to a folder you choose, restore on a new phone, or create a readable PDF report of your
progress. FitLens needs no account, sign-in or internet connection, and nothing is sent to online services.

## What's new

### Backup files
- **Save a full backup:** go to **Sync → Backups → Save backup**. It creates one `.fitlens` file with all your data and
  photos. Keep a copy off your phone, such as on a computer, USB drive or SD card.
- **Restore after reinstalling, or on a new phone:** use **Restore backup**, or just open a `.fitlens` file from your
  file manager. FitLens shows what the backup contains (date made, photos, workouts, body records, dates covered)
  and asks before replacing anything.
- **Safe restores:** the backup is unpacked and checked before your current data is touched. If a file is damaged or
  was made by a newer version of FitLens, the restore stops and your data stays as it was.
- Archives saved with earlier versions of FitLens can still be restored.

### Automatic backups
- Choose a folder outside FitLens (for example Documents, Downloads or an SD card) and FitLens backs up there
  **daily or weekly**, checked each time you open the app. Backups in that folder survive uninstalling the app.
- FitLens keeps the newest **3, 5 or 10** backups and removes older ones automatically.
- Use **Back up now** any time. Interrupted backups are cleaned up and never mistaken for complete ones.

### PDF progress report
- **Create PDF report** produces a readable, printable record in the FitLens style:
  - a **cover** with your first and latest photos side by side and totals for the period;
  - **measurement cards**, each with a chart (photo days marked) and start, latest, change, low and high values;
  - a **training summary** showing sessions, best set and estimated 1RM for every exercise;
  - a **daily log** with each day's photos, pose, measurements (with change since the previous entry), workout
    duration, sets, PRs and comments.
- Choose the period (all, last year, 3 months, 1 month or custom dates), the sections, 0–4 photos per day, and
  whether to include only days with photos.
- **Dark** pages match the app. **Light** pages are designed for printing. There's an optional high-quality photo
  setting, and an estimate of the file size before you create it.

### New phone transfer
- On Android 12 and newer, FitLens data moves across when you set up a new phone by cable or direct phone-to-phone
  transfer.
- Google cloud backup is now turned off for FitLens, so your photos and data aren't copied to online services.

## Improved
- A fresh install now offers **Restore from a backup** on the first screen.

## Known limitations
- On Android 10 and 11, phone-to-phone transfer can't be separated from cloud backup, so it's turned off. Use a
  backup file instead.
- Very large PDF reports (hundreds of photos) can take a few minutes. Fewer photos per day or a shorter period is
  faster and gives a smaller file.
