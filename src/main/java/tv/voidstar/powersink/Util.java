package tv.voidstar.powersink;

import org.bukkit.Location;

/**
 * 工具辅助类。在 Paper 1.20.1 中，Bukkit Location 本身就是与维度关联的坐标，
 * 因此不再需要 Forge BlockPos / DimensionManager 桥接。
 */
public class Util {

    /**
     * 返回人类可读的位置字符串："世界名 @ x,y,z"
     */
    public static String locationToString(Location loc) {
        if (loc == null || loc.getWorld() == null) return "未知";
        return loc.getWorld().getName() + " @ " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
