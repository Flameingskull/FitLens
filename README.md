# FitLens

<p align="center"><img src="branding/fitlens-icon-512.png" alt="FitLens icon" width="180"></p>

**Your workouts, body stats and progress photos in one private Android app.**

[![Latest release](https://img.shields.io/github/v/release/Flameingskull/FitLens?label=latest%20release)](https://github.com/Flameingskull/FitLens/releases/latest)
[![Build](https://github.com/Flameingskull/FitLens/actions/workflows/build.yml/badge.svg)](https://github.com/Flameingskull/FitLens/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

FitLens puts your training history, body measurements and progress photos together, day by day. It lets you see what
your body looked like next to what you lifted and what you measured. Everything stays on your phone.

## Purpose and where it's heading

FitLens started as a companion to [FitNotes](https://www.fitnotesapp.com/). It read FitNotes backups and matched
each progress photo to that day's workout and measurements. **It is now a workout logger in its own right.** The aim
is to record workouts the way FitNotes does, plus what FitNotes can't: photos, progress videos, PDF reports and
custom metrics.

- **Today:** FitLens opens on today's training log and moves around the way FitNotes does. It logs your workouts
  (an exercise library with ten exercise types plus types you make yourself, each with up to three values and a
  metric of your own, set-by-set entry with set types, effort, comments and a done tick, workouts made of days you
  name with planned sets and rests, a workout timer and a rest timer), analyses your training (with graphs you can
  compare side by side, overlay with a body measurement and pin to an overview, and strength relative to your
  bodyweight), tracks goals, and imports and shows your FitNotes history. It also manages progress photos, tracks body
  measurements (with body fat calculated from them) and custom metrics, shows the nearest progress photo beside a
  graph point, makes slideshows, videos and PDF reports that remember your options, and backs everything up locally. Every
  exercise, set and workout records whether it came from FitNotes or was created in FitLens, so the two histories sit
  side by side without colliding.
- **In progress:** FitNotes is the guide for every screen that does what a FitNotes screen does, so moving across
  feels familiar. A screen-by-screen pass laid each FitLens screen beside FitNotes and fixed the differences, one
  group per release from 1.0.75 to 1.0.87: the day log and workout drawer, the exercise screen, the library and
  workout editor, the calendar and Body Tracker, Analysis, the photo screens and finally Settings, now FitNotes's
  single list. In 1.0.96 the photo, viewer, compare, slideshow and PDF screens joined the same design system, and in
  1.0.101 Settings had an accessibility pass and gained Reset settings to defaults. From 1.0.103 to 1.0.107 every
  screen's wording moved into the app's string resources, with counts that read correctly for one or many, ready for
  translations ([#94](https://github.com/Flameingskull/FitLens/issues/94); the messages built while saving, importing
  and backing up follow in [#156](https://github.com/Flameingskull/FitLens/issues/156))
  ([parity epic #134](https://github.com/Flameingskull/FitLens/issues/134), building on the
  [redesign epic #79](https://github.com/Flameingskull/FitLens/issues/79)). Next come the rest of FitNotes parity
  ([#59](https://github.com/Flameingskull/FitLens/issues/59)), the rest of the analysis hub
  ([#58](https://github.com/Flameingskull/FitLens/issues/58)) and the rest of the
  [feature request list](https://github.com/Flameingskull/FitLens/issues?q=is%3Aissue+is%3Aopen+label%3Aenhancement).
- **FitNotes stays supported** as an import source. You can import during first-run setup or at any time afterwards.
  Imports always merge and never overwrite FitLens data. There's no export back to FitNotes.

### Exercises, workouts and routines

- An **exercise** is one movement, such as Bench Press, kept in the exercise library.
- A **workout** is one you make yourself (FitNotes calls it a routine): a name and days you name freely, such as
  "Monday" or "Push Day", each holding its exercises and how their sets are filled. FitLens suggests the next day each
  time you train.
- A **logged workout** is what you recorded on a date. Adding a workout adds a whole day of exercises at once; a
  control that adds one exercise always says **Add exercise**.

### Principles

- **Local only.** No accounts, no cloud services, no subscriptions and **no internet permission**. Google cloud backup
  is turned off for FitLens. Backups go only to a folder you choose.
- **Every update installs over the last one** and keeps your data. Database changes are always migrated, never reset.
- **Android phones** (Android 10 or newer), across phone screen sizes. A self-hosted web version may come later.
- **The FitLens look:** luxury black and vibrant gold. Cards, bars and buttons are smoked glass with fine gold
  rims, graphs are red over a translucent gold fill, rises show in green and falls in red, and all text is ivory or
  gold, never black. Everything is set in **Manrope**, a clean, highly legible typeface bundled with the app, on one
  calm type scale, with FitNotes-style uppercase section headings over a fine gold rule. Text wraps between words,
  never inside one. The FitLens character stands
  faintly behind every screen, and its torso appears in notifications and beside the magnifying glass in search
  fields.

---

## What it does

- **Logs your workouts.** Open any day — past, present or a day you haven't trained yet — add exercises to it, and
  record each set. The set entry screen shows only the fields that exercise uses, pre-fills from the last time you
  did it, and has − and + buttons either side of each value for nudging the numbers. Sets read as in FitNotes, each value in its own column with its
  unit. Each set has a speech-bubble button for its own comment, and the exercise itself can carry an **exercise
  comment** for that day: a large box for detailed notes ("left shoulder tight"), shown under its sets and in its
  History. The last one appears under the box the next time you log the exercise, and its editor lists the earlier
  ones. Sets can be edited or
  deleted, and a deleted set can be brought straight back with **Undo**. A new set
  that beats your best weight for that many reps or more is marked as a **personal record** straight away, with an
  optional vibration and message. **Settings → Calculate Personal Records** works every PR mark out again from your history.
  **Settings** also chooses whether the screen stays on while you log, whether new sets fill in from
  your last workout or start empty, and whether the next set is selected after you update one.
- **Set types and effort.** Mark a set as warm-up, drop set or to failure (shown as a gold **W**, **D** or **F**).
  Warm-ups stay out of records and statistics unless you choose to count them. Optionally record how hard each set
  felt, as **RPE** or **reps in reserve**.
- **Exercise library.** Every category and exercise in one place, with quick add, notes, editing and deletion, side
  by side on wide screens. Star the ones you use most and they come first in every picker. Each exercise has a type:
  Weight & reps, Distance & time, Weight & distance, Time, Weight & time (a weighted plank or loaded carry), Reps &
  time, Reps & distance, or Weight, Reps or Distance alone. It can also have its own weight step, rest time and the
  graph it opens on, all shown at a glance by the **(i)** button on its screen. New and edited exercises open in a full-screen form, as
  in FitNotes, where ✓ saves and ✓+ saves and starts the next exercise in the same category. **Merge into…** joins a duplicate (say
  "Bench Press" and "Barbell Bench Press") into one exercise with all its history, goals, comments and places in your
  workouts; later FitNotes imports follow the merge. Starting without a FitNotes backup, you can add a starter library of common exercises, only when you
  ask and never on top of exercises you already have.
- **Workout editing.** Add exercises to a day, and add or edit the workout comment under the day's summary. **Copy Workout** copies a logged
  workout to another day (leaving out any exercises you choose), moves it, or copies a previous workout into today,
  and a workout can be deleted.
  Each of these opens as a sheet, and the result can be undone. **Share workout** sends a workout to any app as text or
  as a FitLens image card (with one of that day's photos only if you choose it), with a checklist of exactly which
  exercises and sets to include.
- **Workouts.** The exercise library's title (**+** on the day log) switches between **All exercises**, each of your
  workouts and **Create new workout**. A workout shows its days as cards, with the next one marked in gold, and
  **Log all** adds a whole day at once, with Undo. In the editor, each day has **+** (Add exercise) and a menu to
  rename, duplicate, move, copy or delete it, and each exercise chooses how its sets are filled: **copy previous**,
  **predefined** sets (a blank weight or reps copies last time's), or **none**. Each exercise can also plan its
  **rest**: **copy previous rest** (the rests from its last workout, as sets can be copied), or one rest for every
  set or a rest per set, and a longer break before the next exercise; a day's menu sets them for every exercise at
  once, and each exercise shows its plan, such as "Rest 90 s · then 2 min". On the day log's ⋮ menu, **Add
  workout** adds a day from any workout (or exercises you pick on the spot) after a review, **Replace this workout**
  swaps the day's sets for one, and **Save as a workout day** turns a logged day into a new workout or a day of an
  existing one. Exercises can be swapped on a day or for good.
- **Timers.** **⋮ → Workout time** sets a workout's start and finish, or runs a workout timer (optionally started by
  the first set). The **rest timer** sits in the top bar as an alarm clock; while a rest runs, its countdown takes the
  clock's place, on the exercise screen and the day log. It takes any length from 1 second to 60 minutes, pauses,
  starts after each set if you like, and plays a sound (your choice of the phone's sounds, at its own volume) and
  vibrates when rest is over. The default length and these options are also in **Settings → Rest Timer**.
  An exercise can have its own rest time, and a workout can plan rests per set and between exercises: after each set
  the timer uses that set's planned rest, then the workout's, then the exercise's own, then your usual length, and
  after an exercise's last set it uses the planned break before the next one. A day keeps the rests it was logged with,
  even if the workout changes later. Both timers keep running with the screen off, in a notification with its own
  buttons.
- **Reorder, supersets and ticking sets off.** Drag exercises into a new order in the workout drawer, or move
  exercises and sets up or down; a superset always moves as one. Group
  exercises into **supersets**: they sit together with a gold bar, and saving a set moves you round-robin to the next
  exercise in the group, with the rest timer starting after each round. Workouts keep their supersets. Every set on
  the exercise screen has a **done tick**, which can start the rest timer. Ticking an exercise's last set shows
  "Done. Next: …" with **Undo** and opens the next exercise of the day a moment later, in the workout's order; after the
  last one, the day log opens with **Workout complete**, offering to stop the workout timer. With **Settings → Mark Sets
  Complete**, the day log and the workout drawer show how many of each exercise's sets are done. Saving or ticking a
  set gives a short pulse, and a new personal record its own pattern, following the phone's touch feedback setting.
- **Goals.** Each exercise has a **Goals** tab for targets such as max weight, estimated 1RM, reps, or volume in a
  set or workout, with progress bars and an optional goal line on its graph. Body measurements can have a goal too:
  increase, decrease or a specific value.
- **Imports FitNotes backups** (`.fitnotes`), which contain workouts, sets, PRs, comments, workout times, exercises,
  categories, all body tracker measurements and custom measurements. It also accepts Body Tracker CSV exports.
  Before importing, FitLens shows what will be added and what is skipped as already present, and offers to save a
  FitLens backup first (see [FitNotes imports and your FitLens data](#fitnotes-imports-and-your-fitlens-data)).
- **Optional FitNotes folder sync** for moving over gradually. Point FitLens at the folder where FitNotes saves its
  backups, then tap **Sync now**, or turn on automatic sync to import the newest backup whenever FitLens opens.
  Automatic sync is **off by default**. You can also share a backup from FitNotes straight into FitLens.
- **Bulk photo import:** choose many photos or a whole folder, or share photos from your gallery. You pick the pose
  (Front, Side, Back or Other) for the batch as you import, or set a **pose for new photos** in **Settings → Progress
  Photos & Media** and FitLens stops asking. Each photo is dated from:
  1. camera metadata (EXIF "date taken"),
  2. then the media library date,
  3. then a date in the file name (for example `IMG_20230826_132000.jpg`, `PXL_…` or `Screenshot_2023-08-26…`),
  4. then the file's modified date. Photos dated this way are flagged for you to check.

  Duplicates are detected and skipped, so re-importing a folder is safe.
- **Manual entry:** add photos to a specific day and log measurements by hand, with a date, time and comment. A value
  you logged can be changed or deleted on the measurement's Track tab (tap it there, or in its History). If the same value later arrives in a
  FitNotes backup, the FitNotes copy is skipped and your entry is kept.
- **Measurements:** the body tracker's **Measurements** screen lists every measurement with an on/off switch, adds
  the standard set (bodyweight and eight tape measurements), and creates your own, such as calories or sleep, with
  their own unit. **Body fat** and **Height** are there for everyone. You can link one to a FitNotes measurement so
  imports fill it in, and imports keep your on/off choices.
- **Body fat from your measurements:** enter body fat by hand, or tap **Calculate from measurements**. It uses the US
  Navy circumference method (metric body-density form with Siri's equation, typically within about ±3.5 percentage
  points of underwater weighing), working from your latest height, neck and waist (plus hips for women). Each one is
  shown with its date, and any value more than 14 days old is flagged. A missing measurement is named so you can enter
  it on the spot, and impossible inputs are refused with the reason. **Settings → Sex (Body Fat)** holds the one
  choice the formula needs.
- **Guided setup:** a fresh install opens a short, skippable setup: restore a FitLens backup, or choose your units,
  an automatic backup folder, a FitNotes import, your progress photos, the starter exercise library and the standard
  body measurements.
  **Settings → Show Setup Again** opens it any time.
- **Settings:** open it from the **⋮** menu on the day log. As in FitNotes, it's one list. **SETTINGS** has Theme,
  Unit System, Calendar Week Start, Default Weight Increment, Home Screen Settings, Track Personal Records, Mark Sets
  Complete, Auto-Select Next Set and Keep Screen On, in FitNotes's order, then FitLens's own: your units, sex for the
  body fat formula, the rest timer, how new sets fill in, set types, effort, warm-ups, the workout timer, the
  estimated 1RM formula and settings, and **Progress Photos & Media** (the pose for new photos, how the gallery is
  grouped, remembered slideshow and video options, and the PDF report's page style and photos per day). **DATA**
  has Backup, Restore, Automatic Backup, Spreadsheet Export, Calculate Personal Records, Delete Workout History,
  **Reset Settings to Defaults** and Import From FitNotes, and **OTHER** has Help (short guides inside the app),
  Feedback, Change Log (this update's notes, which also appear once after each update), Show Setup Again, Privacy
  Policy and About (version, privacy, licences, and **Save speed**: how long saving a set takes on your phone).
  On/off settings are checkboxes, and the search field filters the list so a setting can be changed right there.
  TalkBack reads each row as one sentence (its title, value or state, then its explanation), and a setting that
  can't be used yet stays in place, dimmed, and says why.
  **Unit System** switches weight, distance and body measurements to metric or imperial together, with the separate
  units below it for any mix (stored values never change, and the plate calculator's plates convert too). Any
  exercise can keep its own weight unit, and any body measurement its own unit. Settings that belong to the phone,
  such as backup folders and schedules, stay on the phone and are never replaced by a restore. Preferences such as
  your units, chart types, comparisons, pinned graphs and photo and media options travel with your backups.
- **Data tools** (in Settings):
  - **CSV export:** workouts (with set and exercise comments) or body data for any date range, in kilograms or
    pounds, saved to a file or shared, for spreadsheets. The columns are listed on the page, with a custom metric's
    name, value and unit at the end. A CSV can't be restored; that's what backups are for.
  - **Delete workout history** by date range, by exercise or both, after a preview of what will go. Exercises,
    categories, photos and body data are kept, personal records are worked out again, and deleted FitNotes sets stay
    deleted on the next import. A safety copy is taken first, so it can be undone.
  - **Reset settings to defaults:** puts units, the rest timer, logging, display, photo and report options back as
    they were when FitLens was new, with **Undo** straight afterwards. Workouts, photos, measurements, backups,
    backup folders and schedules, pinned graphs and your body fat profile are kept. Home Screen Settings, Rest Timer
    and Progress Photos & Media can also be reset on their own, from **Reset this section** in each page's ⋮ menu.
- **Getting around:** as in FitNotes, there's no tab bar. The **day log** is home, and its top bar has **Calendar**,
  **+** (the exercise library and workout switcher), the rest countdown while you rest, and a **⋮** menu with the
  day's workout actions, then Analysis, Body tracker, Photos and Settings. Each of those slides in on top of the log,
  and Back returns you to it. Rotating the phone, or Android closing FitLens in the background, keeps your place:
  the screen you were on and the ones under it, with their open sheets, selections and filters. Choices such as a
  period, a pose or a sort order sit in compact dropdowns, so the data gets the screen; the controls you use
  mid-workout stay in view.
- **Views:**
  - **Day log (home):** laid out as FitNotes's. It opens on today, under a flat ‹ TODAY › bar: swipe or use the
    arrows to move one day at a time, empty days included, tap the day to jump to any date, and long-press it to come
    back to today. The day's body values come first, in one card with each name and its value, and the change since
    the previous value (amount, date and the value it moved from) on its own line. Then come the day's progress photos
    and a card for each exercise: its name, a gold tick once every set is done, and its sets as bold figures in
    columns. Tap an exercise to log its sets, or long-press it for its options (comment, history, records, reorder,
    superset, swap, remove). The workout's time, totals and comment follow. An empty day reads "Workout log empty"
    and offers **Add exercise**, **Add workout** and **Copy previous workout**.
  - **All days** (from the Calendar's top bar): a timeline of every day, with that day's photos, measurements and
    workout summary.
  - **Calendar:** as in FitNotes, a month grid (swipe between months) with category dots, photo and measurement
    dots, today in gold and the selected day in deep gold. The selected day's workout shows below the grid (beside it
    on wide screens and unfolded foldables); **Open day**
    (or a second tap) opens its log, and tapping one of its exercises opens that exercise's **overview** (history,
    graph, records, stats and goals in one sheet). The search button **filters** the calendar by exercise or
    category and weight, reps, distance or time, fading the days that don't match and counting those that do.
  - **Body Tracker:** as in FitNotes, one screen with **Track**, **History** and **Graph** tabs for all your
    measurements. **Track** lists each one with how long ago it was logged, its latest value and the change since the
    value before (coloured by your goal), or "Tap to record a value". **History** shows every value, newest day first,
    for all measurements or one. **Graph** shows one measurement with its range, trend line, goal line, photo days and
    stats. Tap a measurement to log a value: the day, the value with − and + buttons, the time and a comment, with its
    own History and Graph (Body fat adds **Calculate from measurements**). The pencil opens **Measurements**, where each measurement shows its unit and goal and a
    checkbox turns tracking on or off.
  - **Exercise library:** as in FitNotes, it opens on **All exercises** with the search field always at the top,
    then your categories, then a category's exercises. Tap an exercise to log it, or long-press to choose several and go through them in turn.
    The exercise form's **Your types** makes exercise types of your own: pick what each set records (weight, reps,
    distance, time, or a metric of your own with its name and unit, such as jump height in cm). Set entry, history,
    graphs (**Best** and **Total** for the metric), records, predefined workout sets and CSV export follow the type.
  - **Exercise screen:** **Track** (log sets: each field under an uppercase heading, its value between square − and
    + buttons, straight under the tabs, with the day's sets below as a plain list), **History** (each day under a
    FitNotes heading such as **MONDAY, SEPTEMBER 28**, every day you've done it, with its totals, that day's
    exercise comment and **Copy to today**; tap a set to correct or delete it) and **Graph** tabs. The Graph tab has
    FitNotes's list for weight exercises: **Estimated 1RM**, **Max weight**, **Workout volume**, **Workout reps**,
    **Max reps**, **Max volume** (the best single set), **Max weight for reps** (the heaviest set at a rep count you
    choose) and **Personal records** (a line through each day you set a record). Timed and distance exercises graph
    their longest set, total time and distance, and distance-and-time exercises add **Max distance**, **Max speed**
    and **Max pace**. Weight-and-reps exercises end the list with **Relative Strength**: each day's best estimated
    1RM divided by the bodyweight logged nearest that day (within 14 days; days without one are left out and marked).
    Its menu button opens the **workout drawer**: the day's exercises in order,
    to jump between or reorder, with **Add exercise**, **Add to superset** and **Home** at the bottom. As in FitNotes,
    the top bar then has the rest timer, a trophy that opens **Records**, **Stats** (Max weight, Estimated 1RM, Max reps, Max volume, Workout reps and Workout volume, each with
    its date, which a tap opens, plus totals and first and last logged, for a period or any date range), **Goals** and
    a **1RM calculator**.
    Its **(i)** button shows the exercise's notes and settings, with **Edit**. Its ⋮ menu has a **set calculator** (percentages of your 1RM, or a warm-up ramp) and a **plate calculator**
    (plates per side for your bar and plates); both fill in the set.
    Rep-max records run from 1RM to 15RM, actual and estimated, for the last workout, week, month, year or all time. A heavier or equal lift for more reps counts as the record for every
    lower rep count too. By default, estimated maxes follow the validation studies: from 2 to 10 reps the mean of the
    Mayhew and Wathan formulas (the two found most accurate there), from 11 to 15 reps Wathan alone, marked as
    approximate (≈), and nothing above 15 reps; **Settings → Estimated 1RM Formula** can switch to Epley, Brzycki, Lombardi,
    O'Conner, Mayhew or Wathan instead, with a worked example of each, and **Estimated 1RM Settings** (also the gear
    beside the calculator) sets the most reps a set can have to count. Pounds convert with the exact definition
    (0.45359237 kg). Records can also cover a date range you choose.
  - **Analysis:** **Overview**, the first tab, shows every graph you've starred, Workouts or exercise graphs, as
    compact cards with the latest value and the change over its range; tap one to open it, or drag or use its menu to
    reorder or unpin it (Analysis opens there once something is pinned). **Workouts** shows your workouts, volume, sets, reps or duration per week, month or year, for all
    training, a category or an exercise; duration can be a total or an average per workout, with a graph of every
    workout's length, and its ⋮ menu can overlay a body measurement's average for each period. **Breakdown** splits your training by category or exercise in a donut chart,
    for any week, month or year with training, with arrows to step through the slices and a comparison to the period
    before. **Exercises** shows any exercise's graphs, with the same options as its Graph tab. **Goals** lists every exercise goal with its progress; **+** adds one for any exercise. **Records** puts
    1RM to 15RM for many exercises side by side, for all training, a category or the exercises you choose.
  - **Graphs** (Body tracker, exercise screen and Analysis) are sized from your screen, with their graph type, range,
    **chart type** and options in one compact row of dropdowns. Each graph can be drawn as a **line**, **bars**, an
    **area** or **steps**, and remembers your choice (it travels in backups too). They can add a dashed **trend line**:
    a smoothed curve through your values from eight points on (so a plateau, cut or bulk shows as a bend), otherwise
    the straight best-fit line, and none from fewer than three days. Its figures are written under the graph: the rate
    in the graph's unit per week, month or year, the fitted start and end, and the number of points and R², with a
    note when the trend rests on few points or explains little. Graphs can start their scale **from zero** (bars
    always do), and a line never breaks between points. Tapping a point on an exercise graph or the workout-length
    graph shows the **progress photo nearest that date** (within 14 days), which opens in the viewer, or compares it
    with a second point's photo. An exercise graph's ⋮ can **overlay a body measurement** (bodyweight, body fat or any
    measurement) on its own scale at the right; a shared graph includes body values only if you choose. Every graph opens **full screen** (the expand button or a double
    tap), where pinching across zooms the timeline and pinching up and down zooms the values, dragging moves around, and the range and options can change. TalkBack reads each graph's range and
    values and offers zoom and move actions for both. A graph's ⋮ menu can **share it as an image** in the FitLens
    look. An exercise graph's ⋮ can **compare** up to five exercises on one graph, each with its own colour and
    marker (tap a name in the legend to hide it, tap a date for every value on it), as values or as a percentage of
    each one's first value in the range. The **star** beside full screen pins a graph to Analysis → Overview.
  - **Photos:** a gallery grouped by day, week, month, year or pose (remembered), with pose filters and counts.
    Long-press to select photos: the top bar then shows the count, with compare, slideshow and delete, and the ⋮ menu
    sets the pose or date of all of them. The viewer shows each photo edge to edge on black; tap it to fade the
    controls away, swipe for the next, and open the details for the pose, the date and that day's body values. The
    **before/after compare** sits side by side on black, with the change in each body value, and can be shared or
    saved as an image.
- **Slideshow and video:** plays your photos in date order with overlays: the date, a day or week counter, the pose,
  chosen measurements (with the change since the start) and a moving progress chart. Every option is in one options
  sheet, and FitLens remembers them for next time (the dates always start at all photos; **Settings → Progress Photos
  & Media** can switch this off or reset them). It exports an **MP4 video**, rendered on the phone, in Portrait HD,
  Full HD or Square. Videos are saved to *Movies/FitLens* and can be shared.
- **PDF report:** a readable report with your photos, measurement charts, training summary and a daily log, in dark
  (as in the app) or light (for printing). The page style and photos per day start from your Settings choice.
- **Backups, all on your phone** (in **Settings → Backups**):
  - **Backup file:** save everything, photos included, as one `.fitlens` file, or **share** it with an app you
    already use (email, Drive, Dropbox and so on; FitLens itself never uploads anything). Restore it after
    reinstalling or on a new phone, or open it straight from a file manager. The date and time in the file name can
    be switched off.
  - **Automatic backups:** daily or weekly to a folder you choose (for example Documents or an SD card), so they
    survive uninstalling. You choose how many to keep (the newest 3, 5 or 10). They run in the background through Android's job scheduler,
    even when FitLens is closed, whenever the battery isn't low. Optionally, **Back up after changes** saves a backup
    when you leave FitLens after changing something, at most once an hour.
  - **Status and alerts:** the Backups section shows the last successful backup, the next scheduled one and the
    folder's free space. If the folder can't be reached (the SD card was removed or access was lost), a notification
    explains how to fix it.
  - **Safety copy with Undo:** before a restore, a FitNotes import, merging exercises or deleting workout history, FitLens keeps a copy
    of your current data on the phone. For 7 days, **Settings → Backups → Safety copy → Undo** puts it back.
  - **Phone-to-phone transfer** (Android 12+) carries FitLens data across when you set up a new phone with a cable or
    a direct transfer.

FitLens only *reads* FitNotes backups and never changes your FitNotes data. FitLens backups (`.fitlens`) are for
FitLens only.

**Permissions:** the only one FitLens asks for is notifications (Android 13+), and only when you set up automatic
backups or first start a timer. It also uses vibration and a foreground service for the timers, which Android grants
without asking. Photos and folders are accessed through Android's file pickers, and only the ones you choose.

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
changed. The version number goes up with every release (`1.0.<build>`). A number can be skipped: a build that fails
publishes nothing but still uses up its number (as with 1.0.54, 1.0.56 and 1.0.95), and earlier releases skipped some
(1.0.8 was followed by 1.0.13) because pull request checks shared the release build counter
([#78](https://github.com/Flameingskull/FitLens/issues/78)). A higher number is
always the newer build.

## First-time setup (in the app)

The first time FitLens opens, a short guided setup walks through the steps below. Every step can be skipped, and
**Settings → Show Setup Again** brings it back. To do it by hand:

0. **Just want to start logging?** FitLens opens on today's log: tap **+**, pick a category and an exercise, and
   record your sets. Save a day you like as a workout day (**⋮ → Save as a workout day**), or build a workout from
   the library's title, to add a whole day in one go next time. You don't need FitNotes at all: steps 1 and 2 are only for bringing an existing FitNotes history across.
1. **Settings → Import From FitNotes → Import a backup file**, then choose your latest `FitNotes_Backup_….fitnotes`.
2. Optional, if you keep using FitNotes for a while: **Settings → Import From FitNotes → FitNotes backup folder** and pick the folder where FitNotes saves its backups. Tap **Sync now** after making a backup in FitNotes, or turn on
   automatic sync so FitLens imports the newest backup each time it opens.
3. **⋮ → Photos → + → Import a whole folder** (for example your camera folder or a "Progress" album), or choose
   photos. FitLens dates each photo and places it on the right day.
4. If any photos had no camera date, a banner says **"N photos need their date checked"**. Tap it to accept or fix
   the dates.
5. Tag poses (Front/Side/Back) while importing, in the photo viewer, or with multi-select, or choose a pose for every
   new photo in **Settings → Progress Photos & Media**. Slideshows and comparisons can then use one pose.
6. **Settings → Backups:** choose a backup folder and turn on automatic backups.

## Limits

- Sets, and exercises in the workout editor, are reordered with buttons (and TalkBack actions), not by dragging.
  Routines from FitNotes backups aren't imported.
- Graphs use compact dropdowns rather than FitNotes's 1m / 3m / 6m / 1y / all buttons, so the graph gets the room
  ([#125](https://github.com/Flameingskull/FitLens/issues/125)).
- Planned rests apply to days logged from a workout from 1.0.73 on; earlier days use each exercise's own rest or
  your usual length.
- Values imported from FitNotes can be viewed but not edited in FitLens: the next import would bring the original
  back. Change them in FitNotes and import again.
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
Android 16 (API 36) and runs on Android 10 (API 29) or newer.

Open the project in **Android Studio** and click **Run**, or run `./gradlew assembleDebug`. Your own builds are signed
with your debug key, so Android won't install them over the official release. Test on an emulator or a spare phone.
Alternatively, save a backup first (**Settings → Backups → Save backup**) and uninstall the official app.

Unit tests run on the JVM with Robolectric, no emulator needed: `./gradlew testDebugUnitTest`. They build databases
the way older FitLens versions left them and check that every upgrade keeps every row, import a made-up FitNotes
backup to check the merge rules, save and restore `.fitlens` backups, and check the 1RM formulas and unit
conversions, graph zoom, the remembered chart types, pinned graphs and comparisons, and the in-memory data
updates. Screenshot tests draw the shared components at two phone widths and at double text size and compare them
with saved images, so layout slips fail the build.

Every push to `main` runs the unit tests, then is built and signed by GitHub Actions and published as a release; a
failing test stops the release. Pull requests run the tests and get a debug build only.

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

The bundled Manrope typeface is © 2018 The Manrope Project Authors, used under the
[SIL Open Font License 1.1](LICENSES/Manrope-OFL.txt).
