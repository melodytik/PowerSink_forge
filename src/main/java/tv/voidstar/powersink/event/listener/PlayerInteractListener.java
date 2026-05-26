package tv.voidstar.powersink.event.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import tv.voidstar.powersink.*;
import tv.voidstar.powersink.energy.*;
import tv.voidstar.powersink.energy.compat.EnergyCapability;
import tv.voidstar.powersink.energy.compat.EnergyType;

public class PlayerInteractListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 仅处理左键点击方块
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return;

        String heldItem = item.getType().name(); // 例如 "GLOWSTONE_DUST"

        String sinkItem = PowerSinkConfig.getActivationItemSink().toUpperCase();
        String sourceItem = PowerSinkConfig.getActivationItemSource().toUpperCase();
        String removeItem = PowerSinkConfig.getActivationItemRemove().toUpperCase();

        // 内联处理删除操作（需要检查特定节点的所有权）
        if (heldItem.equals(removeItem)) {
            event.setCancelled(true);
            Location location = block.getLocation();
            EnergyNode existing = PowerSinkData.getEnergyNodes().get(PowerSinkData.locationKey(location));
            if (existing == null) {
                PowerSink.sendMessage(player, "§c该方块未注册为能量节点。");
                return;
            }
            if (existing.getPlayerOwner().equals(player.getUniqueId())) {
                if (!player.hasPermission(Constants.REMOVE_NODES_SELF_PERMISSION)) return;
            } else {
                if (!player.hasPermission(Constants.REMOVE_NODES_OTHER_PERMISSION)) {
                    PowerSink.sendMessage(player, "§c该节点属于其他玩家。");
                    return;
                }
            }
            PowerSinkData.delEnergyNode(location);
            return;
        }

        NodeType nodeType = null;
        if (heldItem.equals(sinkItem)) {
            if (!PowerSinkConfig.isAllowCreateSink()) {
                PowerSink.sendMessage(player, "§c接收节点创建已被管理员禁用。");
                return;
            }
            if (!player.hasPermission(Constants.SETUP_SINK_PERMISSION)) {
                PowerSink.sendMessage(player, "§c你没有权限注册接收节点。");
                return;
            }
            nodeType = NodeType.SINK;
        } else if (heldItem.equals(sourceItem)) {
            if (!PowerSinkConfig.isAllowCreateSource()) {
                PowerSink.sendMessage(player, "§c输出节点创建已被管理员禁用。");
                return;
            }
            if (!player.hasPermission(Constants.SETUP_SOURCE_PERMISSION)) {
                PowerSink.sendMessage(player, "§c你没有权限注册输出节点。");
                return;
            }
            nodeType = NodeType.SOURCE;
        } else {
            return;
        }

        event.setCancelled(true); // 防止方块交互副作用

        Location location = block.getLocation();

        // 检查方块状态
        BlockState state = block.getState();
        EnergyType energyType = EnergyCapability.getEnergyStorageType(state);

        if (energyType == EnergyType.NONE) {
            PowerSink.sendMessage(player, "§c该方块不是支持的能源存储设备。");
            return;
        }

        // 对于 Forge 能源，验证可以接收/提取（简化 — 在 tick 时完整检查）
        if (energyType == EnergyType.FORGE) {
            try {
                if (nodeType == NodeType.SINK) {
                    int canReceive = tv.voidstar.powersink.energy.compat.NmsEnergyHelper.simulateReceive(state, 1);
                    if (canReceive <= 0) {
                        PowerSink.sendMessage(player, "§c该方块无法接收能量。");
                        return;
                    }
                } else if (nodeType == NodeType.SOURCE) {
                    int canExtract = tv.voidstar.powersink.energy.compat.NmsEnergyHelper.simulateExtract(state, 1);
                    if (canExtract <= 0) {
                        PowerSink.sendMessage(player, "§c该方块无法输出能量。");
                        return;
                    }
                }
            } catch (Exception ignored) {
                // Forge 不可用 — 继续执行，将在 tick 时优雅失败
            }
        }

        if (PowerSinkData.hasEnergyNode(location)) {
            PowerSink.sendMessage(player, "§c该方块已经注册为能量节点。");
            return;
        }

        int currentCount = PowerSinkData.countNodes(player.getUniqueId(), nodeType);
        int limit = PowerSinkConfig.getNodeLimit(player.getUniqueId(), nodeType);
        if (currentCount >= limit) {
            PowerSink.sendMessage(player, "§c已达到节点数量上限 (" + currentCount + "/" + limit + ")。");
            return;
        }

        EnergyNode node;
        if (nodeType == NodeType.SINK) {
            node = new EnergySink(location, player.getUniqueId(), energyType);
        } else {
            node = new EnergySource(location, player.getUniqueId(), energyType);
        }
        PowerSinkData.addEnergyNode(node);
    }
}
