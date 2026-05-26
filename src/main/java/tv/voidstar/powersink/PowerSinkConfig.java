package tv.voidstar.powersink;

import org.bukkit.configuration.file.FileConfiguration;
import tv.voidstar.powersink.energy.NodeType;
import tv.voidstar.powersink.payout.MoneyCalculator;

import java.io.File;
import java.util.*;

/**
 * 封装 Bukkit 的 config.yml 以获取 PowerSink 设置。
 * 从 config.yml 读取（从 resources/config.yml 默认值自动保存）。
 */
public class PowerSinkConfig {

    private static FileConfiguration config;
    private static final LinkedHashMap<String, List<Integer>> groupLimits = new LinkedHashMap<>();

    public static void init(File dataFolder) {
        // config.yml 由主类中的 Bukkit saveDefaultConfig() 处理
        config = PowerSink.getInstance().getConfig();
        loadGroupLimits();
        MoneyCalculator.init();
    }

    private static void loadGroupLimits() {
        groupLimits.clear();
        List<?> limits = config.getList("limits");
        if (limits != null) {
            for (Object obj : limits) {
                if (obj instanceof Map<?, ?> map) {
                    String group = (String) map.get("group");
                    int sink = map.containsKey("sink") ? ((Number) map.get("sink")).intValue() : 0;
                    int source = map.containsKey("source") ? ((Number) map.get("source")).intValue() : 0;
                    groupLimits.put(group, Arrays.asList(sink, source));
                }
            }
        }
        if (!groupLimits.containsKey("default")) {
            groupLimits.put("default", Arrays.asList(1, 1));
        }
    }

    public static void reload() {
        PowerSink.getInstance().reloadConfig();
        config = PowerSink.getInstance().getConfig();
        loadGroupLimits();
        MoneyCalculator.init();
    }

    // ---- 访问器 ----

    public static int getTickInterval() {
        return config.getInt("powersink.tickInterval", 2);
    }

    public static boolean isAllowCreateSink() {
        return config.getBoolean("powersink.allowCreate.sink", true);
    }

    public static boolean isAllowCreateSource() {
        return config.getBoolean("powersink.allowCreate.source", true);
    }

    public static String getActivationItemSink() {
        return config.getString("activationItems.sink", "GLOWSTONE_DUST");
    }

    public static String getActivationItemSource() {
        return config.getString("activationItems.source", "REDSTONE");
    }

    public static String getActivationItemRemove() {
        return config.getString("activationItems.remove", "BEDROCK");
    }

    public static int getMaxEnergyTransaction() {
        return config.getInt("rates.maxEnergyTransaction", 10000);
    }

    public static String getRatesFunction() {
        return config.getString("rates.function", "log");
    }

    public static double getRatesBase() {
        return config.getDouble("rates.base", 100.0);
    }

    public static double getRatesMultiplier() {
        return config.getDouble("rates.multiplier", 10.0);
    }

    public static double getRatesShift() {
        return config.getDouble("rates.shift", 10.0);
    }

    public static double getRatesRatio() {
        return config.getDouble("rates.ratio", 1.0);
    }

    public static int getNodeLimit(UUID playerUUID, NodeType nodeType) {
        org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(playerUUID);
        for (Map.Entry<String, List<Integer>> entry : groupLimits.entrySet()) {
            String group = entry.getKey();
            if (group.equals("default")) continue;
            if (player.isOnline() && player.getPlayer() != null
                    && player.getPlayer().hasPermission(Constants.LIMIT_BASE_PERMISSION + group)) {
                if (nodeType == NodeType.SINK) return entry.getValue().get(0);
                if (nodeType == NodeType.SOURCE) return entry.getValue().get(1);
            }
        }
        List<Integer> def = groupLimits.getOrDefault("default", Arrays.asList(1, 1));
        if (nodeType == NodeType.SINK) return def.get(0);
        if (nodeType == NodeType.SOURCE) return def.get(1);
        return 0;
    }
}
