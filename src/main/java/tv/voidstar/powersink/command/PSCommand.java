package tv.voidstar.powersink.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import tv.voidstar.powersink.Constants;
import tv.voidstar.powersink.PowerSink;
import tv.voidstar.powersink.PowerSinkData;
import tv.voidstar.powersink.Util;
import tv.voidstar.powersink.energy.EnergyNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 处理 /powersink (别名 /ps) 及其子命令。
 *
 * 子命令：
 *   /ps help                          - 显示帮助
 *   /ps list [玩家名]                 - 列出能量节点
 *   /ps remove <key>                - 通过位置 key 删除节点
 *   /ps reload                        - 重载配置（仅 OP）
 */
public class PSCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help", "h" -> sendHelp(sender, label);
            case "list", "l" -> handleList(sender, args);
            case "remove", "rm", "del", "delete" -> handleRemove(sender, args, label);
            case "reload" -> handleReload(sender);
            default -> {
                PowerSink.sendMessage(sender, "§c未知子命令，请输入 /" + label + " help 查看帮助");
            }
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§6--- PowerSink 帮助 ---");
        sender.sendMessage("§e/" + label + " list [玩家名] §7- 查看能量节点列表");
        sender.sendMessage("§e/" + label + " remove <key> §7- 删除指定节点（key 见列表）");
        sender.sendMessage("§e/" + label + " reload §7- 重载配置文件（需要 OP 权限）");
    }

    private void handleList(CommandSender sender, String[] args) {
        UUID searchPlayer = null;

        if (args.length >= 2) {
            if (!sender.hasPermission(Constants.LIST_NODES_OTHER_PERMISSION)) {
                PowerSink.sendMessage(sender, "§c你没有权限查看其他玩家的节点！");
                return;
            }
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target == null) {
                PowerSink.sendMessage(sender, "§c找不到玩家 '" + args[1] + "'！");
                return;
            }
            searchPlayer = target.getUniqueId();
        } else {
            if (!sender.hasPermission(Constants.LIST_NODES_SELF_PERMISSION)) {
                PowerSink.sendMessage(sender, "§c你没有权限查看自己的节点！");
                return;
            }
            if (sender instanceof Player player) {
                searchPlayer = player.getUniqueId();
            } else {
                PowerSink.sendMessage(sender, "§c控制台必须指定玩家：/ps list <玩家名>");
                return;
            }
        }

        UUID finalSearchPlayer = searchPlayer;

        List<EnergyNode> nodes = PowerSinkData.getEnergyNodes().values().stream()
                .filter(n -> n.getPlayerOwner().equals(finalSearchPlayer))
                .collect(Collectors.toList());

        if (nodes.isEmpty()) {
            PowerSink.sendMessage(sender, "没有找到任何能量节点。");
            return;
        }

        sender.sendMessage("§6--- 能量节点列表（共 " + nodes.size() + " 个）---");
        for (int i = 0; i < nodes.size(); i++) {
            EnergyNode node = nodes.get(i);
            String loc = Util.locationToString(node.getLocation());
            String typeColor = node.getNodeType() == tv.voidstar.powersink.energy.NodeType.SOURCE ? "§a" : "§c";
            String line = "§7[" + (i + 1) + "] " + typeColor + node.getNodeType().getChineseName()
                    + " §7@ §f" + loc + " §8(" + node.getEnergyType().getChineseName() + ")"
                    + " §8key=" + PowerSinkData.locationKey(node.getLocation());
            sender.sendMessage(line);
        }
    }

    private void handleRemove(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            PowerSink.sendMessage(sender, "§c用法：/" + label + " remove <key>");
            PowerSink.sendMessage(sender, "§7key 可以通过 /" + label + " list 查看");
            return;
        }

        String key = args[1];
        EnergyNode node = PowerSinkData.getEnergyNodes().get(key);

        if (node == null) {
            PowerSink.sendMessage(sender, "§c找不到 key 为 '" + key + "' 的节点！");
            return;
        }

        // 检查所有权
        boolean isOwner = false;
        if (sender instanceof Player player) {
            isOwner = node.getPlayerOwner().equals(player.getUniqueId());
        }

        if (isOwner) {
            if (!sender.hasPermission(Constants.REMOVE_NODES_SELF_PERMISSION)) {
                PowerSink.sendMessage(sender, "§c你没有权限删除自己的节点！");
                return;
            }
        } else {
            if (!sender.hasPermission(Constants.REMOVE_NODES_OTHER_PERMISSION)) {
                PowerSink.sendMessage(sender, "§c这个节点属于其他玩家，你没有权限删除！");
                return;
            }
        }

        PowerSink.sendMessage(sender, "§a正在删除节点：§f" + node.getNodeType().getChineseName()
                + " §7@ " + Util.locationToString(node.getLocation()));
        PowerSinkData.delEnergyNode(node.getLocation());
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("powersink.admin")) {
            PowerSink.sendMessage(sender, "§c权限不足。");
            return;
        }
        tv.voidstar.powersink.PowerSinkConfig.reload();
        PowerSinkData.reload();
        tv.voidstar.powersink.payout.MoneyCalculator.init();
        PowerSink.sendMessage(sender, "§a配置文件和数据已重载。");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("help", "list", "remove", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
