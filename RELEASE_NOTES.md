## Overview

This update finishes three features. Cardio exercises get FitNotes's full set of graphs, and any graph can be shared
as a FitLens-styled image. Units can now be set for each exercise and each body measurement. You can also share a
workout as a branded image, choosing exactly which sets to include.

This update includes a small database change: exercises and measurements can now store their own unit. Everything
you've logged is kept exactly as it was.

## What's new

- **Cardio graphs.** Distance-and-time exercises add **Max distance**, **Max speed** and **Max pace** to their
  Graph tab, in the exercise's distance unit. Speed is shown in km/h or mph, or metres per minute for exercises logged
  in metres. Pace reads as minutes and seconds per km or mile, or per 100 m, and its "best" is the fastest.
- **Share a graph as an image.** The graph's ⋮ menu has **Share graph as image**. It creates a black, purple and gold
  image of the graph as shown (range, trend line and goal line included) and opens the share sheet.
- **A weight unit for each exercise.** In the exercise editor, a weight exercise can keep the unit from Settings or
  always use kilograms or pounds. Its set entry, set rows, history, graphs, stats, records, goals, 1RM, set and plate
  calculators, and workout plans all use that unit. Weights are still stored in kilograms, so switching never changes
  what you logged.
- **A unit for each body measurement.** On the Measurements screen, any weight or length measurement can follow
  Settings or always show in its own unit, for example your waist in inches while other lengths stay in centimetres.
  Values are converted for display and stored as you logged them.
- **Share a workout as an image.** The Share workout sheet now offers **Text** or **Image**. The image is a FitLens
  card with the date, duration, exercises, sets, PR marks and comments. You can add one of that day's progress photos,
  but it's never included unless you choose it.
- **Choose individual sets to share.** The share checklist lists every set under its exercise. Tick or untick single
  sets, or a whole exercise at once.

## Improved

- The graph called "Longest set" now has FitNotes's name, **Max time**. Saved default graphs still open on the same
  graph.
- An exercise with its own weight unit uses a step of 2.5 in that unit, unless you've given it its own step, rather
  than converting the Settings step (2.5 kg would have stepped by 5.51 lb).

## Known limitations

- Totals that combine exercises (the day log's volume, Analysis, the PDF report and CSV exports) use the weight unit
  from Settings.
- Your saved plate list is in the Settings unit. In the plate calculator, an exercise with a different unit uses the
  standard plates for its own unit.
- The pace graph's axis shows decimal minutes. The values above and below the graph read as minutes and seconds.
