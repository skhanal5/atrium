# Atrium, design system

Atrium is a home screen for a 2019 Toshiba Fire TV. This file explains what it
looks like, why, and the exact numbers used to build it.

`REDESIGN.md` explains how it got here, and what the first version got wrong.

## The idea

> Every app has a room. The room is made out of the colour inside that app's
> own icon. Point the remote at an app and you walk into its room.

The stock Fire TV home screen fills the top third with an advert and the rest
with sponsored thumbnails. See `../firetv/shots/01-before-stock-home.png`.

A streaming service answers that with its own artwork, which it owns. A
launcher on this TV has none. The banners Fire OS exposes are flat logo plates,
320x180 at best. So the artwork here is not fetched and not shipped. It is
generated, per app, from the one honest source of identity available: the
pixels of the icon.

This was chosen for two reasons, not one.

- It gives every app a hero image that no stock launcher has.
- It is cheap. The TV has a 2019 mobile GPU and 2 GB of RAM. One stretched
  bitmap per frame is nearly free, and the expensive part happens once, on a
  background thread.

## The signature

Point at YouTube and the screen becomes a warm red chamber with light falling
across the top right. Point at Peacock and it becomes a cold blue hall. Point
at an icon with no colour in it and the room keeps the light of the hour.

The colour is read out of the icon's own pixels at startup. It is not a table
of brand colours that somebody typed in.

A raw brand colour at full strength looks like a neon sign, so the room is
built from three colours derived from that one hue:

| Part | Saturation | Value | What it is |
|---|---|---|---|
| Light | hue, capped at 0.44 | 1.00 | what the aperture gives off |
| Lit side | hue, capped at 0.76 | 0.19 | the wall the light lands on |
| Shadow | hue **+168 degrees**, 0.55 | 0.085 | the side away from the light |

The third row is the important one. The dark side of the room is not the same
colour turned down. It is the opposite colour, nearly black. Warm key and cool
shadow is how a lit room actually photographs, and it is the single thing that
stops a coloured screen reading as a tint laid over everything.

## Type

One family, **Jost**, in three weights. Jost is a geometric sans in the Futura
line, which is the typeface language of architecture and signage. Size, weight
and letter spacing carry the hierarchy, not a second typeface.

| Use | Weight | Size | Letter spacing |
|---|---|---|---|
| Hero title | Light 300 | 58sp, auto-fits down to 32sp | -0.005em |
| Hero eyebrow | Medium 500 | 12sp | 0.28em, upper case |
| Hero line | Light 300 | 14.5sp | 0 |
| Clock | Light 300 | 34sp | 0.005em |
| Date | Medium 500 | 11sp | 0.20em, upper case |
| Section heading | Medium 500 | 12sp | 0.26em, upper case |
| Rail item | Regular 400 | 15sp | 0 |
| Button | Medium 500 | 15sp | 0.02em |
| App name | Regular 400 | 12sp | 0 |
| Tool name / line | Regular 400 / Light 300 | 14sp / 11sp | 0 |

Details that are easy to miss and do most of the work:

- **The hero title auto-fits.** App names are not ours. The size steps down 2sp
  at a time until the name fits on one line, rather than wrapping into the row
  below.
- **Small text is upper case and widely spaced. Large text is lower case and
  tight.** The opposite textures are what make the pairing look deliberate
  rather than merely big and small.
- **The clock is a label, not a masthead.** In the first version it was the
  largest thing on screen, which made the screen about the time.
- **A name that needs a character Jost does not have falls back to the system
  font**, for that label only. Without it the name draws as empty boxes.

The font files are static weights. Jost ships as a variable font and Android
7.1 cannot read variable axes, so a variable file would draw every weight the
same. This is a real trap on this TV.

## Colour

Text is a warm off-white, `#F5F3EF`, never pure white. Pure white on a dark
field reads as a computer display. A paper white reads as print.

| Token | Value | Use |
|---|---|---|
| `TEXT_PRIMARY` | `#F5F3EF` | the hero title, the focused name |
| `TEXT_SECONDARY` | 59% | the hero line, resting app names |
| `TEXT_TERTIARY` | 47% | headings, the date, tool lines |
| `TEXT_WARN` | `#E8B079` | the offline notice, the only warning colour |
| `INK` | `#0B0C10` | text on a focused button |
| `SURFACE` | white at 10% | a resting button |
| `HAIRLINE` | white at 36% | the line around a resting button |

