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
 * Immersive Engineering 1.20.1 能量兼容层。
 *
 * IE 1.20.1 (版本 1.20.1-9.2.x) 原生使用 Forge Energy (IEnergyStorage)。
 * 因此 IE 方块也会通过 ForgeCompat 检测到。
 * 此类提供额外的 IE 特定方块检测钩子，以备将来需要。
 */
public class ImmersiveEngineeringCompat {

    public static boolean hasIEEnergy(BlockState state) {
        try {
            Object storage = getIEStorage(state);
            return storage != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static void removeEnergyAndPay(BlockState state, UUID playerOwner) {
        try {
            Object storage = getIEStorage(state);
            if (storage == null) return;

            Class<?> cls = Class.forName("net.minecraftforge.energy.IEnergyStorage");
            int maxTx = PowerSinkConfig.getMaxEnergyTransaction();
            int energyToExtract = (int) cls.getMethod("extractEnergy", int.class, boolean.class)
                    .invoke(storage, maxTx, true);
            if (energyToExtract <= 0) return;

            BigDecimal money = MoneyCalculator.getMoneyCalculator().covertEnergyToMoney(energyToExtract);
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerOwner);
            EconomyResponse res = PowerSink.getEconomy().depositPlayer(offlinePlayer, money.doubleValue());

            if (res.transactionSuccess()) {
                cls.getMethod("extractEnergy", int.class, boolean.class).invoke(storage, energyToExtract, false);
            } else {
                org.bukkit.entity.Player p = Bukkit.getPlayer(playerOwner);
                if (p != null) PowerSink.sendMessage(p, "§c账户已满 — IE 能量未提取。");
            }
        } catch (Exception e) {
            PowerSink.log().severe("ImmersiveEngineeringCompat.removeEnergyAndPay 错误：" + e.getMessage());
        }
    }

    public static void withdrawPaymentAndAddEnergy(BlockState state, UUID playerOwner) {
        try {
            Object storage = getIEStorage(state);
            if (storage == null) return;

            Class<?> cls = Class.forName("net.minecraftforge.energy.IEnergyStorage");
            int maxTx = PowerSinkConfig.getMaxEnergyTransaction();
            int energyToGive = (int) cls.getMethod("receiveEnergy", int.class, boolean.class)
                    .invoke(storage, maxTx, true);
            if (energyToGive <= 0) return;

            BigDecimal money = MoneyCalculator.getMoneyCalculator().covertEnergyToMoney(energyToGive);
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerOwner);

            if (!PowerSink.getEconomy().has(offlinePlayer, money.doubleValue())) {
                org.bukkit.entity.Player p = Bukkit.getPlayer(playerOwner);
                if (p != null) PowerSink.sendMessage(p, "§c资金不足 — IE 能量未注入。");
                return;
            }
            EconomyResponse res = PowerSink.getEconomy().withdrawPlayer(offlinePlayer, money.doubleValue());
            if (res.transactionSuccess()) {
                cls.getMethod("receiveEnergy", int.class, boolean.class).invoke(storage, energyToGive, false);
            }
        } catch (Exception e) {
            PowerSink.log().severe("ImmersiveEngineeringCompat.withdrawPaymentAndAddEnergy 错误：" + e.getMessage());
        }
    }

    private static Object getIEStorage(BlockState state) {
        // IE 1.20.1 原生使用 Forge Energy；返回实际的 IEnergyStorage
        return NmsEnergyHelper.getForgeEnergyStorage(state);
    }
}
