package tv.voidstar.powersink.energy.compat;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import tv.voidstar.powersink.PowerSink;
import tv.voidstar.powersink.PowerSinkConfig;
import tv.voidstar.powersink.payout.MoneyCalculator;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Forge Energy (RF/FE) 兼容层，用于混合服务端（Mohist, Arclight, Ketting）。
 *
 * 在纯 Paper 服务端上没有 Forge 模组，因此 ForgeCompat 作为无操作运行，
 * 仅记录警告。在混合服务端（Mohist 1.20.1, Arclight 1.20.1）上，
 * NMS 类可用，我们通过 NMS 反射访问 IEnergyStorage。
 *
 * 策略：使用反射以避免编译时硬依赖 Forge NMS。
 * 如果运行时缺少类 → hasForgeEnergy() 返回 false → 不会崩溃。
 */
public class ForgeCompat {

    // Forge 1.19+ 将 CapabilityEnergy 移至 ForgeCapabilities
    private static final String[] FORGE_CAP_CLASSES = {
        "net.minecraftforge.common.capabilities.ForgeCapabilities",  // 1.19+
        "net.minecraftforge.energy.CapabilityEnergy"                // 1.18-
    };
    private static Boolean forgeAvailable = null;
    /** 成功加载 Forge 类时所用的 ClassLoader，后续所有 Forge 反射操作必须使用此 loader。 */
    private static ClassLoader forgeClassLoader = null;

    /**
     * 获取加载 Forge 类时所用的 ClassLoader。
     * 混合服务端上插件 ClassLoader 看不到 Forge 类，必须通过此 loader 访问。
     */
    public static ClassLoader getForgeClassLoader() {
        if (forgeAvailable == null) isForgeAvailable(); // 触发检测
        return forgeClassLoader;
    }

    public static boolean hasForgeEnergy(BlockState state) {
        if (!isForgeAvailable()) return false;
        try {
            return NmsEnergyHelper.hasForgeEnergyCapability(state);
        } catch (Exception e) {
            return false;
        }
    }

    public static void removeEnergyAndPay(BlockState state, UUID playerOwner) {
        if (!isForgeAvailable()) {
            PowerSink.log().warning("此服务器类型不支持 Forge Energy。");
            return;
        }
        try {
            int maxTx = PowerSinkConfig.getMaxEnergyTransaction();
            int energyToExtract = NmsEnergyHelper.simulateExtract(state, maxTx);
            if (energyToExtract <= 0) return;

            BigDecimal money = MoneyCalculator.getMoneyCalculator().covertEnergyToMoney(energyToExtract);
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerOwner);
            EconomyResponse res = PowerSink.getEconomy().depositPlayer(offlinePlayer, money.doubleValue());

            if (res.transactionSuccess()) {
                NmsEnergyHelper.doExtract(state, energyToExtract);
            } else {
                org.bukkit.entity.Player p = Bukkit.getPlayer(playerOwner);
                if (p != null) PowerSink.sendMessage(p, "§c账户已满 — 未提取能量。");
            }
        } catch (Exception e) {
            PowerSink.log().severe("ForgeCompat.removeEnergyAndPay 错误：" + e.getMessage());
        }
    }

    public static void withdrawPaymentAndAddEnergy(BlockState state, UUID playerOwner) {
        if (!isForgeAvailable()) {
            PowerSink.log().warning("此服务器类型不支持 Forge Energy。");
            return;
        }
        try {
            int maxTx = PowerSinkConfig.getMaxEnergyTransaction();
            int energyToGive = NmsEnergyHelper.simulateReceive(state, maxTx);
            if (energyToGive <= 0) return;

            BigDecimal money = MoneyCalculator.getMoneyCalculator().covertEnergyToMoney(energyToGive);
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerOwner);

            if (!PowerSink.getEconomy().has(offlinePlayer, money.doubleValue())) {
                org.bukkit.entity.Player p = Bukkit.getPlayer(playerOwner);
                if (p != null) PowerSink.sendMessage(p, "§c资金不足 — 未注入能量。");
                return;
            }
            EconomyResponse res = PowerSink.getEconomy().withdrawPlayer(offlinePlayer, money.doubleValue());
            if (res.transactionSuccess()) {
                NmsEnergyHelper.doReceive(state, energyToGive);
            }
        } catch (Exception e) {
            PowerSink.log().severe("ForgeCompat.withdrawPaymentAndAddEnergy 错误：" + e.getMessage());
        }
    }

    private static boolean isForgeAvailable() {
        if (forgeAvailable == null) {
            for (String className : FORGE_CAP_CLASSES) {
                ClassLoader cl = tryLoadClassAndGetLoader(className);
                if (cl != null) {
                    forgeClassLoader = cl;
                    PowerSink.log().info("[PowerSink] 检测到 Forge Energy capability：" + className);
                    forgeAvailable = true;
                    return true;
                }
            }
            forgeAvailable = false;
            PowerSink.log().warning("[PowerSink] 在所有 classloader 上均未找到 Forge Energy。"
                    + "FORGE 能量节点将无法使用。");
        }
        return forgeAvailable;
    }

    /**
     * 尝试使用多种 classloader 策略加载类，并返回找到时的 ClassLoader。
     * 返回 null 表示所有 classloader 都找不到。
     */
    static ClassLoader tryLoadClassAndGetLoader(String className) {
        // 1. CraftServer ClassLoader — Bukkit 桥接本身运行在 Forge 内
        try {
            ClassLoader serverCL = org.bukkit.Bukkit.getServer().getClass().getClassLoader();
            Class.forName(className, false, serverCL);
            PowerSink.log().info("[PowerSink] 通过 CraftServer ClassLoader 找到 " + className);
            return serverCL;
        } catch (ClassNotFoundException ignored) {}

        // 2. System ClassLoader
        try {
            ClassLoader sysCL = ClassLoader.getSystemClassLoader();
            Class.forName(className, false, sysCL);
            PowerSink.log().info("[PowerSink] 通过 System ClassLoader 找到 " + className);
            return sysCL;
        } catch (ClassNotFoundException ignored) {}

        // 3. Thread context ClassLoader
        try {
            ClassLoader ctxCL = Thread.currentThread().getContextClassLoader();
            if (ctxCL != null) {
                Class.forName(className, false, ctxCL);
                PowerSink.log().info("[PowerSink] 通过 Thread context ClassLoader 找到 " + className);
                return ctxCL;
            }
        } catch (ClassNotFoundException ignored) {}

        // 4. Default（调用类的 ClassLoader）
        try {
            Class.forName(className);
            PowerSink.log().info("[PowerSink] 通过默认 ClassLoader 找到 " + className);
            return ForgeCompat.class.getClassLoader();
        } catch (ClassNotFoundException ignored) {}

        return null;
    }

    /**
     * 尝试使用多种 classloader 策略加载类（兼容旧接口，不返回 loader）。
     */
    static boolean tryLoadClass(String className) {
        return tryLoadClassAndGetLoader(className) != null;
    }
}
