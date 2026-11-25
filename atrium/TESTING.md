# Atrium, test and performance report

Everything here was measured on the real TV, a Toshiba `AFTKMST12` running Fire
OS 6.7.1.1 (Android 7.1.2, API 25), over ADB. Nothing in this file is estimated
and nothing is from a simulator.

Dates of the runs: 18 October 2026 for the first version, 20 October 2026 for
the redesign in `REDESIGN.md`, and 24 November 2026 for the content home
screen. Build: `versionCode 1`, debug.

The first two sections are the current design. The sections after it describe the
previous design, the generated "room". They are kept because what they found
about this TV still holds.

## Your genres, 24 November 2026

Tested on the device:

- **Settings > Genres** opens a list of 18 genres. The list is longer than the
  screen, and it scrolls to follow focus.
- Select turned Comedy and Science fiction on, and each showed On. The list
  stayed open between picks.
- **Done** closed the list. The feed was fetched again, and a **Your genres**
  row appeared after On your apps, with the note "Comedy and Science fiction".
  Movies and shows took turns: Iron Man, Black Mirror, The Hunger Games, New Girl.
- The choice was kept after a reinstall and a restart.
- Turning both genres off and pressing Back also closed the list. The row was
  gone after the fetch, and Settings showed Genres as None.
- Memory, from `dumpsys meminfo` after a restart and a visit to every row:
  45.6 MB total without genres, 43.3 MB with two genres. The row adds nothing
  measurable.

Found on the device, and fixed:

- A list long enough to scroll put its title on the dimmed top bar. Menus now
  start lower.

Found in review, and fixed:

- Genres changed while a fetch was running were dropped until Atrium next came
  to the front. A load with other genres now waits and runs when the fetch
  ends. A unit test covers it. It was not timed by hand on the TV.


Tested on the device:

- **Get apps** in the top bar, and the Get apps tile at the end of Your apps,
  both open the screen.
- Typing "spo" with the TV keyboard filtered the grid to Spotify, then the
  apps with the topic "sports".
- Spotify, not installed, opened its Appstore page. Back returned to Atrium.
- **Search the full Appstore** opened the Appstore home, with Search on it.
- Every package name in `AppCatalog` opened its own Appstore page. Twitch,
  YouTube TV, Kodi and Pandora opened "App not Found" and were left out.

Found on the device, and fixed:

- Long rows did not slide, so focus could move off screen to the right. This
  was in the content home screen too.
- Amazon Music showed Installed and would not open. The debloat disables it.
  Disabled apps no longer count as installed.
- Moving up into the top bar went to the item used last, such as Settings.
  It now lands on the current screen's item.
- Opening the Appstore by its launcher entry shows "Your Apps & Channels",
  not the store. The store's home screen is opened by name instead.

Not tested: a second TV keyboard, such as a phone app, and typing with a
remote that has a microphone.

## Content home screen, 24 November 2026

### Performance

Frame timing, with `dumpsys gfxinfo` reset first, then 27 fast remote presses
across Trending, On your apps and Popular shows:

| | |
|---|---|
| Frames | 1,303 |
| Janky frames | 42 (3.2%) |
| Median | 5ms |
| 90th, 95th, 99th percentile | 9ms, 16ms, 21ms |

The budget is 16.7ms. The previous design had 0 janky frames out of 469. Most
of the new jank is the first frame that draws a new card picture, when the
picture is copied to the GPU.

Memory, from `dumpsys meminfo` after the same run:

| | Before the fix | After |
|---|---|---|
| Total PSS | 131 MB | 87 MB |
| Java heap (holds decoded pictures on Android 7.1) | 48 MB | 28 MB |
| Graphics | 64 MB | 39 MB |

The fix: hero pictures are shaded into RGB_565 with dithering instead of full
colour, only the next trending picture is loaded ahead, and the memory picture
cache is capped at 12 MB. The previous design used 29.4 MB. The difference is
the artwork. The TV has about 285 MB free.

Cold start, `am start -W` after `am force-stop`, three runs: 609ms, 552ms,
490ms. The previous design measured 371 to 424ms.

