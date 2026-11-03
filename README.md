# Butcher Drops

Rare-drop cutscenes for Hypixel SkyBlock (Fabric, Minecraft 26.2).

When you get a rare drop, the mob's head lands on a chopping board, a cleaver chops it in half, the drop's
effect bursts out (runes for books, golden threads for wool, a colour spiral for dyes, blood for slayers,
bones for dungeons, crops for the Garden...), then the item is revealed with light rays, its rarity title,
your Magic Find and its price.

Client-side and purely visual: it only reads chat and draws on your screen.

## Install
1. Fabric Loader for **26.2** and **Fabric API**.
2. Download `butcher-drops-*.jar` from the [latest release](../../releases/tag/latest) and put it in `.minecraft/mods`.

## What triggers it
- `RARE / VERY RARE / CRAZY RARE / INSANE / PRAY TO RNGESUS / PET DROP!` (slayers, Diana, dungeons, fishing, pests, everything)
- `RARE CROP!` / `VERY RARE CROP!` and Crop Fever (Garden)
- `RARE REWARD!` dungeon chest messages for your own name

## Commands
| Command | |
|---|---|
| `/butcher test [chimera, wool, dye, judgement, overflux, ...]` | play a test cutscene |
| `/butcher simulate <chat line>` | run any chat line through the detector |
| `/butcher skip` | stop the current cutscene |
| `/butcher toggle` / `sound` / `music` / `prices` | switch things on or off |
| `/butcher speed <0.25-3>` / `volume <0-2>` | |
| `/butcher mode [type] [full\|compact\|off]` | per drop type: full cutscene, reveal only, or nothing |
| `/butcher everywhere` | also react outside Hypixel |

Settings live in `config/butcherdrops.json`.

## Building
GitHub Actions builds every push and publishes the jar as the `latest` release. Locally: JDK 25, then `./gradlew build`.

`tools/preview/Preview.java` renders the cutscene to PNG frames without Minecraft (handy for tweaking the animation).
