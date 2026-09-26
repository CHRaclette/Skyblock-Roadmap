require("dotenv").config();

const fs = require("fs");
const path = require("path");
const express = require("express");

const {
    Client,
    GatewayIntentBits,
    EmbedBuilder,
    ActionRowBuilder,
    ButtonBuilder,
    ButtonStyle,
    ChannelType,
    PermissionsBitField,
    SlashCommandBuilder
} = require("discord.js");

const roadmap = require("./roadmap");

// ============================================================
// CONFIG
// ============================================================

const PORT = Number(process.env.PORT || 3000);

const STATE_FILE = path.join(
    __dirname,
    "skyblock-bot-state.json"
);

const HYPIXEL_ITEMS_URL =
    "https://api.hypixel.net/v2/resources/skyblock/items";

const HYPIXEL_BAZAAR_URL =
    "https://api.hypixel.net/v2/skyblock/bazaar";

const API_REFRESH_MS =
    30 * 60 * 1000;

const SKYHELPER_API_URL = String(
    process.env.SKYHELPER_API_URL ||
    "https://api.altpapier.dev"
).replace(/\/$/, "");

const SKYHELPER_CACHE_MS =
    60 * 1000;

const BAZAAR_CACHE_MS =
    30 * 1000;

const FEATURE_REFRESH_MS =
    120 * 1000;

const client = new Client({
    intents: [
        GatewayIntentBits.Guilds,
        GatewayIntentBits.GuildMessages,
        GatewayIntentBits.MessageContent
    ]
});

// ============================================================
// STATE
// ============================================================

const DEFAULT_FLIP_SETTINGS = {
    minProfit: 1000,
    minMargin: 1,
    minVolume: 100,
    maxResults: 15
};

// Starter NPC prices.
// These are editable from the webboard.
// The dashboard can therefore be extended with more NPC prices.
const DEFAULT_NPC_BUY_PRICES = {
    SAND: {
        price: 4,
        npc: "Farm Merchant"
    },

    OAK_LOG: {
        price: 5,
        npc: "Lumber Merchant"
    },

    SLIME_BALL: {
        price: 14,
        npc: "Adventurer"
    },

    ROTTEN_FLESH: {
        price: 8,
        npc: "Adventurer"
    },

    BONE: {
        price: 8,
        npc: "Adventurer"
    },

    GUNPOWDER: {
        price: 10,
        npc: "Adventurer"
    },

    STRING: {
        price: 10,
        npc: "Adventurer"
    }
};

// Automatic functional Discord channels.
//
// These can still be renamed, moved and reordered using the webboard.
const FEATURE_CHANNEL_DEFINITIONS = [
    {
        key: "skyhelper",

        categoryName: "SkyHelper",

        channelName: "skyhelper-user-suche",

        topic:
            "SkyHelper: Minecraft-Spieler suchen • " +
            "/profile • /stats • /nw"
    },

    {
        key: "bazaar",

        categoryName: "Bazaar",

        channelName: "bazaar-flips",

        topic:
            "Live Bazaar Order-Flips • " +
            "beste Buy-Orders vs. Sell-Offers"
    },

    {
        key: "npc",

        categoryName: "NPC Flips",

        channelName: "npc-flips",

        topic:
            "NPC -> Bazaar und Bazaar -> NPC Flip-Möglichkeiten"
    },

    {
        key: "recipes",

        categoryName: "Rezepte",

        channelName: "rezepte",

        topic:
            "Hypixel API Rezepte und Crafting-Ziele"
    },

    {
        key: "targets",

        categoryName: "Crafting Ziele",

        channelName: "crafting-ziele",

        topic:
            "Aktive Crafting-Ziele und Abschlussmeldungen"
    }
];

const defaultState = {
    activeTargets: [],

    selectedDoneChannelByGuild: {},

    lastApiSync: null,

    featureChannelsByGuild: {},

    featureMessagesByGuild: {},

    flipSettingsByGuild: {},

    npcBuyPricesByGuild: {}
};

function loadState() {
    try {
        if (!fs.existsSync(STATE_FILE)) {
            return structuredClone(defaultState);
        }

        const loaded = JSON.parse(
            fs.readFileSync(
                STATE_FILE,
                "utf8"
            )
        );

        return {
            ...structuredClone(defaultState),

            ...loaded,

            featureChannelsByGuild:
                loaded.featureChannelsByGuild || {},

            featureMessagesByGuild:
                loaded.featureMessagesByGuild || {},

            flipSettingsByGuild:
                loaded.flipSettingsByGuild || {},

            npcBuyPricesByGuild:
                loaded.npcBuyPricesByGuild || {}
        };
    } catch (error) {
        console.error(
            "[STATE] Konnte State nicht laden:",
            error.message
        );

        return structuredClone(defaultState);
    }
}

const state = loadState();

function saveState() {
    try {
        fs.writeFileSync(
            STATE_FILE,
            JSON.stringify(
                state,
                null,
                2
            ),
            "utf8"
        );
    } catch (error) {
        console.error(
            "[STATE] Konnte State nicht speichern:",
            error.message
        );
    }
}

// ============================================================
// GLOBAL API DATA
// ============================================================

let itemCatalog = new Map();

let recipeCatalog = new Map();

let apiStatus = {
    success: false,

    count: 0,

    recipeCount: 0,

    syncedAt: null,

    error: null
};

let bazaarCache = {
    time: 0,

    data: null
};

const skyHelperCache = new Map();

// ============================================================
// HELPERS
// ============================================================

function titleCaseId(value) {
    return String(value || "")
        .toLowerCase()
        .replace(/_/g, " ")
        .replace(/\b\w/g, c => c.toUpperCase());
}

function formatNumber(value) {
    if (
        typeof value !== "number" ||
        !Number.isFinite(value)
    ) {
        return "—";
    }

    return new Intl.NumberFormat(
        "de-CH"
    ).format(value);
}

function formatCoins(value) {
    const number = Number(value);

    if (!Number.isFinite(number)) {
        return "—";
    }

    return `${formatNumber(number)} coins`;
}

function normalizeIngredientKey(key) {
    return String(key || "")
        .replace(/^minecraft:/i, "")
        .replace(/^SKYBLOCK:/i, "")
        .trim();
}

// ============================================================
// GENERIC FETCH
// ============================================================

async function fetchJson(
    url,
    options = {}
) {
    const controller =
        new AbortController();

    const timeout =
        setTimeout(
            () => controller.abort(),
            15_000
        );

    try {
        const headers = {
            Accept: "application/json",

            "User-Agent":
                "SkyblockFutureDashboard/3.0",

            ...(options.headers || {})
        };

        if (
            process.env.HYPIXEL_API_KEY
        ) {
            headers["API-Key"] =
                process.env.HYPIXEL_API_KEY;
        }

        const response =
            await fetch(
                url,
                {
                    ...options,

                    headers,

                    signal:
                        controller.signal
                }
            );

        const text =
            await response.text();

        let data;

        try {
            data = JSON.parse(text);
        } catch {
            throw new Error(
                `Ungültige JSON-Antwort (${response.status})`
            );
        }

        if (!response.ok) {
            throw new Error(
                `HTTP ${response.status}: ` +
                `${data?.cause || data?.error || "Unbekannter Fehler"}`
            );
        }

        return data;
    } finally {
        clearTimeout(timeout);
    }
}

// ============================================================
// RECIPE PARSER
// ============================================================

function parseRecipe(
    recipe,
    itemsById
) {
    if (!recipe) {
        return [];
    }

    const result = [];

    const pushIngredient = (
        rawId,
        rawCount
    ) => {
        const id =
            normalizeIngredientKey(
                rawId
            );

        const count =
            Number(rawCount);

        if (
            !id ||
            id === "ANVIL" ||
            !Number.isFinite(count) ||
            count <= 0
        ) {
            return;
        }

        const ingredient =
            itemsById.get(id);

        result.push({
            id,

            name:
                ingredient?.name ||
                titleCaseId(id),

            count
        });
    };

    const walk = (
        node
    ) => {
        if (!node) {
            return;
        }

        if (Array.isArray(node)) {
            for (
                const entry of node
            ) {
                if (
                    entry &&
                    typeof entry === "object" &&
                    !Array.isArray(entry)
                ) {
                    const id =
                        entry.id ??
                        entry.item ??
                        entry.item_id ??
                        entry.material;

                    const count =
                        entry.count ??
                        entry.amount ??
                        entry.quantity ??
                        entry.qty;

                    if (
                        id != null &&
                        count != null
                    ) {
                        pushIngredient(
                            id,
                            count
                        );

                        continue;
                    }
                }

                walk(entry);
            }

            return;
        }

        if (
            typeof node ===
            "object"
        ) {
            const id =
                node.id ??
                node.item ??
                node.item_id ??
                node.material;

            const count =
                node.count ??
                node.amount ??
                node.quantity ??
                node.qty;

            if (
                id != null &&
                count != null
            ) {
                pushIngredient(
                    id,
                    count
                );

                return;
            }

            for (
                const [
                    key,
                    value
                ] of Object.entries(node)
            ) {
                if (
                    typeof value ===
                        "number" ||
                    typeof value ===
                        "string"
                ) {
                    pushIngredient(
                        key,
                        value
                    );
                } else {
                    walk(value);
                }
            }
        }
    };

    walk(recipe);

    const merged =
        new Map();

    for (
        const ingredient of result
    ) {
        const old =
            merged.get(
                ingredient.id
            );

        if (old) {
            old.count +=
                ingredient.count;
        } else {
            merged.set(
                ingredient.id,
                {
                    ...ingredient
                }
            );
        }
    }

    return [
        ...merged.values()
    ];
}

// ============================================================
// HYPIXEL ITEM SYNC
// ============================================================

async function syncSkyblockItems() {
    console.log(
        "[API] Synchronisiere SkyBlock Items …"
    );

    try {
        const data =
            await fetchJson(
                HYPIXEL_ITEMS_URL
            );

        if (
            !data?.success ||
            !Array.isArray(
                data.items
            )
        ) {
            throw new Error(
                data?.cause ||
                "Hypixel API lieferte keine gültige Item-Liste."
            );
        }

        const nextItems =
            new Map();

        for (
            const item of data.items
        ) {
            if (
                !item?.id ||
                !item?.name
            ) {
                continue;
            }

            nextItems.set(
                String(item.id),
                {
                    ...item,

                    id:
                        String(item.id)
                }
            );
        }

        const nextRecipes =
            new Map();

        for (
            const item of
                nextItems.values()
        ) {
            if (!item.recipe) {
                continue;
            }

            const ingredients =
                parseRecipe(
                    item.recipe,
                    nextItems
                );

            if (
                !ingredients.length
            ) {
                continue;
            }

            nextRecipes.set(
                item.id,
                {
                    itemId:
                        item.id,

                    name:
                        item.name,

                    outputCount:
                        Number(
                            item.recipe?.output ??
                            item.recipe?.output_count ??
                            1
                        ) || 1,

                    ingredients,

                    rawRecipe:
                        item.recipe
                }
            );
        }

        itemCatalog =
            nextItems;

        recipeCatalog =
            nextRecipes;

        apiStatus = {
            success: true,

            count:
                itemCatalog.size,

            recipeCount:
                recipeCatalog.size,

            syncedAt:
                new Date().toISOString(),

            error: null
        };

        state.lastApiSync =
            apiStatus.syncedAt;

        saveState();

        console.log(
            `[API] ${itemCatalog.size} Items geladen, ` +
            `${recipeCatalog.size} Rezepte erkannt.`
        );
    } catch (error) {
        apiStatus = {
            ...apiStatus,

            success: false,

            error:
                error.message
        };

        console.error(
            "[API] Fehler:",
            error.message
        );
    }
}

setInterval(
    syncSkyblockItems,
    API_REFRESH_MS
).unref();

// ============================================================
// SKYHELPER API
// ============================================================

async function skyHelperRequest(
    endpoint,
    options = {}
) {
    const cacheKey =
        `${options.method || "GET"}:` +
        `${endpoint}:` +
        `${options.body || ""}`;

    const cached =
        skyHelperCache.get(
            cacheKey
        );

    if (
        cached &&
        Date.now() -
            cached.time <
            SKYHELPER_CACHE_MS
    ) {
        return cached.data;
    }

    const data =
        await fetchJson(
            `${SKYHELPER_API_URL}${endpoint}`,
            options
        );

    skyHelperCache.set(
        cacheKey,
        {
            time: Date.now(),

            data
        }
    );

    return data;
}

async function getSkyHelperProfiles(
    user
) {
    return skyHelperRequest(
        `/v2/profiles/${encodeURIComponent(user)}`
    );
}

async function getSkyHelperProfile(
    user,
    profile
) {
    return skyHelperRequest(
        `/v2/profile/` +
        `${encodeURIComponent(user)}/` +
        `${encodeURIComponent(profile)}`
    );
}

async function getSkyHelperNetworth(
    profileData,
    bankBalance,
    onlyNetworth = false
) {
    return skyHelperRequest(
        "/v2/networth",
        {
            method: "POST",

            headers: {
                "Content-Type":
                    "application/json"
            },

            body:
                JSON.stringify({
                    profileData,

                    bankBalance:
                        Number(
                            bankBalance || 0
                        ),

                    onlyNetworth
                })
        }
    );
}

function pickProfileMember(
    profile
) {
    if (
        !profile ||
        typeof profile !==
            "object"
    ) {
        return null;
    }

    if (profile.member) {
        return profile.member;
    }

    if (
        profile.profile?.member
    ) {
        return profile
            .profile.member;
    }

    const members =
        profile.members ||
        profile.profile?.members;

    if (
        members &&
        typeof members ===
            "object"
    ) {
        const first =
            Object.values(
                members
            )[0];

        if (first) {
            return first;
        }
    }

    return profile;
}

function flattenSkyblockStats(
    member
) {
    if (
        !member ||
        typeof member !==
            "object"
    ) {
        return {};
    }

    const skills =
        member
            .player_data
            ?.experience ||
        member
            .experience_skill ||
        {};

    const dungeons =
        member
            .dungeons
            ?.dungeon_types ||
        member.dungeons ||
        {};

    const slayer =
        member
            .slayer
            ?.slayer_bosses ||
        member
            .slayer_bosses ||
        {};

    return {
        skills,

        dungeons,

        slayer
    };
}

async function skyHelperPlayerSummary(
    user,
    profileName = null
) {
    const profilesResponse =
        profileName
            ? await getSkyHelperProfile(
                user,
                profileName
            )
            : await getSkyHelperProfiles(
                user
            );

    const profiles =
        profilesResponse?.profiles ||
        profilesResponse?.data ||
        profilesResponse;

    let profile =
        profiles;

    if (
        Array.isArray(
            profiles
        )
    ) {
        profile =
            profiles[0];
    } else if (
        profiles &&
        typeof profiles ===
            "object" &&
        profileName
    ) {
        profile =
            profiles;
    } else if (
        profiles &&
        typeof profiles ===
            "object"
    ) {
        const entries =
            Object.values(
                profiles
            );

        profile =
            entries[0] ||
            profiles;
    }

    const member =
        pickProfileMember(
            profile
        );

    const bank =
        profile?.banking
            ?.balance ??
        profile?.profile
            ?.banking
            ?.balance ??
        0;

    let networth =
        null;

    try {
        if (member) {
            networth =
                await getSkyHelperNetworth(
                    member,
                    bank,
                    false
                );
        }
    } catch (error) {
        console.warn(
            "[SKYHELPER] Networth konnte nicht berechnet werden:",
            error.message
        );
    }

    return {
        user,

        profile,

        member,

        bankBalance:
            bank,

        networth,

        stats:
            flattenSkyblockStats(
                member
            ),

        raw:
            profilesResponse
    };
}

// ============================================================
// BAZAAR
// ============================================================

async function getBazaar() {
    if (
        bazaarCache.data &&
        Date.now() -
            bazaarCache.time <
            BAZAAR_CACHE_MS
    ) {
        return bazaarCache.data;
    }

    const data =
        await fetchJson(
            HYPIXEL_BAZAAR_URL
        );

    if (
        !data?.success
    ) {
        throw new Error(
            data?.cause ||
            "Bazaar API Fehler."
        );
    }

    bazaarCache = {
        time: Date.now(),

        data
    };

    return data;
}

function getBestBuyOrder(
    product
) {
    const orders =
        Array.isArray(
            product?.buy_summary
        )
            ? product.buy_summary
            : [];

    return (
        orders
            .map(order => ({
                price:
                    Number(
                        order.price
                    ),

                amount:
                    Number(
                        order.amount
                    )
            }))
            .filter(
                order =>
                    Number.isFinite(
                        order.price
                    ) &&
                    Number.isFinite(
                        order.amount
                    ) &&
                    order.amount >
                        0
            )
            .sort(
                (
                    a,
                    b
                ) =>
                    b.price -
                    a.price
            )[0] ||
        null
    );
}

