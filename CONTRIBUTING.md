# Contributing to FitLens

Thanks for helping. Bug reports, feature ideas and code are all welcome.

## Reporting bugs and requesting features

Open an issue with the **Bug report** or **Feature request** form. Before you post, search the existing issues in
case someone has already reported it. For bugs, the steps to reproduce, your FitLens version and your phone model
make a fix much faster. Please don't attach FitNotes backups or photos that you don't want to be public.

Issues are triaged and labelled (`priority: high|medium|low`, `ready`, `needs-info`). Items marked `ready` are planned
for the next release.

## Contributing code

1. Fork the repository and create a branch from `main`.
2. Make your change. Please:
   - follow the existing Kotlin and Compose style;
   - use the brand colours and theme in `app/src/main/java/com/fitlens/companion/ui/Theme.kt` (black, imperial
     purple and gold) rather than new colours;
   - keep existing users' data safe. A database change must bump `Db.VERSION` and add an `onUpgrade` migration that
     keeps existing data;
   - don't change the `applicationId`, the version numbering or the signing setup.
3. Open a pull request that explains what changed and why, with `Fixes #N` if it resolves an issue.
   GitHub Actions builds a debug APK for every pull request. You can download it from the run's artifacts to test.
4. The maintainer reviews and merges. Every merge to `main` publishes a new signed release.

Official releases are signed with a private key that isn't in this repository, so a build you make yourself can't
update the official app. See the README for how to test your own builds.

## Licence

By contributing, you agree that your contributions are licensed under the project's [MIT License](LICENSE).
