package tv.voidstar.powersink.energy.compat;

import org.bukkit.block.BlockState;
import org.bukkit.plugin.Plugin;
import tv.voidstar.powersink.PowerSink;
import tv.voidstar.powersink.energy.EnergyNode;

import java.util.Optional;

/**
 * 能量操作的中心调度器。
 *
 * 在 Paper 1.20.1 中，模组（Mekanism, IE）运行在 Fabric/Forge 服务端，而非 Paper。
 * PowerSink 面向 PAPER 服务端，因此软依赖的模组支持仅在那些模组暴露
 * 兼容 Bukkit 的 API（例如通过桥接插件）时才有效。
 *
 * 对于纯 Paper 设置，主要的能量后端是兼容原版的
 * Bukkit BlockEntityState 方法，由类似 EcoEnchants/PowerStorage 的插件使用，
 * 或者通过软依赖 ItemsAdder/FancyHolograms 的能量 API。
 *
 * 此类提供：
 *   1. 基于 NMS 的 Forge Energy，通过 net.minecraft.world.level.block.entity.BlockEntity
 *      + net.minecraftforge.energy.IEnergyStorage capability（需要 NMS 访问）。
 *   2. 可选的 Mekanism 桥接（如果类路径中存在 mekanism-bukkit-api）。
 *   3. 可选的 Immersive Engineering 桥接（相同条件）。
 *
 * 如果没有可用的，则回退到无操作并记录警告。
 */
public class EnergyCapability {

    private static boolean mekanismAvailable = false;
    private static boolean immersiveEngineeringAvailable = false;

    public static void init(Plugin plugin) {
        // 检测可选的模组 API — 使用 ForgeCompat.tryLoadClass 进行多 ClassLoader 回退
        mekanismAvailable = ForgeCompat.tryLoadClass("mekanism.api.energy.IEnergyContainer");
        if (mekanismAvailable) {
            plugin.getLogger().info("[PowerSink] 检测到 Mekanism API。");
        } else {
            // 回退：在 Forge ClassLoader 中查找（混合服上 Mod 类可能只在 Forge CL 可见）
            ClassLoader forgeCL = ForgeCompat.getForgeClassLoader();
            if (forgeCL != null) {
                try {
                    Class.forName("mekanism.api.energy.IEnergyContainer", false, forgeCL);
                    mekanismAvailable = true;
                    plugin.getLogger().info("[PowerSink] 通过 Forge ClassLoader 检测到 Mekanism API。");
                } catch (ClassNotFoundException ignored) {}
            }
        }

        immersiveEngineeringAvailable = ForgeCompat.tryLoadClass("blusunrize.immersiveengineering.api.energy.MutableEnergyStorage");
        if (immersiveEngineeringAvailable) {
            plugin.getLogger().info("[PowerSink] 检测到 Immersive Engineering API。");
        } else {
            ClassLoader forgeCL = ForgeCompat.getForgeClassLoader();
            if (forgeCL != null) {
                try {
                    Class.forName("blusunrize.immersiveengineering.api.energy.MutableEnergyStorage", false, forgeCL);
                    immersiveEngineeringAvailable = true;
                    plugin.getLogger().info("[PowerSink] 通过 Forge ClassLoader 检测到 Immersive Engineering API。");
                } catch (ClassNotFoundException ignored) {}
            }
        }
    }

    public static boolean isMekanismAvailable() { return mekanismAvailable; }
    public static boolean isIEAvailable() { return immersiveEngineeringAvailable; }

    /**
     * 检测给定 BlockState 处方块的能量类型。
     */
    public static EnergyType getEnergyStorageType(BlockState state) {
        if (state == null) return EnergyType.NONE;

        if (mekanismAvailable && MekanismCompat.hasMekanismEnergy(state)) {
            return EnergyType.MEKANISM;
        }
        if (immersiveEngineeringAvailable && ImmersiveEngineeringCompat.hasIEEnergy(state)) {
            return EnergyType.IMMERSIVE_ENGINEERING;
        }
        // 通过 NMS 尝试 Forge Energy（在运行混合服务端如 Mohist/Arclight 时有效）
        if (ForgeCompat.hasForgeEnergy(state)) {
            return EnergyType.FORGE;
        }
        return EnergyType.NONE;
    }

    /** 输出节点 tick：从方块抽取能量 → 支付给玩家 */
    public static void removeEnergyAndPay(EnergyNode node) {
        Optional<BlockState> stateOpt = node.getBlockState();
        if (stateOpt.isEmpty()) {
            PowerSink.log().warning("removeEnergyAndPay: 节点无 BlockState，" + node.getLocation());
            return;
        }
        BlockState state = stateOpt.get();
        switch (node.getEnergyType()) {
            case FORGE -> ForgeCompat.removeEnergyAndPay(state, node.getPlayerOwner());
            case MEKANISM -> {
                if (mekanismAvailable) MekanismCompat.removeEnergyAndPay(state, node.getPlayerOwner());
            }
            case IMMERSIVE_ENGINEERING -> {
                if (immersiveEngineeringAvailable) ImmersiveEngineeringCompat.removeEnergyAndPay(state, node.getPlayerOwner());
            }
            default -> PowerSink.log().warning("removeEnergyAndPay: 未知的 EnergyType " + node.getEnergyType());
        }
    }

    /** 接收节点 tick：从玩家扣款 → 注入能量到方块 */
    public static void withdrawPaymentAndAddEnergy(EnergyNode node) {
        Optional<BlockState> stateOpt = node.getBlockState();
        if (stateOpt.isEmpty()) {
            PowerSink.log().warning("withdrawPaymentAndAddEnergy: 节点无 BlockState，" + node.getLocation());
            return;
        }
        BlockState state = stateOpt.get();
        switch (node.getEnergyType()) {
            case FORGE -> ForgeCompat.withdrawPaymentAndAddEnergy(state, node.getPlayerOwner());
            case MEKANISM -> {
                if (mekanismAvailable) MekanismCompat.withdrawPaymentAndAddEnergy(state, node.getPlayerOwner());
            }
            case IMMERSIVE_ENGINEERING -> {
                if (immersiveEngineeringAvailable) ImmersiveEngineeringCompat.withdrawPaymentAndAddEnergy(state, node.getPlayerOwner());
            }
            default -> PowerSink.log().warning("withdrawPaymentAndAddEnergy: 未知的 EnergyType " + node.getEnergyType());
        }
    }
}