function getBestSellOffer(
    product
) {
    const offers =
        Array.isArray(
            product?.sell_summary
        )
            ? product.sell_summary
            : [];

    return (
        offers
            .map(offer => ({
                price:
                    Number(
                        offer.price
                    ),

                amount:
                    Number(
                        offer.amount
                    )
            }))
            .filter(
                offer =>
                    Number.isFinite(
                        offer.price
                    ) &&
                    Number.isFinite(
                        offer.amount
                    ) &&
                    offer.amount >
                        0
            )
            .sort(
                (
                    a,
                    b
                ) =>
                    a.price -
                    b.price
            )[0] ||
        null
    );
}

async function findBazaarProduct(
    query
) {
    const data =
        await getBazaar();

    const products =
        Object.values(
            data.products || {}
        );

    const q =
        String(query || "")
            .toLowerCase()
            .trim();

    return products
        .filter(product => {
            const item =
                itemCatalog.get(
                    product.product_id
                );

            return (
                String(
                    product.product_id ||
                    ""
                )
                    .toLowerCase()
                    .includes(q) ||

                String(
                    item?.name ||
                    ""
                )
                    .toLowerCase()
                    .includes(q)
            );
        })
        .sort(
            (a, b) =>
                String(
                    a.product_id
                ).localeCompare(
                    String(
                        b.product_id
                    )
                )
        )
        .slice(0, 10)
        .map(product => {
            const bestBuy =
                getBestBuyOrder(
                    product
                );

            const bestSell =
                getBestSellOffer(
                    product
                );

            return {
                id:
                    product.product_id,

                name:
                    itemCatalog.get(
                        product.product_id
                    )?.name ||
                    titleCaseId(
                        product.product_id
                    ),

                buyPrice:
                    product.quick_status
                        ?.buyPrice ??
                    null,

                sellPrice:
                    product.quick_status
                        ?.sellPrice ??
                    null,

                bestBuyOrder:
                    bestBuy?.price ??
                    null,

                bestSellOffer:
                    bestSell?.price ??
                    null,

                buyVolume:
                    product.quick_status
                        ?.buyVolume ??
                    null,

                sellVolume:
                    product.quick_status
                        ?.sellVolume ??
                    null
            };
        });
}

// ============================================================
// FLIP SETTINGS
// ============================================================

function getGuildFlipSettings(
    guildId
) {
    return {
        ...DEFAULT_FLIP_SETTINGS,

        ...(state.flipSettingsByGuild[
            guildId
        ] || {})
    };
}

function getGuildNpcPrices(
    guildId
) {
    const custom =
        state.npcBuyPricesByGuild[
            guildId
        ] || {};

    return {
        ...DEFAULT_NPC_BUY_PRICES,

        ...custom
    };
}

function getFlipVolume(
    product
) {
    const quickStatus =
        product?.quick_status ||
        {};

    const movingWeek =
        Number(
            quickStatus.movingWeek ||
            0
        );

    if (
        movingWeek >
        0
    ) {
        return (
            movingWeek / 7
        );
    }

    return Math.max(
        Number(
            quickStatus.buyVolume ||
            0
        ),

        Number(
            quickStatus.sellVolume ||
            0
        )
    );
}

// ============================================================
// BAZAAR ORDER FLIPS
// ============================================================

function buildBazaarOrderFlips(
    guildId = null
) {
    const settings =
        guildId
            ? getGuildFlipSettings(
                guildId
            )
            : DEFAULT_FLIP_SETTINGS;

    const flips = [];

    for (
        const product of Object.values(
            bazaarCache.data
                ?.products || {}
        )
    ) {
        const bestBuy =
            getBestBuyOrder(
                product
            );

        const bestSell =
            getBestSellOffer(
                product
            );

        if (
            !bestBuy ||
            !bestSell ||
            bestBuy.price <= 0
        ) {
            continue;
        }

        // Buy order at highest buy order
        // and sell offer at lowest sell offer.
        const spread =
            bestSell.price -
            bestBuy.price;

        const margin =
            (
                spread /
                bestBuy.price
            ) * 100;

        const volume =
            getFlipVolume(
                product
            );

        if (
            spread <
                settings.minProfit ||
            margin <
                settings.minMargin ||
            volume <
                settings.minVolume
        ) {
            continue;
        }

        const item =
            itemCatalog.get(
                product.product_id
            );

        flips.push({
            type: "order",

            id:
                product.product_id,

            name:
                item?.name ||
                titleCaseId(
                    product.product_id
                ),

            buyOrder:
                bestBuy.price,

            sellOffer:
                bestSell.price,

            profit:
                spread,

            margin,

            volume,

            buyAmount:
                bestBuy.amount,

            sellAmount:
                bestSell.amount
        });
    }

    return flips
        .sort(
            (a, b) =>
                b.profit -
                a.profit ||
                b.margin -
                a.margin
        )
        .slice(
            0,
            settings.maxResults
        );
}

// ============================================================
// NPC FLIPS
// ============================================================

function buildNpcFlips(
    guildId = null
) {
    const settings =
        guildId
            ? getGuildFlipSettings(
                guildId
            )
            : DEFAULT_FLIP_SETTINGS;

    const npcPrices =
        guildId
            ? getGuildNpcPrices(
                guildId
            )
            : DEFAULT_NPC_BUY_PRICES;

    const npcToBazaar = [];

    const bazaarToNpc = [];

    for (
        const product of Object.values(
            bazaarCache.data
                ?.products || {}
        )
    ) {
        const item =
            itemCatalog.get(
                product.product_id
            );

        if (!item) {
            continue;
        }

        const bestBuy =
            getBestBuyOrder(
                product
            );

        const bestSell =
            getBestSellOffer(
                product
            );

        const volume =
            getFlipVolume(
                product
            );

        if (
            !bestBuy ||
            !bestSell ||
            volume <
                settings.minVolume
        ) {
            continue;
        }

        // ----------------------------------------------------
        // NPC -> BAZAAR
        // ----------------------------------------------------

        const configured =
            npcPrices[
                product.product_id
            ];

        if (
            configured?.price >
            0
        ) {
            const profit =
                bestSell.price -
                configured.price;

            const margin =
                (
                    profit /
                    configured.price
                ) * 100;

            if (
                profit >=
                    settings.minProfit &&
                margin >=
                    settings.minMargin
            ) {
                npcToBazaar.push({
                    type:
                        "npc_to_bazaar",

                    id:
                        product.product_id,

                    name:
                        item.name,

                    npc:
                        configured.npc ||
                        "NPC",

                    npcBuy:
                        configured.price,

                    sellOffer:
                        bestSell.price,

                    profit,

                    margin,

                    volume
                });
            }
        }

        // ----------------------------------------------------
        // BAZAAR -> NPC
        // ----------------------------------------------------

        const npcSell =
            Number(
                item.npc_sell_price
            );

        if (
            Number.isFinite(
                npcSell
            ) &&
            npcSell > 0
        ) {
            const profit =
                npcSell -
                bestSell.price;

            const margin =
                (
                    profit /
                    bestSell.price
                ) * 100;

            if (
                profit >=
                    settings.minProfit &&
                margin >=
                    settings.minMargin
            ) {
                bazaarToNpc.push({
                    type:
                        "bazaar_to_npc",

                    id:
                        product.product_id,

                    name:
                        item.name,

                    npc:
                        "NPC Sell",

                    bazaarBuy:
                        bestSell.price,

                    npcSell,

                    profit,

                    margin,

                    volume
                });
            }
        }
    }

    npcToBazaar.sort(
        (a, b) =>
            b.profit -
            a.profit ||
            b.margin -
            a.margin
    );

    bazaarToNpc.sort(
        (a, b) =>
            b.profit -
            a.profit ||
            b.margin -
            a.margin
    );

    return {
        npcToBazaar:
            npcToBazaar.slice(
                0,
                settings.maxResults
            ),

        bazaarToNpc:
            bazaarToNpc.slice(
                0,
                settings.maxResults
            )
    };
}

// ============================================================
// FLIP FORMATTERS
// ============================================================

function flipLine(
    flip
) {
    if (
        flip.type ===
        "order"
    ) {
        return (
            `**${flip.name}** — ` +
            `+${formatNumber(
                flip.profit
            )} coins / item ` +
            `(${flip.margin.toFixed(
                2
            )}%)\n` +

            `Order: ` +
            `${formatNumber(
                flip.buyOrder
            )} → ` +

            `Offer: ` +
            `${formatNumber(
                flip.sellOffer
            )} • ` +

            `Vol. ~` +
            `${formatNumber(
                Math.round(
                    flip.volume
                )
            )}/Tag`
        );
    }

    if (
        flip.type ===
        "npc_to_bazaar"
    ) {
        return (
            `**${flip.name}** — ` +
            `+${formatNumber(
                flip.profit
            )} coins / item ` +
            `(${flip.margin.toFixed(
                2
            )}%)\n` +

            `${flip.npc}: ` +
            `${formatNumber(
                flip.npcBuy
            )} → ` +

            `Bazaar: ` +
            `${formatNumber(
                flip.sellOffer
            )} • ` +

            `Vol. ~` +
            `${formatNumber(
                Math.round(
                    flip.volume
                )
            )}/Tag`
        );
    }

    return (
        `**${flip.name}** — ` +
        `+${formatNumber(
            flip.profit
        )} coins / item ` +
        `(${flip.margin.toFixed(
            2
        )}%)\n` +

        `Bazaar: ` +
        `${formatNumber(
            flip.bazaarBuy
        )} → ` +

        `NPC: ` +
        `${formatNumber(
            flip.npcSell
        )} • ` +

        `Vol. ~` +
        `${formatNumber(
            Math.round(
                flip.volume
            )
        )}/Tag`
    );
}

function buildFlipEmbed(
    title,
    lines,
    color,
    footer
) {
    const embed =
        new EmbedBuilder()
            .setTitle(
                title
            )
            .setColor(
                color
            )
            .setDescription(
                lines.length
                    ? lines.join(
                        "\n\n"
                    )
                    : "Keine Flips mit den aktuellen Filtern gefunden."
            )
            .setTimestamp();

    if (footer) {
        embed.setFooter({
            text: footer
        });
    }

    return embed;
}

// ============================================================
// SLASH COMMANDS ( / )
// ============================================================
//
// Every command below is registered per Discord server (guild)
// as soon as the bot logs in, and again whenever it joins a new
// server. Discord shows the "description" text automatically in
// the "/" command picker, so that IS the built-in command list —
// no separate help file to maintain.

const SLASH_COMMANDS = [
    new SlashCommandBuilder()
        .setName("profile")
        .setDescription(
            "SkyHelper Profil eines Spielers anzeigen (Networth, Bank, Profilname)."
        )
        .addStringOption(option =>
            option
                .setName("spieler")
                .setDescription("Minecraft-Name oder UUID")
                .setRequired(true)
        )
        .addStringOption(option =>
            option
                .setName("profil")
                .setDescription("SkyBlock-Profilname, z. B. Banana (optional)")
                .setRequired(false)
        ),

    new SlashCommandBuilder()
        .setName("stats")
        .setDescription(
            "Spieler-Stats über SkyHelper anzeigen (identisch zu /profile)."
        )
        .addStringOption(option =>
            option
                .setName("spieler")
                .setDescription("Minecraft-Name oder UUID")
                .setRequired(true)
        )
        .addStringOption(option =>
            option
                .setName("profil")
                .setDescription("SkyBlock-Profilname, z. B. Banana (optional)")
                .setRequired(false)
        ),

    new SlashCommandBuilder()
        .setName("nw")
        .setDescription(
            "Nur den Networth eines Spielers anzeigen."
        )
        .addStringOption(option =>
            option
                .setName("spieler")
                .setDescription("Minecraft-Name oder UUID")
                .setRequired(true)
        )
        .addStringOption(option =>
            option
                .setName("profil")
                .setDescription("SkyBlock-Profilname (optional)")
                .setRequired(false)
        ),

    new SlashCommandBuilder()
        .setName("bz")
        .setDescription(
            "Ein Item im Hypixel Bazaar suchen (Buy-/Sell-Preise)."
        )
        .addStringOption(option =>
            option
                .setName("item")
                .setDescription("Item-Name, z. B. Enchanted Diamond")
                .setRequired(true)
        ),

    new SlashCommandBuilder()
        .setName("flips")
        .setDescription(
            "Die aktuell besten Bazaar Order-Flips anzeigen."
        ),

    new SlashCommandBuilder()
        .setName("npcflip")
        .setDescription(
            "NPC→Bazaar- und Bazaar→NPC-Flip-Möglichkeiten anzeigen."
        ),

    new SlashCommandBuilder()
        .setName("ziel")
        .setDescription(
            "Alle aktiven Crafting-Ziele mit Fortschritt anzeigen."
        ),

    new SlashCommandBuilder()
        .setName("help")
        .setDescription(
            "Übersicht aller verfügbaren Befehle dieses Bots."
        )
].map(command => command.toJSON()).concat(roadmap.ROADMAP_COMMANDS);

async function registerGuildSlashCommands(
    guild
) {
    try {
        await guild.commands.set(
            SLASH_COMMANDS
        );

        console.log(
            `[SLASH] Befehle für ${guild.name} registriert.`
        );
    } catch (error) {
        console.error(
            `[SLASH] Konnte Befehle für ${guild.name} nicht registrieren:`,
            error.message
        );
    }
}

// ---- Shared builders used by BOTH the "/" commands and the
// ---- legacy "!" text commands, so the logic only lives once.

async function buildProfileEmbed(
    user,
    profile
) {
    const data =
        await skyHelperPlayerSummary(
            user,
            profile
        );

    const networth =
        data.networth?.networth ??
        data.networth?.totalNetworth ??
        data.networth?.value;

    return new EmbedBuilder()
        .setTitle(
            `🛰️ SkyHelper Profile • ${user}`
        )
        .setColor(
            0x7c5cff
        )
        .addFields(
            {
                name: "Networth",

                value:
                    networth == null
                        ? "—"
                        : `${formatNumber(Number(networth))} coins`,

                inline: true
            },

            {
                name: "Bank",

                value:
                    `${formatNumber(Number(data.bankBalance || 0))} coins`,

                inline: true
            },

            {
                name: "Profile",

                value:
                    profile || "automatisch",

                inline: true
            }
        )
        .setFooter({
            text:
                "SkyHelperAPI • Hypixel SkyBlock"
        });
}

async function buildNetworthLine(
    user,
    profile
) {
    const data =
        await skyHelperPlayerSummary(
            user,
            profile
        );

    const networth =
        data.networth?.networth ??
        data.networth?.totalNetworth ??
        data.networth?.value;

    return (
        `💰 **${user}** — Networth: **${
            networth == null
                ? "nicht verfügbar"
                : formatNumber(Number(networth))
        } coins**`
    );
}

async function buildBazaarSearchEmbed(
    query
) {
    const products =
        await findBazaarProduct(
            query
        );

    if (!products.length) {
        return null;
    }

    return new EmbedBuilder()
        .setTitle(
            `📈 Bazaar • ${query}`
        )
        .setColor(
            0x42e8ff
        )
        .setDescription(
            products
                .slice(0, 5)
                .map(
                    product =>
                        `**${product.name}**\n` +
                        `Buy: \`${
                            product.buyPrice == null
                                ? "—"
                                : formatNumber(product.buyPrice)
                        }\` • ` +
                        `Sell: \`${
                            product.sellPrice == null
                                ? "—"
                                : formatNumber(product.sellPrice)
                        }\``
                )
                .join("\n\n")
        );
}

async function buildOrderFlipsEmbed(
    guildId
) {
    if (!bazaarCache.data) {
        await getBazaar();
    }

    const flips =
        buildBazaarOrderFlips(
            guildId
        );

    return buildFlipEmbed(
        "📈 Beste Bazaar Order-Flips",

        flips.slice(0, 10).map(flipLine),

        0x42e8ff,

        "Buy Order → Sell Offer"
    );
}

async function buildNpcFlipsEmbed(
    guildId
) {
    if (!bazaarCache.data) {
        await getBazaar();
    }

    const flips =
        buildNpcFlips(
            guildId
        );

    const lines = [
        ...flips.npcToBazaar
            .slice(0, 5)
            .map(flip => `🛒 ${flipLine(flip)}`),

        ...flips.bazaarToNpc
            .slice(0, 5)
            .map(flip => `🏪 ${flipLine(flip)}`)
    ];

    return buildFlipEmbed(
        "🏪 NPC Flip Scanner",

        lines,

        0x45f0a3,

        "NPC→Bazaar + Bazaar→NPC"
    );
}

