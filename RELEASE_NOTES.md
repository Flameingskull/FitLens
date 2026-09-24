## Overview

1.0.8 made FitLens a workout logger. 1.0.9 makes that logging trustworthy. Everything here came out of reviewing the
new logging code rather than from reports, so several of these are faults you might never have hit — but two of them
could have cost you data, and those are fixed first.

There are no new features in this build. It is a stability and correctness release.

## Fixed

- **A failed background save can no longer close the app.** Saving, deleting and undoing all run in the background,
  and nothing was catching their failures — so a single unexpected error closed FitLens outright. The clearest way to
  hit it: delete a set, delete that exercise while the "Set deleted" message is still showing, then tap **Undo**.
  Failures are now reported as a message and the app carries on.
- **Installing an older FitLens no longer traps you.** Going back to an earlier version left the app crashing every
  time it opened, and the only way out was uninstalling — which deletes every workout, photo and measurement. Older
  versions now open your data and carry on, ignoring anything newer they don't recognise.
- **Undo after deleting restores everything.** Undoing a deleted set brought it back without its personal-record
  mark. Undoing a deleted workout restored only the first of a day's start/finish times. Both now come back whole,
  and the confirmation says when a day's times are about to be removed.
- **Workout duration now appears.** Start and finish times are stored in a format the duration calculation couldn't
  read, so every workout silently showed no duration at all — in the day view **and** in PDF reports. All three
  places that read these times now share one parser.
- **"Save & new" keeps the exercise editor open.** It behaved exactly like "Save": it closed the editor and jumped
  away, which made adding several exercises in a row impossible.
- **Category dots are visible again on the Training tab.** A category with no colour chosen was drawn fully
  transparent instead of falling back to a neutral tone.
- **Bodyweight sets read properly.** Sets with no weight showed as "0 kg × 10 reps", as though the weight had been
  lost. They now simply show the reps.
- **Editing a set no longer nudges its weight (pounds only).** Weights are stored in kilograms and shown rounded in
  your unit. Changing only the reps and saving used to write the rounded figure back, shifting the stored weight
  slightly each time. An untouched weight field is now left exactly as it was.

## Improved

- **The build keeps itself current.** GitHub Actions are updated to their current versions, and the Gradle build has
  moved to the Kotlin `compilerOptions` setup, which recent Kotlin versions require — this had been blocking every
  proposed Kotlin update.
- **Database upgrades are now safe to repeat.** Each upgrade step checks whether its change is already present, so an
  interrupted or replayed upgrade can't fail part-way through.

## Known limitations

- **Personal records still aren't calculated.** The "PR" mark continues to come only from imported FitNotes data;
  sets logged in FitLens don't earn one yet. That's the records engine, due next.
- **Settings still live on the Sync tab**, and restoring a backup still can't be undone.
- Moving a workout onto a day that already has one can still leave two comments and two sets of times on that day.
- Sets and exercises still can't be reordered within a workout.

## Upgrading

Install over your existing FitLens. Your data is kept, and this build adds no new database fields.
