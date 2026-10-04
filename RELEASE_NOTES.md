## Overview

This update lets you shape FitLens around how you train. You can now make your **own exercise types**: choose
what each set records, and add a measure of your own with its unit, such as jump height in centimetres or a band's
resistance level. Set entry, history, graphs, records and CSV export all follow the type you build.

Analysis → Workouts can now draw a **body measurement's average** over your training totals, so you can see your
bodyweight next to your weekly volume. Body values also stay private by default: a shared graph includes them only
when you choose.

This update adds to the database. Everything you've logged is kept, and backups from earlier versions restore as
usual. Install it over the current app as normal.

## What's new

- **Your own exercise types.** In the exercise form, under Type, **Your types** lists the types you've made, and
  **New type** creates one. Give it a name, then tick what each set records: weight, reps, distance, time, or **a
  metric of your own** with its name and unit. A type records one to three values. A selected type of your own can
  be edited from the same row, and deleted once no exercise uses it.
- **Set entry follows the type.** Only the fields the type records are shown, and a custom metric gets its own
  field with − and + buttons. Your sets show it in its own column with its unit, in the day log, History and
  everywhere else sets are listed.
- **Graphs and records for your metric.** An exercise of your own type offers **Best** and **Total** graphs for its
  metric, alongside the graphs for the other values it records. The Records tab shows the best value you've
  logged and when, and the farthest distance for distance types.
- **Body measurement over Workouts totals.** In Analysis → Workouts, the ⋮ menu's **Overlay a body measurement…**
  draws a measurement's weekly, monthly or yearly average on its own scale at the right, matching the graph's
  period. Tapping a period shows that period's average under the graph.

## Improved

- **Body values are shared only by choice.** Sharing an exercise graph with a body overlay now asks whether to
  include it. If you do, its first and last values in the range are written under the graph. Sharing the Relative
  Strength graph asks first too, since it reveals your bodyweight.
- **CSV export** adds three columns at the end: the custom metric's name, value and unit. Spreadsheets built on
  earlier exports keep their columns.
- The new exercise type form is the first screen whose text comes from the app's text resources, the groundwork for
  translations.

## Known limitations

- A custom metric isn't yet part of a workout's predefined sets, so logging a workout day leaves it blank. Enter it
  when you log each set.
- Changing an exercise's type can change which graph it opens on. Choose it again under Opens on graph if needed.
- The rest of the app's text will move into text resources screen by screen.
