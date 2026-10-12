package de.coinshop;

import de.coinshop.Database.RankEntry;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Die Ranglisten.
 *
 * Die Zahlen stammen aus den normalen Minecraft-Statistiken der Spieler (dieselben wie unter
 * "Statistiken" im Spielmenü). Das Plugin liest sie regelmäßig für alle Online-Spieler (und beim
 * Verlassen des Servers) und speichert sie in der Datenbank - so gibt es auch Ranglisten für
 * Spieler, die gerade offline sind.
 *
 * Die ersten 5 Plätze jeder Rangliste bekommen Bonus-Einkommen (Standard: 10, 5, 3, 2, 1 Coins pro Tag).
 */
public final class RankingManager {

    /** Alle Ranglisten. "stat" ist der Name in der Datenbank, "ascending" = wenigster Wert gewinnt. */
    public enum Rank {
        DIAMONDS("diamonds", Material.DIAMOND_ORE, "diamonds", false),
        DEBRIS("debris", Material.ANCIENT_DEBRIS, "debris", false),
        WOOD("wood", Material.OAK_LOG, "wood", false),
        STONE("stone", Material.COBBLESTONE, "stone", false),
        FISHING("fishing", Material.FISHING_ROD, "fishing", false),
        MOBS("mobs", Material.IRON_SWORD, "mobs", false),
        DEATHS_LEAST("deaths-least", Material.TOTEM_OF_UNDYING, "deaths", true),
        DEATHS_MOST("deaths-most", Material.SKELETON_SKULL, "deaths", false),
        TRADES("trades", Material.EMERALD, "trades", false),
        WALKED("walked", Material.LEATHER_BOOTS, "walked", false),
        BOSSES("bosses", Material.DRAGON_HEAD, "bosses", false),
        PLACED("placed", Material.BRICKS, "placed", false),
        MINED("mined", Material.DIAMOND_PICKAXE, "mined", false);

        private final String id;
        private final Material icon;
        private final String stat;
        private final boolean ascending;

        Rank(String id, Material icon, String stat, boolean ascending) {
            this.id = id;
            this.icon = icon;
            this.stat = stat;
            this.ascending = ascending;
        }

        public String id() {
            return id;
        }

        public Material icon() {
            return icon;
        }

        public String stat() {
            return stat;
        }

        public boolean ascending() {
            return ascending;
        }
    }

    // ---- Welche Blöcke zählen zu "Holz" und "Stein"? ----
    private static final List<Material> WOOD_BLOCKS = new ArrayList<>();
    private static final List<Material> STONE_BLOCKS = new ArrayList<>();
    private static final List<Material> PLACEABLE_BLOCKS = new ArrayList<>();
    private static final List<Material> ALL_BLOCKS = new ArrayList<>();

