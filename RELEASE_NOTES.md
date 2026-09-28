## Overview

This build is about logging live in the gym. Sets now read the way FitNotes shows them, each set has its own comment
button, and the rest timer's countdown sits in the top bar where you can see it at a glance. The rest timer also takes
any exact length now, not just the presets. The day log's menu has lost a few entries that other buttons already
cover.

There's no database change in this update, and nothing you've logged changes when you install it.

## What's new

- **A comment on every set.** On the exercise screen's Track tab, each set starts with a speech-bubble button. Tap it
  to open a **Comment** box for that set, then **Save** or **Cancel**. The bubble turns gold when the set has a
  comment. Saving an empty comment removes it. This doesn't select the set or touch the entry fields, so you can add a
  note to an earlier set mid-workout.
- **Rest countdown in the top bar.** The rest timer button is now an alarm clock. While a rest runs, the time left
  replaces it in gold, and it's dimmed while paused. Tap it to open the rest timer. The same countdown appears on the day
  log's top bar while you're resting, so it stays in view after you go back to the day.
- **Any rest length.** The rest timer opens with a length stepper, as in FitNotes: − and + in 5-second steps either
  side of a number you can also type, with the minutes and seconds beside it. Any length from 1 second to 60 minutes
  works. The presets stay underneath as quick chips. Changing the length during a rest applies to that rest from now.
  An exercise's own rest time (Edit exercise) uses the same stepper, plus **Default** to follow the rest timer.

## Improved

- **Sets shown as in FitNotes.** Each value sits right-aligned in its own column with its unit after it, such as
  "85 kg   6 reps". The heading row over the sets has gone. The day log and History no longer number the sets, while
  the Track tab keeps the set number next to the comment button and the done tick box. Bodyweight still reads **BW**,
  and TalkBack still reads each set as one sentence.
- **The rest timer sheet** puts the length first, and on an exercise with its own rest time, the stepper edits that
  exercise's length. **Use the default length** switches it back.
- **A tidier day log menu.** **Exercise library** (it's on **+**), **All days** (it's on the Calendar's top bar) and
  **Previous / Next day with data** (use the day arrows, swipe or the Calendar) have been removed from the ⋮ menu.

## Removed

- The **Set comment** field in the Track form. Set comments now use the speech-bubble button on each set.
- The rest strip under the exercise screen's tabs. The countdown is now in the top bar.

## Known limitations

- Distances show "dist" as their unit until unit settings arrive (#7).
- The ⋮ menu still lists **Workouts** and **Routines** until those pages merge into one (#106).
- Comments on an individual exercise within a day's workout are still to come (#107).
