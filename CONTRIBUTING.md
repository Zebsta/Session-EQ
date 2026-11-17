# Contributing

Use a branch and a pull request for changes. Keep audio behavior changes separate
from UI changes where practical, and describe any new permissions or background work.

Before opening a pull request, run:

```sh
./gradlew verifyCore :app:assembleDebug
```

For audio lifecycle changes, also test on a real device:

- a known external player session, ON/OFF/ON, and return from background;
- pause/resume and session replacement;
- closing one of several sessions and closing the final session;
- OFF with the screen closed: no retained foreground service or native effects;
- auto-connect ON: final CLOSE releases effects but retains quiet waiting service;
- new OPEN from checked app uses new session ID; unselected app/AIMP never auto-attach;
- uncheck final app: idle service is released; saved checked apps remain editable;
- unchanged screenshot Rock values and Windows preset data.

Do not introduce polling, wake locks, network access or automatic grants of
permissions without explaining why they are needed. Hardware audio behavior
cannot be established by JVM tests alone.

Never commit tokens, signing keys, local SDK paths, device dumps or original
third-party application archives. Release signing keys must stay outside the repo.

## Branches and releases

`main` contains merged work. Use short-lived `feature/*`, `fix/*`, `docs/*` or
`cleanup/*` branches and target pull requests at `main`. The `build` check must
pass before merging. Delete completed branches after merging; preserve unfinished
work before deleting its branch. GitHub is configured to delete merged branches.

Published versions use annotated `vX.Y.Z` tags and GitHub Releases with APKs and
SHA-256 checksums. Archived experiments use `archive/*` tags, not permanent
feature branches. See [the release checklist](docs/BUILD.md#порядок-публикации).
