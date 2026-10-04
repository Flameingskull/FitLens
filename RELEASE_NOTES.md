## Overview

This update builds on what makes FitLens different: it holds your training, your body measurements and your
progress photos in one place, and now its graphs bring them together. A new **Relative Strength** graph weighs your
lifts against your bodyweight, any body measurement can be drawn over an exercise graph, and two points on a graph
can open their progress photos side by side.

FitLens also gains its own **What's new**, **Help** and **About** pages. These notes are now built into the app, so
after each update you'll see what changed, even offline.

There's no database change. Everything you've logged is kept. Install it over the current app as usual.

## What's new

- **Relative Strength graph.** On every weight-and-reps exercise, the Graph tab's list now ends with Relative
  Strength: each day's best estimated 1RM divided by the bodyweight you logged nearest that day, within 14 days.
  A 1.50 means you lifted an estimated one and a half times your bodyweight. Days with no bodyweight that close
  aren't guessed: they're left out and marked with a small ring on the time axis. It can be compared with other
  exercises and pinned to the Analysis overview like any other graph.
- **Body measurement overlay.** From an exercise graph's ⋮ menu, **Overlay a body measurement…** draws bodyweight,
  body fat or any of your measurements over the graph, on its own scale on a second axis at the right. Tapping a
  point shows the measurement's value nearest that date. The legend hides or shows it, and **None** removes it.
  Shared graph images leave it out.
- **Compare two points' photos.** Tap a point that has a progress photo near it, choose **Compare with another
  point's photo**, then tap a second point: Compare opens with the photos nearest both dates, earlier first.
- **What's new after each update.** The first time you open FitLens after an update, these notes appear. They're
  also in Settings → **Change Log**, with a link to every earlier release. A new phone going through setup isn't
  shown them.
- **Settings → Help** now opens short guides inside the app: logging a workout, workouts and routines, comments,
  the body tracker, progress photos, the PDF report, backups, importing from FitNotes, and analysis. Links to the
  full guide, the bug report and feature request forms, and the releases follow.
- **Settings → About** is now a page: the version and build (long-press to copy them with your phone's model and
  Android version, ready for a bug report), What's new, the privacy summary, open-source licences (FitLens, the
  Manrope font and the libraries it's built with), and links to the source code, bug reports and releases.
  **Privacy Policy** opens it too.

## Improved

- Links from Help and About open in your browser. FitLens itself still has no internet permission.

## Known limitations

- The body overlay is chosen for each exercise while its screen is open. It isn't remembered for next time, and it
  isn't part of pinned graphs.
- Relative Strength uses the measurement named Bodyweight (or Body Weight). It's only on weight-and-reps exercises.
- Still to come in linking graphs with your body and photos (#56): weekly measurement averages over Analysis →
  Workouts totals, and a choice to include body values when sharing.
