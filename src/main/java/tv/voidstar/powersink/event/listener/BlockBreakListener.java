package tv.voidstar.powersink.event.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import tv.voidstar.powersink.PowerSinkData;

public class BlockBreakListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        org.bukkit.Location loc = event.getBlock().getLocation();
        if (PowerSinkData.hasEnergyNode(loc)) {
            PowerSinkData.delEnergyNode(loc);
        }
    }
}
