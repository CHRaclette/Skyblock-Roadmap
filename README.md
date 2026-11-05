# SkyAnimations

_(formerly "Butcher Drops")_ — commands work as `/sa ...` or `/skyanimations ...`.

Rare-drop cutscenes for Hypixel SkyBlock (Fabric, Minecraft 26.2).

When you get a rare drop, the mob's head lands on a chopping board, a cleaver chops it in half, the drop's
effect bursts out (runes for books, golden threads for wool, a colour spiral for dyes, blood for slayers,
bones for dungeons, crops for the Garden...), then the item is revealed with light rays, its rarity title,
your Magic Find and its price.

Client-side and purely visual: it only reads chat and draws on your screen.

## Install
1. Fabric Loader for **26.2** and **Fabric API**.
2. Download `skyanimations-*.jar` from the [latest release](../../releases/tag/latest) and put it in `.minecraft/mods`.

## Garden extras
- **Crop Fever**: a "you caught it" intro with falling crops, a countdown bar while it lasts (with rolls and coins so far),
  and a summary card of everything it dropped when it's cured. Small rolls are pop-ups; CRAZY RARE and up get the full cutscene.
- **Pests**: a pop-up when pests spawn ("4 Pests spawned in Plot - 14"), a pop-up for each pest you vacuum with its reward,
  and rare pest drops get their own scene: the pest buzzes around and gets sucked into a vacuum that spits out the drop.

## Run scenes (CS2 case)
- **Crystal Nucleus:** the crystal hangs at the top, the loot bundle falls into a hotbar while a list says what you got.
  The rarest item comes last: a case pops out, opens like a CS2 case, the reel of every Nucleus RNG-Meter item
  (Pickonimbus 2000 … Divan's Alloy) spins and lands on your drop. `/sa nucleus` = only runs with a rare item.
- **Dungeon reward chests:** open a Wood…Bedrock chest (also via Croesus), click *Open Reward Chest*: the chest opens
  at the top, items drop into the bar, a rare item rolls in the case. Obsidian and Bedrock always roll. `/sa chests` = all / rare / off.
- **RNG Meter:** when Hypixel says *RNG METER! Reselected the …*, the meter fills to 100 %, the case rolls onto the item
  with a rainbow glow (replaces the normal cutscene for that drop).

## Special drops
- **Dyes:** a paint bomb in the exact colour of the dye, big name, paint drips.
- **Scatha / Crystal Hollows pets:** something burrows through a sand strip, the pet bursts out, slides across and snaps in with its rarity glow.

## More scenes
- **Inferno Demonlord drops:** the world goes dark, the blaze floats between four fire pillars that ignite one by one,
  hit markers flash, the blaze glows white and bursts into gold, and the drop (e.g. the High Class Archfiend Dice) appears
  with a gold fountain, typed-in name and a coin counter. `/sa blaze`, test: `/sa test hcblaze`.
- **Glacite Mineshaft corpses:** the corpse frozen in ice, every pickaxe hit spreads the cracks and knocks an item into
  the hotbar. Before a Shattered Locket / Frostbitten Dye (or anything big) the ice glows, then shatters; golden stars
  (paint for dyes) pull together and the item appears with "1 in N corpses", your count and the coins. `/sa corpse`,
  test: `/sa test corpse` / `/sa test corpse dye`.
- **Scatha:** a worm of Scatha segments gets sliced by a cleaver, the head flies to the middle: PET DROP!, rarity,
  coins and "Your Nth Scatha pet". Test: `/sa test scatha`.
- **Archfiend Dice:** slot machine; a 6 pays out with a coin counter, a 7 is the Archfiend Dye. `/sa test dice 6`.

## Ironman
On an Ironman profile (♲ in the tab list) every price is the item's NPC sell price. `/sa ironman` = auto / on / off.

## Sticker book
Press **J** (or `/sa book`). Every rare drop earns a sticker on its chapter page (Diana, Slayer, Dungeons, Garden,
Pests, Fishing, Other). The more often (and the rarer) you drop an item, the better its sticker:
Paper (1 pt) → Bronze (3) → Silver (10) → Gold (25) → Diamond (60) → Holo (150).
RARE = 1 pt, VERY RARE = 2, PET = 3, CRAZY RARE = 4, INSANE = 6, PRAY TO RNGESUS = 8.
New and upgraded stickers get slapped onto your screen after the cutscene. Known drops you don't have yet show as "???".

**Mastery:** every sticker shows 1–5 stars (Paper = 1★ … Diamond = 5★, Holo = 5★ + crown), the tooltip shows how far you are to the next star.
**Ledger Level:** all sticker points (plus pests and Crop Fevers) count as XP for one overall level.
**Badges:** Bronze / Silver / Gold for collecting and grinding each chapter, Mastery, Legend, Blessed (jackpot drops),
Exterminator (pests), Feverish (Crop Fevers) and Loaded (coins). Earning a tier slams the medal onto your screen.
Saved per profile in `config/skyanimations/stickers-<profile>.json`. `/sa test stickers` shows every level.

## What triggers it
- `RARE / VERY RARE / CRAZY RARE / INSANE / PRAY TO RNGESUS / PET DROP!` (slayers, Diana, dungeons, fishing, pests, everything)
- `RARE CROP!` / `VERY RARE CROP!` and Crop Fever (Garden)
- `RARE REWARD!` dungeon chest messages for your own name

## Commands
| Command | |
|---|---|
| `/sa test [chimera, wool, dye, judgement, overflux, ...]` | play a test cutscene |
| `/sa simulate <chat line>` | run any chat line through the detector |
| `/sa skip` | stop the current cutscene |
| `/sa toggle` / `sound` / `music` / `prices` | switch things on or off |
| `/sa speed <0.25-3>` / `volume <0-2>` | |
| `/sa mode [type] [full\|compact\|off]` | per drop type: full cutscene, reveal only, or nothing |
| `/sa everywhere` | also react outside Hypixel |
| `/sa test fever` / `test pests` / `test vacuum` | try the Garden features |
| `/sa test nucleus [common]` / `test chest` / `test rngmeter` | try the case scenes |
| `/sa test necrondye` / `test scatha` | try the dye and Scatha scenes |
| `/sa give all` / `give <paper…holo> [name]` / `give reset` | fill the sticker book with test stickers (reset removes only those) |
| `/sa nucleus` / `chests` / `ironman` | Nucleus only-rare, dungeon chest mode, Ironman prices |
| `/sa farming [setting]` | list / toggle: feverintro, fevertimer, feversummary, fevertoasts, pestspawn, pestkills, vacuum, fevercutscene |

Settings live in `config/skyanimations.json`.

## Building
GitHub Actions builds every push and publishes the jar as the `latest` release. Locally: JDK 25, then `./gradlew build`.

`tools/preview/Preview.java` renders the cutscene to PNG frames without Minecraft (handy for tweaking the animation).
