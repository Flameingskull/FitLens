## Overview

This build finishes several of FitLens's workout tools. Each exercise can have its own rest time. The rest timer can
play a sound of your choice at the volume you set. The calendar can highlight the days that match a filter, such as
"Bench Press, 80 kg or more for at least 5 reps". Workout duration now has an average per workout and a graph of
every workout's length.

This update upgrades FitLens's database so each exercise can store its own rest time. Nothing you've logged changes,
and backups from earlier versions restore the same way.

## What's new

- **Rest time per exercise.** In **Edit exercise**, choose a rest time for that exercise, or keep **As in the rest
  timer**. The rest timer then uses that length after the exercise's sets, both when it starts on its own and when
  you start it from the rest timer on that exercise.
- **Rest-over sound.** The rest timer has a new **Play a sound when rest is over** switch. Choose the sound from your
  phone's sounds, set its volume, and try it with **Play the sound**. The sound and vibration can each be turned on or
  off.
- **Calendar filter.** Tap the search button on the calendar to filter by an exercise or a category, and by minimum
  and maximum weight and reps, minimum distance or minimum time. One set has to meet every condition. Days that don't
  match fade back, and a bar above the calendar shows the filter with the number of matching days this month and in
  total. Tap **Clear** to remove it. The filter is remembered until you clear it.
- **Workout duration.** **Analysis → Workouts → Duration** can show each week's, month's or year's total, or the
  **Average per workout**. A new **Each workout** graph below it plots every timed workout's length, with trend,
  tap for details and full screen.

## Improved

- The "Rest over" notification is now silent itself, and FitLens plays the sound you chose, so the volume setting
  applies. Android shows this as a new "Rest over" notification category. If you had changed the old one's settings,
  set them again on the new one.

## Known limitations

- The rest-over sound follows your phone's notification volume, scaled by FitLens's volume setting. It can't play
  louder than the phone allows.
- A sound chosen on this phone isn't included in backups, because sounds are files on the phone. After restoring on
  another phone, the default notification sound is used until you choose one.
- Calendar filter distances use the unit they were logged in.
- Only workouts with a start and finish time count towards duration.
