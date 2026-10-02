## Overview

Every graph in FitLens can now be drawn the way you read it best: as a line, bars, an area or steps. Your choice is
remembered for each graph. This release also refreshes the project's README to match the app as it is today.

There are no database changes. Everything you've logged is kept as it was.

## What's new

- **Chart types.** Each graph's controls have a new **chart type** dropdown, next to the range:
  - **Line:** the familiar red line over a gold fill, with a marker on each day;
  - **Bar:** a gold column for each day with a red top, always standing on zero so heights compare truly;
  - **Area:** the gold fill with a fine red edge and no markers, for a calm view of long histories;
  - **Step:** a red line that holds each value until the next one, which suits records and body weight.
  It's on the exercise screen's Graph tab, the body tracker's graphs, Analysis → Workouts (and the length of each
  workout) and Analysis → Exercises, and in every full-screen graph.
- **Remembered per graph.** Each graph keeps its own chart type: for example, bars for workout volume and a step line
  for estimated 1RM. Your choices travel with your `.fitlens` backups along with your other preferences.
- **Shared images match.** Sharing a graph as an image draws it in the chart type you're viewing.

## Improved

- **Graph controls on small phones.** The graph, range and chart type dropdowns now scroll sideways when they don't
  fit, so the options menu and the full-screen button always stay in view.
- **Full-screen bars.** In full screen, bar charts zoom across time; their value scale stays fixed at zero.

## Known limitations

- The Breakdown tab's donut chart is unchanged.
- The PDF report keeps its line graphs.
- The screen-by-screen comparison with FitNotes starts in the next update, with the day log and workout drawer
  (#141).
