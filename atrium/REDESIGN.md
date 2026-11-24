# Atrium 2, the redesign

> **History.** This describes the previous design, the generated "room". It
> was replaced on 24 November 2026 by the content home screen in `DESIGN.md`.
> It is kept because what it found about this TV still holds.

The first version of Atrium worked and was too plain. This is what was wrong
with it, the three directions that were drawn to replace it, which one was
built, and what this TV allows.

## Phase 1. What was wrong

Looking at `../firetv/shots/atrium-03-home-youtube.png`, the old home screen:

- **Was empty, not spacious.** One row of icons, a clock, and four words. Most
  of the screen held nothing at all, and nothing composed the parts into a
  picture.
- **Read as a dock.** A line of equal icons along the bottom of a dark screen
  is what every Android device does.
- **Put the clock first.** The time was the largest thing on the screen, so the
  screen was about what time it is rather than about what you came to watch.
- **Had a background with no focal point.** It was atmospheric and it was the
  same everywhere, so the eye had nowhere to land.
- **Ended in a debug footer.** `NETWORK  SYSTEM  FIRE TV  ATRIUM` as four bare
  words looked like something left in by mistake.
- **Had no single idea.** It was a set of parts that each looked fine.

## Phase 2. Three directions

### A. Marquee
Full bleed artwork with an editorial column over it. The app name set as a
poster title, a filmstrip of wide cards along the bottom, no rail. Strongest
single image. No room for a growing app library, and nowhere to put the
utilities without the footer coming back.

### B. Chamber
The room itself becomes the artwork. Every app gets its own generated
composition, made from the colour inside its own icon: a deep field, an
aperture of light, shafts, a floor. The hero names what you point at, a rail
says where you are, and the rows sit on the dark part of the picture. Light is
the brand, not a photograph.

### C. Gallery
The streaming layout from the mockups, taken literally. Hero with key art, a
Quick Resume row of show cards, app row, Explore row.

### What was chosen, and why

**B, with the rail and the row rhythm of C.**

C cannot be built honestly on this TV, and that is not a style judgement:

- **There is no key art.** Fire OS exposes an app banner. On this device those
  banners are flat logo plates. YouTube's is 320x180 with a logo on white.
  Blown up to a hero it looks like exactly what it is.
- **There is no watch history.** No legitimate API gives a third-party launcher
  your Netflix or Prime Video progress. A Quick Resume row would be invented.
- **There is no Live TV or Inputs.** Both the inputs screen and the tune-to-
  input intent are held at protection level `signature|amazon`. Tested on the
  device, and ADB is refused too.

A has no answer for the app library or the utilities.

B turns the one asset this TV really has, the colour inside each app's own
icon, into the artwork. It is original rather than borrowed, it costs nothing
to ship, it works offline, and it is cheap enough for a Mali-T820.

## Phase 3. What it is now

```
 28   the clock, quiet, above the rail
116   the rail        Home  ·  Apps  ·  Settings
 92   the hero        THIS EVENING / YouTube / Opened 2 hours ago
214                   Open   Options
290   "your apps"     a row of app tiles
436   "tools"         Network · System · Fire TV · Atrium
```

- **The hero** describes what the remote is pointing at. Move along the row and
  the name changes, the line under it changes, and the whole screen changes
  colour. The line is real: it is when this launcher last started that app,
  recorded by this launcher when it did.
- **The artwork** is generated per app and crossfades over 420ms. Warm key
  light, cool shadow, a bloom where the light is strongest, grain over
  everything. The structure is seeded from the package name, so every app's
  room is its own and the same every time.
- **The rail** replaces the footer. Three destinations, all real.
- **The apps row** leaves the brands alone. Same tile, same corner, same size,
  one house focus treatment around them.
- **The tools row** is the old footer, rebuilt as panels with a mark, a name
  and a line saying what each one does.

## Phase 4. Performance decisions

The target is a Mali-T820 MP2, four cores, 1.58 GB of RAM, Android 7.1.

- A room is generated at **512x288** and stretched to 1920x1080. Everything in
  it is smooth, so the free bilinear filtering of the upscale is what makes the
  light soft. There is no blur pass anywhere.
- Generating happens on **one background thread**, and only the most recent
  request is honoured. Running fast along the row does not queue up rooms
  nobody will see.
- **Eight rooms are kept**, about 4.7 MB. The rest are let go.
- Rooms for every installed app are **warmed in the background** at the lowest
  priority after the apps load, so moving along the row is instant.
- Drawing the screen is **one stretched bitmap and one tiled rect**, or two
  bitmaps during a crossfade.
- App tiles are **composed once** at the exact size they are shown, rounded
  corners included. No clip path and no save layer per frame.
- Focus animates **view properties only**. The GPU already holds each tile's
  drawing, so focusing one costs a matrix change and no redraw.
- "Reduce motion" sets every duration to zero. Every state still ends correct.

## What is kept from the prototype

The logic was good. It was the surface that was not.

- `AppRepository`, which queries both launcher categories because Fire OS apps
  are inconsistent about which one they declare.
- `AccentExtractor`, which reads the colour out of an icon's pixels.
- `Handoff`, which decides what this TV will actually let a launcher open by
  reading the target's manifest rather than trusting `resolveActivity`.
- `HomeWatcher`, `WakeService` and `BootReceiver`.
- `Prefs`, the menus, hidden apps, rearranging, reduce motion, the 12 and 24
  hour clock, and the light presets.
- The type system: one family, Jost, in three weights, on a 960x540dp canvas.

## What was removed, and why

- **The mirrored reflections.** They tied the design to a shelf, and a mirror
  under every icon is a 2012 idea.
- **The masthead clock.** It is now a label, because it is not what the screen
  is about.
- **The word footer.** It is the tools row.
- **The single moving light.** The whole picture changes now, which is a
  stronger version of the same idea.

## What is not finished

- **A still picture cannot show the dissolve.** The screenshots in
  `../firetv/shots/` are of this build, and `TESTING.md` was re-measured on it,
  but the one thing that makes the screen feel alive, the room changing as you
  move, only exists in motion.
- **A large app library.** This TV has seven apps. The Apps grid wraps and
  scrolls, but it has not been seen with fifty.
- **TMDB artwork.** Deliberately left out. It needs an API key, and a hero
  advertising a film this TV cannot play is the thing to avoid. The hero is
  built so that a real bitmap source could be dropped in later.
- **Recently opened as an order.** The launcher records when each app was last
  opened and says so in the hero. It does not reorder the row by it, because a
  row that moves under you is worse than one that does not.
