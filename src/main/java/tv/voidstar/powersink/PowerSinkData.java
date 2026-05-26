package tv.voidstar.powersink;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import tv.voidstar.powersink.energy.EnergyNode;
import tv.voidstar.powersink.energy.EnergySink;
import tv.voidstar.powersink.energy.EnergySource;
import tv.voidstar.powersink.energy.NodeType;
import tv.voidstar.powersink.energy.compat.EnergyType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PowerSinkData {

    private static File dataFile;
    private static final LinkedHashMap<String, EnergyNode> energyNodes = new LinkedHashMap<>();

    /** key = "世界名,x,y,z" */
    public static String locationKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public static void init(File dataFolder) {
        if (!dataFolder.exists()) dataFolder.mkdirs();
        dataFile = new File(dataFolder, "energynodes.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) {
                PowerSink.log().severe("无法创建 energynodes.yml：" + e.getMessage());
            }
        }
    }

    public static void load() {
        energyNodes.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection nodes = yaml.getConfigurationSection("energyNodes");
        if (nodes == null) return;

        for (String key : nodes.getKeys(false)) {
            ConfigurationSection section = nodes.getConfigurationSection(key);
            if (section == null) continue;
            try {
                String type = section.getString("type");
                String worldName = section.getString("world");
                int x = section.getInt("x");
                int y = section.getInt("y");
                int z = section.getInt("z");
                UUID owner = UUID.fromString(Objects.requireNonNull(section.getString("owner")));
                EnergyType energyType = EnergyType.fromString(section.getString("energyType", "FORGE"));

                org.bukkit.World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    PowerSink.log().warning("世界 '" + worldName + "' 未加载，跳过节点 " + key);
                    continue;
                }
                Location loc = new Location(world, x, y, z);
                EnergyNode node;
                if ("SOURCE".equals(type)) {
                    node = new EnergySource(loc, owner, energyType);
                } else {
                    node = new EnergySink(loc, owner, energyType);
                }
                energyNodes.put(locationKey(loc), node);
            } catch (Exception e) {
                PowerSink.log().severe("加载能量节点 '" + key + "' 时出错：" + e.getMessage());
            }
        }
        PowerSink.log().info("已加载 " + energyNodes.size() + " 个能量节点。");
    }

    public static void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        int idx = 0;
        for (EnergyNode node : energyNodes.values()) {
            Location loc = node.getLocation();
            String path = "energyNodes.node" + idx;
            yaml.set(path + ".type", node.getNodeType().name());
            yaml.set(path + ".world", loc.getWorld().getName());
            yaml.set(path + ".x", loc.getBlockX());
            yaml.set(path + ".y", loc.getBlockY());
            yaml.set(path + ".z", loc.getBlockZ());
            yaml.set(path + ".owner", node.getPlayerOwner().toString());
            yaml.set(path + ".energyType", node.getEnergyType().name());
            idx++;
        }
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            PowerSink.log().severe("无法保存能量节点：" + e.getMessage());
        }
    }

    public static void addEnergyNode(EnergyNode node) {
        energyNodes.put(locationKey(node.getLocation()), node);
        notifyPlayerNodeModified(node, "创建");
        save();
    }

    public static void delEnergyNode(Location location) {
        String key = locationKey(location);
        EnergyNode node = energyNodes.remove(key);
        if (node != null) {
            notifyPlayerNodeModified(node, "删除");
            save();
        }
    }

    public static boolean hasEnergyNode(Location location) {
        return energyNodes.containsKey(locationKey(location));
    }

    public static int countNodes(UUID player, NodeType nodeType) {
        int count = 0;
        for (EnergyNode node : energyNodes.values()) {
            if (node.getPlayerOwner().equals(player)) {
                if (nodeType == null || node.getNodeType() == nodeType) count++;
            }
        }
        return count;
    }

    public static void notifyPlayerNodeModified(EnergyNode node, String verb) {
        String nodeType = node.getNodeType().name();
        nodeType = "能量" + nodeType.charAt(0) + nodeType.substring(1).toLowerCase();
        PowerSink.log().info(nodeType + " @ " + node.getLocation() + " " + verb + "。");

        int owned = countNodes(node.getPlayerOwner(), node.getNodeType());
        int allowed = PowerSinkConfig.getNodeLimit(node.getPlayerOwner(), node.getNodeType());
        String msg = nodeType + "已" + verb + "。(" + owned + "/" + allowed + ")";

        org.bukkit.entity.Player player = Bukkit.getPlayer(node.getPlayerOwner());
        if (player != null) {
            PowerSink.sendMessage(player, msg);
        }
    }

    public static void reload() {
        load();
    }

    public static Map<String, EnergyNode> getEnergyNodes() {
        return Collections.synchronizedMap(energyNodes);
    }
}
