## Overview

This update finishes moving FitLens's wording into one place in the app. The photo screens, first-run setup, the rest
timer and its notifications, the calculators, the PDF report, the body fat calculator and the shared controls used on
every screen now take their text from the app's own string resources, ready for future translations. Counts read
properly throughout, for example "1 photo needs its date checked", "1 workout" and "about 1 rep".

There's no database change. Everything you've logged, your photos, poses and settings are kept. Install it over the
current app as usual.

## Improved

- **Photos.** The gallery, its selection bar, the pose and date sheets, Check photo dates, the photo viewer, Compare
  and the photo picker use consistent wording. Pose names, photo groupings and where a photo's date came from are
  worded the same way everywhere, including Settings.
- **Slideshow and video.** The options sheet, the play controls and the video's own day and week counter and pose
  label use the same wording, as do the shared workout and graph images.
- **First-run setup.** Every step, from units and automatic backups to FitNotes imports, photos and the starter
  library, is reworded consistently, with counts that read correctly for one or many.
- **Rest timer.** The timer sheet, the top-bar countdown, the "Rest over" message and the notification and its
  buttons use the same wording. TalkBack reads rest lengths and set times as "1 minute 30 seconds".
- **Calculators.** The set calculator and plate calculator, including "Each side" and what's loaded on the bar.
- **PDF progress report.** The cover, Body measurements, Training and Daily log sections and their counts.
- **Body fat calculator.** The measurement names, where to measure, and the reasons a result can't be worked out.
- **Everywhere.** Back, Cancel, OK, Save and the other shared buttons, the range and options menus, trend lines
  under graphs, and what TalkBack reads for graphs, set rows and reorder handles.

## Known limitations

- Messages built while saving, importing or backing up (backup and restore results, the FitNotes import summary's
  lines and photo import results) still keep their own wording. They will move over in a later update.
- Your own names (exercises, measurements, custom metrics) are shown as you entered them.