function buildHelpEmbed() {
    return new EmbedBuilder()
        .setTitle(
            "🧭 SkyBlock Dashboard Befehle"
        )
        .setColor(
            0x7c5cff
        )
        .setDescription(
            "`/profile <Spieler> [Profil]` — SkyHelper Profil\n" +
            "`/stats <Spieler> [Profil]` — Stats\n" +
            "`/nw <Spieler> [Profil]` — Networth\n" +
            "`/bz <Item>` — Bazaar Preis\n" +
            "`/flips` — Bazaar Order-Flips\n" +
            "`/npcflip` — NPC Flip-Scanner\n" +
            "`/ziel` — aktive Crafting-Ziele\n" +
            "`/help` — diese Übersicht\n" +
            roadmap.HELP_LINES + "\n" +
            "Die alten `!`-Befehle (z. B. `!flips`) funktionieren weiterhin."
        );
}

// ============================================================
// FEATURE CHANNEL MANAGEMENT
// ============================================================

async function ensureFeatureChannels(
    guild
) {
    if (!guild) {
        return {};
    }

    const mapping =
        state.featureChannelsByGuild[
            guild.id
        ] || {};

    for (
        const feature of
            FEATURE_CHANNEL_DEFINITIONS
    ) {
        let channel =
            mapping[
                feature.key
            ]
                ? guild.channels.cache.get(
                    mapping[
                        feature.key
                    ]
                )
                : null;

        /*
         * If the feature channel was moved or renamed manually
         * through the webboard, retain the current parent/category.
         */
        let category =
            channel?.parentId
                ? guild.channels.cache.get(
                    channel.parentId
                )
                : guild.channels.cache.find(
                    candidate =>
                        candidate.type ===
                            ChannelType.GuildCategory &&
                        candidate.name ===
                            feature.categoryName
                );

        if (
            !category ||
            category.type !==
                ChannelType.GuildCategory
        ) {
            try {
                category =
                    await guild.channels.create(
                        {
                            name:
                                feature.categoryName,

                            type:
                                ChannelType.GuildCategory
                        }
                    );
            } catch (error) {
                console.error(
                    `[FEATURE] Kategorie ${feature.categoryName}:`,
                    error.message
                );

                continue;
            }
        }

        if (!channel) {
            channel =
                guild.channels.cache.find(
                    candidate =>
                        candidate.type ===
                            ChannelType.GuildText &&
                        candidate.name ===
                            feature.channelName &&
                        candidate.parentId ===
                            category.id
                );
        }

        if (!channel) {
            try {
                channel =
                    await guild.channels.create(
                        {
                            name:
                                feature.channelName,

                            type:
                                ChannelType.GuildText,

                            parent:
                                category.id,

                            topic:
                                feature.topic
                        }
                    );
            } catch (error) {
                console.error(
                    `[FEATURE] Kanal ${feature.channelName}:`,
                    error.message
                );

                continue;
            }
        } else if (
            channel.parentId !==
                category.id &&
            channel.manageable
        ) {
            await channel.setParent(
                category.id,
                {
                    lockPermissions:
                        false
                }
            ).catch(
                () => {}
            );
        }

        mapping[
            feature.key
        ] = channel.id;
    }

    state.featureChannelsByGuild[
        guild.id
    ] = mapping;

    saveState();

    return mapping;
}

async function getFeatureChannel(
    guild,
    key
) {
    const mapping =
        state.featureChannelsByGuild[
            guild.id
        ] || {};

    const channelId =
        mapping[key];

    const known =
        channelId
            ? guild.channels.cache.get(
                channelId
            )
            : null;

    if (known) {
        return known;
    }

    const fresh =
        await ensureFeatureChannels(
            guild
        );

    return fresh[key]
        ? guild.channels.cache.get(
            fresh[key]
        )
        : null;
}

async function upsertFeatureMessage(
    guild,
    key,
    payload
) {
    const channel =
        await getFeatureChannel(
            guild,
            key
        );

    if (
        !channel ||
        !channel.isTextBased()
    ) {
        return;
    }

    state.featureMessagesByGuild[
        guild.id
    ] ||= {};

    const messageId =
        state.featureMessagesByGuild[
            guild.id
        ][key];

    let message =
        messageId
            ? await channel.messages
                .fetch(
                    messageId
                )
                .catch(
                    () => null
                )
            : null;

    if (message) {
        await message
            .edit(payload)
            .catch(() => {});
    } else {
        message =
            await channel
                .send(payload)
                .catch(() => null);

        if (message) {
            state.featureMessagesByGuild[
                guild.id
            ][key] =
                message.id;
        }
    }

    saveState();
}

// ============================================================
// AUTO FEATURE MESSAGES
// ============================================================

async function publishBazaarFlipChannel(
    guild
) {
    if (!bazaarCache.data) {
        await getBazaar();
    }

    const flips =
        buildBazaarOrderFlips(
            guild.id
        );

    const settings =
        getGuildFlipSettings(
            guild.id
        );

    const embed =
        buildFlipEmbed(
            "📈 Bazaar Order Flips",

            flips.map(
                flipLine
            ),

            0x42e8ff,

            `Min. ${formatNumber(
                settings.minProfit
            )} coins • ` +

            `${settings.minMargin}% Margin • ` +

            "Live Bazaar"
        );

    await upsertFeatureMessage(
        guild,
        "bazaar",
        {
            embeds: [
                embed
            ]
        }
    );
}

async function publishNpcFlipChannel(
    guild
) {
    if (!bazaarCache.data) {
        await getBazaar();
    }

    const flips =
        buildNpcFlips(
            guild.id
        );

    const lines = [
        ...flips.npcToBazaar.map(
            flip =>
                `🛒 ${flipLine(
                    flip
                )}`
        ),

        ...flips.bazaarToNpc.map(
            flip =>
                `🏪 ${flipLine(
                    flip
                )}`
        )
    ];

    const settings =
        getGuildFlipSettings(
            guild.id
        );

    const embed =
        buildFlipEmbed(
            "🏪 NPC Flips",

            lines,

            0x45f0a3,

            `NPC→Bazaar: ` +
            `${flips.npcToBazaar.length} • ` +

            `Bazaar→NPC: ` +
            `${flips.bazaarToNpc.length} • ` +

            `Min. ${formatNumber(
                settings.minProfit
            )} coins`
        );

    await upsertFeatureMessage(
        guild,
        "npc",
        {
            embeds: [
                embed
            ]
        }
    );
}

async function publishStaticFeatureMessages(
    guild
) {
    const skyhelper =
        new EmbedBuilder()
            .setTitle(
                "🛰️ SkyHelper User-Suche"
            )
            .setColor(
                0x8b5cf6
            )
            .setDescription(
                "Hier kannst du Spieler über den Bot analysieren.\n\n" +

                "`/profile <MinecraftName> [Profil]` — Profile + Networth\n" +

                "`/stats <MinecraftName> [Profil]` — Spieler-Stats\n" +

                "`/nw <MinecraftName> [Profil]` — nur Networth\n\n" +

                "Alternativ kannst du die Suche direkt im Web-Dashboard verwenden."
            )
            .setFooter({
                text:
                    "SkyHelperAPI • Hypixel SkyBlock"
            });

    const recipes =
        new EmbedBuilder()
            .setTitle(
                "📚 API Rezepte"
            )
            .setColor(
                0x7c5cff
            )
            .setDescription(
                "Der Rezept-Navigator im Webboard nutzt die echten Hypixel-API-Daten.\n" +

                "Im Discord kannst du mit `/ziel` die aktiven Crafting-Ziele anzeigen."
            );

    const targets =
        new EmbedBuilder()
            .setTitle(
                "🎯 Crafting Ziele"
            )
            .setColor(
                0x42e8ff
            )
            .setDescription(
                "Aktive Ziele werden hier verwaltet. Zutaten können über die Interaktions-Buttons abgehakt werden; bei Abschluss kann der konfigurierte Done-Kanal benachrichtigen."
            );

    await upsertFeatureMessage(
        guild,
        "skyhelper",
        {
            embeds: [
                skyhelper
            ]
        }
    );

    await upsertFeatureMessage(
        guild,
        "recipes",
        {
            embeds: [
                recipes
            ]
        }
    );

    await upsertFeatureMessage(
        guild,
        "targets",
        {
            embeds: [
                targets
            ]
        }
    );
}

async function refreshFeatureDashboards() {
    if (!client.isReady()) {
        return;
    }

    try {
        await getBazaar();

        for (
            const guild of
                client.guilds.cache.values()
        ) {
            await ensureFeatureChannels(
                guild
            );

            await publishStaticFeatureMessages(
                guild
            );

            await publishBazaarFlipChannel(
                guild
            );

            await publishNpcFlipChannel(
                guild
            );
        }
    } catch (error) {
        console.error(
            "[FLIPS] Dashboard-Refresh fehlgeschlagen:",
            error.message
        );
    }
}

setInterval(
    refreshFeatureDashboards,
    FEATURE_REFRESH_MS
).unref();

// ============================================================
// TARGETS
// ============================================================

function makeTarget(
    itemId
) {
    const item =
        itemCatalog.get(
            itemId
        );

    const recipe =
        recipeCatalog.get(
            itemId
        );

    if (
        !item ||
        !recipe
    ) {
        return null;
    }

    return {
        id:
            `${Date.now()}-` +
            `${Math.random()
                .toString(36)
                .slice(2, 8)}`,

        itemId,

        name:
            item.name,

        createdAt:
            new Date().toISOString(),

        items:
            recipe.ingredients.map(
                (
                    ingredient,
                    index
                ) => ({
                    id:
                        `${index}-` +
                        `${ingredient.id}`,

                    ingredientId:
                        ingredient.id,

                    name:
                        ingredient.name,

                    count:
                        ingredient.count,

                    done:
                        false
                })
            )
    };
}

function getTargetDescription(
    target
) {
    if (
        !target.items.length
    ) {
        return "Keine API-Rezeptzutaten vorhanden.";
    }

    return target.items
        .map(
            item =>
                `${item.done ? "✅" : "⬜"} ` +
                `**${formatNumber(
                    item.count
                )}× ${item.name}**`
        )
        .join("\n");
}

async function sendAllTargetsToChannel(
    channel
) {
    if (
        !channel ||
        !channel.isTextBased()
    ) {
        return;
    }

    if (
        !state.activeTargets.length
    ) {
        await channel.send(
            "📡 Aktuell sind keine aktiven API-Rezeptziele vorhanden."
        );

        return;
    }

    for (
        let index = 0;
        index <
            state.activeTargets.length;
        index++
    ) {
        const target =
            state.activeTargets[
                index
            ];

        const apiItem =
            itemCatalog.get(
                target.itemId
            );

        const allDone =
            target.items.length >
                0 &&
            target.items.every(
                item =>
                    item.done
            );

        const embed =
            new EmbedBuilder()
                .setTitle(
                    `${allDone ? "🏆" : "🎯"} ` +
                    `${target.name}`
                )
                .setDescription(
                    getTargetDescription(
                        target
                    )
                )
                .setColor(
                    allDone
                        ? 0x42f59b
                        : 0x7c5cff
                )
                .addFields(
                    {
                        name:
                            "API-ID",

                        value:
                            `\`${target.itemId}\``,

                        inline:
                            true
                    },

                    {
                        name:
                            "Seltenheit",

                        value:
                            apiItem?.tier ||
                            "—",

                        inline:
                            true
                    },

                    {
                        name:
                            "Fortschritt",

                        value:
                            `${target.items.filter(
                                item =>
                                    item.done
                            ).length}/` +
                            `${target.items.length}`,

                        inline:
                            true
                    }
                )
                .setFooter({
                    text:
                        `Ziel ${index + 1} • Hypixel API`
                });

        const row =
            new ActionRowBuilder();

        for (
            let i = 0;
            i <
                Math.min(
                    target.items.length,
                    5
                );
            i++
        ) {
            row.addComponents(
                new ButtonBuilder()
                    .setCustomId(
                        `target:${target.id}:${i}`
                    )
                    .setLabel(
                        `${i + 1}. ` +
                        `${String(
                            target
                                .items[i]
                                .name
                        ).slice(
                            0,
                            70
                        )}`
                    )
                    .setStyle(
                        target.items[i]
                            .done
                            ? ButtonStyle.Success
                            : ButtonStyle.Secondary
                    )
            );
        }

        await channel.send({
            embeds: [
                embed
            ],

            components:
                row.components
                    .length
                    ? [row]
                    : []
        });
    }
}

// ============================================================
// DISCORD EVENTS
// ============================================================

client.once(
    "ready",
    async () => {
        console.log(
            `[BOT] Eingeloggt als ${client.user.tag}`
        );

        roadmap.startAutoUpdate(
            client
        );

        await syncSkyblockItems();

        client.user.setActivity(
            "SkyBlock • Future Dashboard",
            {
                type: 0
            }
        );

        for (
            const guild of
                client.guilds.cache.values()
        ) {
            await ensureFeatureChannels(
                guild
            );

            await registerGuildSlashCommands(
                guild
            );
        }

        await refreshFeatureDashboards();
    }
);

client.on(
    "guildCreate",
    async guild => {
        console.log(
            `[BOT] Neuem Server beigetreten: ${guild.name}`
        );

        await ensureFeatureChannels(
            guild
        );

        await registerGuildSlashCommands(
            guild
        );

        await publishStaticFeatureMessages(
            guild
        ).catch(
            () => {}
        );
    }
);

// ============================================================
// COMMAND HANDLER (LEGACY "!" TEXT COMMANDS)
// ============================================================

client.on(
    "messageCreate",
    async message => {
        if (
            message.author.bot
        ) {
            return;
        }

        const content =
            message.content.trim();

        // ----------------------------------------------------
        // !ziel
        // ----------------------------------------------------

        if (
            content === "!ziel"
        ) {
            await message.channel.send(
                "🛰️ **Aktuelle SkyBlock-Ziele**"
            );

            await sendAllTargetsToChannel(
                message.channel
            );

            return;
        }

        // ----------------------------------------------------
        // !help
        // ----------------------------------------------------

        if (
            content === "!help" ||
            content === "!skyblock"
        ) {
            await message.reply({
                embeds: [
                    buildHelpEmbed()
                ]
            });

            return;
        }

        const [
            command,
            ...args
        ] =
            content.split(
                /\s+/
            );

        // ----------------------------------------------------
        // SKYHELPER PROFILE
        // ----------------------------------------------------

        if (
            command ===
                "!profile" ||
            command ===
                "!stats"
        ) {
            const user =
                args[0];

            const profile =
                args[1];

            if (!user) {
                await message.reply(
                    "Nutze `!profile <MinecraftName> [Profil]` oder `/profile`."
                );

                return;
            }

            try {
                const embed =
                    await buildProfileEmbed(
                        user,
                        profile
                    );

                await message.reply({
                    embeds: [
                        embed
                    ]
                });
            } catch (error) {
                await message.reply(
                    `⚠️ SkyHelper-Abfrage fehlgeschlagen: ${error.message}`
                );
            }

            return;
        }

        // ----------------------------------------------------
        // !nw
        // ----------------------------------------------------

        if (
            command === "!nw" ||
            command === "!networth"
        ) {
            const user =
                args[0];

            if (!user) {
                await message.reply(
                    "Nutze `!nw <MinecraftName> [Profil]` oder `/nw`."
                );

                return;
            }

            try {
                const line =
                    await buildNetworthLine(
                        user,
                        args[1]
                    );

                await message.reply(
                    line
                );
            } catch (error) {
                await message.reply(
                    `⚠️ Networth konnte nicht geladen werden: ${error.message}`
                );
            }

            return;
        }

        // ----------------------------------------------------
        // BAZAAR ORDER FLIPS
        // ----------------------------------------------------

        if (
            command ===
                "!flips" ||
            command ===
                "!orderflip"
        ) {
            try {
                const embed =
                    await buildOrderFlipsEmbed(
                        message.guild
                            ?.id ||
                        null
                    );

                await message.reply({
                    embeds: [
                        embed
                    ]
                });
            } catch (error) {
                await message.reply(
                    `⚠️ Flip-Scan fehlgeschlagen: ${error.message}`
                );
            }

            return;
        }

        // ----------------------------------------------------
        // NPC FLIPS
        // ----------------------------------------------------

        if (
            command ===
                "!npcflip" ||
            command ===
                "!npcflips"
        ) {
            try {
                const embed =
                    await buildNpcFlipsEmbed(
                        message.guild
                            ?.id ||
                        null
                    );

                await message.reply({
                    embeds: [
                        embed
                    ]
                });
            } catch (error) {
                await message.reply(
                    `⚠️ NPC-Flip-Scan fehlgeschlagen: ${error.message}`
                );
            }

            return;
        }

        // ----------------------------------------------------
        // BAZAAR SEARCH
        // ----------------------------------------------------

        if (
            command === "!bz" ||
            command === "!bazaar"
        ) {
            const query =
                args.join(" ");

            if (!query) {
                await message.reply(
                    "Nutze `!bz <Item>` oder `/bz`."
                );

                return;
            }

            try {
                const embed =
                    await buildBazaarSearchEmbed(
                        query
                    );

                if (!embed) {
                    await message.reply(
                        `🔎 Kein Bazaar-Item für **${query}** gefunden.`
                    );

                    return;
                }

                await message.reply({
                    embeds: [
                        embed
                    ]
                });
            } catch (error) {
                await message.reply(
                    `⚠️ Bazaar konnte nicht geladen werden: ${error.message}`
                );
            }
        }
    }
);

