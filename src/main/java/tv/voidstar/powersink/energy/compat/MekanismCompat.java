package tv.voidstar.powersink.energy.compat;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import tv.voidstar.powersink.PowerSink;
import tv.voidstar.powersink.PowerSinkConfig;
import tv.voidstar.powersink.payout.MoneyCalculator;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mekanism 1.20.1 能量兼容层。
 *
 * 所有 NMS / Mekanism API 访问都通过反射完成。
 * 如果 Mekanism 不存在，这些方法将优雅地返回而不会出错。
 */
public class MekanismCompat {

    public static boolean hasMekanismEnergy(BlockState state) {
        try {
            return getMekanismContainer(state) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static void removeEnergyAndPay(BlockState state, UUID playerOwner) {
        try {
            Object container = getMekanismContainer(state);
            if (container == null) return;

            double current = getEnergy(container);
            int maxTx = PowerSinkConfig.getMaxEnergyTransaction();
            if (current <= 0) return;
            long extract = (long) Math.min(current, maxTx);

            BigDecimal money = MoneyCalculator.getMoneyCalculator().covertEnergyToMoney(extract);
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerOwner);
            EconomyResponse res = PowerSink.getEconomy().depositPlayer(offlinePlayer, money.doubleValue());

            if (res.transactionSuccess()) {
                setEnergy(container, current - extract);
            } else {
                org.bukkit.entity.Player p = Bukkit.getPlayer(playerOwner);
                if (p != null) PowerSink.sendMessage(p, "§c账户已满 — Mekanism 能量未提取。");
            }
        } catch (Exception e) {
            PowerSink.log().severe("MekanismCompat.removeEnergyAndPay 错误：" + e.getMessage());
        }
    }

    public static void withdrawPaymentAndAddEnergy(BlockState state, UUID playerOwner) {
        try {
            Object container = getMekanismContainer(state);
            if (container == null) return;

            double current = getEnergy(container);
            double max = getMaxEnergy(container);
            int maxTx = PowerSinkConfig.getMaxEnergyTransaction();
            if (current >= max) return;
            long give = (long) Math.min(max - current, maxTx);

            BigDecimal money = MoneyCalculator.getMoneyCalculator().covertEnergyToMoney(give);
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerOwner);

            if (!PowerSink.getEconomy().has(offlinePlayer, money.doubleValue())) {
                org.bukkit.entity.Player p = Bukkit.getPlayer(playerOwner);
                if (p != null) PowerSink.sendMessage(p, "§c资金不足 — Mekanism 能量未注入。");
                return;
            }
            EconomyResponse res = PowerSink.getEconomy().withdrawPlayer(offlinePlayer, money.doubleValue());
            if (res.transactionSuccess()) {
                setEnergy(container, current + give);
            }
        } catch (Exception e) {
            PowerSink.log().severe("MekanismCompat.withdrawPaymentAndAddEnergy 错误：" + e.getMessage());
        }
    }

    // ---- 反射辅助方法 ----

