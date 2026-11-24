# Atrium, design

Atrium is the home screen for a 2019 Toshiba Fire TV. This file says what it
looks like, why, and the numbers used to build it.

`REDESIGN.md` is the history of the previous design. `TESTING.md` is what was
measured on the TV.

## The idea

- The home screen is about what to watch, not about the launcher.
- Real movies and shows fill the screen. Your apps sit in one row under them.
- Every title says where you can watch it on this TV, and takes you there.

The previous design, a dark "room" coloured by the focused app's icon, was
calm and fast. It was also bland, and it had no way to find something to
watch or to get a new app.

## Where the content comes from

- **TMDB**, a free movie database, with a read access token. See the README.
- Three rows: Trending this week, On your apps, and Popular shows.
- "On your apps" is titles included with the streaming apps on this TV. Today
  that is Peacock and Apple TV.
- Where to watch is TMDB's United States data, which TMDB gets from JustWatch.
- The rows are saved on the TV. They show at once on start, and still show
  with the network down. They are fetched again after six hours, or when a
  streaming app is installed or removed.

## What it does not do

Each of these was tested on the TV.

- **No "continue watching".** No app on this TV can read another app's history.
- **No opening a title inside Peacock or Apple TV.** TMDB does not give those
  apps' own title IDs. **Open** starts the app at its home screen.
- **No Prime Video.** It closes again as soon as another app opens it.
- **No Appstore search.** The search link opens the Amazon home screen on
  Fire OS 6. Atrium opens one app's Appstore page by package name instead.
- **No Starz or MGM+.** Their Fire TV package names could not be confirmed.

## Layout

The TV draws at 1920x1080 with a density of 320dpi, so the canvas is
**960x540dp**. One dp is two pixels.

| Part | Position |
|---|---|
| Side margins | 48dp. A TV can crop 5% of each edge, which is 48dp |
| Top bar | 20dp from the top, 36dp high |
| Hero | 72dp from the top, 450dp wide |
| Rows | Start at 342dp and scroll up under the hero |

- The **hero** shows one title: kind, name, facts, two lines about it, where
  to watch it on this TV, and two buttons.
- On a movie or show, the hero shows that title. On the top bar or your apps,
  it shows the top five trending titles in turn, eight seconds each.
- The **rows** slide sideways so the focused card is at the left edge.
  Further down, the focused row moves up to sit under the hero.

| Item | Size |
|---|---|
| App tile | 108 x 61dp plate, 39dp icon, name under it |
| Wide card | 200 x 112.5dp, backdrop picture |
| Poster card | 98 x 147dp, poster picture |
| Where-to-watch card | 165 x 56dp |

## Type

One family, **Inter**, in four weights: Regular, Medium, SemiBold, Bold.

- Inter was drawn for screens. It stays clear from a sofa and does not compete
  with the artwork.
- The files are fixed weights cut down to Latin characters, about 47 KB each.
  Android 7.1 cannot read variable fonts.
- A name with characters Inter does not have, such as Japanese, falls back to
  the system font for that one label.

| Use | Weight | Size |
|---|---|---|
| Hero title | Bold | 52sp, down to 29sp for long names |
| Hero facts | Medium | 11.5sp |
| Hero text | Regular | 12sp |
| Row heading | SemiBold | 12.5sp |
| Button | Bold | 12.5sp |
| Card name | SemiBold | 10.5sp |

## Colour

The artwork carries the colour. Everything Atrium draws is warm white on ink.

| Token | Value | Use |
|---|---|---|
| `TEXT` | `#F4F1EC` | Titles and focused names |
| `TEXT_2` | 74% | Facts, text, resting names |
| `TEXT_3` | 52% | Labels and credits |
| `INK` | `#0A0D14` | The background, and text on a focused control |
| `OK` | `#8FE3A4` | "Open": an app you have |
| `GET` | `#FFC56E` | "Get the app": an app you do not have |
| `WARN` | `#E8B079` | "Offline" |

- An app tile's plate is the icon's own colour, darkened.
- "Open" and "Get the app" differ in words as well as colour.

## Focus

- The focused item turns white or gets a white ring, and grows a little.
- Focus is shown by brightness and size, so it does not rely on colour.
- Up and down return to the item you last had in each row. This is
  `FocusGrid`. Android's own focus search picks the nearest view instead,
  which is rarely what a person means.

## Motion

| Move | Duration |
|---|---|
| Focus | 220ms |
| A row sliding, rows scrolling | 240ms |
| Hero text out, then in | 130ms, 260ms |
| Artwork dissolving | 600ms |

- Focus is fast. The artwork is slow.
- Nothing makes you wait. Input works during every animation.
- Reduce motion sets every duration to zero.

## How it is drawn

The GPU is a Mali-T820 MP2. It cannot draw three full-screen layers at 60
frames a second.

- The dark shading over the artwork is drawn into the picture once, on a
  background thread. Drawing the screen is one picture, or two while one
  dissolves into the next.
- Shaded pictures are 960x540 in RGB_565, dithered so dark areas do not band.
- Pictures are downloaded at the smallest TMDB size that fits, and decoded no
  larger than drawn.
- Pictures stay in a 12 MB memory cache and a 48 MB disk cache.
- Card corners are drawn with a bitmap shader, not a clip path.
- Focus changes only view properties, so the GPU reuses what it drew.

## Menus

Menus are words over the darkened screen. There is no panel. The focused row
brightens and a short line appears on its left, so focus is shown by position
as well as brightness.
