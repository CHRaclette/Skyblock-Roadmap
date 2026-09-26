// Handgeschriebene Guides für den HotM- und den Garden-Tab.
// Einmal ausführen: node scripts/guides-data.js
// Schreibt MINING_GUIDE und PEST_GUIDE in docs/roadmap-data.json (und die Bot-Kopie).
// Das tägliche Update überschreibt diese Schlüssel nicht.
// Fakten: Hypixel SkyBlock Wiki + Elite Farmers Wiki, Stand 26.09.2026.

const fs = require("fs");
const path = require("path");

const MINING_GUIDE = {
    stages: [
        {
            id: "powder",
            title: "Powder aufbauen",
            when: "Sobald Mining Fortune, Efficient Miner oder Professional unter ~30 stehen",
            where: "Crystal Hollows, Hard Stone",
            do: [
                "Hard Stone mit Pickobulus abbauen. Jeder Block hat 0,2 % Grundchance auf eine Treasure Chest, Great Explorer 20 erhöht das und öffnet die Truhe ohne Lockpicking.",
                "Die Truhen geben Gemstone und Mithril Powder. Das ist der schnellste Weg, beide Powder-Arten gleichzeitig aufzubauen.",
                "Nebenbei die ersten 4 Commissions pro Tag erledigen: Bonus-HotM-XP und Powder.",
                "2x-Powder-Events und Mining Fiesta abwarten, wenn sie bald kommen: Das Event verdoppelt jede Quelle."
            ],
            spend: "Mithril Powder: Efficient Miner, dann Mining Fortune. Gemstone Powder: Professional.",
            gear: "Pickaxe mit Pickobulus, Mole und Efficient Miner hoch, Scatha-Pet falls vorhanden (mehr Truhen), sonst Mole-Pet."
        },
        {
            id: "gem",
            title: "Gemstones starten",
            when: "Drill mit Breaking Power 8+ und Professional ab ~50",
            where: "Crystal Hollows: Jungle (Amethyst), Magma Fields (Topaz)",
            do: [
                "Mit Ruby (BP 6) und Amethyst (BP 7) anfangen, sobald der Drill das schafft.",
                "Topaz (BP 8) in den Magma Fields: Yog Armor schützt vor Lava, Bal-Pet ist dort das Mining-Pet. Beides habt ihr.",
                "Grobe Gemstones zu Flawed → Fine → Flawless craften (je 80 der Vorstufe). Pristine gibt direkt Flawed-Drops.",
                "Als Ironman ist das eure einzige Gemstone-Quelle: Slots in Yog und Divan, Drill-Upgrades, Gemstone Gauntlet."
            ],
            spend: "Professional zuerst, dann Speedy Mineman und Fortunate Mineman. Gem Lover für mehr Gemstone Fortune.",
            gear: "Gemstone Drill LT-522 (BP 8) oder Titanium Drill DR-X455 (BP 8), später Topaz Drill KGR-12 (BP 9)."
        },
        {
            id: "glacite",
            title: "Glacite & Mineshafts",
            when: "HotM 9+ und Drill mit Breaking Power 9",
            where: "Glacite Tunnels, Glacite Mineshafts",
            do: [
                "Tunnels-Commissions geben 750 HotM XP statt 400. Das ist der schnellste Weg zu HotM 10.",
                "Mineshafts: Jasper, Onyx, Aquamarine, Citrine, Peridot (alle BP 9) und Frozen Corpses für Glacite-Gear.",
                "Fossilien mit dem Excavator geben ebenfalls Glacite Powder.",
                "Warm Heart zuerst, sonst friert man in den Tunnels zu schnell ein."
            ],
            spend: "Glacite Powder: Warm Heart, Surveyor, Strong Arm, Metal Head, Rags to Riches.",
            gear: "Titanium Drill DR-X555 oder Topaz Drill KGR-12 (beide BP 9), Glacite Armor."
        },
        {
            id: "divan",
            title: "Divan's Drill & Armor",
            when: "HotM 7+, genug Gemstones und Divan-Teile",
            where: "Forge, Teile aus den Mines of Divan",
            do: [
                "Divan's Drill: BP 10, 1'800 Mining Speed, bester Drill im Spiel.",
                "Armor of Divan mit Gemstone-Slots, dafür braucht ihr die Flawless Gemstones aus Stufe 2.",
                "Mithril und Gemstone Powder weiter in die Tree-Perks stecken, bis Professional und Mining Fortune maxed sind."
            ],
            spend: "Rest-Powder in Powder Buff (falls nicht maxed), dann Core of the Mountain.",
            gear: "Divan's Drill + Armor of Divan."
        }
    ],
    powder: [
        ["Treasure Chests", "Gemstone + Mithril", "Hard Stone in den Crystal Hollows mit Pickobulus. Mole, Efficient Miner, Great Explorer, Powder Buff.", "Hauptquelle, sobald Great Explorer 20 steht"],
        ["Commissions", "Alle drei", "Dwarven Mines → Mithril, Crystal Hollows → Gemstone, Glacite Tunnels → Glacite. Die ersten 4 pro Tag geben Bonus.", "Täglich"],
        ["Gemstones minen", "Gemstone", "Direkt beim Abbauen, dazu Gemstones als Loot.", "Ab Stufe 2"],
        ["Glacite minen", "Glacite", "Glacite-Blöcke in den Tunnels, Frozen Corpses, Fossil Excavator.", "Ab Stufe 3"],
        ["Daily Powder / Daily Grind", "Alle drei", "Bonus fürs erste Erz bzw. die erste Commission des Tages, skaliert mit dem HotM-Tier.", "Wenn die Perks freigeschaltet sind"],
        ["2x Powder Event / Mining Fiesta", "Alle drei", "Verdoppelt alles bzw. gibt Extra-Powder. Für diese Zeit die grossen Grind-Sessions planen.", "Event-Kalender"]
    ],
    player: {
        relaxo: {
            stage: "powder",
            now: "HotM 8, aber Mining Fortune, Efficient Miner und Professional stehen auf 1. Powder Buff und Great Explorer sind schon maxed. Chest-Powder lohnt sich für dich also sofort.",
            next: [
                "Jetzt: Crystal Hollows, Hard Stone mit Pickobulus. Mole steht auf 144/200, das weiter hochziehen.",
                "Mithril Powder: Efficient Miner 1 → 50, dann Mining Fortune 1 → 50.",
                "Gemstone Powder: Professional 1 → ~50.",
                "Parallel Titanium Drill DR-X355 in der Forge, dann auf DR-X455 (BP 8) upgraden.",
                "Dann Gemstones starten: Amethyst im Jungle, danach Topaz in den Magma Fields mit Yog Armor und Bal (Leg 100)."
            ]
        },
        amin: {
            stage: "gem",
            now: "HotM 9, Professional 58 und Speedy Mineman 48: Du kannst sofort Gemstones minen. Mining Fortune 35 und Powder Buff 39 sind noch nicht maxed.",
            next: [
                "Jetzt: Topaz in den Magma Fields mit Yog Armor. Bal ist erst Epic 57, das Mole-Pet (Leg 75) ist die Alternative.",
                "Powder Buff 39 → 50 zuerst, das wirkt auf alles danach.",
                "Professional 58 → 100+, dann Fortunate Mineman freischalten.",
                "Tunnels-Commissions für HotM 10 (750 XP pro Commission). Warm Heart ist schon auf 20.",
                "Drill mit BP 9 für die Mineshafts, dann Jasper und Corpses."
            ]
        }
    }
};

