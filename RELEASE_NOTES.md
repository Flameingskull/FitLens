## Overview

Body fat is now a standard part of the body tracker. Everyone has a **Body fat** measurement and a **Height**
measurement, and body fat can be entered by hand as before or **calculated from your body measurements**. The
calculation uses the US Navy circumference method, the most accurate of the tape-measure formulas, and works from
the measurements you've already logged. If any it needs are missing, it tells you which ones and asks you to add
them first.

This update includes a small database update that adds the two new measurements. Everything you've logged is kept as
it was, and so are all your settings. Install it over the current app as usual.

## What's new

- **Body fat and Height for everyone.** Both now appear in the body tracker, whether or not you added the standard
  measurements. If you already have a body fat measurement, including FitNotes's "Body Fat", it's kept and used, and
  no duplicate is added.
- **Calculate body fat from your measurements.** On Body fat's Track tab, or when logging body fat from the day log,
  tap **Calculate from measurements**. The calculator:
  - uses your latest height, neck and waist, plus hips for women, and shows each one with the date it was logged;
  - names any measurement that's missing, lets you enter it right there, and calculates only once everything is in.
    Values you enter are saved to your body tracker on that day;
  - flags any measurement more than 14 days older than the day you're logging, so you can re-measure first;
  - says where to measure each one (the neck just below the Adam's apple, the waist at the navel for men and at its
    narrowest for women, the hips at their widest);
  - converts inch values exactly, and refuses impossible inputs (a waist no larger than the neck, a height that can't
    be right, or a result outside 2–75%) with the reason.

  **Use** fills in the result. Check it and save it like any other value. Its comment records that it was calculated
  and from which measurements.
- **The formula.** The US Navy circumference method (Hodgdon & Beckett), in its original metric body-density form
  with Siri's equation. Checked against underwater weighing, it has a typical error of about ±3.5 percentage points.
- **Sex for the calculation.** The formula differs for men and women. The calculator asks for it the first time, and
  you can change it any time in **Settings → Sex (Body Fat)**. It's used for nothing else and is kept in your
  backups.

## Known limitations

- Height is logged in your length unit (centimetres or inches), not in feet and inches.
- Body fat entered by hand isn't compared with the calculated value.
