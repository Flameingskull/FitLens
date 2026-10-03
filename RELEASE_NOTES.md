## Overview

This update makes trend lines exact. Every graph's **Trend line** option is now worked out by one shared, tested
calculation, fitted to exactly the points it describes. Each trend comes with its figures written underneath: how
fast the value is changing, where the trend starts and ends, how many points it rests on and how closely it fits. A
trend line should never look like a generic slope laid over your data, and now it can't.

There are no database changes. Everything you've logged is kept as it was, and so are all your settings. Install it
over the current app as usual.

## Fixed

- **Trend lines follow your data.** With enough points (eight or more), the trend is now a smoothed curve through your
  values, a locally weighted regression, so a plateau, a cut or a bulk shows as the bend it really is instead of
  being hidden by one straight line. With fewer points it's the straight best-fit line. Graphs with fewer than three
  points on different days no longer show a trend at all, because two points always make a "perfect" line that says
  nothing.
- **Every trend states its figures.** Under each graph with the trend on: the rate in the graph's own unit per week,
  month or year (whichever suits the range), the fitted value at the start and the end with their dates, and the
  straight-line fit it's based on (the number of points and R², how much of the variation the trend explains). This
  now appears on exercise graphs, compared exercises (one line each), Body tracker measurements, Analysis → Workouts
  and the workout-length graph, where before several of them drew a line with no figures.
- **Weak trends are flagged.** A trend from fewer than five points says to read it with care. One that explains less
  than a third of the variation says the values vary more than they trend.
- **Workouts totals leave out the period in progress.** The current week, month or year isn't finished, so its
  partial total used to pull the trend down. It's no longer counted in the trend, and a note under the graph says so.
- **The line and the rate always agree.** Analysis → Workouts used to draw its line against dates but work out its
  stated rate against the order of the points, so months of different lengths gave two different answers. Both now
  come from the same fit.
- **Zoomed graphs get their own trend.** In full screen, the trend and its figures describe exactly the stretch
  you've zoomed to.
- **Pace trends read in minutes.** A pace graph's trend now reads "min /km" (or your distance unit), not just "/km".
- **Shared graph images match the screen.** A shared graph draws the same trend, kept inside the graph's frame, and
  prints its figures under the summary.

## Known limitations

- Figures for a fitted trend are shown to one decimal place, in the graph's unit. Pace trends are given in decimal
  minutes rather than minutes and seconds.
- The Analysis overview's pinned cards don't show trend lines.
