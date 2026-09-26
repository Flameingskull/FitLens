## Overview

This build lets your timers keep going with the screen off, and it rebuilds the calendar after FitNotes's. The rest
timer and workout timer now run in a notification with their own buttons, and an alert tells you when rest is over,
even when the phone is locked. The calendar shows the selected day's workout below the month, so you can look back
through your training without leaving it.

Your data isn't touched by this update.

## What's new

- **Timers with the screen off.** While the rest timer or workout timer runs, a notification shows the countdown or
  elapsed time.
  - The rest timer's notification has **Pause/Resume**, **+15 s** and **Stop rest**.
  - The workout timer's notification has **Stop workout**.
  - When rest is over, a **Rest over** alert vibrates and appears even with the phone locked. The **Vibrate when rest
    is over** setting still decides whether it vibrates.
  - The notification goes away by itself when no timer is running. FitLens asks for permission to show
    notifications the first time you start a timer (Android 13 and newer).
- **A FitNotes-style calendar.**
  - It opens on this month. Swipe or use the arrows for other months, and the header counts the month's workouts.
  - Each day shows up to three category dots, plus dots for photos and measurements. Today has a gold ring, and the
    day you select is filled purple.
  - Below the grid, the selected day's exercises, sets, body values and photos, with **Open day**. Tapping the
    selected day again opens it too.
  - The top bar has **Today** and a **list** of every day.

## Improved

- **The README** has been brought up to date with saved workouts, routines, the timers, sharing and the new
  calendar.

## Known limitations

- Each exercise doesn't have its own rest length yet, and the alert has no sound choice.
- The calendar can't yet highlight the days of one category.
- Workout duration graphs are still to come ([#12](https://github.com/Flameingskull/FitLens/issues/12)).