These are higher than they first were. Values that looked refined on a desk
monitor were unreadable on a 43 inch panel from eight feet. The only distance
that counts is the sofa.

The time of day still sets the mood underneath. Dawn, Day, Dusk and Night each
carry their own light colour and strength, and the user can pin the room to
Warm, Cool or Monochrome instead.

## Layout

The TV composes at 1920x1080 at 320dpi, so the canvas is **960x540dp** and 1dp
is 2 physical pixels. The panel is 4K but Android does not draw at 4K.

Everything is placed in `HomeLayout.java`:

```
  28   clock, above the rail
  92   hero: eyebrow, name, one line
 116   rail: Home, Apps, Settings
       Open and Options, 16 under whatever the hero measured
 300   "your apps"
 312   the row of app tiles
 444   "tools"
 454   the row of destinations
 510   bottom safe margin
```

The buttons are the one thing without a fixed number. The hero is as tall as
the name and the line under it need, and the name changes size to fit the
width, so a fixed number would put the buttons on top of the text for some app
names and leave a hole for others.

The rail sits at x=28 and the content column starts at x=236. A TV may crop 5%
of the picture, which is 27dp here, and nothing that matters is outside 28dp.

The artwork fills the whole screen behind all of it. The light is pushed into
the **top right**, and both the left side and the bottom are held down:

- the left 56% of the picture keeps at most a twentieth of the light, so 64sp
  type always has a dark field under it
- the light falls away below 40% of the height, so the two rows of content sit
  on near black

That is why the picture can be bright and the type can still be read. The
artwork and the layout were designed against each other, not one over the
other.

## Apps

The rail's Apps item opens every app as a grid, in the same tiles, with the
same focus language and the same margins. The room behind it fades to half
strength while it is open, so the names do not compete with the light. The
rail stays in view, so the way back is always on screen.

- 78dp tile, 14dp apart, 15dp corner radius, name underneath at 12sp.
- Every icon is clipped to the same rounded square, so the row has one
  silhouette whatever shape the original icons are.
- An icon that is a bare logo on a transparent background gets a faint plate
  behind it, or it would float. This is detected by looking at how clear the
  outer eighth of the icon is.
- The brands are left alone. What makes the row one thing is the treatment
  around them, not a filter over them.

### Focus

Focus is shown four ways at once, and none of them is a colour on its own:

1. The focused tile is the only one at full strength. The rest hold at 72%.
2. It is larger, 1.09 against 1.0.
3. It **grows from its foot**, so the row does not shuffle sideways.
4. A 2dp rim is drawn around it, in that app's own colour, and the hero and the
   whole room take the same colour.

That means focus is clear to someone who cannot separate colours, and clear
from across a room, where brightness is the thing the eye reads first.

Vertical moves are aimed, not guessed. Leaving the row upwards to the buttons
and coming back down returns to the tile you left, because the nearest view in
the direction pressed is not what a person means.

## Motion

Two rules hold it together.

- **Focus is fast, the room is slow.** The thing the remote controls reacts at
  once. The artwork dissolves behind it. That difference is what makes the room
  feel large and the input feel immediate.
- **Nothing ever makes the user wait.** Every animation is decorative. Input is
  accepted during all of them. Pressing Select starts the app on the same frame
  and the press animation runs alongside it.

| Move | Duration | Curve |
|---|---|---|
| Focus | 220ms | `SWIFT` 0.2, 0, 0, 1 |
| Hero out, then in | 130ms, 260ms | `SMOOTH`, `SWIFT` |
| The room dissolving | 420ms | `GENTLE` 0.33, 0, 0.1, 1 |
| Press in / out | 90 / 160ms | `SMOOTH` / `SWIFT` |
| Opening | 700ms | `GENTLE` |
| Section change | 240ms | `SWIFT` |
| Menu | 200ms | `SWIFT` |

The hero leaves before the next one arrives rather than crossfading through it,
because two sets of 64sp type on top of one another is a smear.

"Reduce motion" sets every duration to zero. Every state still ends up correct,
it just gets there at once.

### The opening

On a cold start the room comes up from dark over 700ms. It is the one piece of
theatre in the launcher and it never blocks anything. The apps are laid out,
focused and ready on the first frame. Only the brightness is animated.

