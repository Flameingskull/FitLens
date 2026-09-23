# FitLens — progress photos for FitNotes

FitLens is a standalone Android app that sits alongside FitNotes. It reads your FitNotes backups
and matches every progress photo to its date, so each day shows the photo *and* the FitNotes data together.

## What it does

- **Imports FitNotes backups** (`.fitnotes`). These contain everything: workouts, sets, PRs, comments, workout times,
  exercises, categories, all body tracker measurements and custom measurements. It also accepts Body Tracker CSV exports.
- **Semi-automatic sync:** point FitLens at the folder where FitNotes saves its backups. Whenever you open FitLens it
  imports the newest backup if it has changed. You can also share a backup from FitNotes straight into FitLens.
- **Bulk photo import:** choose many photos, a whole folder, or share photos from your gallery. Each photo is dated from:
  1. camera metadata (EXIF "date taken"),
  2. then the media library date,
  3. then a date in the file name (for example `IMG_20230826_132000.jpg` or `PXL_…`, `Screenshot_2023-08-26…`),
  4. then the file's modified date. Photos dated this way are flagged for you to check.

  Duplicates are detected and skipped, so re-importing a folder is safe.
- **Manual entry:** add photos to a specific day, and add measurements by hand. A manual entry is merged automatically
  once the same value arrives in a FitNotes backup.
- **Views:**
  - **Log:** a timeline of every day, with that day's photos, measurements and workout summary.
  - **Day:** everything for one date. It shows the photos; the measurements with the change since the previous entry;
    and the full workout with sets, PRs, comments and duration. If there's no photo that day, it shows the nearest one.
  - **Calendar:** a month grid with photo thumbnails and dots for photo, measurement and workout category.
  - **Body:** for each measurement, a graph (1M/3M/6M/1Y/All), stats (start, latest, change, min, max, weekly rate)
    and a history table. Days with photos are marked on the graph. Tapping a point shows that day's photo.
  - **Training:** exercises grouped by category. Each exercise has graphs (est. 1RM, max weight, volume, reps, time),
    its history, and rep-max records (actual and estimated).
  - **Photos:** a gallery grouped by month with pose tags (Front/Side/Back/Other), multi-select, a full-screen
    viewer with that day's measurements, and a **before/after compare** that you can share or save as an image.
- **Slideshow and video:** plays your photos in date order with overlays: the date, day/week counter, pose, chosen
  measurements (with change since the start) and a moving progress chart. It exports an **MP4 video**, made on the phone,
  in Portrait HD, Full HD or Square. The video is saved to *Movies/FitLens* and can be shared.
- **Archive:** save or restore everything (photos included) as one `.zip` file.

FitLens only *reads* FitNotes backups. It never changes your FitNotes data.

---

## Download and install

1. On your Android phone (Android 10 or newer), open the
   [latest release](https://github.com/Flameingskull/FitLens/releases/latest) and download `FitLens-1.0.N.apk`.
   No GitHub account is needed.
2. Open the downloaded file. Android will ask you to allow installs from your browser or file manager. Allow it once.
3. New releases install **over** the old one and keep all your data.

Each release also includes the full source code, SHA-256 checksums and a list of what changed.

## Report a bug or request a feature

Use the [Issues](https://github.com/Flameingskull/FitLens/issues/new/choose) tab and choose **Bug report** or
**Feature request**. You need a free GitHub account. Every report is triaged, and fixes arrive in the next release.

## Contribute code

See [CONTRIBUTING.md](CONTRIBUTING.md). In short: fork the repository, make your change, and open a pull request.
GitHub builds a test APK for every pull request.

## Build it yourself

Open the project in **Android Studio** and click **Run**, or run `./gradlew assembleDebug`. Your own builds are
signed with your debug key, so Android won't install them over the official release. Test on an emulator or a
spare phone, or save an archive (Sync → Save archive) and uninstall the official app first.
## First-time setup (in the app)

1. **Sync tab → Import backup file** and choose your latest `FitNotes_Backup_….fitnotes`.
2. **Sync tab → Choose folder** and pick the folder where FitNotes saves its backups. From then on, making a backup
   in FitNotes and opening FitLens updates everything automatically.
3. **Photos tab → + → Import a whole folder** (for example your camera folder or a "Progress" album) or choose photos.
   FitLens dates each photo and places it on the right day.
4. If any photos had no camera date, a banner says **"N photos need their date checked"**. Tap it to accept or fix the dates.
5. Tag poses (Front/Side/Back) in the photo viewer or with multi-select. Slideshows and comparisons can then use one pose.

## Limits

- Android doesn't let one app read another app's private data, and FitNotes has no interface for other apps. So FitLens
  can't pull data out of FitNotes directly or make FitNotes create a backup. The backup-folder sync above is the closest
  to automatic that Android allows. It works best if you keep the backups FitNotes saves to your phone in one folder.
- Photos are copied into FitLens, so deleting a photo in your gallery doesn't remove it from FitLens (and vice versa).
  Use **Save archive** before changing phones.
