## Overview

FitLens now remembers where you were. Turning your phone, or coming back after Android has closed FitLens in the
background to free memory, used to drop you back on the day log. From this update you return to the same screen,
showing the same exercise, day or photo, with its sheets and selections as you left them.

Under the hood, the app's screens now run on Android's standard navigation system (Navigation Compose), and the
busiest screens keep their state in ViewModels. This is groundwork that every future screen is built on. There's no
database change: everything you've logged and every setting is kept. Install it over the current app as usual.

## Improved

- **Your place survives a rotation or a restart in the background.** Every screen, with what it was showing (the
  day, the exercise and its tab, the measurement, the photo, the Settings page), comes back after you turn the phone
  or switch back to FitLens after a while away. Back still works through the same screens in the same order.
- **Open sheets, dialogs and selections are kept** on the day log (copy, move, share, workout time, the rest timer
  and the rest), the photo gallery (selected photos, pose, date and delete dialogs) and the photo viewer.
- **Screens you return to keep their place.** Going back to a screen lower in the stack, such as the photo gallery
  after viewing a photo, now keeps its scroll position and choices instead of starting over.
- **Back behaves as before.** A pushed screen closes, the day log is home, and Back on the day log leaves the app,
  as in FitNotes. Screens still slide in when opened and back out when closed, and moving to another day or the next
  exercise still changes the screen in place.
- **Shared files open as before**, whether FitLens was already running or not: FitNotes backups go to Import From
  FitNotes, `.fitlens` backups to Backup, and photos ask for their pose.

## Known limitations

- The remaining screens keep their sheets and dialogs in memory only, so a dialog left open on them closes when the
  phone turns. Their screen and arguments are still restored. They move to the same saved state as they are next
  worked on.
- Rotation and returning after Android has closed the app are best checked on a real phone. To test the second, turn
  on Developer options → "Don't keep activities", open a few screens, switch away and back.