    static {
        List<String> stoneNames = List.of("STONE", "COBBLESTONE", "MOSSY_COBBLESTONE", "DEEPSLATE",
                "COBBLED_DEEPSLATE", "GRANITE", "DIORITE", "ANDESITE", "TUFF", "CALCITE", "BLACKSTONE",
                "BASALT", "SMOOTH_BASALT", "END_STONE", "NETHERRACK", "DRIPSTONE_BLOCK");
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isBlock()) {
                continue;
            }
            ALL_BLOCKS.add(m);
            String n = m.name();
            // Holz: alle Stämme/Stiele und ihre "Holz"-Varianten (auch entrindet), dazu Bambusblöcke
            if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE")
                    || n.equals("BAMBOO_BLOCK") || n.equals("STRIPPED_BAMBOO_BLOCK")) {
                WOOD_BLOCKS.add(m);
            }
            if (stoneNames.contains(n)) {
                STONE_BLOCKS.add(m);
            }
            if (m.isItem() && !m.isAir()) {
                PLACEABLE_BLOCKS.add(m);
            }
        }
    }

    private final CoinShopPlugin plugin;

    public RankingManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Einstellungen
    // ------------------------------------------------------------------

    /** Bonus pro Platz (Platz 1 zuerst). */
    public List<Long> bonuses() {
        List<Long> list = new ArrayList<>();
        for (long v : plugin.getConfig().getLongList("rankings.bonus")) {
            list.add(Math.max(0, v));
        }
        if (list.isEmpty()) {
            list.addAll(List.of(10L, 5L, 3L, 2L, 1L));
        }
        return list;
    }

    public long minPlaytimeMinutes() {
        return Math.max(0, plugin.getConfig().getLong("rankings.min-playtime-minutes", 120));
    }

    public int syncSeconds() {
        return Math.max(30, plugin.getConfig().getInt("rankings.sync-seconds", 120));
    }

    // ------------------------------------------------------------------
    //  Statistiken einlesen
    // ------------------------------------------------------------------

    private static long mined(Player p, List<Material> materials) {
        long sum = 0;
        for (Material m : materials) {
            try {
                sum += p.getStatistic(Statistic.MINE_BLOCK, m);
            } catch (IllegalArgumentException ignored) {
                // Material hat keine Statistik - überspringen
            }
        }
        return sum;
    }

    private static long killed(Player p, EntityType type) {
        try {
            return p.getStatistic(Statistic.KILL_ENTITY, type);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    /** Liest die Statistiken eines Online-Spielers und speichert sie in der Datenbank. */
    public void sync(Player p) {
        try {
            Map<String, Long> values = new LinkedHashMap<>();
            values.put("diamonds", mined(p, List.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE)));
            values.put("debris", mined(p, List.of(Material.ANCIENT_DEBRIS)));
            values.put("wood", mined(p, WOOD_BLOCKS));
            values.put("stone", mined(p, STONE_BLOCKS));
            values.put("mined", mined(p, ALL_BLOCKS));
            values.put("fishing", (long) p.getStatistic(Statistic.FISH_CAUGHT));
            values.put("mobs", (long) p.getStatistic(Statistic.MOB_KILLS));
            values.put("deaths", (long) p.getStatistic(Statistic.DEATHS));
            values.put("trades", (long) p.getStatistic(Statistic.TRADED_WITH_VILLAGER));
            long cm = (long) p.getStatistic(Statistic.WALK_ONE_CM)
                    + p.getStatistic(Statistic.SPRINT_ONE_CM)
                    + p.getStatistic(Statistic.CROUCH_ONE_CM);
            values.put("walked", cm / 100);
            values.put("bosses", killed(p, EntityType.WARDEN) + killed(p, EntityType.ENDER_DRAGON)
                    + killed(p, EntityType.ELDER_GUARDIAN) + killed(p, EntityType.WITHER));

            long placed = 0;
            for (Material m : PLACEABLE_BLOCKS) {
                try {
                    placed += p.getStatistic(Statistic.USE_ITEM, m);
                } catch (IllegalArgumentException ignored) {
                    // kein benutzbares Item - überspringen
                }
            }
            values.put("placed", placed);

            // Spielzeit in Minuten (Minecraft zählt in Ticks: 20 pro Sekunde)
            values.put("playtime", (long) p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60);

            plugin.database().ensurePlayer(p.getUniqueId(), p.getName());
            plugin.database().setStats(p.getUniqueId(), values);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Statistiken von " + p.getName() + " konnten nicht gespeichert werden: " + e);
        }
    }

    public void syncOnline() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            sync(p);
        }
    }

    // ------------------------------------------------------------------
    //  Ranglisten abfragen
    // ------------------------------------------------------------------

    public List<RankEntry> top(Rank rank, int limit) {
        if (rank == Rank.DEATHS_LEAST) {
            return plugin.database().fewestDeaths(minPlaytimeMinutes(), limit);
        }
        return plugin.database().topStat(rank.stat(), limit);
    }

    /** Platz des Spielers (1 = Erster) oder 0, wenn er nicht (unter den ersten 200) gelistet ist. */
    public int placeOf(Rank rank, UUID uuid) {
        List<RankEntry> list = top(rank, 200);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid().equals(uuid)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Bonus, den ein Platz (1 = Erster) bringt. */
    public long bonusForPlace(int place) {
        List<Long> b = bonuses();
        return place >= 1 && place <= b.size() ? b.get(place - 1) : 0;
    }

    /** Wie viel Bonus-Einkommen jeder Spieler aus allen Ranglisten zusammen bekommt. */
    public Map<UUID, Long> dailyBonuses() {
        Map<UUID, Long> result = new HashMap<>();
        int places = bonuses().size();
        for (Rank rank : Rank.values()) {
            List<RankEntry> list = top(rank, places);
            for (int i = 0; i < list.size(); i++) {
                long bonus = bonusForPlace(i + 1);
                if (bonus > 0) {
                    result.merge(list.get(i).uuid(), bonus, Long::sum);
                }
            }
        }
        return result;
    }
}