The APK is 317 KB. Inter adds about 190 KB, cut down to Latin characters.

### Tested on the device

- Home, Trending, On your apps and Popular shows, each focused, with the hero
  and artwork following the remote.
- The detail screen and Where to watch.
- **Open** in Peacock: Peacock came to the front.
- **Watch trailer**: YouTube came to the front.
- **Get the app** on Hulu: the Hulu Appstore page opened.
- Every package name in `StreamingApps` opened its own Appstore page. Each was
  checked by screenshot. Four names opened "App not Found" and were fixed or
  removed.
- The app menu, by holding Select and by the Menu button.
- Moving an app along the row, and putting it first again.
- Settings, with every value shown.
- A feed saved by an older build was fetched again when the streaming apps
  did not match.

### Found on the device, and fixed

- Rows scrolling up drew over the hero. The row area now clips by hand.
- Prime Video closes again when another app opens it. It is left out.
- The Appstore search link opens the Amazon home screen. Each app's page is
  opened by package name instead.
- The Menu button opened the app menu and its release closed it. It now opens
  on release.
- A long press sent over ADB opened the app instead of its menu. Android's
  long-press flag is now honoured as well as the hold time.

### Not tested

- Pulling the network cable with the new screen. The offline copy is covered
  by unit tests only.
- **Open** in Apple TV from the detail screen. It starts the app by package
  name, as for Peacock, which was tested. This button itself was not pressed.
- Reduce motion with the new screen.
- A library of many apps. This TV has 9 launchable apps.
- Running for days, through several six-hour feed refreshes.

## Performance

### Frame timing

Measured with `dumpsys gfxinfo`, reset first, then 24 focus moves along the app
row. The moves go right, right, left, left, so every single press changes the
app and starts a new room dissolving. This is the most expensive thing the
launcher does.

Two runs, one with the rooms already built and one from a cold start where
they are still being generated in the background:

| | Rooms warm | Rooms cold |
|---|---|---|
| Frames rendered | 619 | 621 |
| **Janky frames** | **2 (0.32%)** | **1 (0.16%)** |
| 50th percentile | 5ms | 5ms |
| 90th percentile | 7ms | 7ms |
| 95th percentile | 8ms | 8ms |
| 99th percentile | 16ms | 13ms |
| Missed vsync | 0 | 0 |
| High input latency | 0 | 0 |
| Slow UI thread | 0 | 0 |
| Slow bitmap uploads | 0 | 0 |

The budget for 60fps is 16.7ms. The median frame uses 5ms of it.

A second row of apps, moved along without a room change, gives 359 frames and
1 janky frame (0.28%), 90th percentile 7ms. So the room costs about nothing
once it is on screen.

### What the room costs while it dissolves

One run stands apart and is kept here because it is the honest worst case. On
the very first launch after an install, with no room built yet and the system
still optimising the code, 24 moves gave **37 janky frames out of 158 (23.4%)**
with a cluster of frames at 20ms. Every one of them was blamed on slow draw
commands, not on slow Java.

The reason is arithmetic. While one room dissolves into the next, this view
draws the whole screen three times: the old room, the new room over it, and
the grain over both. The rest of the time it draws it twice.

So the third pass was taken away. The grain is only there to break up banding
in a still picture, and while the picture is moving there is nothing still to
band. The outgoing room is also drawn without filtering, since it is covered
within a fifth of a second.

That first-launch case was not re-run under identical conditions, so the two
numbers above are the claim, and the 23.4% figure is recorded as what a cold
first start looked like before the change rather than as a before and after.

### Memory

| | |
|---|---|
| Total PSS | 29.4 MB |
| Native heap | 7.5 MB |
| Dalvik heap | 15.8 MB |
| GPU texture cache | 4.9 MB |
| Views on screen | 39, holding 50 kB of display lists |

The TV has 1.58 GB of RAM with about 394 MB available, so Atrium is using
roughly 7% of what is free. The per-app heap cap is 192 MB and the Dalvik heap
is nowhere near it.

