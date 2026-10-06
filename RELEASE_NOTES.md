## Overview

This update makes photo import date your progress photos more reliably. When a photo has lost its camera date but
was opened in an editor or cropper, FitLens no longer files it on the day it was edited.

Install it over the current app as usual. There's no database upgrade, and everything you've logged, your photos and
your settings are kept.

## Improved

- **Photo dates come from when the photo was taken.** FitLens still uses the camera's capture date first. If a photo
  doesn't have one, it now tries the date your phone's gallery recorded and the date in the file name before the
  photo's "last edited" time.
- **Edited-only dates are flagged.** A photo dated only by its "last edited" time now shows as "Photo metadata, last
  edited (check this)" and is counted among the photos that need their date checked, so you can confirm or fix it.

## Known limitations

- Photos imported before this update keep the dates they were given. Change any that look wrong from the photo
  viewer or the date review.
