package de.coinshop;

import de.coinshop.SecretAchievements.Secret;
import org.bukkit.Material;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/** Die Auslöser der geheimen Erfolge. (Verkäufe und Fledermaus-Sichtung stehen in SecretAchievements.) */
public final class SecretListener implements Listener {

    private final CoinShopPlugin plugin;

    public SecretListener(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** Blumentopf platziert. */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == Material.FLOWER_POT) {
            plugin.secrets().unlock(event.getPlayer(), Secret.FLOWER_POT);
        }
    }

    /** Phantom / Fledermaus / Babyzombie / Schwein getötet. */
    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        EntityType type = event.getEntityType();
        if (type == EntityType.PHANTOM) {
            plugin.secrets().unlock(killer, Secret.PHANTOM);
        } else if (type == EntityType.BAT) {
            plugin.secrets().unlock(killer, Secret.BAT);
        } else if (type == EntityType.PIG) {
            plugin.secrets().unlock(killer, Secret.PIG);
        }
        if (event.getEntity() instanceof Zombie zombie && zombie.isBaby()) {
            plugin.secrets().unlock(killer, Secret.BABY_ZOMBIE);
        }
    }

    /** Einen anderen Spieler mit einem Ei getroffen. */
    @EventHandler
    public void onEggHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Egg egg
                && event.getHitEntity() instanceof Player hit
                && egg.getShooter() instanceof Player shooter
                && !shooter.getUniqueId().equals(hit.getUniqueId())) {
            plugin.secrets().unlock(shooter, Secret.EGG);
        }
    }

    /** Eine Lohenrute aufgesammelt. */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getItem().getItemStack().getType() == Material.BLAZE_ROD) {
            plugin.secrets().unlock(player, Secret.BLAZE_ROD);
        }
    }

    /** Versucht, mit einem Baby-Villager zu handeln (Rechtsklick). */
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getRightClicked() instanceof Villager villager && !villager.isAdult()) {
            plugin.secrets().unlock(event.getPlayer(), Secret.BABY_VILLAGER);
        }
    }

    /** Einen "Komischen Trank" aus dem Braustand genommen. */
    @EventHandler(ignoreCancelled = true)
    public void onBrewTake(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.BREWING
                || event.getClickedInventory() != event.getInventory()
                || event.getRawSlot() > 2
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item != null && item.getType() == Material.POTION
                && item.getItemMeta() instanceof PotionMeta meta
                && meta.getBasePotionType() == PotionType.AWKWARD) {
            plugin.secrets().unlock(player, Secret.AWKWARD_POTION);
        }
    }
}
