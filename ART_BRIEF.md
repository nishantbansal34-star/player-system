# Art brief — Shadow System

The app loads these files from `app/src/main/assets/art/` when they exist. Until then it shows built-in drawn fallbacks, so nothing breaks.
Format: WebP, quality ~80, under 250 KB each. Original artwork only: no characters, logos or screenshots from Solo Leveling or any other series.

| File | Size | Where it shows |
|---|---|---|
| `hero.webp` | 1080 × 1350 (4:5) | Behind the top of Home, faded at 55% and masked downward. Keep the top-left 40% calm and dark; the player card sits there. |
| `training.webp` | 1080 × 1920 (9:16) | Behind the training / deep-work timer at 35%, fading out by 75% of the height. Subject in the upper third; centre must stay dark for the timer ring. |
| `boss-<slug>.webp` | 1080 × 1080 (1:1) | Boss battle header (cropped to a wide band) and the 96 px boss thumbnail on Home. Monster centred, slightly low, readable as a silhouette. |

Boss slugs: `stone-golem`, `frost-wyrm`, `iron-ogre`, `alpha-shadow-wolf`, `venom-arachnid`, `flame-salamander`, `lich-acolyte`, `cave-troll`, `storm-harpy`, `bone-knight`.

## Prompts

Shared style line (append to every prompt):
"dark fantasy digital painting, cinematic lighting, deep black and midnight-indigo background, controlled electric violet (#8B5CFF) and royal blue (#4169FF) energy, high contrast, painterly detail, no text, no logo, no watermark, original design, not based on any existing anime character"

- **hero** — "A lone hooded shadow warrior seen from behind, standing on a ruined stone balcony at night, faint violet energy rising from the ground like smoke, distant gothic towers, a thin glowing runic circle at his feet, vast empty dark sky in the upper left."
- **training** — "Silhouette of an athlete mid-movement inside a dark stone training hall, violet-blue energy streaks tracing the motion, dust particles in a single cold light beam from above, the centre of the image dark and empty."
- **boss-flame-salamander** — "A colossal fire salamander crawling out of a volcanic gate, molten red-orange skin cracks, embers and heat haze, glowing ember eyes, black basalt ground; the violet palette only as faint rim light" (fire bosses override the palette with red-orange).
- **boss-stone-golem** — "An ancient stone golem with glowing blue rune seams rising in a collapsed cathedral."
- **boss-frost-wyrm** — "A serpentine frost wyrm coiled around a frozen pillar, pale blue breath, ice shards."
- **boss-iron-ogre** — "A hulking ogre in rusted iron plates dragging a chain in a torch-lit dungeon."
- **boss-alpha-shadow-wolf** — "A giant black wolf made of smoke and shadow, violet eyes, howling on a cliff under a cold moon."
- **boss-venom-arachnid** — "A giant spider with toxic green markings in a web-filled cavern, dripping venom."
- **boss-lich-acolyte** — "A skeletal robed sorcerer floating above a ritual circle, green-violet soul fire."
- **boss-cave-troll** — "A massive moss-covered cave troll with a stone club emerging from darkness."
- **boss-storm-harpy** — "A winged harpy queen in a lightning storm above jagged peaks, crackling blue lightning."
- **boss-bone-knight** — "An armoured skeleton knight with a cracked greatsword, blue spectral flames in the visor."

After generating, send the images to Claude in this project and it will convert, compress and add them.
