## Overview

This update is about FitLens's own strengths: your progress photos, the slideshow and video, and the PDF report. They
now remember how you like them. A new **Settings → Progress Photos & Media** page sets the defaults once, and the
photo screens have been rebuilt in the same black-and-gold design as the rest of the app. Training graphs also gain
a first link to your photos: tap a point and the progress photo nearest that date appears beneath it.

There's no database change. The new preferences are stored like your other settings, and they travel with your
`.fitlens` backups. Everything you've logged is kept. Install it over the current app as usual.

## What's new

- **Settings → Progress Photos & Media**, under FitLens's own settings:
  - **Pose for new photos:** Ask each time (as before), None, Front, Side, Back or Other. With a pose chosen, imports
    of one photo or many, including photos shared from your gallery, use it without asking.
  - **Group photos by:** Day, Week, Month, Year or Pose. The Photos screen opens grouped the way you chose, and
    changing it there changes it here too. Weeks start on your calendar's first day of the week.
  - **Remember slideshow and video options** (on by default), and **Reset slideshow and video options**.
  - **PDF report pages** (dark or light) and **PDF photos per day** (none to 4): the defaults the report starts from.
- **The slideshow remembers your options.** Pose, one photo per day, timing, cross-fade, the date, counter and pose
  labels, the data on the video and its order, the title and the video size all come back next time. The dates always
  start at all your photos, and a slideshow of photos you selected shows all of them, whatever pose was remembered.
- **The nearest progress photo under a graph point.** Tap a point on an exercise graph, or on Analysis → Workouts'
  workout-length graph, and the progress photo nearest that date (within 14 days) appears with how many days apart
  they are. Tap it to open the photo.

## Improved

- **Photos** uses the FitLens top bar: **+** imports photos, ▶ opens the slideshow, and the ⋮ menu has Import a whole
  folder, Check photo dates and the photo settings. Selecting photos turns the bar into a selection bar showing the
  count, with Compare, Slideshow and Delete, and Set pose, Change date and Select all in its menu.
- **The photo viewer is edge to edge on black.** Tap the photo to fade the top bar and details away and see it whole;
  tap again to bring them back. Compare and Delete are icons, Change date and Open this day are in the ⋮ menu, and the
  details show the pose, where the date came from, and that day's body values.
- **Compare** sits on black under a gold rule, with Swap and Share as icons and Save to gallery in its menu. The photo
  picker matches the app.
- **Slideshow and video** shows the preview with play, pause and skip buttons, a summary of the options in use, and one
  **options sheet** for everything.
- **The PDF report's options** open as a FitLens sheet, with the period, sections, style and photos per day.
- **Sheets instead of dialogs:** the pose question before an import, Set pose and deleting photos now open as FitLens
  sheets. On/off options on these screens are checkboxes, as in Settings.

## Known limitations

- Training graphs show the nearest photo for a tapped point. Overlaying a body measurement, a relative-strength graph
  and comparing photos picked from two graph points are still to come
  ([#56](https://github.com/Flameingskull/FitLens/issues/56)).
- Analysis → Workouts' weekly, monthly and yearly totals don't show a photo, because each point covers a whole period
  rather than one day.
- The PDF report remembers only its page style and photos per day; the period and sections are chosen for each report.