const PEST_GUIDE = {
    facts: [
        ["Freischaltung", "Garden 5. Ihr seid Garden 14, damit sind alle Pest-Arten offen (die letzte braucht Garden 12)."],
        ["Spawn", "0,2 % Chance pro abgebautem Crop, nachdem der Cooldown (5 min) abgelaufen ist. Pests erscheinen in deinem Plot oder den 4 Nachbar-Plots."],
        ["Bonus Pest Chance", "Standard ist 1 Pest pro Spawn. Je 100 Bonus Pest Chance kommt eine Pest dazu, der Rest ist eine Chance auf eine weitere."],
        ["Fortune-Strafe", "Ab 4 Pests im Garden sinkt deine Farming Fortune: erst 5 %, dann in 15-%-Schritten bis −75 %. Also rechtzeitig töten."],
        ["Belohnung", "Jede Pest: Enchanted Crops (skaliert mit Fortune), 1'000 Coins, 1'500 Farming XP, Pest-Bestiary (bis +102 Farming Fortune) und 1 Pest als Währung."],
        ["Phillip-Buff", "40 Pests bei Pesthunter Phillip abgeben: +200 Farming Fortune für 30 Minuten (5 pro Pest)."],
        ["Offline", "1 Pest pro Stunde, wenn niemand im Garden ist. Pest Traps fangen etwa 1 Pest alle 15 Minuten."]
    ],
    shop: [
        ["Vacuum", "SkyMart", "Pflicht, nur damit kann man Pests töten", "jetzt"],
        ["Sprayonator", "25 Copper", "Plot 30 min sprühen: +25 Bonus Pest Chance, Pests 1,5× häufiger dort. Köder bestimmt die Pest-Art.", "jetzt"],
        ["Pesthunter Badge", "25 Pests", "+20 Bonus Pest Chance, Accessory (MP)", "jetzt"],
        ["Pesthunter's Necklace, Cloak, Belt, Gloves", "je 40 Pests", "je +5 Bonus Pest Chance und −10 % Cooldown", "bald"],
        ["Pesthunter Ring → Artifact → Relic", "50 · 100 · 200 Pests", "+40 · +60 · +80 Bonus Pest Chance, jeweils mit Vorstufe", "bald"],
        ["Turbo → Hyper → InfiniVacuum", "200 · 500 · 1'000 Copper", "150 · 200 · 300 Schaden, +10 · +15 · +20 Farming Fortune", "später"],
        ["Hedgehog-Pet", "500 Pests", "Legendary: +100 Farming Fortune und +35 Overbloom gegen Pests, doppelter Schaden an Pests", "später"],
        ["Pest Vest", "250 Pelts bei Talbot", "Cloak: +10 Bonus Pest Chance, −15 % Cooldown. Ersetzt den Pesthunter's Cloak.", "später"],
        ["InfiniVacuum Hooverius", "2'500 Copper + Chirping Stereo", "400 Schaden, +25 Farming Fortune, Vinyls für gezielte Pests", "Endziel"],
        ["Mosquito-Pet", "Spider Slayer 7", "+50 Bonus Pest Chance auf Level 100", "Endziel"]
    ],
    loop: [
        ["Plot sprühen", "Sprayonator mit dem Köder für die Pest, die du willst. Hält 30 Minuten."],
        ["Normal farmen", "Farming-Setup (Fermento/Squash, Elephant), bis der Pest-Cooldown fast abgelaufen ist."],
        ["Pest-Setup kurz vorher", "Pesthunter-Equipment (und später Pest Vest) anziehen. Das verkürzt den Cooldown und erhöht die Bonus Pest Chance."],
        ["Weiterfarmen bis Spawn", "Sobald Pests erscheinen: /setspawn, dann mit dem Vacuum (und später Hedgehog) alle Pests töten."],
        ["Zurück und wiederholen", "/warp garden, zurück ins Farming-Setup. Pests sammeln, bis 40 für den Phillip-Buff da sind."]
    ],
    baits: [
        ["Slug", "Mushroom", "Plant Matter", "Ironman: bester NPC-Wert laut Elite-Farmers-Guide, droppt Slug-Pet"],
        ["Beetle", "Nether Wart", "Dung", "Pesterminator-I-Bücher für die eigene Armor"],
        ["Moth", "Cocoa Beans", "Honey Jar", "Wriggling Larva (+2 Bonus Pest Chance, max 5)"],
        ["Cricket", "Carrot", "Honey Jar", "Chirping Stereo für den Hooverius"],
        ["Mite", "Cactus", "Tasty Cheese", "Atmospheric Filter"],
        ["Field Mouse", "zufällig", "–", "Squeaky Toy (Squeaky-Reforge: −2,5 % Cooldown pro Teil)"]
    ],
    bpc: {
        relaxo: [
            ["Fermento Armor", 70, true],
            ["Wriggling Larva 2/5", 4, true],
            ["Pesthunter Badge → Relic", 80, false],
            ["Pesthunter-Equipment 4×", 20, false],
            ["Sprayonator", 25, false],
            ["Larva 3 weitere", 6, false],
            ["Pest Vest statt Cloak", 5, false]
        ],
        amin: [
            ["Squash Armor", 60, true],
            ["Pesthunter Badge → Relic", 80, false],
            ["Pesthunter-Equipment 4×", 20, false],
            ["Sprayonator", 25, false],
            ["Fermento statt Squash (Farming 40)", 10, false],
            ["Wriggling Larva 5/5", 10, false],
            ["Pest Vest statt Cloak", 5, false]
        ]
    },
    player: {
        relaxo: {
            now: "Garden 14, Fermento Armor, Wriggling Larva 2/5. Pesthunter Badge noch nicht gekauft.",
            next: [
                "Vacuum und Sprayonator holen, dann einfach beim normalen Farmen alle Pests töten.",
                "Die ersten 25 Pests: Pesthunter Badge (gibt auch MP).",
                "Danach die vier Pesthunter-Teile (je 40) und parallel Ring → Artifact → Relic.",
                "Moths (Cocoa, Honey Jar) für die restlichen 3 Wriggling Larva.",
                "Dann Hedgehog (500 Pests) und der Phillip-Buff bei jeder längeren Farming-Session."
            ]
        },
        amin: {
            now: "Garden 14, Squash Armor, noch keine Wriggling Larva. Farming 36.",
            next: [
                "Vacuum und Sprayonator holen, Pests beim normalen Farmen mitnehmen. Jede Pest gibt 1'500 Farming XP, das hilft Richtung Farming 40.",
                "Pesthunter Badge für 25 Pests (MP).",
                "Bei Farming 40 Squash zu Fermento: +10 Bonus Pest Chance und mehr Fortune.",
                "Moths für Wriggling Larva, danach Pesthunter-Equipment."
            ]
        }
    }
};

for (const file of [
    path.join(__dirname, "..", "docs", "roadmap-data.json"),
    path.join(__dirname, "..", "discord-bot", "bot", "roadmap-data.json")
]) {
    const data = JSON.parse(fs.readFileSync(file, "utf8"));
    data.MINING_GUIDE = MINING_GUIDE;
    data.PEST_GUIDE = PEST_GUIDE;
    fs.writeFileSync(file, JSON.stringify(data, null, 1));
    console.log("geschrieben:", file);
}
