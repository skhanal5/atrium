# atrium: rules for working in this repo

This repo holds Atrium, a home screen for one Toshiba Fire TV, and the tools
used to set up that TV. Read `README.md` first. It has the TV's facts, setup,
and what Fire OS allows.

## Merging pull requests

The owner allows Claude to merge its own pull requests into `main`. This was
granted on 24 November 2026. Merge only when every check below passes, and
follow them exactly.

### Checks before a merge

1. **Tests pass at the head commit.** Run `./gradlew testDebugUnitTest` in
   `atrium/`. Every test passes. No test was skipped or deleted to pass.
2. **Code review approved the head commit.** The `code-review` skill ran on
   the branch and ended with `Verdict: Approved`. The SHA in its first line
   matches `git rev-parse --short HEAD`, with no commits after it.
   - Skip this check only for a change to `.md` files alone.
3. **It works on the TV.** For any change to what runs on the TV, install the
   build, use the changed screens with the remote over ADB, and look at
   screenshots. Put what you checked in the PR's Testing section.
   - If the TV does not answer, it is asleep. Ask the owner to wake it. Do not
     merge until it is checked.
4. **The PR text follows `create-a-pr`.** A PR over 500 lines says why under
   Risks.
5. **The base is right, and there are no conflicts.**

### How to merge

- The repo allows **squash merges only**:
  `gh pr merge <n> --squash --delete-branch`.
- Never push or force-push to `main`.
- Never merge a pull request someone else opened unless the owner asks.
- Never merge with a check failing. Fix it, or ask.

### Stacked pull requests

A stacked PR is based on another PR's branch, not on `main`.

- Merge the bottom PR into `main` first.
- Then retarget the next PR to `main`: `gh pr edit <n> --base main`.
- Squash merges rewrite history, so rebase the next branch onto `main`:
  `git rebase --onto origin/main <old base head> <branch>`. Then
  `git push --force-with-lease`, and run the tests again.
- Never merge a PR into its parent branch. That leaves the work off `main`.
  This happened once. Two stacked PRs were merged into their base branches,
  and a third PR had to bring them over.

## Building and testing

```sh
cd atrium
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew testDebugUnitTest      # unit tests, about 1 second
./gradlew assembleDebug          # the APK
./gradlew lintDebug              # one error is expected, see below
```

- Lint stops on one known error: `targetSdk 29` is older than Google Play
  wants. It is set on purpose, and the app is not on Google Play. Any other
  lint error is new and must be fixed.
- `firetv/test-home.sh` runs the one older test, `HomePressTest`.

## The TV

- Address `10.0.0.71`. Use `tools/tv.py`, not the `adb` binary. The `adb`
  binary fails on this Mac because of Local Network privacy.
- Install: `TV_HOST=10.0.0.71 firetv/install-atrium.sh`. It builds, installs
  and opens Atrium.
- Run commands: `printf '%s\n' "cmd1" "cmd2" | TV_HOST=10.0.0.71 tools/venv/bin/python -I tools/tv.py --batch`
- Press keys with `input keyevent DPAD_DOWN`, `DPAD_CENTER`, `BACK`, and so on.
  Hold a key with `input keyevent --longpress DPAD_CENTER`.
- Screenshot: `screencap -p /sdcard/s.png`, then `tools/tv.py --pull /sdcard/s.png <file>`.
- Which app is in front: `dumpsys activity activities | grep mFocusedActivity`.
- Crashes: `logcat -d -t 400 AndroidRuntime:E *:S`.
- Smoothness: `dumpsys gfxinfo dev.skhanal.atrium reset`, use the remote,
  then `dumpsys gfxinfo dev.skhanal.atrium`.
- Memory: `dumpsys meminfo dev.skhanal.atrium`. The TV has about 285 MB free.
- `firetv/shoot-atrium.sh` retakes the README screenshots.
- Driving the remote takes over the TV. Say so before you do it.
- Put anything you change back the way you found it, such as app order.
- Screenshots can show the owner's accounts. Check them before you commit them.

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

- The token is `TMDB_READ_TOKEN` in `~/.gradle/gradle.properties`.
- Never print it, put it in a file in this repo, or put it in a commit.
- Keep the TMDB credit in Settings and in the README.

## Code

- No third-party libraries in the app. Test libraries are fine.
- Logic that can run without Android goes in plain Java with JUnit tests, in
  `content/` or `ui/FocusGrid`. Drawing code is checked on the TV.
- Write tests first. Follow the `tdd` and `testing` skills.
- Comments say why, not what.
- Text the screens show is in English, in Java, so it can be tested.

## Writing

Docs, commits and PR text use plain English:

- Short sentences, one idea each, active voice.
- Bullets over paragraphs.
- No em dashes and no semicolons.