// ============================================================
// SLASH COMMAND HANDLER
// ============================================================

async function handleSlashCommand(
    interaction
) {
    const { commandName } =
        interaction;

    try {
        if (
            await roadmap.handleRoadmapCommand(
                interaction
            )
        ) {
            return;
        }

        if (
            commandName ===
            "help"
        ) {
            await interaction.reply({
                embeds: [
                    buildHelpEmbed()
                ]
            });

            return;
        }

        if (
            commandName === "profile" ||
            commandName === "stats"
        ) {
            const user =
                interaction.options.getString(
                    "spieler",
                    true
                );

            const profile =
                interaction.options.getString(
                    "profil"
                );

            await interaction.deferReply();

            const embed =
                await buildProfileEmbed(
                    user,
                    profile
                );

            await interaction.editReply({
                embeds: [
                    embed
                ]
            });

            return;
        }

        if (
            commandName === "nw"
        ) {
            const user =
                interaction.options.getString(
                    "spieler",
                    true
                );

            const profile =
                interaction.options.getString(
                    "profil"
                );

            await interaction.deferReply();

            const line =
                await buildNetworthLine(
                    user,
                    profile
                );

            await interaction.editReply(
                line
            );

            return;
        }

        if (
            commandName === "bz"
        ) {
            const query =
                interaction.options.getString(
                    "item",
                    true
                );

            await interaction.deferReply();

            const embed =
                await buildBazaarSearchEmbed(
                    query
                );

            if (!embed) {
                await interaction.editReply(
                    `🔎 Kein Bazaar-Item für **${query}** gefunden.`
                );

                return;
            }

            await interaction.editReply({
                embeds: [
                    embed
                ]
            });

            return;
        }

        if (
            commandName === "flips"
        ) {
            await interaction.deferReply();

            const embed =
                await buildOrderFlipsEmbed(
                    interaction.guild
                        ?.id ||
                    null
                );

            await interaction.editReply({
                embeds: [
                    embed
                ]
            });

            return;
        }

        if (
            commandName === "npcflip"
        ) {
            await interaction.deferReply();

            const embed =
                await buildNpcFlipsEmbed(
                    interaction.guild
                        ?.id ||
                    null
                );

            await interaction.editReply({
                embeds: [
                    embed
                ]
            });

            return;
        }

        if (
            commandName === "ziel"
        ) {
            await interaction.reply(
                "🛰️ **Aktuelle SkyBlock-Ziele**"
            );

            await sendAllTargetsToChannel(
                interaction.channel
            );

            return;
        }
    } catch (error) {
        console.error(
            `[SLASH:${commandName}]`,
            error
        );

        const payload = {
            content:
                `⚠️ Befehl fehlgeschlagen: ${error.message}`
        };

        if (
            interaction.deferred ||
            interaction.replied
        ) {
            await interaction
                .editReply(
                    payload
                )
                .catch(() => {});
        } else {
            await interaction
                .reply({
                    ...payload,

                    ephemeral:
                        true
                })
                .catch(() => {});
        }
    }
}

// ============================================================
// INTERACTIONS (SLASH COMMANDS + BUTTONS)
// ============================================================

client.on(
    "interactionCreate",
    async interaction => {
        if (
            interaction.isChatInputCommand()
        ) {
            await handleSlashCommand(
                interaction
            );

            return;
        }

        if (
            !interaction.isButton()
        ) {
            return;
        }

        if (
            !interaction.customId.startsWith(
                "target:"
            )
        ) {
            return;
        }

        const [
            ,
            targetId,
            itemIndexRaw
        ] =
            interaction.customId.split(
                ":"
            );

        const itemIndex =
            Number(
                itemIndexRaw
            );

        const target =
            state.activeTargets.find(
                current =>
                    current.id ===
                    targetId
            );

        if (
            !target ||
            !target.items[
                itemIndex
            ]
        ) {
            return interaction.reply(
                {
                    content:
                        "Dieses Ziel existiert nicht mehr.",

                    ephemeral:
                        true
                }
            );
        }

        target.items[
            itemIndex
        ].done =
            !target.items[
                itemIndex
            ].done;

        saveState();

        const apiItem =
            itemCatalog.get(
                target.itemId
            );

        const allDone =
            target.items.length >
                0 &&
            target.items.every(
                item =>
                    item.done
            );

        const embed =
            new EmbedBuilder()
                .setTitle(
                    `${allDone ? "🏆" : "🎯"} ` +
                    `${target.name}`
                )
                .setDescription(
                    getTargetDescription(
                        target
                    )
                )
                .setColor(
                    allDone
                        ? 0x42f59b
                        : 0x7c5cff
                )
                .addFields(
                    {
                        name:
                            "API-ID",

                        value:
                            `\`${target.itemId}\``,

                        inline:
                            true
                    },

                    {
                        name:
                            "Seltenheit",

                        value:
                            apiItem?.tier ||
                            "—",

                        inline:
                            true
                    },

                    {
                        name:
                            "Fortschritt",

                        value:
                            `${
                                target.items.filter(
                                    item =>
                                        item.done
                                ).length
                            }/` +
                            `${target.items.length}`,

                        inline:
                            true
                    }
                );

        const row =
            new ActionRowBuilder();

        for (
            let i = 0;
            i <
                Math.min(
                    target.items.length,
                    5
                );
            i++
        ) {
            row.addComponents(
                new ButtonBuilder()
                    .setCustomId(
                        `target:${target.id}:${i}`
                    )
                    .setLabel(
                        `${i + 1}. ` +
                        `${String(
                            target
                                .items[i]
                                .name
                        ).slice(
                            0,
                            70
                        )}`
                    )
                    .setStyle(
                        target.items[i]
                            .done
                            ? ButtonStyle.Success
                            : ButtonStyle.Secondary
                    )
            );
        }

        await interaction.update(
            {
                embeds: [
                    embed
                ],

                components:
                    row.components
                        .length
                        ? [row]
                        : []
            }
        );

        if (
            allDone &&
            interaction.guild
        ) {
            const selectedId =
                state.selectedDoneChannelByGuild[
                    interaction.guild.id
                ];

            const doneChannel =
                (
                    selectedId &&
                    interaction.guild.channels.cache.get(
                        selectedId
                    )
                ) ||
                interaction.guild.channels.cache.find(
                    channel =>
                        channel.isTextBased() &&
                        [
                            "done",
                            "erledigt",
                            "completed"
                        ].includes(
                            channel.name.toLowerCase()
                        )
                );

            if (
                doneChannel?.isTextBased()
            ) {
                await doneChannel
                    .send(
                        `🏆 **Ziel abgeschlossen:** ${target.name}\n` +
                        `Alle ${target.items.length} API-Zutaten wurden abgehakt.`
                    )
                    .catch(
                        () => {}
                    );
            }
        }
    }
);

// ============================================================
// EXPRESS
// ============================================================

const app =
    express();

app.use(
    express.json({
        limit:
            "1mb"
    })
);

app.use(
    express.urlencoded({
        extended:
            true
    })
);

roadmap.mountRoadmapRoutes(
    app,
    client
);

// Roadmap page on its own port (ROADMAP_PORT, default 3001), safe to expose publicly
roadmap.startPublicServer(
    client
);

// ============================================================
// GUILD JSON
// ============================================================

function jsonGuild(
    guild
) {
    return {
        id:
            guild.id,

        name:
            guild.name,

        iconURL:
            guild.iconURL({
                extension:
                    "png",

                size:
                    128
            }),

        features:
            FEATURE_CHANNEL_DEFINITIONS.map(
                feature => ({
                    ...feature,

                    channelId:
                        state.featureChannelsByGuild[
                            guild.id
                        ]?.[
                            feature.key
                        ] ||
                        null
                })
            ),

        flipSettings:
            getGuildFlipSettings(
                guild.id
            ),

        channels:
            guild.channels.cache
                .sort(
                    (
                        a,
                        b
                    ) =>
                        a.rawPosition -
                        b.rawPosition
                )
                .map(
                    channel => ({
                        id:
                            channel.id,

                        name:
                            channel.name,

                        type:
                            channel.type,

                        typeName:
                            channel.type ===
                            ChannelType.GuildCategory
                                ? "category"
                                : "channel",

                        parentId:
                            channel.parentId,

                        position:
                            channel.rawPosition,

                        manageable:
                            Boolean(
                                guild.members.me
                                    ?.permissions.has(
                                        PermissionsBitField.Flags
                                            .ManageChannels
                                    )
                            ),

                        canPurge:
                            channel.type ===
                            ChannelType.GuildText,

                        isDoneChannel:
                            state
                                .selectedDoneChannelByGuild[
                                guild.id
                            ] ===
                            channel.id
                    })
                )
    };
}

// ============================================================
// SKYHELPER ROUTES
// ============================================================

app.get(
    "/api/skyhelper/player/:user",
    async (
        req,
        res
    ) => {
        try {
            const result =
                await skyHelperPlayerSummary(
                    req.params.user,

                    req.query.profile
                        ? String(
                            req.query.profile
                        )
                        : null
                );

            res.json({
                ok:
                    true,

                ...result
            });
        } catch (error) {
            console.error(
                "[SKYHELPER PLAYER]",
                error
            );

            res.status(
                502
            ).json({
                ok:
                    false,

                error:
                    error.message,

                endpoint:
                    SKYHELPER_API_URL
            });
        }
    }
);

app.get(
    "/api/skyhelper/bazaar",
    async (
        req,
        res
    ) => {
        try {
            const products =
                await findBazaarProduct(
                    req.query.q ||
                    ""
                );

            res.json({
                ok:
                    true,

                products
            });
        } catch (error) {
            console.error(
                "[BAZAAR]",
                error
            );

            res.status(
                502
            ).json({
                ok:
                    false,

                error:
                    error.message
            });
        }
    }
);

// ============================================================
// FLIP ROUTES
// ============================================================

app.get(
    "/api/flips",
    async (
        req,
        res
    ) => {
        try {
            const guildId =
                req.query.guildId
                    ? String(
                        req.query.guildId
                    )
                    : null;

            const type =
                String(
                    req.query.type ||
                    "order"
                );

            if (
                !bazaarCache.data
            ) {
                await getBazaar();
            }

            if (
                type ===
                "npc"
            ) {
                return res.json({
                    ok:
                        true,

                    ...buildNpcFlips(
                        guildId
                    )
                });
            }

            res.json({
                ok:
                    true,

                flips:
                    buildBazaarOrderFlips(
                        guildId
                    )
            });
        } catch (error) {
            console.error(
                "[API FLIPS]",
                error
            );

            res.status(
                502
            ).json({
                ok:
                    false,

                error:
                    error.message
            });
        }
    }
);

// ============================================================
// FEATURE CHANNEL ROUTE
// ============================================================

app.post(
    "/api/guilds/:guildId/features/sync",
    async (
        req,
        res
    ) => {
        try {
            const guild =
                client.guilds.cache.get(
                    req.params.guildId
                );

            if (!guild) {
                return res.status(
                    404
                ).json({
                    ok:
                        false,

                    error:
                        "Server nicht gefunden."
                });
            }

            const channels =
                await ensureFeatureChannels(
                    guild
                );

            await publishStaticFeatureMessages(
                guild
            );

            await publishBazaarFlipChannel(
                guild
            );

            await publishNpcFlipChannel(
                guild
            );

            res.json({
                ok:
                    true,

                channels
            });
        } catch (error) {
            console.error(
                "[FEATURE SYNC]",
                error
            );

            res.status(
                500
            ).json({
                ok:
                    false,

                error:
                    error.message
            });
        }
    }
);

// ============================================================
// FLIP SETTINGS
// ============================================================

app.get(
    "/api/guilds/:guildId/flip-settings",
    (
        req,
        res
    ) => {
        res.json({
            ok:
                true,

            settings:
                getGuildFlipSettings(
                    req.params.guildId
                )
        });
    }
);

app.post(
    "/api/guilds/:guildId/flip-settings",
    (
        req,
        res
    ) => {
        const current =
            getGuildFlipSettings(
                req.params.guildId
            );

        const next = {
            minProfit:
                Math.max(
                    0,
                    Number(
                        req.body.minProfit ??
                        current.minProfit
                    )
                ),

            minMargin:
                Math.max(
                    0,
                    Number(
                        req.body.minMargin ??
                        current.minMargin
                    )
                ),

            minVolume:
                Math.max(
                    0,
                    Number(
                        req.body.minVolume ??
                        current.minVolume
                    )
                ),

            maxResults:
                Math.min(
                    50,

                    Math.max(
                        3,

                        Number(
                            req.body.maxResults ??
                            current.maxResults
                        )
                    )
                )
        };

        state.flipSettingsByGuild[
            req.params.guildId
        ] =
            next;

        saveState();

        res.json({
            ok:
                true,

            settings:
                next
        });
    }
);

// ============================================================
// NPC PRICE ROUTES
// ============================================================

app.get(
    "/api/guilds/:guildId/npc-prices",
    (
        req,
        res
    ) => {
        res.json({
            ok:
                true,

            prices:
                getGuildNpcPrices(
                    req.params.guildId
                )
        });
    }
);

app.post(
    "/api/guilds/:guildId/npc-prices",
    (
        req,
        res
    ) => {
        const id =
            normalizeIngredientKey(
                req.body.id ||
                ""
            ).toUpperCase();

        const price =
            Number(
                req.body.price
            );

        const npc =
            String(
                req.body.npc ||
                "NPC"
            );

        if (
            !id ||
            !Number.isFinite(
                price
            ) ||
            price <= 0
        ) {
            return res.status(
                400
            ).json({
                ok:
                    false,

                error:
                    "Item-ID und positiver NPC-Kaufpreis sind erforderlich."
            });
        }

        state.npcBuyPricesByGuild[
            req.params.guildId
        ] ||=
            {};

        state.npcBuyPricesByGuild[
            req.params.guildId
        ][id] = {
            price,

            npc
        };

        saveState();

        res.json({
            ok:
                true,

            prices:
                getGuildNpcPrices(
                    req.params.guildId
                )
        });
    }
);

app.delete(
    "/api/guilds/:guildId/npc-prices/:id",
    (
        req,
        res
    ) => {
        const id =
            normalizeIngredientKey(
                req.params.id
            ).toUpperCase();

        if (
            state.npcBuyPricesByGuild[
                req.params.guildId
            ]
        ) {
            delete state
                .npcBuyPricesByGuild[
                    req.params.guildId
                ][id];

            saveState();
        }

        res.json({
            ok:
                true,

            prices:
                getGuildNpcPrices(
                    req.params.guildId
                )
        });
    }
);

// ============================================================
// STATE ROUTE
// ============================================================

app.get(
    "/api/state",
    (
        req,
        res
    ) => {
        res.json({
            ok:
                true,

            bot: {
                ready:
                    client.isReady(),

                tag:
                    client.user?.tag ||
                    null,

                guildCount:
                    client.guilds.size
            },

            api:
                apiStatus,

            recipes:
                [
                    ...recipeCatalog
                        .values()
                ].map(
                    recipe => ({
                        itemId:
                            recipe.itemId,

                        name:
                            recipe.name,

                        ingredients:
                            recipe.ingredients
                    })
                ),

            targets:
                state.activeTargets,

            flipSettingsByGuild:
                state.flipSettingsByGuild,

            npcBuyPricesByGuild:
                state.npcBuyPricesByGuild,

            guilds:
                client.guilds.cache.map(
                    jsonGuild
                )
        });
    }
);

// ============================================================
// ITEM ROUTE
// ============================================================

app.get(
    "/api/item/:id",
    (
        req,
        res
    ) => {
        const item =
            itemCatalog.get(
                req.params.id
            );

        if (!item) {
            return res.status(
                404
            ).json({
                ok:
                    false,

                error:
                    "Item nicht gefunden."
            });
        }

        res.json({
            ok:
                true,

            item,

            recipe:
                recipeCatalog.get(
                    req.params.id
                ) ||
                null
        });
    }
);

// ============================================================
// API SYNC
// ============================================================

app.post(
    "/api/sync",
    async (
        req,
        res
    ) => {
        await syncSkyblockItems();

        res.json({
            ok:
                apiStatus.success,

            api:
                apiStatus
        });
    }
);

// ============================================================
// TARGET ROUTES
// ============================================================

app.post(
    "/api/targets",
    (
        req,
        res
    ) => {
        const itemId =
            String(
                req.body.itemId ||
                ""
            );

        const target =
            makeTarget(
                itemId
            );

        if (!target) {
            return res.status(
                400
            ).json({
                ok:
                    false,

                error:
                    "Kein echtes Rezept aus der Hypixel API für dieses Item gefunden."
            });
        }

        state.activeTargets.push(
            target
        );

        saveState();

        res.json({
            ok:
                true,

            target
        });
    }
);

