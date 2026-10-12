package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database.RankEntry;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.RankingManager;
import de.coinshop.RankingManager.Rank;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Die Top 10 einer Rangliste. */
public final class RankingDetailMenu extends Menu {

    private static final int[] SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    public RankingDetailMenu(CoinShopPlugin plugin, Player viewer, Rank rank) {
        super(plugin, 4, plugin.messages().get("gui.rank.detail-title",
                "name", plugin.messages().get("gui.rank.cat." + rank.id() + ".name")));
        Messages msg = plugin.messages();
        RankingManager rankings = plugin.rankings();
        UUID uuid = viewer.getUniqueId();
        Component unit = msg.get("gui.rank.cat." + rank.id() + ".unit");

        List<RankEntry> top = rankings.top(rank, 200);

        // Oben Mitte: Beschreibung + dein eigener Platz
        List<Component> infoLore = new ArrayList<>(msg.getList("gui.rank.cat." + rank.id() + ".desc"));
        infoLore.add(Component.empty());
        int own = 0;
        long ownValue = 0;
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).uuid().equals(uuid)) {
                own = i + 1;
                ownValue = top.get(i).value();
            }
        }
        if (own > 0) {
            infoLore.add(msg.get("gui.rank.own-lore", "place", own, "value", ownValue, "unit", unit));
        } else {
            infoLore.add(msg.get("gui.rank.not-listed"));
        }
        set(4, ItemUtil.icon(rank.icon(), msg.get("gui.rank.cat-name",
                "name", msg.get("gui.rank.cat." + rank.id() + ".name")), infoLore), null);

        if (top.isEmpty()) {
            set(13, ItemUtil.icon(Material.BARRIER, msg.get("gui.rank.empty"), List.of()), null);
        }
        for (int i = 0; i < top.size() && i < SLOTS.length; i++) {
            RankEntry entry = top.get(i);
            int place = i + 1;
            Material material = switch (place) {
                case 1 -> Material.GOLD_INGOT;
                case 2 -> Material.IRON_INGOT;
                case 3 -> Material.COPPER_INGOT;
                default -> Material.PAPER;
            };
            List<Component> lore = new ArrayList<>();
            lore.add(msg.get("gui.rank.entry-value", "value", entry.value(), "unit", unit));
            long bonus = rankings.bonusForPlace(place);
            if (bonus > 0) {
                lore.add(msg.get("gui.rank.entry-bonus", "bonus", bonus));
            }
            ItemStack icon = ItemUtil.icon(material, msg.get("gui.rank.entry-name", "place", place,
                    "name", entry.name()), lore);
            icon.setAmount(place);
            set(SLOTS[i], icon, null);
        }

        set(29, balanceIcon(viewer), null);
        set(31, backButton(), (player, click) -> new RankingMenu(plugin, player).open(player));

        fillEmpty();
    }
}
