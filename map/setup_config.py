"""Passt die von BlueMap erzeugten Standard-Einstellungen an.

Aufruf: python setup_config.py <config-ordner> <welt-ordner>

- erlaubt den Download der Minecraft-Texturen
- speichert die Karte unkomprimiert (nötig für GitHub Pages)
- legt pro vorhandener Dimension eine Karte an, mit dem Spawn als Startpunkt
"""
import re
import sys
from pathlib import Path

import nbtlib

cfg = Path(sys.argv[1])
world = Path(sys.argv[2])


def set_key(path: Path, key: str, value: str) -> None:
    text = path.read_text(encoding="utf-8")
    # Listen ("key: [ ... ]") können über mehrere Zeilen gehen
    pattern = rf"(?m)^{re.escape(key)}:[ \t]*(\[[^\]]*\]|.*)$"
    new, n = re.subn(pattern, lambda _: f"{key}: {value}", text)
    if n == 0:
        new = text.rstrip() + f"\n{key}: {value}\n"
    path.write_text(new, encoding="utf-8")


set_key(cfg / "core.conf", "accept-download", "true")
set_key(cfg / "core.conf", "render-thread-count", "4")
set_key(cfg / "core.conf", "metrics", "false")
set_key(cfg / "storages" / "file.conf", "compression", "none")
set_key(cfg / "webapp.conf", "scripts", '[ "credit.js" ]')
set_key(cfg / "webapp.conf", "use-cookies", "false")

# Welt-Name und Spawn aus level.dat
data = nbtlib.load(world / "level.dat").get("Data", {})
name = str(data.get("LevelName", "Minecraft-Map"))
spawn_x, spawn_z = 0, 0
if "SpawnX" in data:
    spawn_x, spawn_z = int(data["SpawnX"]), int(data["SpawnZ"])
elif "spawn" in data and "pos" in data["spawn"]:  # neueres Format
    pos = data["spawn"]["pos"]
    spawn_x, spawn_z = int(pos[0]), int(pos[2])
print(f"Welt: {name}, Spawn: {spawn_x} / {spawn_z}")

maps_dir = cfg / "maps"
for old in maps_dir.glob("*.conf"):
    old.unlink()


def has_regions(region_dir: Path) -> bool:
    return region_dir.is_dir() and any(region_dir.glob("*.mca"))


dims = [
    ("overworld", "minecraft:overworld", name, world / "region", 0, "#7dabff", 1, 55),
    ("nether", "minecraft:the_nether", f"{name} (Nether)", world / "DIM-1" / "region", 1, "#290000", 0, -10000),
    ("end", "minecraft:the_end", f"{name} (End)", world / "DIM1" / "region", 2, "#080010", 0, -10000),
]

made = 0
for map_id, dim, title, region, sort, sky, sky_light, caves in dims:
    if not has_regions(region):
        continue
    start = f"{{ x: {spawn_x}, z: {spawn_z} }}" if map_id == "overworld" else "{ x: 0, z: 0 }"
    (maps_dir / f"{map_id}.conf").write_text(
        f'''world: "{world.as_posix()}"
dimension: "{dim}"
name: "{title}"
sorting: {sort}
start-pos: {start}
sky-color: "{sky}"
sky-light: {sky_light}
ambient-light: 0.1
remove-caves-below-y: {caves}
render-edges: true
enable-perspective-view: true
enable-flat-view: true
enable-free-flight-view: true
enable-hires: true
storage: "file"
''',
        encoding="utf-8",
    )
    made += 1
    print(f"Karte angelegt: {map_id}")

if made == 0:
    sys.exit("Keine Region-Dateien in der Welt gefunden")
