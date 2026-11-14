package dev.che1s.betadrop;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public final class BlockBreakListener implements Listener {

    private final BetaDropPlugin plugin;
    private final RuleEngine engine;

    public BlockBreakListener(BetaDropPlugin plugin, RuleEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @EventHandler(priority = Event.Priority.Highest, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getItemInHand();
        Material hand = itemInHand != null ? itemInHand.getType() : Material.AIR;
        int handData = itemInHand != null ? itemInHand.getDurability() : 0;

        Block block = event.getBlock();
        Material blockType = block.getType();
        int blockData = block.getData();
        List<ItemStack> drops = findDrops(hand, handData, blockType, blockData);
        if (drops.isEmpty()) {
            return;
        }

        event.setCancelled(true);
        Location location = block.getLocation();
        World world = block.getWorld();
        block.setType(Material.AIR);

        for (ItemStack drop : drops) {
            world.dropItemNaturally(location, drop);
        }

        sendAirBlockCorrection(player, location);
    }

    private List<ItemStack> findDrops(Material hand, int handData, Material blockType, int blockData) {
        List<ItemStack> drops = engine.findDrops(hand, handData, blockType, blockData);
        if (!drops.isEmpty()) {
            return drops;
        }

        if (blockType == Material.GLOWING_REDSTONE_ORE) {
            return engine.findDrops(hand, handData, Material.REDSTONE_ORE, blockData);
        }
        if (blockType == Material.REDSTONE_ORE) {
            return engine.findDrops(hand, handData, Material.GLOWING_REDSTONE_ORE, blockData);
        }
        return drops;
    }

    private void sendAirBlockCorrection(final Player player, final Location location) {
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.sendBlockChange(location, Material.AIR, (byte) 0);
                }
            }
        }, 1L);
    }
}