app.delete(
    "/api/targets/:id",
    (
        req,
        res
    ) => {
        const before =
            state.activeTargets.length;

        state.activeTargets =
            state.activeTargets.filter(
                target =>
                    target.id !==
                    req.params.id
            );

        if (
            before ===
            state.activeTargets.length
        ) {
            return res.status(
                404
            ).json({
                ok:
                    false,

                error:
                    "Ziel nicht gefunden."
            });
        }

        saveState();

        res.json({
            ok:
                true
        });
    }
);

// ============================================================
// DONE CHANNEL
// ============================================================

app.post(
    "/api/guilds/:guildId/done-channel",
    (
        req,
        res
    ) => {
        const guild =
            client.guilds.cache.get(
                req.params.guildId
            );

        if (!guild) {
            return res.status(
                404
            ).json({
                ok:
                    false,

                error:
                    "Server nicht gefunden."
            });
        }

        const channelId =
            String(
                req.body.channelId ||
                ""
            );

        if (
            channelId &&
            !guild.channels.cache.has(
                channelId
            )
        ) {
            return res.status(
                400
            ).json({
                ok:
                    false,

                error:
                    "Kanal gehört nicht zu diesem Server."
            });
        }

        state.selectedDoneChannelByGuild[
            guild.id
        ] =
            channelId;

        saveState();

        res.json({
            ok:
                true
        });
    }
);

// ============================================================
// CREATE CHANNEL
// ============================================================

app.post(
    "/api/guilds/:guildId/channels",
    async (
        req,
        res
    ) => {
        try {
            const guild =
                client.guilds.cache.get(
                    req.params.guildId
                );

            if (!guild) {
                return res.status(
                    404
                ).json({
                    ok:
                        false,

                    error:
                        "Server nicht gefunden."
                });
            }

            const name =
                String(
                    req.body.name ||
                    ""
                ).trim();

            const type =
                req.body.type ===
                "category"
                    ? ChannelType.GuildCategory
                    : ChannelType.GuildText;

            const parentId =
                req.body.parentId
                    ? String(
                        req.body.parentId
                    )
                    : undefined;

            if (!name) {
                return res.status(
                    400
                ).json({
                    ok:
                        false,

                    error:
                        "Name fehlt."
                });
            }

            const channel =
                await guild.channels.create(
                    {
                        name:
                            name.slice(
                                0,
                                100
                            ),

                        type,

                        parent:
                            type ===
                            ChannelType.GuildText
                                ? parentId
                                : undefined
                    }
                );

            res.json({
                ok:
                    true,

                channel:
                    jsonGuild(
                        guild
                    )
                        .channels.find(
                            item =>
                                item.id ===
                                channel.id
                        )
            });
        } catch (error) {
            console.error(
                "[CHANNEL CREATE]",
                error
            );

            res.status(
                500
            ).json({
                ok:
                    false,

                error:
                    error.message
            });
        }
    }
);

// ============================================================
// RENAME CHANNEL / CATEGORY
// ============================================================

app.patch(
    "/api/guilds/:guildId/channels/:channelId",
    async (
        req,
        res
    ) => {
        try {
            const guild =
                client.guilds.cache.get(
                    req.params.guildId
                );

            const channel =
                guild?.channels.cache.get(
                    req.params.channelId
                );

            if (
                !guild ||
                !channel
            ) {
                return res.status(
                    404
                ).json({
                    ok:
                        false,

                    error:
                        "Kanal nicht gefunden."
                });
            }

            const name =
                String(
                    req.body.name ||
                    ""
                ).trim();

            if (!name) {
                return res.status(
                    400
                ).json({
                    ok:
                        false,

                    error:
                        "Name fehlt."
                });
            }

            await channel.setName(
                name.slice(
                    0,
                    100
                )
            );

            res.json({
                ok:
                    true,

                channel:
                    jsonGuild(
                        guild
                    )
                        .channels.find(
                            item =>
                                item.id ===
                                channel.id
                        )
            });
        } catch (error) {
            console.error(
                "[CHANNEL RENAME]",
                error
            );

            res.status(
                500
            ).json({
                ok:
                    false,

                error:
                    error.message
            });
        }
    }
);

// ============================================================
// DELETE CHANNEL
// ============================================================

app.delete(
    "/api/guilds/:guildId/channels/:channelId",
    async (
        req,
        res
    ) => {
        try {
            const guild =
                client.guilds.cache.get(
                    req.params.guildId
                );

            const channel =
                guild?.channels.cache.get(
                    req.params.channelId
                );

            if (
                !guild ||
                !channel
            ) {
                return res.status(
                    404
                ).json({
                    ok:
                        false,

                    error:
                        "Kanal nicht gefunden."
                });
            }

            await channel.delete(
                "Deleted from Future Dashboard"
            );

            res.json({
                ok:
                    true
            });
        } catch (error) {
            console.error(
                "[CHANNEL DELETE]",
                error
            );

            res.status(
                500
            ).json({
                ok:
                    false,

                error:
                    error.message
            });
        }
    }
);

// ============================================================
// PURGE
// ============================================================

app.post(
    "/api/guilds/:guildId/channels/:channelId/purge",
    async (
        req,
        res
    ) => {
        try {
            const guild =
                client.guilds.cache.get(
                    req.params.guildId
                );

            const channel =
                guild?.channels.cache.get(
                    req.params.channelId
                );

            if (
                !channel ||
                !channel.isTextBased() ||
                channel.type !==
                    ChannelType.GuildText
            ) {
                return res.status(
                    400
                ).json({
                    ok:
                        false,

                    error:
                        "Nur normale Textkanäle können geleert werden."
                });
            }

            const amount =
                Math.min(
                    Math.max(
                        Number(
                            req.body.amount
                        ) ||
                            10,

                        1
                    ),

                    100
                );

            const deleted =
                await channel.bulkDelete(
                    amount,
                    true
                );

            res.json({
                ok:
                    true,

                deleted:
                    deleted.size
            });
        } catch (error) {
            console.error(
                "[CHANNEL PURGE]",
                error
            );

            res.status(
                500
            ).json({
                ok:
                    false,

                error:
                    error.message
            });
        }
    }
);

// ============================================================
// REORDER
// ============================================================

app.post(
    "/api/guilds/:guildId/channels/reorder",
    async (
        req,
        res
    ) => {
        try {
            const guild =
                client.guilds.cache.get(
                    req.params.guildId
                );

            if (!guild) {
                return res.status(
                    404
                ).json({
                    ok:
                        false,

                    error:
                        "Server nicht gefunden."
                });
            }

            const orderedIds =
                Array.isArray(
                    req.body.channelIds
                )
                    ? req.body.channelIds.map(
                        String
                    )
                    : [];

            const validIds =
                orderedIds.filter(
                    id =>
                        guild.channels.cache.has(
                            id
                        )
                );

            if (
                !validIds.length
            ) {
                return res.status(
                    400
                ).json({
                    ok:
                        false,

                    error:
                        "Keine gültigen Kanäle übergeben."
                });
            }

            await guild.channels.setPositions(
                validIds.map(
                    (
                        id,
                        index
                    ) => ({
                        channel:
                            id,

                        position:
                            index
                    })
                )
            );

            res.json({
                ok:
                    true
            });
        } catch (error) {
            console.error(
                "[CHANNEL REORDER]",
                error
            );

            res.status(
                500
            ).json({
                ok:
                    false,

                error:
                    error.message
            });
        }
    }
);

// ============================================================
// MOVE CHANNEL
// ============================================================

app.post(
    "/api/guilds/:guildId/channels/:channelId/move",
    async (
        req,
        res
    ) => {
        try {
            const guild =
                client.guilds.cache.get(
                    req.params.guildId
                );

            const channel =
                guild?.channels.cache.get(
                    req.params.channelId
                );

            if (
                !guild ||
                !channel
            ) {
                return res.status(
                    404
                ).json({
                    ok:
                        false,

                    error:
                        "Kanal nicht gefunden."
                });
            }

            const parentId =
                req.body.parentId
                    ? String(
                        req.body.parentId
                    )
                    : null;

            const position =
                Number.isFinite(
                    Number(
                        req.body.position
                    )
                )
                    ? Number(
                        req.body.position
                    )
                    : channel.rawPosition;

            await channel.setParent(
                parentId,
                {
                    lockPermissions:
                        false
                }
            );

            await channel.setPosition(
                position
            );

            res.json({
                ok:
                    true
            });
        } catch (error) {
            console.error(
                "[CHANNEL MOVE]",
                error
            );

            res.status(
                500
            ).json({
                ok:
                    false,

                error:
                    error.message
            });
        }
    }
);

// ============================================================
// FUTURISTIC WEBBOARD
// ============================================================