    /**
     * 尝试从方块实体获取 Mekanism IEnergyContainer。
     * Mekanism 10.4.x (1.20.1) 通过 IMekanismInventory 或直接暴露此接口。
     *
     * 注意：在混合服务端（Mohist/Arclight）上，插件和 Mod 使用不同的 ClassLoader，
     * 因此不能用 Class.isInstance() 判断接口实现，必须按接口名做字符串比对。
     */
    private static Object getMekanismContainer(BlockState state) throws Exception {
        // 遍历 BlockState 层次结构以找到 getTileEntity()
        Object be = getNmsBE(state);
        if (be == null) return null;

        // 按名称检查 BE 是否实现了 IEnergyContainer（避免 ClassLoader 隔离问题）
        if (implementsInterfaceByName(be, "mekanism.api.energy.IEnergyContainer")) {
            return be;
        }

        // 通过 IMekanismInventory 尝试 getEnergyContainers（同样按名称匹配）
        if (implementsInterfaceByName(be, "mekanism.api.inventory.IMekanismInventory")) {
            try {
                // 模糊匹配 getEnergyContainers 方法（忽略 Direction 参数的类型 ClassLoader）
                for (Method m : be.getClass().getMethods()) {
                    if (m.getName().equals("getEnergyContainers") && m.getParameterCount() == 1) {
                        m.setAccessible(true);
                        java.util.List<?> list = (java.util.List<?>) m.invoke(be, (Object) null);
                        if (list != null && !list.isEmpty()) return list.get(0);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    /**
     * 按名称检查对象是否实现了指定接口（遍历所有接口和父类的接口）。
     * 这解决了混合服务端上插件/Mod ClassLoader 隔离导致的 isInstance 返回 false 的问题。
     */
    private static boolean implementsInterfaceByName(Object obj, String targetInterfaceName) {
        if (obj == null) return false;
        Class<?> clazz = obj.getClass();
        // 遍历整个类层次结构
        while (clazz != null && clazz != Object.class) {
            for (Class<?> iface : clazz.getInterfaces()) {
                if (iface.getName().equals(targetInterfaceName)) return true;
                // 也检查接口的父接口
                if (checkInterfaceHierarchy(iface, targetInterfaceName)) return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    /** 递归检查接口及其父接口是否匹配目标名称。 */
    private static boolean checkInterfaceHierarchy(Class<?> iface, String targetName) {
        for (Class<?> parent : iface.getInterfaces()) {
            if (parent.getName().equals(targetName)) return true;
            if (checkInterfaceHierarchy(parent, targetName)) return true;
        }
        return false;
    }

    /** 委托给 NmsEnergyHelper 以可靠地访问 NMS BlockEntity，并添加直接 CraftBlock 回退。 */
    private static Object getNmsBE(BlockState state) {
        // 策略1: 通过 NmsEnergyHelper（多策略 NMS 路径）
        try {
            return NmsEnergyHelper.getNmsBlockEntity(state);
        } catch (Exception e) {
            // 记录但不中断，尝试回退
        }

        // 策略2: 通过 CraftBlock 直接获取 — 在 Mohist/Arclight 上可能有效
        try {
            org.bukkit.block.Block block = state.getBlock();
            if (block == null) return null;
            // CraftBlock.getNMS() / getHandle()
            for (java.lang.reflect.Method m : block.getClass().getMethods()) {
                String name = m.getName();
                if (name.equals("getNMS") || name.equals("getNMSBlock") || name.equals("getHandle")) {
                    if (m.getParameterCount() == 0) {
                        m.setAccessible(true);
                        Object nmsBlock = m.invoke(block);
                        if (nmsBlock != null) {
                            // 尝试从 NMS Block 获取 BlockEntity
                            for (java.lang.reflect.Method beMethod : nmsBlock.getClass().getMethods()) {
                                if (beMethod.getName().equals("getBlockEntity") && beMethod.getParameterCount() == 1) {
                                    beMethod.setAccessible(true);
                                    // 构造 BlockPos 使用调用者的 ClassLoader
                                    Object blockPos = createBlockPosCompat(nmsBlock, block.getX(), block.getY(), block.getZ());
                                    if (blockPos != null) {
                                        return beMethod.invoke(nmsBlock, blockPos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * 从 contextObj 的 ClassLoader 构造 BlockPos。
     * 用于规避混合服务端上的 ClassLoader 隔离问题。
     */
    private static Object createBlockPosCompat(Object contextObj, int x, int y, int z) {
        try {
            ClassLoader cl = contextObj.getClass().getClassLoader();
            Class<?> blockPosClass;
            if (cl != null) {
                blockPosClass = Class.forName("net.minecraft.core.BlockPos", false, cl);
            } else {
                blockPosClass = Class.forName("net.minecraft.core.BlockPos");
            }
            java.lang.reflect.Constructor<?> ctor = blockPosClass.getConstructor(int.class, int.class, int.class);
            return ctor.newInstance(x, y, z);
        } catch (Exception e) {
            return null;
        }
    }

    private static double getEnergy(Object container) throws Exception {
        // Mekanism 10.4.x: FloatingLong getEnergy() 或 double getEnergy()
        try {
            Method m = container.getClass().getMethod("getEnergy");
            Object result = m.invoke(container);
            if (result instanceof Double) return (Double) result;
            // FloatingLong.doubleValue()
            return (double) result.getClass().getMethod("doubleValue").invoke(result);
        } catch (Exception e) {
            throw new RuntimeException("getEnergy 失败", e);
        }
    }

    private static double getMaxEnergy(Object container) throws Exception {
        try {
            Method m = container.getClass().getMethod("getMaxEnergy");
            Object result = m.invoke(container);
            if (result instanceof Double) return (Double) result;
            return (double) result.getClass().getMethod("doubleValue").invoke(result);
        } catch (Exception e) {
            throw new RuntimeException("getMaxEnergy 失败", e);
        }
    }

    /**
     * 设置 Mekanism 容器能量值。
     * 不依赖参数类型精确匹配（规避 ClassLoader 隔离），
     * 而是直接从容器对象动态发现 setEnergy 方法的参数类型。
     */
    private static void setEnergy(Object container, double value) throws Exception {
        // 从容器对象上按名称查找单参数 setEnergy 方法
        Method setEnergyMethod = null;
        for (Method m : container.getClass().getMethods()) {
            if (m.getName().equals("setEnergy") && m.getParameterCount() == 1) {
                setEnergyMethod = m;
                break;
            }
        }
        if (setEnergyMethod == null) {
            throw new NoSuchMethodException(container.getClass().getName() + ".setEnergy(?) 未找到");
        }

        Class<?> paramType = setEnergyMethod.getParameterTypes()[0];
        Object arg;

        if (paramType == double.class || paramType == Double.class) {
            arg = value;
        } else {
            // 尝试通过参数类型的 fromDouble / parseFloating / create / valueOf 构造
            arg = constructEnergyValue(paramType, value);
        }

        setEnergyMethod.invoke(container, arg);
    }

    /**
     * 尝试从参数类型构造能量值。
     * 支持 Mekanism FloatingLong、Long、Integer 等常见类型。
     */
    private static Object constructEnergyValue(Class<?> paramType, double value) throws Exception {
        String typeName = paramType.getName();

        // Mekanism FloatingLong: FloatingLong.create(double) 或 parseFloating(String)
        if (typeName.contains("FloatingLong")) {
            try {
                Method create = paramType.getMethod("create", double.class);
                return create.invoke(null, value);
            } catch (NoSuchMethodException e1) {
                try {
                    Method parse = paramType.getMethod("parseFloating", String.class);
                    return parse.invoke(null, String.valueOf(value));
                } catch (NoSuchMethodException e2) {
                    // FloatingLong.create(long) — 整数版
                    Method createL = paramType.getMethod("create", long.class);
                    return createL.invoke(null, (long) value);
                }
            }
        }

        // Long
        if (paramType == long.class || paramType == Long.class) {
            return (long) value;
        }

        // Integer
        if (paramType == int.class || paramType == Integer.class) {
            return (int) value;
        }

        throw new IllegalArgumentException("不支持的 setEnergy 参数类型：" + typeName);
    }
}
