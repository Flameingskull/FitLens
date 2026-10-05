## Overview

This update finishes moving FitLens's wording into the app's own string resources. The last messages that were still
built in code now read consistently with the rest of the app: why a workout, exercise, category or exercise type
couldn't be saved, the starter library's result, the exercise and set types, effort marks and distance units, and the
names of unnamed slices in Analysis. Every message FitLens shows now comes from one place, ready for future
translations, and counts read properly for one or many.

There's no database change. Everything you've logged, your photos, poses and settings are kept. Install it over the
current app as usual.

## Improved

- **Saving workouts and exercises.** Messages such as "There's already an exercise called …", "That set no longer
  exists" and "Enter a name for the category" use the same wording everywhere they appear, including when Undo can't
  finish.
- **Exercise types.** The type picker, its examples ("Running, cycling, rowing") and what a custom type records
  ("Weight, reps and height (cm)").
- **Set types and effort.** Working, Warm-up, Drop set and To failure in the set editor, and "RPE 8" or "2 RIR" in set
  lists. TalkBack now says "1 rep in reserve" and "2 reps in reserve" correctly.
- **Distance units.** Kilometres, Miles and Metres in Settings, first-run setup and the exercise editor, and how
  TalkBack reads distances.
- **Starter library.** The result after adding it, for example "Added 1 category", now reads correctly for one or many.
- **Analysis.** Breakdown slices for an exercise or category that no longer exists, and the "Other" slice.
- **Estimated 1RM.** The Automatic (recommended) formula's name in Settings and on the exercise's Stats tab.

## Known limitations

- Your own names (exercises, measurements, custom metrics, workout days) are shown as you entered them.
- Exported CSV files keep their English column names, so spreadsheets and other apps can read them as before.