app.get(
    "/",
    (
        req,
        res
    ) => {
        res.type(
            "html"
        ).send(`<!doctype html>

<html lang="de">

<head>

<meta charset="utf-8">

<meta
    name="viewport"
    content="width=device-width,initial-scale=1"
>

<title>
    SkyBlock // Future Control
</title>

<style>

:root{
  --bg:#070912;
  --panel:#0d1220;
  --panel2:#11182a;
  --line:#202b46;
  --text:#edf4ff;
  --muted:#7f8da8;
  --cyan:#42e8ff;
  --violet:#8b5cf6;
  --green:#45f0a3;
  --red:#ff5470;
  --orange:#ffb454;
  --shadow:0 20px 70px rgba(0,0,0,.42);
}

*{
  box-sizing:border-box;
}

body{
  margin:0;

  color:var(--text);

  font:
    14px/1.5
    Inter,
    ui-sans-serif,
    system-ui,
    -apple-system,
    BlinkMacSystemFont,
    "Segoe UI",
    sans-serif;

  background:
    radial-gradient(
      circle at 12% 5%,
      rgba(66,232,255,.10),
      transparent 28rem
    ),

    radial-gradient(
      circle at 90% 12%,
      rgba(139,92,246,.13),
      transparent 30rem
    ),

    linear-gradient(
      145deg,
      #05070e,
      #0a0d17 48%,
      #070912
    );

  min-height:100vh;
}

body:before{
  content:"";

  position:fixed;

  inset:0;

  pointer-events:none;

  opacity:.18;

  background-image:
    linear-gradient(
      rgba(255,255,255,.025)
      1px,
      transparent 1px
    ),

    linear-gradient(
      90deg,
      rgba(255,255,255,.025)
      1px,
      transparent 1px
    );

  background-size:
    36px 36px;
}

.shell{
  max-width:1500px;

  margin:auto;

  padding:28px;
}

.topbar{
  display:flex;

  justify-content:
    space-between;

  gap:20px;

  align-items:center;

  margin-bottom:24px;
}

.brand{
  display:flex;

  align-items:center;

  gap:14px;
}

.logo{
  width:48px;

  height:48px;

  border-radius:
    16px;

  background:
    linear-gradient(
      135deg,
      var(--cyan),
      var(--violet)
    );

  display:grid;

  place-items:center;

  color:#03050b;

  font-weight:900;

  box-shadow:
    0 0 35px
    rgba(66,232,255,.28);
}

h1{
  margin:0;

  font-size:25px;

  letter-spacing:-.03em;
}

h2,
h3{
  margin-top:0;
}

.sub{
  color:
    var(--muted);
}

.actions{
  display:flex;

  gap:8px;

  flex-wrap:wrap;
}

button,
input,
select{
  font:inherit;

  border-radius:10px;

  border:
    1px solid
    var(--line);

  background:
    #0a1020;

  color:
    var(--text);

  padding:
    10px 12px;
}

button{
  cursor:pointer;
}

.primary{
  background:
    linear-gradient(
      135deg,
      #20bfd7,
      #7651ef
    );

  border:0;

  font-weight:800;
}

.danger{
  background:
    rgba(
      255,
      84,
      112,
      .10
    );

  color:
    #ff8195;
}

.ghost{
  background:
    rgba(
      255,
      255,
      255,
      .025
    );
}

button:hover{
  filter:
    brightness(
      1.14
    );

  transform:
    translateY(-1px);
}

.grid{
  display:grid;

  grid-template-columns:
    1.15fr
    .85fr;

  gap:18px;
}

.full{
  grid-column:
    1/-1;
}

.card{
  background:
    linear-gradient(
      180deg,
      rgba(17,24,42,.92),
      rgba(9,14,26,.94)
    );

  border:
    1px solid
    var(--line);

  border-radius:
    20px;

  padding:
    20px;

  box-shadow:
    var(--shadow);

  backdrop-filter:
    blur(18px);
}

.cardhead{
  display:flex;

  justify-content:
    space-between;

  align-items:
    center;

  gap:12px;

  margin-bottom:
    15px;
}

.cardhead h2{
  font-size:16px;

  margin:0;
}

.stats{
  display:grid;

  grid-template-columns:
    repeat(4,1fr);

  gap:10px;
}

.stat{
  padding:15px;

  border:
    1px solid
    var(--line);

  border-radius:
    15px;

  background:
    rgba(
      255,
      255,
      255,
      .025
    );
}

.stat b{
  display:block;

  font-size:22px;
}

.stat span{
  color:
    var(--muted);

  font-size:12px;
}

.status{
  display:inline-flex;

  align-items:center;

  gap:7px;

  padding:
    7px 10px;

  border-radius:
    99px;

  background:
    rgba(
      69,
      240,
      163,
      .08
    );

  color:
    var(--green);
}

.dot{
  width:7px;

  height:7px;

  border-radius:
    50%;

  background:
    currentColor;

  box-shadow:
    0 0 12px
    currentColor;
}

.search{
  width:100%;

  margin-bottom:10px;
}

.recipe-search-wrap{
  position:relative;

  margin-bottom:6px;
}

.search-row{
  display:grid;

  grid-template-columns:
    minmax(0,1fr)
    minmax(220px,.6fr)
    42px;

  gap:8px;

  align-items:start;
}

.search-row .search{
  margin:0;
}

.autocomplete-wrap{
  min-width:0;

  position:relative;
}

.autocomplete-panel{
  position:absolute;

  left:0;

  right:0;

  top:
    calc(
      100% - 2px
    );

  z-index:50;

  display:none;

  max-height:300px;

  overflow:auto;

  padding:5px;

  background:
    #0b1120;

  border:
    1px solid
    #2b395a;

  border-radius:
    12px;

  box-shadow:
    0 18px 45px
    rgba(0,0,0,.48);
}

.autocomplete-panel.open{
  display:block;
}

.autocomplete-item{
  display:flex;

  align-items:center;

  justify-content:
    space-between;

  gap:12px;

  width:100%;

  padding:
    10px 11px;

  border:0;

  border-radius:
    9px;

  background:
    transparent;

  color:
    var(--text);

  text-align:left;

  cursor:pointer;
}

.autocomplete-item:hover,
.autocomplete-item.active{
  background:
    linear-gradient(
      90deg,
      rgba(66,232,255,.10),
      rgba(139,92,246,.10)
    );
}

.autocomplete-item strong{
  display:block;
}

.autocomplete-item small{
  display:block;

  color:
    var(--muted);

  margin-top:2px;
}

.autocomplete-tier{
  font-size:10px;

  color:#bca7ff;

  background:
    rgba(
      139,
      92,
      246,
      .12
    );

  padding:
    4px 7px;

  border-radius:
    99px;

  white-space:nowrap;
}

.recipe-list{
  max-height:330px;

  overflow:auto;

  padding-right:4px;
}

.recipe{
  display:flex;

  justify-content:
    space-between;

  align-items:
    center;

  gap:12px;

  padding:12px;

  border:
    1px solid
    transparent;

  border-radius:
    13px;
}

.recipe:hover{
  border-color:
    var(--line);

  background:
    rgba(
      255,
      255,
      255,
      .025
    );
}

.badge{
  padding:
    4px 8px;

  border-radius:
    999px;

  background:
    rgba(
      139,
      92,
      246,
      .14
    );

  color:
    #bca7ff;

  font-size:11px;
}

.mono{
  font-family:
    ui-monospace,
    SFMono-Regular,
    Menlo,
    monospace;
}

.targets{
  display:grid;

  gap:10px;
}

.target{
  border:
    1px solid
    var(--line);

  border-radius:
    15px;

  padding:14px;

  background:
    rgba(
      255,
      255,
      255,
      .02
    );
}

.targettop{
  display:flex;

  justify-content:
    space-between;

  gap:10px;
}

.target ul{
  margin:
    10px 0 0;

  padding:0;

  list-style:none;

  color:
    #b9c4d8;
}

.target li{
  padding:
    3px 0;
}

.target li.done{
  color:
    var(--green);
}

.guild{
  border:
    1px solid
    var(--line);

  border-radius:
    18px;

  padding:16px;

  margin-bottom:12px;

  background:
    rgba(
      255,
      255,
      255,
      .018
    );
}

.guildtop{
  display:flex;

  justify-content:
    space-between;

  align-items:
    center;

  margin-bottom:
    12px;
}

.guildname{
  font-weight:800;

  font-size:15px;
}

.channel-board{
  display:grid;

  grid-template-columns:
    repeat(
      auto-fit,
      minmax(
        280px,
        1fr
      )
    );

  gap:12px;
}

.channel-group{
  border:
    1px solid
    var(--line);

  border-radius:
    15px;

  padding:10px;

  background:
    #080d19;

  min-height:70px;
}

.group-title{
  color:
    var(--muted);

  font-size:11px;

  text-transform:
    uppercase;

  letter-spacing:
    .12em;

  padding:
    4px 7px 9px;
}

.channel{
  display:flex;

  align-items:
    center;

  gap:8px;

  padding:10px;

  border-radius:
    11px;

  border:
    1px solid
    transparent;

  background:
    #0d1424;

  margin:
    5px 0;

  cursor:grab;
}

.channel:active{
  cursor:grabbing;
}

.channel.dragging{
  opacity:.35;

  border-color:
    var(--cyan);
}

.handle{
  color:
    #4c5c7a;
}

.channelname{
  flex:1;

  min-width:0;

  white-space:nowrap;

  overflow:hidden;

  text-overflow:ellipsis;
}

.channel small{
  color:
    var(--muted);
}

.dropzone{
  border:
    1px dashed
    #334263;

  border-radius:
    11px;

  padding:8px;

  text-align:center;

  color:
    #53637f;

  font-size:11px;

  margin-top:6px;
}

.dropzone.over{
  border-color:
    var(--cyan);

  color:
    var(--cyan);

  background:
    rgba(
      66,
      232,
      255,
      .05
    );
}

.toolbar{
  display:flex;

  gap:8px;

  flex-wrap:wrap;

  margin-bottom:12px;
}

.toolbar input{
  flex:1;

  min-width:170px;
}

.feature-mini-grid{
  display:flex;

  gap:7px;

  flex-wrap:wrap;

  margin-top:8px;
}

.feature-mini-grid span{
  padding:
    6px 8px;

  border:
    1px solid
    var(--line);

  border-radius:
    9px;

  background:
    rgba(
      255,
      255,
      255,
      .02
    );

  font-size:11px;

  color:
    var(--muted);
}

.api-box{
  font-size:12px;

  color:
    var(--muted);

  border:
    1px solid
    var(--line);

  padding:10px;

  border-radius:
    12px;

  background:
    #090f1d;
}

.api-box code{
  color:
    #d5ddff;
}

.ok{
  color:
    var(--green);
}

.bad{
  color:
    var(--red);
}

.empty{
  padding:30px;

  text-align:center;

  color:
    var(--muted);
}

.toast{
  position:fixed;

  right:22px;

  bottom:22px;

  z-index:99;

  background:
    #0f1729;

  border:
    1px solid
    #334263;

  border-radius:
    13px;

  padding:
    12px 15px;

  box-shadow:
    var(--shadow);

  display:none;
}

@media(max-width:950px){

  .grid{
    grid-template-columns:
      1fr;
  }

  .stats{
    grid-template-columns:
      repeat(2,1fr);
  }

  .shell{
    padding:16px;
  }

  .topbar{
    align-items:
      flex-start;

    flex-direction:
      column;
  }
}

@media(max-width:700px){

  .search-row{
    grid-template-columns:
      1fr 42px;
  }

  .search-row select{
    grid-column:
      1/-1;

    grid-row:2;
  }
}

</style>

</head>

<body>

<div class="shell">

<header class="topbar">

<div class="brand">

<div class="logo">
SB
</div>

<div>

<h1>
SkyBlock // Future Control
</h1>

<div class="sub">
Discord automation • Hypixel API • Channel Matrix
</div>

</div>

</div>

<div class="actions">

<span
    id="botStatus"
    class="status"
>

<i class="dot"></i>

Verbinde …

</span>

<button
    class="ghost"
    onclick="syncAPI()"
>
↻ API synchronisieren
</button>

</div>

</header>

<section
    class="card"
    style="margin-bottom:18px"
>

<div class="stats">

<div class="stat">

<b id="itemCount">
—
</b>

<span>
API Items
</span>

</div>

<div class="stat">

<b id="recipeCount">
—
</b>

<span>
echte API-Rezepte
</span>

</div>

<div class="stat">

<b id="guildCount">
—
</b>

<span>
Discord Server
</span>

</div>

<div class="stat">

<b id="targetCount">
—
</b>

<span>
aktive Ziele
</span>

</div>

</div>

</section>

<main class="grid">

<section class="card">

<div class="cardhead">

<h2>
◈ API Rezept-Navigator
</h2>

<span
    id="apiSync"
    class="badge"
>
lade …
</span>

</div>

<div class="recipe-search-wrap">

<div class="search-row">

<div class="autocomplete-wrap">

<input
    id="recipeSearch"
    class="search"
    autocomplete="off"
    placeholder="Item suchen … z.B. Hyperion"
    list="recipeSuggestions"
>

<datalist
    id="recipeSuggestions"
></datalist>

</div>

<select
    id="recipeSelect"
    title="Rezept direkt auswählen"
>

<option value="">
Rezept auswählen …
</option>

</select>

<button
    class="ghost"
    type="button"
    onclick="clearRecipeSearch()"
>
×
</button>

</div>

<div
    id="recipeAutocomplete"
    class="autocomplete-panel"
></div>

</div>

<div
    id="recipeList"
    class="recipe-list"
></div>

</section>

<section class="card">

<div class="cardhead">

<h2>
◈ Aktive Crafting-Ziele
</h2>

</div>

<div
    id="targets"
    class="targets"
></div>

</section>

<section
    class="card full"
>

<div class="cardhead">

<div>

<h2>
◈ SkyHelper Player Intelligence
</h2>

<div class="sub">
Profile • Networth • Skills • Dungeons • Slayer über die SkyHelperAPI.
</div>

</div>

<span class="badge">
SKYHELPER
</span>

</div>

<div class="toolbar">

<input
    id="playerSearch"
    placeholder="Minecraft Name / UUID"
>

<input
    id="profileSearch"
    placeholder="Profil optional, z.B. Banana"
>

<button
    class="primary"
    onclick="lookupPlayer()"
>
Profil analysieren
</button>

</div>

<div
    id="playerResult"
    class="api-box"
>
Noch kein Spieler geladen.
</div>

</section>

<section
    class="card full"
>

<div class="cardhead">

<div>

<h2>
◈ Live Bazaar
</h2>

<div class="sub">
Live Buy/Sell-Preise direkt aus der Hypixel Bazaar API.
</div>

</div>

</div>

<div class="toolbar">

<input
    id="bazaarSearch"
    placeholder="Item suchen, z.B. Enchanted Diamond"
>

<button
    class="primary"
    onclick="lookupBazaar()"
>
Preis suchen
</button>

</div>

<div
    id="bazaarResult"
    class="api-box"
>
Noch keine Bazaar-Abfrage.
</div>

</section>

<section
    class="card full"
>

<div class="cardhead">

<div>

<h2>
◈ Flip Scanner
</h2>

<div class="sub">
Order-Flips, NPC→Bazaar und Bazaar→NPC. Die Discord-Funktionskanäle werden daraus automatisch aktualisiert.
</div>

</div>

</div>

<div class="toolbar">

<select
    id="flipGuildSelect"
></select>

<select
    id="flipType"
>

<option value="order">
Bazaar Order-Flips
</option>

<option value="npc">
NPC Flips
</option>

</select>

<input
    id="minProfit"
    type="number"
    min="0"
    placeholder="Min. Profit / Item"
>

<input
    id="minMargin"
    type="number"
    min="0"
    step="0.1"
    placeholder="Min. Margin %"
>

<input
    id="minVolume"
    type="number"
    min="0"
    placeholder="Min. Tagesvolumen"
>

<button
    class="primary"
    onclick="saveFlipSettingsAndScan()"
>
Flip-Scan
</button>

</div>

<div
    id="flipResult"
    class="api-box"
>
Noch kein Flip-Scan.
</div>

<div
    style="margin-top:14px"
    class="api-box"
>

<strong>
NPC Einkaufspreise
</strong>

<div class="sub">

Für automatische NPC→Bazaar-Flips kannst du hier weitere NPC-Kaufpreise hinterlegen. Bazaar→NPC wird automatisch über <code>npc_sell_price</code> der Item-Daten erkannt.

</div>

<div
    class="toolbar"
    style="margin-top:9px;margin-bottom:0"
>

<input
    id="npcPriceId"
    placeholder="Item-ID, z.B. SAND"
>

<input
    id="npcPriceValue"
    type="number"
    min="0"
    step="0.01"
    placeholder="NPC Kaufpreis"
>

<input
    id="npcPriceNpc"
    placeholder="NPC Name"
>

<button
    class="ghost"
    onclick="saveNpcPrice()"
>
NPC Preis speichern
</button>

</div>

<div
    id="npcPriceList"
    class="sub"
    style="margin-top:9px"
></div>

</div>

</section>

<section
    class="card full"
>

<div class="cardhead">

<div>

<h2>
◈ Discord Channel Matrix
</h2>

<div class="sub">
Kanäle per Drag & Drop neu sortieren, in Kategorien ziehen, umbenennen, löschen und neu erstellen.
</div>

</div>

</div>

<div
    id="guilds"
></div>

</section>

</main>

</div>

<div
    id="toast"
    class="toast"
></div>

<script>

let appState = null;

let dragChannel = null;

const $ =
    id =>
        document.getElementById(
            id
        );

// ============================================================
// UI HELPERS
// ============================================================

function escapeHtml(
    value
){
    return String(
        value ?? ""
    ).replace(
        /[&<>"']/g,
        c =>
            ({
                "&":
                    "&amp;",

                "<":
                    "&lt;",

                ">":
                    "&gt;",

                '"':
                    "&quot;",

                "'":
                    "&#039;"
            }[c])
    );
}

function toast(
    message,
    error = false
){
    const el =
        $("toast");

    el.textContent =
        message;

    el.style.display =
        "block";

    el.style.borderColor =
        error
            ? "rgba(255,84,112,.6)"
            : "rgba(66,232,255,.45)";

    clearTimeout(
        window.__toast
    );

    window.__toast =
        setTimeout(
            () =>
                el.style.display =
                    "none",

            2800
        );
}

async function request(
    url,
    options = {}
){
    const response =
        await fetch(
            url,
            {
                headers:
                    {
                        "Content-Type":
                            "application/json",

                        ...(
                            options.headers ||
                            {}
                        )
                    },

                ...options
            }
        );

    const data =
        await response
            .json()
            .catch(
                () =>
                    ({})
            );

    if (
        !response.ok ||
        data.ok === false
    ) {
        throw new Error(
            data.error ||
            "Request fehlgeschlagen"
        );
    }

    return data;
}

// ============================================================
// LOAD
// ============================================================

async function load(){
    try {
        appState =
            await request(
                "/api/state"
            );

        render();
    } catch (
        error
    ) {
        toast(
            error.message,
            true
        );
    }
}

// ============================================================
// API SYNC
// ============================================================

async function syncAPI(){
    try {
        toast(
            "Hypixel API wird synchronisiert …"
        );

        await request(
            "/api/sync",
            {
                method:
                    "POST",

                body:
                    "{}"
            }
        );

        await load();

        toast(
            "API-Daten aktualisiert."
        );
    } catch (
        error
    ) {
        toast(
            error.message,
            true
        );
    }
}

// ============================================================
// MAIN RENDER
// ============================================================

function render(){

    const {
        bot,
        api,
        recipes,
        targets,
        guilds
    } =
        appState;

    $("itemCount")
        .textContent =
            Number(
                api.count ||
                0
            ).toLocaleString(
                "de-CH"
            );

    $("recipeCount")
        .textContent =
            Number(
                api.recipeCount ||
                0
            ).toLocaleString(
                "de-CH"
            );

    $("guildCount")
        .textContent =
            guilds.length;

    $("targetCount")
        .textContent =
            targets.length;

    $("botStatus")
        .innerHTML =
            '<i class="dot"></i> ' +
            (
                bot.ready
                    ? "Bot online"
                    : "Bot offline"
            );

    $("botStatus")
        .style.color =
            bot.ready
                ? "var(--green)"
                : "var(--red)";

    $("apiSync")
        .textContent =
            api.syncedAt
                ? "Sync " +
                  new Date(
                      api.syncedAt
                  ).toLocaleString(
                      "de-CH"
                  )
                : "Noch kein Sync";

    renderRecipeControls();

    renderRecipes();

    renderTargets();

    renderFlipControls();

    renderGuilds();
}

// ============================================================
// RECIPE SEARCH
// ============================================================

function getFilteredRecipes(
    query,
    limit = 250
){
    const q =
        String(
            query ||
            ""
        )
            .trim()
            .toLowerCase();

    return appState
        .recipes

        .filter(
            recipe =>
                !q ||

                recipe.name
                    .toLowerCase()
                    .includes(q) ||

                recipe.itemId
                    .toLowerCase()
                    .includes(q)
        )

        .sort(
            (
                a,
                b
            ) => {

                if (!q) {
                    return a.name
                        .localeCompare(
                            b.name
                        );
                }

                const rank =
                    value =>
                        value === q
                            ? 0
                            : value.startsWith(
                                q
                            )
                                ? 1
                                : value.includes(
                                    q
                                )
                                    ? 2
                                    : 3;

                return (
                    Math.min(
                        rank(
                            a.name.toLowerCase()
                        ),

                        rank(
                            a.itemId.toLowerCase()
                        )
                    ) -

                    Math.min(
                        rank(
                            b.name.toLowerCase()
                        ),

                        rank(
                            b.itemId.toLowerCase()
                        )
                    )
                ) ||
                    a.name.localeCompare(
                        b.name
                    );
            }
        )

        .slice(
            0,
            limit
        );
}

function renderRecipeControls(){

    const recipes =
        [
            ...appState.recipes
        ].sort(
            (
                a,
                b
            ) =>
                a.name.localeCompare(
                    b.name
                )
        );

    const select =
        $("recipeSelect");

    const current =
        select.value;

    select.innerHTML =
        '<option value="">Rezept auswählen …</option>' +

        recipes
            .map(
                recipe =>
                    \`<option value="\${escapeHtml(
                        recipe.itemId
                    )}">\${escapeHtml(
                        recipe.name
                    )} — \${escapeHtml(
                        recipe.itemId
                    )}</option>\`
            )
            .join("");

    if (
        recipes.some(
            recipe =>
                recipe.itemId ===
                current
        )
    ) {
        select.value =
            current;
    }

    $("recipeSuggestions")
        .innerHTML =
            recipes
                .slice(
                    0,
                    500
                )
                .map(
                    recipe =>
                        \`<option value="\${escapeHtml(
                            recipe.name
                        )}">\${escapeHtml(
                            recipe.itemId
                        )}</option>\`
                )
                .join("");
}

function renderRecipeAutocomplete(){

    const input =
        $("recipeSearch");

    const panel =
        $("recipeAutocomplete");

    const q =
        input.value.trim();

    if (
        document.activeElement !==
            input ||
        !q
    ) {
        panel.classList.remove(
            "open"
        );

        panel.innerHTML =
            "";

        return;
    }

    const matches =
        getFilteredRecipes(
            q,
            8
        );

    panel.innerHTML =
        matches.length

            ? matches
                .map(
                    recipe =>
                        \`
                        <button
                            class="autocomplete-item"
                            type="button"
                            data-recipe-id="\${escapeHtml(
                                recipe.itemId
                            )}"
                        >

                            <span>

                                <strong>
                                    \${escapeHtml(
                                        recipe.name
                                    )}
                                </strong>

                                <small>
                                    \${escapeHtml(
                                        recipe.itemId
                                    )}
                                    •
                                    \${recipe.ingredients.length}
                                    Zutaten
                                </small>

                            </span>

                            <span
                                class="autocomplete-tier"
                            >
                                \${escapeHtml(
                                    recipe.tier ||
                                    "RECIPE"
                                )}
                            </span>

                        </button>
                        \`
                )
                .join("")

            : '<div class="empty" style="padding:14px">Kein passendes Rezept gefunden.</div>';

    panel.classList.add(
        "open"
    );

    panel
        .querySelectorAll(
            ".autocomplete-item"
        )
        .forEach(
            button => {

                button.addEventListener(
                    "mousedown",
                    event =>
                        event.preventDefault()
                );

                button.addEventListener(
                    "click",
                    () =>
                        selectRecipe(
                            button.dataset
                                .recipeId
                        )
                );
            }
        );
}

function selectRecipe(
    itemId
){

    const recipe =
        appState.recipes.find(
            item =>
                item.itemId ===
                itemId
        );

    if (!recipe) {
        return;
    }

    $("recipeSearch")
        .value =
            recipe.name;

    $("recipeSelect")
        .value =
            recipe.itemId;

    $("recipeAutocomplete")
        .classList.remove(
            "open"
        );

    renderRecipes();
}

function clearRecipeSearch(){

    $("recipeSearch")
        .value =
            "";

    $("recipeSelect")
        .value =
            "";

    $("recipeAutocomplete")
        .classList.remove(
            "open"
        );

    renderRecipes();
}

function renderRecipes(){

    const recipes =
        getFilteredRecipes(
            $("recipeSearch").value,
            250
        );

    $("recipeList")
        .innerHTML =
            recipes.length

                ? recipes
                    .map(
                        recipe =>
                            \`
                            <div class="recipe">

                                <div>

                                    <strong>
                                        \${escapeHtml(
                                            recipe.name
                                        )}
                                    </strong>

                                    <div class="sub mono">
                                        \${escapeHtml(
                                            recipe.itemId
                                        )}
                                        •
                                        \${recipe.ingredients.length}
                                        Zutaten
                                    </div>

                                </div>

                                <div class="actions">

                                    <button
                                        class="ghost"
                                        onclick="selectRecipe('\${escapeHtml(
                                            recipe.itemId
                                        )}')"
                                    >
                                        Anzeigen
                                    </button>

                                    <button
                                        class="primary"
                                        onclick="addTarget('\${encodeURIComponent(
                                            recipe.itemId
                                        )}')"
                                    >
                                        + Ziel
                                    </button>

                                </div>

                            </div>
                            \`
                    )
                    .join("")

                : '<div class="empty">Kein API-Rezept gefunden.</div>';
}

async function addTarget(
    encodedId
){

    try {

        await request(
            "/api/targets",
            {
                method:
                    "POST",

                body:
                    JSON.stringify(
                        {
                            itemId:
                                decodeURIComponent(
                                    encodedId
                                )
                        }
                    )
            }
        );

        await load();

        toast(
            "Crafting-Ziel hinzugefügt."
        );

    } catch (
        error
    ) {

        toast(
            error.message,
            true
        );
    }
}

async function removeTarget(
    id
){

    if (
        !confirm(
            "Dieses Ziel wirklich entfernen?"
        )
    ) {
        return;
    }

    try {

        await request(
            "/api/targets/" +
                encodeURIComponent(
                    id
                ),
            {
                method:
                    "DELETE"
            }
        );

        await load();

        toast(
            "Ziel entfernt."
        );

    } catch (
        error
    ) {

        toast(
            error.message,
            true
        );
    }
}

// ============================================================
// TARGETS
// ============================================================

function renderTargets(){

    const targets =
        appState.targets;

    $("targets")
        .innerHTML =
            targets.length

                ? targets
                    .map(
                        target => {

                            const done =
                                target.items.filter(
                                    item =>
                                        item.done
                                ).length;

                            return \`
                                <div class="target">

                                    <div
                                        class="targettop"
                                    >

                                        <div>

                                            <strong>
                                                \${escapeHtml(
                                                    target.name
                                                )}
                                            </strong>

                                            <div
                                                class="sub mono"
                                            >
                                                \${escapeHtml(
                                                    target.itemId
                                                )}
                                            </div>

                                        </div>

                                        <button
                                            class="danger"
                                            onclick="removeTarget('\${escapeHtml(
                                                target.id
                                            )}')"
                                        >
                                            ×
                                        </button>

                                    </div>

                                    <div
                                        style="margin-top:10px;color:var(--muted)"
                                    >
                                        Fortschritt
                                        \${done}/\${target.items.length}
                                    </div>

                                    <ul>

                                        \${target.items
                                            .map(
                                                item =>
                                                    \`
                                                    <li
                                                        class="\${item.done ? "done" : ""}"
                                                    >
                                                        \${item.done ? "✓" : "○"}
                                                        \${Number(
                                                            item.count
                                                        ).toLocaleString(
                                                            "de-CH"
                                                        )}×
                                                        \${escapeHtml(
                                                            item.name
                                                        )}
                                                    </li>
                                                    \`
                                            )
                                            .join("")}

                                    </ul>

                                </div>
                            \`;
                        }
                    )
                    .join("")

                : '<div class="empty">Noch keine Ziele. Wähle links ein echtes API-Rezept.</div>';
}

// ============================================================
// FLIP WEB UI
// ============================================================

function renderFlipControls(){

    const select =
        $("flipGuildSelect");

    if (!select) {
        return;
    }

    const current =
        select.value;

    select.innerHTML =
        appState.guilds
            .map(
                guild =>
                    \`<option value="\${escapeHtml(
                        guild.id
                    )}">\${escapeHtml(
                        guild.name
                    )}</option>\`
            )
            .join("");

    if (
        appState.guilds.some(
            guild =>
                guild.id ===
                current
        )
    ) {
        select.value =
            current;
    }

    const guildId =
        select.value ||
        appState.guilds[0]
            ?.id;

    if (!guildId) {
        return;
    }

    if (!select.value) {
        select.value =
            guildId;
    }

    const guild =
        appState.guilds.find(
            item =>
                item.id ===
                guildId
        );

    const settings =
        guild?.flipSettings ||
        {};

    $("minProfit")
        .value =
            settings.minProfit ??
            1000;

    $("minMargin")
        .value =
            settings.minMargin ??
            1;

    $("minVolume")
        .value =
            settings.minVolume ??
            100;

    loadNpcPriceList(
        guildId
    );
}

async function saveFlipSettingsAndScan(){

    const guildId =
        $("flipGuildSelect")
            .value;

    if (!guildId) {
        return toast(
            "Kein Discord-Server ausgewählt.",
            true
        );
    }

    try {

        await request(
            "/api/guilds/" +
                guildId +
                "/flip-settings",
            {
                method:
                    "POST",

                body:
                    JSON.stringify(
                        {
                            minProfit:
                                Number(
                                    $("minProfit").value ||
                                    0
                                ),

                            minMargin:
                                Number(
                                    $("minMargin").value ||
                                    0
                                ),

                            minVolume:
                                Number(
                                    $("minVolume").value ||
                                    0
                                ),

                            maxResults:
                                15
                        }
                    )
            }
        );

        await runFlipScan();

        toast(
            "Flip-Filter gespeichert."
        );

    } catch (
        error
    ) {

        toast(
            error.message,
            true
        );
    }
}

async function runFlipScan() {
    const guildId = $("flipGuildSelect").value;
    const type = $("flipType").value;

    if (!guildId) {
        return;
    }

    $("flipResult").innerHTML =
        "⟳ Live Flip-Daten werden geladen …";

    try {
        const url =
            "/api/flips?guildId=" +
            encodeURIComponent(guildId) +
            "&type=" +
            encodeURIComponent(type);

        const data = await request(url);

        const rows =
            type === "npc"
                ? [
                    ...(data.npcToBazaar || []).map(flip => ({
                        ...flip,
                        mode: "NPC → Bazaar"
                    })),

                    ...(data.bazaarToNpc || []).map(flip => ({
                        ...flip,
                        mode: "Bazaar → NPC"
                    }))
                ]
                : (data.flips || []).map(flip => ({
                    ...flip,
                    mode: "Order → Offer"
                }));

        if (!rows.length) {
            $("flipResult").innerHTML =
                '<div class="empty">Keine Flips mit den aktuellen Filtern.</div>';

            return;
        }

        $("flipResult").innerHTML = rows.map(flip => {
            const profit =
                Number(flip.profit || 0)
                    .toLocaleString("de-CH");

            const margin =
                Number(flip.margin || 0)
                    .toFixed(2);

            const volume =
                Number(flip.volume || 0)
                    .toLocaleString("de-CH");

            return (
                '<div class="recipe">' +
                    '<div>' +
                        '<strong>' +
                            escapeHtml(flip.name) +
                        '</strong>' +

                        '<div class="sub mono">' +
                            escapeHtml(flip.id) +
                            ' • ' +
                            escapeHtml(flip.mode) +
                        '</div>' +
                    '</div>' +

                    '<div style="text-align:right">' +
                        '<strong>' +
                            '+' +
                            profit +
                            ' / Item' +
                        '</strong>' +

                        '<br>' +

                        '<span class="sub">' +
                            margin +
                            '% • ~' +
                            volume +
                            '/Tag' +
                        '</span>' +
                    '</div>' +
                '</div>'
            );
        }).join("");

    } catch (error) {
        $("flipResult").innerHTML =
            "⚠ " + escapeHtml(error.message);

        toast(
            error.message,
            true
        );
    }
}

async function saveNpcPrice(){

    const guildId =
        $("flipGuildSelect")
            .value;

    const id =
        $("npcPriceId")
            .value
            .trim();

    const price =
        Number(
            $("npcPriceValue")
                .value
        );

    const npc =
        $("npcPriceNpc")
            .value
            .trim() ||
        "NPC";

    if (
        !guildId ||
        !id ||
        !Number.isFinite(
            price
        ) ||
        price <= 0
    ) {
        return toast(
            "Item-ID und positiver NPC-Kaufpreis erforderlich.",
            true
        );
    }

    try {

        await request(
            "/api/guilds/" +
                guildId +
                "/npc-prices",
            {
                method:
                    "POST",

                body:
                    JSON.stringify(
                        {
                            id,

                            price,

                            npc
                        }
                    )
            }
        );

        $("npcPriceId")
            .value =
                "";

        $("npcPriceValue")
            .value =
                "";

        $("npcPriceNpc")
            .value =
                "";

        await loadNpcPriceList(
            guildId
        );

        await runFlipScan();

        toast(
            "NPC-Kaufpreis gespeichert."
        );

    } catch (
        error
    ) {

        toast(
            error.message,
            true
        );
    }
}

async function loadNpcPriceList(
    guildId
){

    const cached =
        appState
            .npcBuyPricesByGuild
            ?.[guildId];

    let prices =
        cached;

    if (!prices) {

        try {

            prices =
                (
                    await request(
                        "/api/guilds/" +
                            guildId +
                            "/npc-prices"
                    )
                ).prices;

        } catch {

            prices =
                {};
        }
    }

    prices =
        prices ||
        {};

    $("npcPriceList")
        .innerHTML =
            Object.entries(
                prices
            )
                .sort(
                    (
                        [a],
                        [b]
                    ) =>
                        a.localeCompare(
                            b
                        )
                )
                .map(
                    (
                        [
                            id,
                            value
                        ]
                    ) =>
                        \`
                        <span
                            style="display:inline-block;margin:4px 8px 4px 0;padding:5px 8px;border:1px solid var(--line);border-radius:9px"
                        >
                            \${escapeHtml(
                                id
                            )}:
                            \${Number(
                                value.price
                            ).toLocaleString(
                                "de-CH"
                            )}
                            (
                            \${escapeHtml(
                                value.npc ||
                                "NPC"
                            )}
                            )
                        </span>
                        \`
                )
                .join("") ||
            "Noch keine konfigurierten NPC-Kaufpreise.";
}

// ============================================================
// FEATURE SYNC
// ============================================================

async function syncFeatureChannels(
    guildId
){

    try {

        await request(
            "/api/guilds/" +
                guildId +
                "/features/sync",
            {
                method:
                    "POST",

                body:
                    "{}"
            }
        );

        await load();

        toast(
            "Funktionskanäle synchronisiert."
        );

    } catch (
        error
    ) {

        toast(
            error.message,
            true
        );
    }
}

// ============================================================
// GUILD / CHANNEL UI
// ============================================================

function channelIcon(
    channel
){
    return channel.typeName ===
        "category"
            ? "⌁"
            : "◈";
}

function renderGuilds(){

    $("guilds")
        .innerHTML =
            appState.guilds.length

                ? appState.guilds
                    .map(
                        guild => {

                            const categories =
                                guild.channels.filter(
                                    channel =>
                                        channel.typeName ===
                                        "category"
                                );

                            const channels =
                                guild.channels.filter(
                                    channel =>
                                        channel.typeName !==
                                        "category"
                                );

                            const roots =
                                channels.filter(
                                    channel =>
                                        !channel.parentId
                                );

                            const children =
                                new Map(
                                    categories.map(
                                        category =>
                                            [
                                                category.id,
                                                []
                                            ]
                                    )
                                );

                            channels.forEach(
                                channel => {

                                    if (
                                        children.has(
                                            channel.parentId
                                        )
                                    ) {

                                        children.get(
                                            channel.parentId
                                        ).push(
                                            channel
                                        );
                                    }
                                }
                            );

                            return \`
                            <div class="guild">

                                <div
                                    class="guildtop"
                                >

                                    <div
                                        class="guildname"
                                    >

                                        ◉
                                        \${escapeHtml(
                                            guild.name
                                        )}

                                        <span
                                            class="sub"
                                        >
                                            (
                                            \${guild.channels.length}
                                            Channels
                                            )
                                        </span>

                                    </div>

                                    <div
                                        class="actions"
                                    >

                                        <button
                                            class="ghost"
                                            onclick="syncFeatureChannels('\${guild.id}')"
                                        >
                                            ⚡ Funktionskanäle sync
                                        </button>

                                        <button
                                            class="ghost"
                                            onclick="createChannel('\${guild.id}','channel')"
                                        >
                                            + Kanal
                                        </button>

                                        <button
                                            class="ghost"
                                            onclick="createChannel('\${guild.id}','category')"
                                        >
                                            + Kategorie
                                        </button>

                                    </div>

                                </div>

                                <div
                                    class="api-box"
                                    style="margin-bottom:12px"
                                >

                                    <strong>
                                        Funktionskanäle
                                    </strong>

                                    <div
                                        class="feature-mini-grid"
                                    >

                                        \${guild.features
                                            .map(
                                                feature =>
                                                    \`
                                                    <span>

                                                        <b>
                                                            \${escapeHtml(
                                                                feature.categoryName
                                                            )}
                                                        </b>

                                                        →
                                                        #

                                                        \${escapeHtml(
                                                            guild.channels.find(
                                                                channel =>
                                                                    channel.id ===
                                                                    feature.channelId
                                                            )?.name ||
                                                            "nicht angelegt"
                                                        )}

                                                    </span>
                                                    \`
                                            )
                                            .join("")}

                                    </div>

                                </div>

                                <div
                                    class="toolbar"
                                >

                                    <select
                                        onchange="setDoneChannel('\${guild.id}',this.value)"
                                    >

                                        <option value="">
                                            — Kein Erledigt-Kanal —
                                        </option>

                                        \${channels
                                            .map(
                                                channel =>
                                                    \`
                                                    <option
                                                        value="\${channel.id}"
                                                        \${guild.isDoneChannel === channel.id ? "selected" : ""}
                                                    >
                                                        #
                                                        \${escapeHtml(
                                                            channel.name
                                                        )}
                                                    </option>
                                                    \`
                                            )
                                            .join("")}

                                    </select>

                                    <span
                                        class="api-box"
                                    >
                                        Drag:
                                        <b>sortieren</b>
                                        •
                                        auf Kategorie ziehen:
                                        <b>verschieben</b>
                                        •
                                        ✎
                                        <b>umbenennen</b>
                                    </span>

                                </div>

                                <div
                                    class="channel-board"
                                >

                                    <div
                                        class="channel-group"
                                        data-guild="\${guild.id}"
                                        data-parent=""
                                    >

                                        <div
                                            class="group-title"
                                        >
                                            ROOT / ohne Kategorie
                                        </div>

                                        <div
                                            class="dropzone"
                                            data-parent=""
                                        >
                                            Hierher ziehen
                                        </div>

                                        \${roots
                                            .map(
                                                channel =>
                                                    channelHtml(
                                                        guild,
                                                        channel
                                                    )
                                            )
                                            .join("")}

                                    </div>

                                    \${categories
                                        .map(
                                            category =>
                                                \`
                                                <div
                                                    class="channel-group"
                                                    data-guild="\${guild.id}"
                                                    data-parent="\${category.id}"
                                                >

                                                    <div
                                                        class="group-title"
                                                    >
                                                        ⌁
                                                        \${escapeHtml(
                                                            category.name
                                                        )}
                                                    </div>

                                                    <div
                                                        class="dropzone"
                                                        data-parent="\${category.id}"
                                                    >
                                                        Kanal hier ablegen
                                                    </div>

                                                    \${(
                                                        children.get(
                                                            category.id
                                                        ) ||
                                                        []
                                                    )
                                                        .map(
                                                            channel =>
                                                                channelHtml(
                                                                    guild,
                                                                    channel
                                                                )
                                                        )
                                                        .join("")}

                                                    <div
                                                        class="channel"
                                                        style="opacity:.65;cursor:default"
                                                    >

                                                        <span
                                                            class="handle"
                                                        >
                                                            ⌁
                                                        </span>

                                                        <span
                                                            class="channelname"
                                                        >
                                                            \${escapeHtml(
                                                                category.name
                                                            )}
                                                        </span>

                                                        <button
                                                            class="ghost"
                                                            onclick="renameChannel('\${guild.id}','\${category.id}','\${escapeHtml(
                                                                category.name
                                                            ).replace(
                                                                /'/g,
                                                                "\\\\'"
                                                            )}')"
                                                        >
                                                            ✎
                                                        </button>

                                                        <button
                                                            class="danger"
                                                            onclick="deleteChannel('\${guild.id}','\${category.id}')"
                                                        >
                                                            ×
                                                        </button>

                                                    </div>

                                                </div>
                                                \`
                                        )
                                        .join("")}

                                </div>

                            </div>
                            \`;
                        }
                    )
                    .join("")

                : '<div class="empty">Der Bot ist noch auf keinem Discord-Server.</div>';

    bindDragDrop();
}

function channelHtml(
    guild,
    channel
){

    return \`
        <div
            class="channel"
            draggable="true"
            data-id="\${channel.id}"
            data-guild="\${guild.id}"
        >

            <span
                class="handle"
            >
                ⠿
            </span>

            <span
                class="channelname"
            >
                \${channelIcon(
                    channel
                )}

                \${escapeHtml(
                    channel.name
                )}
            </span>

            <small>
                #
            </small>

            <button
                class="ghost"
                onclick="renameChannel('\${guild.id}','\${channel.id}','\${escapeHtml(
                    channel.name
                ).replace(
                    /'/g,
                    "\\\\'"
                )}')"
            >
                ✎
            </button>

            <button
                class="ghost"
                onclick="purgeChannel('\${guild.id}','\${channel.id}')"
            >
                ⌫
            </button>

            <button
                class="danger"
                onclick="deleteChannel('\${guild.id}','\${channel.id}')"
            >
                ×
            </button>

        </div>
    \`;
}

// ============================================================
// DRAG & DROP
// ============================================================

function bindDragDrop(){

    document
        .querySelectorAll(
            ".channel[draggable=true]"
        )
        .forEach(
            element => {

                element.addEventListener(
                    "dragstart",
                    event => {

                        dragChannel =
                            {
                                id:
                                    element.dataset.id,

                                guild:
                                    element.dataset.guild
                            };

                        element.classList.add(
                            "dragging"
                        );

                        event.dataTransfer
                            .effectAllowed =
                                "move";
                    }
                );

                element.addEventListener(
                    "dragend",
                    () => {

                        element.classList.remove(
                            "dragging"
                        );

                        dragChannel =
                            null;

                        document
                            .querySelectorAll(
                                ".dropzone"
                            )
                            .forEach(
                                zone =>
                                    zone.classList.remove(
                                        "over"
                                    )
                            );
                    }
                );
            }
        );

    document
        .querySelectorAll(
            ".dropzone"
        )
        .forEach(
            zone => {

                zone.addEventListener(
                    "dragover",
                    event => {

                        event.preventDefault();

                        zone.classList.add(
                            "over"
                        );
                    }
                );

                zone.addEventListener(
                    "dragleave",
                    () =>
                        zone.classList.remove(
                            "over"
                        )
                );

                zone.addEventListener(
                    "drop",
                    async event => {

                        event.preventDefault();

                        zone.classList.remove(
                            "over"
                        );

                        if (
                            !dragChannel
                        ) {
                            return;
                        }

                        try {

                            await request(
                                "/api/guilds/" +
                                dragChannel.guild +
                                "/channels/" +
                                dragChannel.id +
                                "/move",
                                {
                                    method:
                                        "POST",

                                    body:
                                        JSON.stringify(
                                            {
                                                parentId:
                                                    zone.dataset
                                                        .parent ||
                                                    null
                                            }
                                        )
                                }
                            );

                            await load();

                            toast(
                                "Kanal verschoben."
                            );

                        } catch (
                            error
                        ) {

                            toast(
                                error.message,
                                true
                            );
                        }
                    }
                );
            }
        );

    document
        .querySelectorAll(
            ".channel-group"
        )
        .forEach(
            group => {

                group.addEventListener(
                    "dragover",
                    event =>
                        event.preventDefault()
                );

                group.addEventListener(
                    "drop",
                    async event => {

                        if (
                            !dragChannel
                        ) {
                            return;
                        }

                        event.preventDefault();

                        const ids =
                            [
                                ...group.querySelectorAll(
                                    ".channel[data-id]"
                                )
                            ].map(
                                element =>
                                    element.dataset.id
                            );

                        if (
                            !ids.includes(
                                dragChannel.id
                            )
                        ) {
                            ids.push(
                                dragChannel.id
                            );
                        }

                        try {

                            await request(
                                "/api/guilds/" +
                                dragChannel.guild +
                                "/channels/reorder",
                                {
                                    method:
                                        "POST",

                                    body:
                                        JSON.stringify(
                                            {
                                                channelIds:
                                                    ids
                                            }
                                        )
                                }
                            );

                            await load();

                            toast(
                                "Channel-Reihenfolge synchronisiert."
                            );

                        } catch (
                            error
                        ) {

                            toast(
                                error.message,
                                true
                            );
                        }
                    }
                );
            }
        );
}

// ============================================================
// CHANNEL CREATE
// ============================================================

async function createChannel(
    guildId,
    type
){

    const name =
        prompt(
            type ===
                "category"

                ? "Name der Kategorie:"

                : "Name des Kanals:"
        );

    if (!name) {
        return;
    }

    try {

        await request(
            "/api/guilds/" +
                guildId +
                "/channels",
            {
                method:
                    "POST",

                body:
                    JSON.stringify(
                        {
                            name,

                            type
                        }
                    )
            }
        );

        await load();

        toast(
            type ===
                "category"

                ? "Kategorie erstellt."

                : "Kanal erstellt."
        );

    } catch (
        error
    ) {

        toast(
            error.message,
            true
        );
    }
}

// ============================================================
// CHANNEL RENAME
// ============================================================

async function renameChannel(
    guildId,
    channelId,
    currentName
){

    const name =
        prompt(
            "Neuer Name:",
            currentName ||
            ""
        );

    if (
        !name ||
        name.trim() ===
            currentName
    ) {
        return;
    }

    try {

        await request(
            "/api/guilds/" +
            guildId +
            "/channels/" +
            channelId,
            {
                method:
                    "PATCH",

                body:
                    JSON.stringify(
                        {
                            name:
                                name.trim()
                        }
                    )
            }
        );

        await load();

        toast(
            "Kanal umbenannt."
        );

    } catch (
        error
    ) {

        toast(
            error.message,
            true
        );
    }
}

// ============================================================
// CHANNEL DELETE
// ============================================================

async function deleteChannel(
    guildId,
    channelId
){

    if (
        !confirm(
            "Discord-Kanal wirklich löschen?"
        )
    ) {
        return;
    }

    try {

        await request(
            "/api/guilds/" +
            guildId +
            "/channels/" +
            channelId,
            {
                method:
                    "DELETE"
            }
        );

        await load();

        toast(
            "Kanal gelöscht."
        );

    } catch (
        error
    ) {

        toast(
            error.message,
            true
        );
    }
}

// ============================================================
// PURGE
// ============================================================

async function purgeChannel(
    guildId,
    channelId
){

    const amount =
        prompt(
            "Wie viele Nachrichten sollen gelöscht werden? (1–100)",
            "20"
        );

    if (!amount) {
        return;
    }

    try {

        const result =
            await request(
                "/api/guilds/" +
                guildId +
                "/channels/" +
                channelId +
                "/purge",
                {
                    method:
                        "POST",

                    body:
                        JSON.stringify(
                            {
                                amount:
                                    Number(
                                        amount
                                    )
                            }
                        )
                }
            );

        toast(
            result.deleted +
            " Nachrichten gelöscht."
        );

    } catch (
        error
    ) {

        toast(
            error.message,
            true
        );
    }
}

// ============================================================
// DONE CHANNEL
// ============================================================

async function setDoneChannel(
    guildId,
    channelId
){

    try {

        await request(
            "/api/guilds/" +
            guildId +
            "/done-channel",
            {
                method:
                    "POST",

                body:
                    JSON.stringify(
                        {
                            channelId
                        }
                    )
            }
        );

        toast(
            "Erledigt-Kanal gespeichert."
        );

    } catch (
        error
    ) {

        toast(
            error.message,
            true
        );
    }
}

// ============================================================
// PLAYER SEARCH
// ============================================================

async function lookupPlayer(){

    const user =
        $("playerSearch")
            .value
            .trim();

    const profile =
        $("profileSearch")
            .value
            .trim();

    if (!user) {

        toast(
            "Minecraft Name oder UUID eingeben.",
            true
        );

        return;
    }

    $("playerResult")
        .innerHTML =
            "⟳ SkyHelper-Daten werden geladen …";

    try {

        const query =
            profile

                ? "?profile=" +
                  encodeURIComponent(
                      profile
                  )

                : "";

        const data =
            await request(
                "/api/skyhelper/player/" +
                encodeURIComponent(
                    user
                ) +
                query
            );

        const nw =
            data.networth?.networth ??
            data.networth?.totalNetworth ??
            data.networth?.value ??
            null;

        const member =
            data.member ||
            {};

        const stats =
            data.stats ||
            {};

        const skillKeys =
            Object.keys(
                stats.skills ||
                {}
            ).slice(
                0,
                8
            );

        $("playerResult")
            .innerHTML =
                \`
                <div
                    style="display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:10px"
                >

                    <div class="stat">

                        <b>
                            \${nw == null
                                ? "—"
                                : Number(
                                    nw
                                ).toLocaleString(
                                    "de-CH"
                                )}
                        </b>

                        <span>
                            Networth
                        </span>

                    </div>

                    <div class="stat">

                        <b>
                            \${Number(
                                data.bankBalance ||
                                0
                            ).toLocaleString(
                                "de-CH"
                            )}
                        </b>

                        <span>
                            Bank
                        </span>

                    </div>

                    <div class="stat">

                        <b>
                            \${escapeHtml(
                                member
                                    ?.leveling
                                    ?.experience ??
                                "—"
                            )}
                        </b>

                        <span>
                            Level-XP
                        </span>

                    </div>

                    <div class="stat">

                        <b>
                            \${skillKeys.length}
                        </b>

                        <span>
                            Skill-Daten
                        </span>

                    </div>

                </div>

                <div
                    style="margin-top:12px"
                >

                    <strong>
                        \${escapeHtml(
                            user
                        )}
                    </strong>

                    <span class="sub">
                        • SkyHelper API:
                        \${escapeHtml(
                            SKYHELPER_API_LABEL
                        )}
                    </span>

                </div>
                \`;

    } catch (
        error
    ) {

        $("playerResult")
            .innerHTML =
                "⚠ " +
                escapeHtml(
                    error.message
                );

        toast(
            error.message,
            true
        );
    }
}

// ============================================================
// BAZAAR SEARCH
// ============================================================

async function lookupBazaar(){

    const query =
        $("bazaarSearch")
            .value
            .trim();

    if (!query) {

        toast(
            "Item eingeben.",
            true
        );

        return;
    }

    $("bazaarResult")
        .innerHTML =
            "⟳ Bazaar wird abgefragt …";

    try {

        const data =
            await request(
                "/api/skyhelper/bazaar?q=" +
                encodeURIComponent(
                    query
                )
            );

        if (
            !data.products.length
        ) {

            $("bazaarResult")
                .innerHTML =
                    "Kein Bazaar-Produkt gefunden.";

            return;
        }

        $("bazaarResult")
            .innerHTML =
                data.products
                    .map(
                        product =>
                            \`
                            <div class="recipe">

                                <div>

                                    <strong>
                                        \${escapeHtml(
                                            product.name
                                        )}
                                    </strong>

                                    <div
                                        class="sub mono"
                                    >
                                        \${escapeHtml(
                                            product.id
                                        )}
                                    </div>

                                </div>

                                <div
                                    style="text-align:right"
                                >

                                    <div>
                                        Buy:
                                        <strong>
                                            \${product.buyPrice == null
                                                ? "—"
                                                : Number(
                                                    product.buyPrice
                                                ).toLocaleString(
                                                    "de-CH"
                                                )}
                                        </strong>
                                    </div>

                                    <div>
                                        Sell:
                                        <strong>
                                            \${product.sellPrice == null
                                                ? "—"
                                                : Number(
                                                    product.sellPrice
                                                ).toLocaleString(
                                                    "de-CH"
                                                )}
                                        </strong>
                                    </div>

                                    <div class="sub">
                                        Order:
                                        \${product.bestBuyOrder == null
                                            ? "—"
                                            : Number(
                                                product.bestBuyOrder
                                            ).toLocaleString(
                                                "de-CH"
                                            )}

                                        •

                                        Offer:
                                        \${product.bestSellOffer == null
                                            ? "—"
                                            : Number(
                                                product.bestSellOffer
                                            ).toLocaleString(
                                                "de-CH"
                                            )}
                                    </div>

                                </div>

                            </div>
                            \`
                    )
                    .join("");
    } catch (
        error
    ) {

        $("bazaarResult")
            .innerHTML =
                "⚠ " +
                escapeHtml(
                    error.message
                );

        toast(
            error.message,
            true
        );
    }
}

// ============================================================
// CONSTANT
// ============================================================

const SKYHELPER_API_LABEL =
    "api.altpapier.dev";

// ============================================================
// RECIPE INPUT EVENTS
// ============================================================

$("recipeSearch")
    .addEventListener(
        "input",
        () => {

            renderRecipes();

            renderRecipeAutocomplete();
        }
    );

$("recipeSearch")
    .addEventListener(
        "focus",
        renderRecipeAutocomplete
    );

$("recipeSearch")
    .addEventListener(
        "keydown",
        event => {

            const panel =
                $("recipeAutocomplete");

            const items =
                [
                    ...panel.querySelectorAll(
                        ".autocomplete-item"
                    )
                ];

            if (
                !panel.classList.contains(
                    "open"
                ) ||
                !items.length
            ) {
                return;
            }

            const active =
                panel.querySelector(
                    ".autocomplete-item.active"
                );

            let index =
                items.indexOf(
                    active
                );

            if (
                event.key ===
                "ArrowDown"
            ) {

                event.preventDefault();

                index =
                    (
                        index +
                        1
                    ) %
                    items.length;

            } else if (
                event.key ===
                "ArrowUp"
            ) {

                event.preventDefault();

                index =
                    index <=
                        0

                        ? items.length -
                          1

                        : index -
                          1;

            } else if (
                event.key ===
                "Enter"
            ) {

                event.preventDefault();

                (
                    active ||
                    items[0]
                ).click();

                return;

            } else if (
                event.key ===
                "Escape"
            ) {

                panel.classList.remove(
                    "open"
                );

                return;

            } else {

                return;
            }

            items.forEach(
                item =>
                    item.classList.remove(
                        "active"
                    )
            );

            items[
                index
            ].classList.add(
                "active"
            );

            items[
                index
            ].scrollIntoView(
                {
                    block:
                        "nearest"
                }
            );
        }
    );

$("recipeSelect")
    .addEventListener(
        "change",
        event =>
            event.target.value
                ? selectRecipe(
                    event.target.value
                )
                : clearRecipeSearch()
    );

document.addEventListener(
    "click",
    event => {

        if (
            !event.target.closest(
                ".recipe-search-wrap"
            )
        ) {

            $("recipeAutocomplete")
                .classList.remove(
                    "open"
                );
        }
    }
);

// ============================================================
// FLIP EVENTS
// ============================================================

$("flipGuildSelect")
    .addEventListener(
        "change",
        async () => {

            renderFlipControls();

            await runFlipScan();
        }
    );

$("flipType")
    .addEventListener(
        "change",
        runFlipScan
    );

// ============================================================
// PLAYER ENTER SHORTCUT
// ============================================================

$("playerSearch")
    .addEventListener(
        "keydown",
        event => {

            if (
                event.key ===
                "Enter"
            ) {

                lookupPlayer();
            }
        }
    );

$("bazaarSearch")
    .addEventListener(
        "keydown",
        event => {

            if (
                event.key ===
                "Enter"
            ) {

                lookupBazaar();
            }
        }
    );

// ============================================================
// INITIAL LOAD
// ============================================================

load();

setInterval(
    load,
    15_000
);

</script>

</body>

</html>`);
    }
);

// ============================================================
// START SERVER
// ============================================================

app.listen(
    PORT,
    () => {

        console.log(
            `[DASHBOARD] http://localhost:${PORT}`
        );
    }
);

// ============================================================
// LOGIN BOT
// ============================================================

if (
    !process.env.DISCORD_TOKEN
) {

    console.error(
        "[BOT] DISCORD_TOKEN fehlt in .env"
    );

} else {

    client.login(
        process.env.DISCORD_TOKEN
    ).catch(
        error => {

            console.error(
                "[BOT] Login fehlgeschlagen:",
                error.message
            );
        }
    );
}