This is up from 16.9 MB in the first version. The increase is the artwork: up
to eight generated rooms are held at about 590 KB each, and every app tile is
composed once into its own bitmap. Both are deliberate trades of memory for
frame time.

### Start up

Measured with `am start -W`, three runs after `am force-stop`:

| Run | TotalTime | WaitTime |
|---|---|---|
| 1 | 371ms | 403ms |
| 2 | 414ms | 432ms |
| 3 | 424ms | 441ms |

Slower than the 227ms of the first version, and the reason is the artwork. The
first room has to exist before the screen means anything, so it is built on the
way in. The apps are laid out, focused and ready to open on the first frame, so
nothing waits for it.

### Size

The APK is **283 KB**. There are no third-party dependencies at all. For
comparison the earlier launcher in this repository is 1.66 MB, because it pulls
in AndroidX RecyclerView.

## What was tested on the device

All of these were driven with real key events over ADB and the result checked
in a screenshot.

| Test | Result |
|---|---|
| Install over ADB | Pass |
| First launch after install | Pass |
| Later cold starts | Pass |
| App discovery | Pass. 7 apps shown, 3 system apps hidden by default |
| Real names and icons | Pass |
| Launching an app | Pass. YouTube opened |
| Returning from an app | Pass. Focus, light and label all restored to YouTube |
| Left and right along the app row | Pass |
| Down from the apps to the footer | Pass. Lands on the nearest word |
| Up from the footer back to the apps | Pass |
| Left and right inside the footer | **Failed, then fixed.** See below |
| Menu button opens the app menu | Pass |
| Back closes a menu | Pass |
| Back on the home screen | Pass. Does nothing, as intended |
| Preferences screen | Pass. Every row shows its current value |
| Changing the light preset | Pass. Room changed, and the choice survived a reinstall |
| Hidden apps screen | Pass |
| Colour read from app icons | Pass. Green for AirPlay, teal for Silk, red for YouTube |
| Clock updates on the minute | Pass |
| Rearranging apps | Pass. Entered from the menu, moved an app right and back, order persisted |
| Settings handoff: Network | Pass. Opens the Fire TV network screen |
| Settings handoff: System | Pass. Opens the My Fire TV screen |
| App info from the app menu | Pass. Opens and stays open |

### The settings buttons were wrong

Reported by the user: "the display and sound button doesnt work". It did not,
and neither did Settings. The cause was a bad test on my part. Availability was
decided by asking whether an intent **resolved**, which is not the same as
being allowed to **start** it. On Fire OS the difference is large:

- `android.settings.DISPLAY_SETTINGS` has two handlers and both are guarded by
  `com.amazon.tv.permission.LAUNCHER_SETTINGS`, which is `signature|amazon`. No
  installed app can ever hold it. The button could not have worked.
- Because it had two handlers, the intent resolved to the system chooser rather
  than to a settings screen.
- `android.settings.SETTINGS` resolved to the Amazon launcher's own settings
  entry, so pressing it dumped the user on the Amazon home screen.

Availability is now decided by reading the target activity's manifest entry
through `PackageManager.getActivityInfo`: is it exported, is it enabled, and
does it demand a permission this app does not hold. That is the only check that
knows about the permission wall. A resolution that lands on `ResolverActivity`
is also treated as unavailable.

The footer is now Network, System and Atrium. Display and Sound, Accessibility,
Preferences and My Account are all unreachable and are not offered. Apps was
dropped as well: that screen opens and then closes itself within milliseconds
unless it is given a package to show.

### The one real navigation failure

Pressing right on the last word of the footer jumped focus **up into the app
row**, because that app was further right on the screen. The next press of
Select then opened an app instead of the footer item the user thought was
selected.

The focus finder was doing what it is designed to do: find any view in the
direction pressed. The fix wires each row's ends back to themselves, so left
and right mean "along this row" and nothing else. Up and down are still left to
the focus finder, which picks the nearest item in the other row and gets it
right.

Other defects found and fixed while testing on the device:

