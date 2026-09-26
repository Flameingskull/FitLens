## Overview

This build changes how FitLens is organised. It now opens on **today's training log** and moves around the way
FitNotes does. The bottom tab bar is gone. Calendar and **+** sit in the top bar, and everything else is in the
**⋮** menu. The day log has been rebuilt in the FitLens look, with day-by-day swiping, the day's photos and body
values at the top, and clear actions for an empty day. The workout dialogs are now bottom sheets, and each change
you make with them can be undone.

Your data isn't touched by this update. Everything you've logged, imported or set up stays as it was.

## What's new

- **Today's log is home.** FitLens opens on today. The top bar has **Calendar**, **+** (add an exercise) and a
  **⋮** menu with Analysis, Exercises, Body tracker, Photos, All days, the exercise library and Settings. Each of
  those opens on top of the log, and Back returns you to it.
- **Move day by day.** The arrows, or a swipe anywhere on the page, move one calendar day at a time, empty days
  included, so you can log a workout on any day. The date reads Today, Yesterday or Tomorrow when it's close. Tap it
  to pick any date, or long-press it (or tap **Back to today**) to return to today. **Previous / Next day with data**
  in the ⋮ menu jump over the gaps.
- **The day at a glance.**
  - The day's progress photos appear as a slim strip at the top, with an **Add photo** tile.
  - Body values logged that day appear in a card. Tap it to open the Body tracker.
  - A summary line shows the duration (when recorded), sets and volume, with the workout comment underneath. Tap
    the comment to edit it.
  - Each exercise has its own card with its sets. Tap a card to log sets. Its ⋮ menu opens the exercise's history
    or deletes that exercise's sets for the day, with Undo.
- **An empty day shows how to start.** **Start new workout** and **Copy previous workout** appear in the middle
  of the page.
- **Day log settings.** Settings → Units & display now has **Show categories** (the colour bar on each card) and
  **Sets shown per exercise** (All, or 1 to 10). A card with more sets than that ends with "+N more sets".
- **Workout sheets.** The workout comment, delete, copy and move options, and Copy previous workout, now open as
  bottom sheets:
  - Copy and move let you pick the day with quick chips (Yesterday, Today, Tomorrow) or a date picker, then check
    what goes. A copy can leave exercises out.
  - Copies, moves, deleted workouts, deleted comments and deleted exercise sets can all be undone from the message
    that follows.

## Improved

- **Calendar opens the log itself.** Tapping a day in the calendar takes you to the training log on that day,
  instead of opening a separate day screen.
- **Analysis has its own screen.** Analysis now opens from the ⋮ menu. The Exercises list, which lists every
  exercise with history, has its own entry too.

## Known limitations

- **+** opens the exercise picker for now. The full exercise library with its routine switcher comes in a later
  build, together with routines.
- Setting the workout time, the rest timer and sharing a workout aren't on the day log yet. They'll be added to the
  ⋮ menu as they're built.
- A move always takes the whole workout. To move only some exercises, copy them and then delete them from the
  original day.
- If FitLens stays open past midnight, it keeps showing the previous day until you tap **Back to today**.
