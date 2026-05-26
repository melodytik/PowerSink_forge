package tv.voidstar.powersink.energy;

import org.bukkit.Location;
import org.bukkit.block.BlockState;
import tv.voidstar.powersink.PowerSink;
import tv.voidstar.powersink.Util;
import tv.voidstar.powersink.energy.compat.EnergyType;

import java.util.Optional;
import java.util.UUID;

/**
 * 能量节点的抽象基类（接收节点 / 输出节点）。
 * 在 1.20.1 Paper 中，我们存储 Bukkit Location 并在需要时获取 BlockState。
 */
public abstract class EnergyNode {

    protected Location location;
    protected UUID playerOwner;
    protected EnergyType energyType;

    protected EnergyNode(Location location, UUID playerOwner, EnergyType energyType) {
        this.location = location;
        this.playerOwner = playerOwner;
        this.energyType = energyType;
    }

    /** 反序列化用的无参构造函数。 */
    protected EnergyNode() {}

    // ---- 访问器 ----

    public Location getLocation() { return location; }
    public UUID getPlayerOwner() { return playerOwner; }
    public EnergyType getEnergyType() { return energyType; }

    /**
     * 返回此节点位置的 Bukkit BlockState，如果区块未加载或方块没有 BlockEntityState，则返回 empty。
     */
    public Optional<BlockState> getBlockState() {
        if (location == null || location.getWorld() == null) return Optional.empty();
        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            PowerSink.log().warning("节点所在区块未加载：" + Util.locationToString(location));
            return Optional.empty();
        }
        BlockState state = location.getBlock().getState();
        return Optional.of(state);
    }

    public abstract void handleEnergyTick();
    public abstract NodeType getNodeType();
}