- The reflection under a bare logo appeared as a detached slab, because the
  reflection mirrored the raw icon while the cell drew a plate with the icon
  inset in it. Both now come from one composed image, so they cannot disagree.
- The menu scrim was too light. The clock and the app row showed through and
  collided with the menu text.
- A green app icon turned the whole left of the screen green and washed out the
  icons beside it. The tint is now blended less far and the light is narrower.
- Closing a menu opened from the footer dropped focus back onto the apps
  instead of where it came from.
- Two hidden apps are both called "Downloads" on this TV. The package name is
  now shown beside each, so they can be told apart.

## The Home button

Tested on the device:

| Attempt | Result |
|---|---|
| `cmd package set-home-activity` pointed at Atrium | Reports `Success`, Home still opens the Amazon launcher |
| `android:priority="5"` on Atrium's HOME filter | Android reset it to 0. `dumpsys package` shows no `mPriority` for our filter |
| `pm disable-user --user 0 com.amazon.firehomestarter` | `SecurityException: Cannot disable a protected package`. Nothing changed |

The Home button is routed by `com.amazon.firehomestarter`, whose HOME filter
declares `mPriority=1`. Every other HOME handler on this TV sits at 0, and the
package is protected against being disabled.

These attempts do not replace the system Home handler. The log-based Home
workaround added on 22 October is described below.

### Opening Atrium when the TV turns on: works

Tested on the device, with the TV left on the Amazon home screen, put to sleep,
and woken:

| | Foreground app |
|---|---|
| Before sleep | `com.amazon.tv.launcher/.ui.HomeActivity_vNext` |
| t+1s after wake | `com.amazon.tv.launcher/.ui.HomeActivity_vNext` |
| t+2s after wake | `dev.skhanal.atrium/.AtriumActivity` |
| t+3s to t+8s | `dev.skhanal.atrium/.AtriumActivity` |

So the Amazon screen is visible for **roughly one to two seconds** and then
Atrium opens over it and stays. The screenshot afterwards shows Atrium drawn
correctly, with the dusk light for the hour and the focused app restored.

The delay before Atrium opens is 900ms, set in `WakeService.SETTLE_MS`. It is
deliberately not shorter: the Amazon launcher is coming up at the same moment,
and starting too early risks it arriving on top afterwards, which would be
worse than the flash.

The accessibility watcher could not be enabled. On 22 October, Fire OS cleared
its secure setting immediately. The new Home handler does not use accessibility.

## Grain, and why it had to be cut

The first grain setting looked right while developing and was obviously grainy
on the TV. The cause is specific to this panel: it is 4K but Android draws at
1080p, so every drawn pixel is shown as a 2x2 block, and per-pixel noise is
four times larger in area than it appears on a monitor.

Measured on the device, in a flat dark area of the background:

| | neighbouring pixel difference | black level |
|---|---|---|
| Before | mean 1.43, max 10 | 20.0 |
| After | mean 0.22, max 2 | 13.5 |

The noise is about six times weaker, and the blacks sit where they should. The
old version was white-only, which could lighten but never darken, so it lifted
the black level as well as dithering it. The new one is half light and half
dark, so its average is neutral.

## What was not tested

Stated plainly, because an untested claim is worth nothing.

- **The offline notice.** Testing it means taking the TV off the network, which
  would also cut the ADB connection used to observe it. The code path is
  guarded and falls back to showing nothing if the state cannot be read.
- **The empty state.** It needs every app hidden. The drawing code is exercised
  by the same layout as everything else, but the state itself was not reached
  on the device.
- **Holding Select to open the app menu.** `input keyevent --longpress` does
  not hold the key, so it registers as an ordinary press. The Menu button opens
  the same menu and that was tested. The hold uses Android's own long-press
  handling, which was not confirmed on this hardware.
- **Reduce motion.** The setting stores and reads back correctly. The visual
  result was not checked in a screenshot.
- **Low memory behaviour**, and the TV running out of RAM while Atrium is in
  the background.
- **A long app list that scrolls.** This TV only has 7 launchable apps, so the
  row never needed to scroll. The code that keeps the light attached to a
  scrolling row is therefore unexercised.

