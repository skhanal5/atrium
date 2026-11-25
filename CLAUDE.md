# atrium: rules for working in this repo

This repo holds Atrium, a home screen for a Toshiba Fire TV, and the tools used
to set up that TV. Read `README.md` first. It has the TV's facts, setup, and
what Fire OS allows.

Personal settings, such as your TV's address, go in `CLAUDE.local.md`. Git
ignores that file.

## Pull requests

- Branch from `main`. Never push or force-push to `main`.
- The repo allows **squash merges only**.
- Before a merge:
  - Every unit test passes. No test was skipped or deleted to pass.
  - A change to what runs on the TV was checked on a real TV. The PR's
    Testing section says what was checked.
  - A PR over 500 lines says why.

### Keep personal email out of commits

The repo is public. A personal email in a commit cannot be taken back.

- Commit with your GitHub noreply address. Set it for this repo with
  `git config user.email <id>+<user>@users.noreply.github.com`.
- `gh pr merge` uses your account's default email for the merge commit. Pass
  `--author-email` with your noreply address.
- GitHub keeps every PR's commits, even after a force-push. Check the email
  before you push, not after.

### Stacked pull requests

A stacked PR is based on another PR's branch, not on `main`.

- Merge the bottom PR into `main` first.
- Then retarget the next PR to `main`: `gh pr edit <n> --base main`.
- Squash merges rewrite history, so rebase the next branch onto `main`:
  `git rebase --onto origin/main <old base head> <branch>`. Then
  `git push --force-with-lease`, and run the tests again.
- Never merge a PR into its parent branch. That leaves the work off `main`.

## Building and testing

Set `JAVA_HOME` to a JDK 17 and `ANDROID_HOME` to the Android SDK. Then:

```sh
cd atrium
./gradlew testDebugUnitTest      # unit tests, about 1 second
./gradlew assembleDebug          # the APK
./gradlew lintDebug              # one error is expected, see below
```

- Lint stops on one known error: `targetSdk 29` is older than Google Play
  wants. It is set on purpose, and the app is not on Google Play. Any other
  lint error is new and must be fixed.
- `firetv/test-home.sh` runs the one older test, `HomePressTest`.

## The TV

Set `TV_HOST` to the TV's IP address.

- Use `tools/tv.py`, not the `adb` binary. The `adb` binary fails on macOS
  because of Local Network privacy.
- Install: `firetv/install-atrium.sh`. It builds, installs and opens Atrium.
- Run commands: `printf '%s\n' "cmd1" "cmd2" | tools/venv/bin/python -I tools/tv.py --batch`
- Press keys with `input keyevent DPAD_DOWN`, `DPAD_CENTER`, `BACK`, and so on.
  Hold a key with `input keyevent --longpress DPAD_CENTER`.
- Screenshot: `screencap -p /sdcard/s.png`, then `tools/tv.py --pull /sdcard/s.png <file>`.
- Which app is in front: `dumpsys activity activities | grep mFocusedActivity`.
- Crashes: `logcat -d -t 400 AndroidRuntime:E *:S`.
- Smoothness: `dumpsys gfxinfo dev.skhanal.atrium reset`, use the remote,
  then `dumpsys gfxinfo dev.skhanal.atrium`.
- Memory: `dumpsys meminfo dev.skhanal.atrium`. The TV has about 285 MB free.
- `firetv/shoot-atrium.sh` retakes the README screenshots.

### Checking a change on the TV

- Install the build, use the changed screens with the remote over ADB, and
  look at screenshots.
- Driving the remote takes over the TV. Say so before you do it.
- Put anything you change back the way you found it, such as app order or
  picked genres.
- Screenshots can show account or profile names. Check them before you
  commit them.

## Android 7.1 traps

The TV runs Android 7.1 (API 25).

- `URLEncoder.encode(String, Charset)` and `ByteArrayOutputStream.toString(Charset)`
  crash on this TV, and **lint does not flag them**. Use `content/Text`.
- `java.nio.file` needs Android 8. Use `java.io`.
- `List.of`, `Set.of` and `String.join` are safe. The build tool rewrites them.
- When unsure about a method, build the APK and look at the call with
  `dexdump`. A method the build tool rewrites shows as `ExternalSyntheticBackport`.
- Fonts must be fixed weights. Android 7.1 cannot read variable fonts.

## Fire OS facts, tested on this TV

- An Appstore page opens with `amzn://apps/android?p=<package>`. A wrong
  package shows "App not Found". Check every new package name on the TV.
- The Appstore search link (`?s=`) opens the Amazon home screen. It does not search.
- Prime Video closes when another app opens it.
- Peacock accepts no links to a title. Apple TV has links, but TMDB does not
  give Apple's title IDs.
- YouTube links must name `com.amazon.firetv.youtube`, or Android shows a chooser.
- Most Fire TV settings screens are blocked. See the table in `README.md`.

## TMDB

- The build reads the token from `TMDB_READ_TOKEN` in `~/.gradle/gradle.properties`.
- Never print it, put it in a file in this repo, or put it in a commit.
- Keep the TMDB credit in Settings and in the README.

## Code

- No third-party libraries in the app. Test libraries are fine.
- Logic that can run without Android goes in plain Java with JUnit tests, in
  `content/` or `ui/FocusGrid`. Drawing code is checked on the TV.
- Write the test first, and watch it fail before you write the code.
- Comments say why, not what.
- Text the screens show is in English, in Java, so it can be tested.

## Writing

Docs, commits and PR text use plain English:

- Short sentences, one idea each, active voice.
- Bullets over paragraphs.
- No em dashes and no semicolons.
