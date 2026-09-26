## Overview

This build adds three FitNotes tools you use during a workout. You can **time your workout** from the day log,
**share it** as text, and run a **rest timer** between sets on the exercise screen.

Your data isn't touched by this update.

## What's new

- **Workout time and timer.** On the day log, **⋮ → Workout time** opens a sheet with the workout's start and finish.
  - Set either time with a time picker, and see the duration as you change them.
  - On today, **Start timer now** starts timing. While it runs, the day's summary counts up and the menu offers
    **Stop workout timer**, with Undo.
  - **Clear the time** removes it.
  - **Settings → Workout & logging → Start the timer with the first set** starts it for you when you save today's
    first set.
- **Share a workout.** **⋮ → Share workout** lists the day's exercises, all ticked, with options for the date,
  duration, comment and PR marks. Untick anything you'd rather keep to yourself, then share the workout as text with
  any app. Body values are never included.
- **Rest timer.** The bell in the exercise screen's top bar opens the rest timer:
  - a large gold countdown, with **−15 s**, **+15 s**, **Pause**, **Restart** and **Stop**;
  - choose the rest length (30 seconds to 5 minutes);
  - **Start after saving a set** starts it every time you save a set;
  - **Vibrate when rest is over**, which is on by default.

  While it runs, a slim bar under the tabs shows the time left, on every exercise, so it keeps going as you move
  through your workout.

## Known limitations

- The rest timer and workout timer run while FitLens is open. A notification that keeps them running with the screen
  off comes later ([#20](https://github.com/Flameingskull/FitLens/issues/20),
  [#12](https://github.com/Flameingskull/FitLens/issues/12)).
- Each exercise doesn't have its own rest length yet. There's one length for all.
- Sharing a workout as a branded image comes later ([#11](https://github.com/Flameingskull/FitLens/issues/11)).
- A day with several time ranges from FitNotes is replaced by a single range when you save its time.
