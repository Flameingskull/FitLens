# FitLens

**Your workouts, body stats and progress photos in one private Android app.**

[![Latest release](https://img.shields.io/github/v/release/Flameingskull/FitLens?label=latest%20release)](https://github.com/Flameingskull/FitLens/releases/latest)
[![Build](https://github.com/Flameingskull/FitLens/actions/workflows/build.yml/badge.svg)](https://github.com/Flameingskull/FitLens/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

FitLens puts your training history, body measurements and progress photos together, day by day. It lets you see what
your body looked like next to what you lifted and what you measured. Everything stays on your phone.

## Purpose and where it's heading

FitLens started as a companion to [FitNotes](https://www.fitnotesapp.com/). It reads FitNotes backups and matches
each progress photo to that day's workout and measurements. It is now being built into **a full workout logger in its
own right**. The aim is to record workouts the way FitNotes does, plus what FitNotes can't: photos, progress
videos, PDF reports and custom metrics.

- **Today (1.0.6 and later):** FitLens imports and shows your FitNotes history, manages progress photos, tracks body
  measurements and custom metrics, makes slideshows, videos and PDF reports, and backs everything up locally. The
  data model already records whether each exercise, set and workout came from FitNotes or was created in FitLens.
- **In progress:** logging workouts directly in FitLens
  ([#6](https://github.com/Flameingskull/FitLens/issues/6), the foundation for most logging features), followed by a
  proper Settings screen ([#38](https://github.com/Flameingskull/FitLens/issues/38)) and the rest of the
  [feature request list](https://github.com/Flameingskull/FitLens/issues?q=is%3Aissue+is%3Aopen+label%3Aenhancement).
- **FitNotes stays supported** as an import source. You can import during first-run setup or at any time afterwards.
  Imports always merge and never overwrite FitLens data. There's no export back to FitNotes.

### Principles

- **Local only.** No accounts, no cloud services, no subscriptions and **no internet permission**. Google cloud backup
  is turned off for FitLens. Backups go only to a folder you choose.
- **Every update installs over the last one** and keeps your data. Database changes are always migrated, never reset.
- **Android phones** (Android 10 or newer), across phone screen sizes. A self-hosted web version may come later.

---

## What it does

- **Imports FitNotes backups** (`.fitnotes`), which contain workouts, sets, PRs, comments, workout times, exercises,
  categories, all body tracker measurements and custom measurements. It also accepts Body Tracker CSV exports.
  Before importing, FitLens shows what will be added and what is skipped as already present, and offers to save a
  FitLens backup first (see [FitNotes imports and your FitLens data](#fitnotes-imports-and-your-fitlens-data)).
- **Optional FitNotes folder sync** for moving over gradually. Point FitLens at the folder where FitNotes saves its
  backups, then tap **Sync now**, or turn on automatic sync to import the newest backup whenever FitLens opens.
  Automatic sync is **off by default**. You can also share a backup from FitNotes straight into FitLens.
- **Bulk photo import:** choose many photos or a whole folder, or share photos from your gallery. You pick the pose
  (Front, Side, Back or Other) for the batch as you import. Each photo is dated from:
  1. camera metadata (EXIF "date taken"),
  2. then the media library date,
  3. then a date in the file name (for example `IMG_20230826_132000.jpg`, `PXL_…` or `Screenshot_2023-08-26…`),
  4. then the file's modified date. Photos dated this way are flagged for you to check.

  Duplicates are detected and skipped, so re-importing a folder is safe.
- **Manual entry:** add photos to a specific day and add measurements by hand. If the same value later arrives in a
  FitNotes backup, the FitNotes copy is skipped and your entry is kept.
- **Custom metrics:** create your own measurements, such as calories, sleep or a tape measurement FitNotes doesn't
  have, with their own unit. You can link one to a FitNotes measurement so imports fill it in.
- **Views:**
  - **Log:** a timeline of every day, with that day's photos, measurements and workout summary.
  - **Day:** everything for one date: the photos, the measurements with the change since the previous entry, and the
    full workout with sets, PRs, comments and duration. If there's no photo that day, it shows the nearest one.
  - **Calendar:** a month grid with photo thumbnails, and dots for photos, measurements and workout categories.
  - **Body:** for each measurement, a graph (1M/3M/6M/1Y/All), stats (start, latest, change, min, max, weekly rate)
    and a history table. Days with photos are marked on the graph, and tapping a point shows that day's photo.
  - **Training:** exercises grouped by category. Each exercise has graphs (est. 1RM, max weight, volume, reps, time),
    its history, and rep-max records (actual and estimated).
  - **Photos:** a gallery you can group by month or by pose, with pose filters and counts. It also has multi-select
    for bulk pose tagging, a full-screen viewer with that day's measurements, and a **before/after compare** you can
    share or save as an image.
- **Slideshow and video:** plays your photos in date order with overlays: the date, a day or week counter, the pose,
  chosen measurements (with the change since the start) and a moving progress chart. It exports an **MP4 video**,
  rendered on the phone, in Portrait HD, Full HD or Square. Videos are saved to *Movies/FitLens* and can be shared.
- **PDF report:** a readable report with your photos, measurement charts, training summary and a daily log, in dark
  (as in the app) or light (for printing).
- **Backups, all on your phone** (on the **Sync** tab, under **Backups**):
  - **Backup file:** save everything, photos included, as one `.fitlens` file. Restore it after reinstalling or on a
    new phone, or open it straight from a file manager.
  - **Automatic backups:** daily or weekly to a folder you choose (for example Documents or an SD card), so they
    survive uninstalling. Only the newest few are kept. They run in the background through Android's job scheduler,
    even when FitLens is closed, whenever the battery isn't low. Optionally, **Back up after changes** saves a backup
    when you leave FitLens after changing something, at most once an hour.
  - **Status and alerts:** the Backups section shows the last successful backup, the next scheduled one and the
    folder's free space. If the folder can't be reached (the SD card was removed or access was lost), a notification
    explains how to fix it.
  - **Phone-to-phone transfer** (Android 12+) carries FitLens data across when you set up a new phone with a cable or
    a direct transfer.

FitLens only *reads* FitNotes backups and never changes your FitNotes data. FitLens backups (`.fitlens`) are for
FitLens only.

**Permissions:** the only one FitLens asks for is notifications (Android 13+), and only when you set up automatic
backups. Photos and folders are accessed through Android's file pickers, and only the ones you choose.

## FitNotes imports and your FitLens data

Every category, exercise, set, workout comment and workout time records who owns it: **FitNotes** (imported) or
**FitLens** (created or edited in FitLens). FitLens gives every row its own id and keeps the FitNotes id only for
reference, so the two never collide. Importing a FitNotes backup follows these rules:

1. An import only **adds**. It never deletes, edits or overwrites anything already in FitLens, whoever created it.
2. Categories and exercises are matched **by name**, ignoring upper and lower case, so imported history and history
   logged in FitLens join up under one exercise. If both exist, the FitLens version is kept as it is.
3. A set counts as already present when FitLens has a set on the same date, for the same exercise, with the same
   weight, reps, distance and time. Identical sets are counted, so 3 × 5 × 100 kg in the backup matches three such
   sets in FitLens. Importing the same backup twice changes nothing.
4. Workout comments and times are skipped when the same comment, or the same start and end, is already on that date.
   Body measurements are skipped when the same measurement, date, time and value exists, or when you entered the same
   value by hand that day.
5. **Your changes in FitLens win.** After you rename an exercise or category, the FitNotes name still maps to it.
   After you delete imported data, or edit an imported set, comment or time, the next import doesn't bring the
   original back. Re-creating a deleted exercise with the same name lets its FitNotes history import again.

The rules are also documented in the code (`data/Workouts.kt`).

---

## Download and install

1. On your Android phone (Android 10 or newer), open the
   [latest release](https://github.com/Flameingskull/FitLens/releases/latest) and download `FitLens-1.0.N.apk`.
   No GitHub account is needed.
2. Open the downloaded file. Android will ask you to allow installs from your browser or file manager. Allow it once.
3. New releases install **over** the old one and keep all your data.

Each release includes the APK, the full source code, SHA-256 checksums and professionally written notes on what
changed. The version number goes up with every build (`1.0.<build>`).

## First-time setup (in the app)

1. **Sync tab → Import backup file**, then choose your latest `FitNotes_Backup_….fitnotes`.
2. Optional, if you keep using FitNotes for a while: **Sync tab → FitNotes backup folder → Choose folder** and pick
   the folder where FitNotes saves its backups. Tap **Sync now** after making a backup in FitNotes, or turn on
   automatic sync so FitLens imports the newest backup each time it opens.
3. **Photos tab → + → Import a whole folder** (for example your camera folder or a "Progress" album), or choose
   photos. FitLens dates each photo and places it on the right day.
4. If any photos had no camera date, a banner says **"N photos need their date checked"**. Tap it to accept or fix
   the dates.
5. Tag poses (Front/Side/Back) while importing, in the photo viewer, or with multi-select. Slideshows and comparisons
   can then use one pose.
6. **Sync tab → Backups:** choose a backup folder and turn on automatic backups.

## Limits

- Workouts can't be logged directly in FitLens yet. That's the next major step
  ([#6](https://github.com/Flameingskull/FitLens/issues/6)). Until then, log in FitNotes and import.
- Settings live on the **Sync** tab for now. A dedicated Settings screen is planned
  ([#38](https://github.com/Flameingskull/FitLens/issues/38)).
- Android doesn't let one app read another app's private data, and FitNotes has no interface for other apps. So
  FitLens can't pull data out of FitNotes directly or make FitNotes create a backup. Folder sync is the closest to
  automatic that Android allows. It works best if FitNotes saves its backups to one folder on your phone.
- If a set you already imported is later edited or deleted in FitNotes, the next import adds the changed version as a
  new set.
- Background backups follow Android's battery rules, so a scheduled backup can run a few hours after it's due.
- Photos are copied into FitLens, so deleting a photo in your gallery doesn't remove it from FitLens, and the reverse.
  Save a backup, or turn on automatic backups, before changing phones.

---

## Report a bug or request a feature

Use the [Issues](https://github.com/Flameingskull/FitLens/issues/new/choose) tab and choose **Bug report** or
**Feature request**. You need a free GitHub account. Every report is triaged, and the planned work is visible in the
open issues. Fixes and features arrive in the next release.

## Contribute code

See [CONTRIBUTING.md](CONTRIBUTING.md). In short: fork the repository, make your change and open a pull request.
GitHub builds a test APK for every pull request.

## Build it yourself

The app is native Android: **Kotlin** and **Jetpack Compose** (Material 3), with a local SQLite database. It targets
Android 15 (API 35) and runs on Android 10 (API 29) or newer.

Open the project in **Android Studio** and click **Run**, or run `./gradlew assembleDebug`. Your own builds are signed
with your debug key, so Android won't install them over the official release. Test on an emulator or a spare phone.
Alternatively, save a backup first (**Sync → Backups → Save backup**) and uninstall the official app.

Every push to `main` is built and signed by GitHub Actions and published as a release. Pull requests get a debug
build only.

## Maintenance

Dependencies and Actions are kept current automatically by **Dependabot** (`.github/dependabot.yml`): Gradle
dependencies weekly, GitHub Actions monthly. Kotlin, the Compose compiler plugin and KSP arrive as one grouped pull
request, because their versions are locked to one another and updating them separately breaks the build. Dependabot
pull requests run the `check` job only — a debug build with no secrets — so nothing is published until a merge.

**Every year, before 31 August**, raise `compileSdk` and `targetSdk` to the API level Google Play then requires,
raising AGP and the Gradle wrapper if that level needs it, and review the behaviour changes for that Android version:
permissions, the photo picker, the foreground services and notifications used by the rest timer and automatic
backups, and edge-to-edge. Confirm the exact level against Google's current policy page at the time.

Updates never change `applicationId` (`com.fitlens.companion`), the signing setup, the signing secrets or
`BUILD_OFFSET`. Every build stays an update that installs over the previous one and keeps its data.

## License

[MIT](LICENSE) © 2026 Flameingskull