It does not replay when you come back from an app. That would be charming once
and irritating forever.

## Menus

Menus are **words in the dark**. No card, no panel, no border. The room stays
visible behind a scrim, so opening a menu feels like the lights going down. The
column of rows sits in the same place as the hero, so opening one does not move
the eye anywhere it was not already looking.

The focused row brightens and a short rule slides in on its left. The rule is
the second, non-colour cue.

## The artwork

One room is: a shell, an aperture, shafts, a floor, a bloom and grain.

- **The shell** lifts towards the ceiling and falls away below the horizon at
  78% of the height, so the room has a top and a bottom before any light lands
  in it.
- **The aperture** is a soft ellipse of light at 78% across and 24% down. It is
  the focal point of the whole screen.
- **Four shafts** lean 0.34 off vertical. Their positions, widths and
  brightnesses come from a hash of the package name, so no two apps get the
  same room and one app always gets the same room. Even spacing reads as a
  pattern, and a pattern reads as wallpaper rather than as light.
- **Each shaft has a halo** 3.4 times its width. A beam with only a bright core
  reads as a scratch on the screen. Light in dusty air glows around the bright
  part.
- **The floor reflects**, dimmer and squashed, which stops the bottom of the
  picture reading as a black band.
- **An S curve** is applied to the amount of light before the colours are
  mixed. A straight ramp was what made the first attempt read as one flat tint.
- **A bloom** washes the brightest 18% towards white, the way an overexposed
  highlight does. Without it the brightest part of the picture is still only
  the app's colour, and the whole thing stays flat.
- **Fine grain** is laid over everything, and it has to be very weak. A dark
  gradient on an 8-bit panel bands into visible steps, and a little noise
  breaks the steps up. This panel is 4K while Android draws at 1080p, so every
  drawn pixel becomes a 2x2 block and noise is four times larger in area than
  it looks while developing. It is half light and half dark, because white-only
  noise can only lighten and so lifts the blacks.

## How it is drawn

The GPU is a Mali-T820 MP2. A real blur or a per-frame gradient would not hold
60fps. So:

- A room is computed **once**, at 512x288, on a background thread, into one
  bitmap that is then stretched over the screen. Everything in it is smooth, so
  the bilinear filtering that comes free with the upscale is doing useful work.
- Only the **most recent** request is built. Running along the row does not
  queue up rooms nobody will see.
- **Eight rooms are kept**, about 4.7 MB. Rooms for every installed app are
  warmed in the background at the lowest priority after the apps load, so
  moving along the row is instant.
- Drawing the screen is **one bitmap draw and one tiled rect**, or two bitmaps
  during a dissolve.
- Each app tile, the rounded clip and the plate behind a bare logo, is composed
  into one bitmap while the apps load. Drawing a tile is one bitmap draw.
- The wash behind a tool chip is painted through a **shader**, not a clip path,
  because a clip path costs a save layer and this GPU charges for those.
- Focus animates **view properties only**. The GPU already holds each tile's
  drawing, so moving and fading one costs a matrix change and no redraw.
- When nothing is moving, nothing is drawn.

## Measured

Measured on the TV against this version. 619 frames while moving along the app
row, with a new room dissolving on every press: 2 dropped frames, a median of
5ms against the 16.7ms budget, and nothing worse than 16ms at the 99th
percentile. Full numbers and method in `TESTING.md`.

One rule came out of that run. While a room dissolves, the screen is drawn
three times over: the old room, the new one, and the grain. That third pass is
the one thing this GPU cannot afford, so the grain is left out until the
dissolve ends. There is no still picture to band while the picture is moving.

## What it does not do

Atrium offers nothing that this TV refuses to do.

There is no Inputs destination, because there cannot be one. Both the Fire TV
inputs screen and the tune-to-input intent are protected at level
`signature|amazon`. No installed app can hold those, and even ADB shell is
refused. A dead button would be a lie.

There is no "continue watching" row and no Live TV. No legitimate API gives a
third-party launcher your Netflix or Prime Video history, and this TV has no
tuner a launcher can reach. Inventing either would mean showing fake data as
though it were real.

What the hero says about an app is the one thing of this kind that is true: the
launcher recorded the time when it started that app, so it can say when you
last opened it.
