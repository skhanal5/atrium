# Design studies

These are **renders, not device screenshots**. They are drawn on a computer,
to settle the composition without needing the TV switched on.

They use the same numbers as the code: the same 960x540dp layout, the same
light maths, the same type sizes and the same Jost weights.

They also use the **real app icons**. `icons/` holds the icon of each app on
this TV, taken out of its own package file and named after the package. The
study reads the colour out of those icons exactly as `AccentExtractor.java`
does, so a room in a study is the room the TV will build. One app is not covered: AirPlay
Server draws its icon from a vector, which cannot be read here, so it shows as
a flat tile.

Real screenshots taken from the TV are in `../../firetv/shots/`.

`study.py` draws them. It mirrors `Field.java` and `HomeLayout.java`, so the
numbers in it have to be kept in step with the Java. Run it with the system
Python, which has Pillow and numpy:

```sh
python3 -I atrium/design/study.py out.png home
```

The second argument picks the state: `home`, `teal`, `plain`, `night` or
`apps`.

Current design, after the redesign in `../REDESIGN.md`:

| File | Shows |
|---|---|
| `study2-red.png` | YouTube focused. Its icon is red, so the room is red |
| `study2-teal.png` | Silk Browser focused. Its icon is teal, so the room is teal |
| `study2-plain.png` | Peacock focused. Its icon is black and white, so there is no colour to take and the room keeps the light of the hour |
| `study2-night.png` | YouTube again, under the dimmest preset, Night |
| `study2-apps.png` | The All apps section, with the room pushed back behind it |

The first version of the launcher, kept for comparison:

| File | Shows |
|---|---|
| `study-day.png` | Day light, a red app focused |
| `study-night.png` | Night light, a cyan app focused |
| `study-dusk.png` | Dusk light, an orange app focused |