## Reproducing this

```sh
# frame timing
TV_HOST=10.0.0.71 tools/venv/bin/python -I tools/tv.py \
  'dumpsys gfxinfo dev.skhanal.atrium reset'
# ... drive the remote ...
TV_HOST=10.0.0.71 tools/venv/bin/python -I tools/tv.py \
  'dumpsys gfxinfo dev.skhanal.atrium' | head -20

# memory
TV_HOST=10.0.0.71 tools/venv/bin/python -I tools/tv.py \
  'dumpsys meminfo dev.skhanal.atrium'

# start up
TV_HOST=10.0.0.71 tools/venv/bin/python -I tools/tv.py \
  'am force-stop dev.skhanal.atrium; am start -W -n dev.skhanal.atrium/.AtriumActivity'
```

## Home button update, 22 October 2026

Tested on the same Toshiba `AFTKMST12`, build `NS6711`.
Home events were sent with `input keyevent 3` over ADB.

| Test | Result |
|---|---|
| Enable the accessibility service over ADB | Fire OS immediately cleared it |
| Grant `READ_LOGS` to Atrium | Pass |
| Start the shell helper and reconnect over ADB | Pass. Helper answered `atrium-home 1` |
| Home from YouTube with the helper | Pass |
| Home from Network settings | Pass. Network stayed open until Home was pressed |
| Open Amazon home on purpose | Pass. It stayed open until Home was pressed |
| Home while already in Atrium | Pass. Atrium regained focus |
| Home after stopping the helper | Pass after the timing fix below. About five seconds |
| Restore helper after fallback test | Pass |
| Final foreground app | Atrium. Fast helper running |

The Home log arrives before Amazon finishes opening its home screen.
The first fallback attempt opened Atrium too early, then Amazon covered it.
HomeKeyService now waits 200ms before opening Atrium. The fallback test then
returned focus at `21:46:05.283`, after Home at `21:46:00.278`: **5.005s**.
The final helper test moved focus from Amazon at `21:47:37.584` to Atrium at
`21:47:37.832`: **248ms**. These are focus times, not screen draw times. This helper was removed after
a direct task move worked without it.

Checks:

- `bash firetv/test-home.sh` passed. It covers Home, duplicate events, the
  repeat limit, empty input, unrelated logs, settings focus and long Home logs.
- Build and shell syntax checks passed.
- Android lint passed with the existing Google Play target SDK rule excluded
  through a temporary Gradle init script. This app is installed over ADB.
- Lint still reports warnings, including the helper's use of a private Android
  API. The helper is specific to Fire OS 6 / Android 7.1.

Not tested in this run: a full TV reboot, physical remote long holds, turning
the preference off and on, and recovery after prolonged memory pressure.
Stopping the helper verified the slow path without rebooting the TV.

## Home button follow-up, 22 October 2026

The shell helper needed a Mac command after each full TV reboot. This was not
acceptable for daily use. On the same TV, `ActivityManager.AppTask.moveToFront`
brought Atrium forward without the helper. A single move after a 500ms settle
wait returned Atrium from YouTube. The Home press was logged at `21:59:49.331`
and Atrium gained focus at `21:59:50.004`, a **673ms** interval. A repeated
Home press with two task moves also worked. The helper was stopped for these
tests.

The app uses the task move when Atrium has an existing task. If no task exists,
it starts the activity through the slower path. A full TV reboot was tested next. Fire OS delivered `BOOT_COMPLETED` after
roughly a minute. BootReceiver opened Atrium and created its task. A Home press
from YouTube then moved focus from Amazon at `22:07:37.842` to Atrium at
`22:07:38.381`: **539ms**. The helper process was absent. No Mac command was
run after reboot. Before the boot event, the Home watcher is not yet active.

The clean build was then installed. Its first Home press happened while Fire OS
was still starting the updated service, so Amazon stayed in front. After the
service started, a Home press from YouTube moved focus from Amazon at
`22:08:30.942` to Atrium at `22:08:31.480`: **538ms**. The old shell helper
was not running. The clean build contains no helper code.
