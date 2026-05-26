package tv.voidstar.powersink;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import tv.voidstar.powersink.command.PSCommand;
import tv.voidstar.powersink.energy.EnergyNode;
import tv.voidstar.powersink.energy.compat.EnergyCapability;
import tv.voidstar.powersink.event.listener.BlockBreakListener;
import tv.voidstar.powersink.event.listener.PlayerInteractListener;
import tv.voidstar.powersink.payout.MoneyCalculator;

import java.util.logging.Logger;

public class PowerSink extends JavaPlugin {

    private static PowerSink instance;
    private static Economy economy = null;

    @Override
    public void onEnable() {
        instance = this;

        // 初始化配置
        saveDefaultConfig();
        PowerSinkConfig.init(getDataFolder());
        MoneyCalculator.init();

        // 初始化能量兼容层
        EnergyCapability.init(this);

        // 连接 Vault 经济
        if (!setupEconomy()) {
            getLogger().severe("未找到 Vault/经济插件。PowerSink 无法运行，正在禁用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 加载数据
        PowerSinkData.init(getDataFolder());
        PowerSinkData.load();

        // 注册命令
        registerCommands();

        // 注册事件
        getServer().getPluginManager().registerEvents(new BlockBreakListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(), this);

        // 定时任务：每 N tick 处理一次能量节点
        int interval = PowerSinkConfig.getTickInterval();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (EnergyNode node : PowerSinkData.getEnergyNodes().values()) {
                node.handleEnergyTick();
            }
        }, interval, interval);

        getLogger().info("PowerSink v" + getDescription().getVersion() + " 已启用。作者: nyamura");
    }

    @Override
    public void onDisable() {
        getLogger().info("PowerSink 正在禁用 — 保存数据中。");
        PowerSinkData.save();
        getLogger().info("PowerSink 已禁用。");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return true;
    }

    private void registerCommands() {
        PSCommand psCmd = new PSCommand();
        PluginCommand cmd = getCommand("powersink");
        if (cmd != null) {
            cmd.setExecutor(psCmd);
            cmd.setTabCompleter(psCmd);
        }
    }

    // ---- 静态访问器 ----

    public static PowerSink getInstance() {
        return instance;
    }

    public static Logger log() {
        return instance.getLogger();
    }

    public static Economy getEconomy() {
        return economy;
    }

    public static void sendMessage(org.bukkit.command.CommandSender receiver, String message) {
        receiver.sendMessage("§e[PowerSink] §r" + message);
    }
}
