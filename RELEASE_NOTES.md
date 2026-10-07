## Overview

This is a behind-the-scenes update that makes FitLens smoother when something changes in the background. Each
screen now redraws only when the data it actually shows changes.

Install it over the current app as usual. There's no database upgrade, and everything you've logged, your photos and
your settings are kept.

## Improved

- **Screens redraw only when their data changes.** FitLens now notices which kinds of data each screen uses (your
  exercises and workouts, logged sets, workout notes, body values or photos). When something else changes, the
  screen is left as it is. For example, a photo import finishing in the background no longer redraws the exercise
  screen you're logging on, and a body value saved elsewhere no longer redraws your workout. This builds on 1.0.121,
  which stopped the calculations behind those screens from being redone.
- **Always up to date.** A unit or week-start change still redraws every screen straight away, and so does any change
  to the data a screen shows.

## Known limitations

- Saving a set still redraws the screens that show your sets, which is expected. How long a save takes on a phone
  with years of history is still being measured. Settings › About › Save speed shows the figures.
