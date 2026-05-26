package tv.voidstar.powersink.energy.compat;

import org.bukkit.block.BlockState;
import tv.voidstar.powersink.PowerSink;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 基于 NMS 的辅助类，用于访问混合服务端上的 Forge Energy capability
 * (Mohist 1.20.1, Arclight 1.20.1, Ketting 1.20.1)。
 *
 * 所有 NMS 访问都通过反射完成，因此此文件可以干净地针对纯 Paper API 编译。
 * 如果运行时缺少 Forge 类，方法将返回安全的默认值 (false / 0)。
 *
 * Forge 1.19+ 重命名了 capability 类：
 *   新 (1.19+): net.minecraftforge.common.capabilities.ForgeCapabilities
 *   旧 (1.18-): net.minecraftforge.energy.CapabilityEnergy
 *
 * 访问路径：
 *   Bukkit BlockState → NMS BlockEntity → Forge Capability → IEnergyStorage
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class NmsEnergyHelper {

    private static boolean loggedNmsFailure = false;

    // ---- NMS BlockEntity 提取（多策略） ----

    /**
     * 解包 Bukkit BlockState → NMS BlockEntity。
     * 尝试多种策略，因为 Mohist/Arclight 桥接实现各不相同。
     * 所有方法匹配都按名称进行，以规避混合服务端上插件/Mod ClassLoader 隔离问题。
     */
    public static Object getNmsBlockEntity(BlockState state) throws Exception {
        Exception lastError = null;

        // 策略1: BlockState.getHandle() — 某些 Mohist 版本将此添加到 CraftBlockState
        // 注意：Mohist 的 getHandle() 可能返回 NMS IBlockData（BlockState），不是 BlockEntity！
        // 必须校验返回类型，如果是 BlockState 则跳过继续尝试策略2。
        try {
            Method getHandle = findMethodByName(state.getClass(), "getHandle");
            if (getHandle != null) {
                Object result = getHandle.invoke(state);
                if (result != null && isBlockEntityType(result)) {
                    return result;
                }
            }
        } catch (Exception e) { lastError = e; }

        // 策略2: BlockState.getBlock() → CraftWorld.getHandle() → ServerLevel.getBlockEntity(BlockPos)
        try {
            org.bukkit.block.Block block = state.getBlock();
            if (block == null) throw new RuntimeException("BlockState.getBlock() 返回 null");
            org.bukkit.World world = block.getWorld();
            if (world == null) throw new RuntimeException("Block.getWorld() 返回 null");

            // CraftWorld.getHandle() → NMS ServerLevel
            Method getHandle = findMethodByName(world.getClass(), "getHandle");
            if (getHandle == null) throw new RuntimeException("CraftWorld.getHandle() 不存在");
            Object nmsWorld = getHandle.invoke(world);

            // 按名称 + 参数数量查找 getBlockEntity（排除多参数重载）
            Method getBlockEntity = findMethodByName(nmsWorld.getClass(), 1, "getBlockEntity");
            if (getBlockEntity != null) {
                // 构造 NMS BlockPos：从 nmsWorld 可达的 ClassLoader 加载 BlockPos
                Object blockPos = createBlockPos(nmsWorld, block.getX(), block.getY(), block.getZ());
                if (blockPos != null) {
                    Object result = getBlockEntity.invoke(nmsWorld, blockPos);
                    if (result != null) return result;
                }
            }
        } catch (Exception e) { lastError = e; }

        // 策略2b: 通过 CraftBlock 直接获取 NMS Block
        try {
            org.bukkit.block.Block block = state.getBlock();
            Method getNMSBlock = findMethodByName(block.getClass(), "getNMS", "getNMSBlock", "getHandle");
            if (getNMSBlock != null) {
                Object nmsBlock = getNMSBlock.invoke(block);
                if (nmsBlock != null) {
                    // nmsBlock.getBlockEntity(pos, ...)
                    Method beMethod = findMethodByName(nmsBlock.getClass(), 1, "getBlockEntity");
                    if (beMethod != null) {
                        Object blockPos = createBlockPos(nmsBlock, block.getX(), block.getY(), block.getZ());
                        if (blockPos != null) {
                            Object result = beMethod.invoke(nmsBlock, blockPos);
                            if (result != null) return result;
                        }
                    }
                }
            }
        } catch (Exception e) { lastError = e; }

        // 策略3: 直接内部字段 — CraftBlockState.tileEntity / blockEntity / snapshot
        try {
            Field f = findField(state.getClass(), "tileEntity", "blockEntity", "snapshot", "tile");
            if (f != null) {
                f.setAccessible(true);
                Object result = f.get(state);
                if (result != null) return result;
            }
        } catch (Exception e) { lastError = e; }

        // 所有策略都失败 — 每个服务器会话记录一次
        if (!loggedNmsFailure) {
            loggedNmsFailure = true;
            PowerSink.log().warning("[PowerSink] 无法从 " +
                    state.getClass().getName() + " 提取 NMS BlockEntity。最后错误：" +
                    (lastError != null ? lastError.toString() : "所有策略返回 null"));
            PowerSink.log().warning("[PowerSink] Forge/Mekanism/IE 能量节点将无法工作。" +
                    "确保你运行在 Mohist/Arclight 1.20.1 上。");
        }
        throw new RuntimeException("NMS BlockEntity 提取失败", lastError);
    }

    /**
     * 构造 NMS BlockPos，使用给定对象的 ClassLoader 以确保类型兼容。
     * 这解决了混合服务端上 Class.forName 加载的 BlockPos 与 NMS 方法
     * 期望的参数类型来自不同 ClassLoader 的问题。
     */
    private static Object createBlockPos(Object contextObj, int x, int y, int z) {
        try {
            // 从 contextObj 的 ClassLoader 获取 BlockPos
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
            // 回退：尝试默认 ClassLoader
            try {
                Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos");
                java.lang.reflect.Constructor<?> ctor = blockPosClass.getConstructor(int.class, int.class, int.class);
                return ctor.newInstance(x, y, z);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    // ---- 反射工具 ----

    /**
     * 检查对象是否为 NMS BlockEntity 类型（按类名判断，规避 ClassLoader 隔离）。
     * Mohist 的 getHandle() 可能返回 IBlockData（BlockState），需要排除。
     */
    private static boolean isBlockEntityType(Object obj) {
        if (obj == null) return false;
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            String name = clazz.getName();
            // 排除 BlockState 类型
            if (name.contains("BlockState") || name.contains("BlockData")) return false;
            // 包含 BlockEntity 或 TileEntity 的类型
            if (name.contains("BlockEntity") || name.contains("TileEntity")) return true;
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    /** 按名称查找方法（不检查参数类型，用于规避 ClassLoader 隔离）。 */
    private static Method findMethodByName(Class<?> clazz, String... names) {
        return findMethodByName(clazz, -1, names);
    }

    /**
     * 按名称 + 参数数量查找方法。
     * paramCount = -1 表示不限制参数数量。
     */
    private static Method findMethodByName(Class<?> clazz, int paramCount, String... names) {
        Class<?> c = clazz;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (paramCount >= 0 && m.getParameterCount() != paramCount) continue;
                for (String name : names) {
                    if (m.getName().equals(name)) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            Method m = clazz.getMethod(name, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            // 遍历超类
            Class<?> c = clazz;
            while (c != null) {
                try {
                    Method m = c.getDeclaredMethod(name, paramTypes);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException ignored) {}
                c = c.getSuperclass();
            }
            return null;
        }
    }

    private static Field findField(Class<?> clazz, String... names) {
        Class<?> c = clazz;
        while (c != null) {
            for (String name : names) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {}
            }
            c = c.getSuperclass();
        }
        return null;
    }

    // ---- Forge Capability (IEnergyStorage) ----

    // Forge 1.19+ 移动了 capability 类
    private static final String[] FORGE_CAP_CLASSES = {
        "net.minecraftforge.common.capabilities.ForgeCapabilities",  // 1.19+
        "net.minecraftforge.energy.CapabilityEnergy"                // 1.18-
    };

    /** 通过 Forge ClassLoader 加载类，规避插件 ClassLoader 隔离。 */
    private static Class<?> loadForgeClass(String name) throws ClassNotFoundException {
        ClassLoader forgeCL = ForgeCompat.getForgeClassLoader();
        if (forgeCL != null) {
            return Class.forName(name, false, forgeCL);
        }
        return Class.forName(name);
    }

    /** 获取 Forge Energy Capability 对象 (FORGE_CAP_CLASSES[i].ENERGY) */
    private static Object getEnergyCapability() throws Exception {
        ClassLoader forgeCL = ForgeCompat.getForgeClassLoader();
        for (String className : FORGE_CAP_CLASSES) {
            try {
                Class<?> capClass;
                if (forgeCL != null) {
                    capClass = Class.forName(className, false, forgeCL);
                } else {
                    capClass = Class.forName(className);
                }
                Object cap = capClass.getField("ENERGY").get(null);
                if (cap != null) {
                    return cap;
                }
            } catch (ClassNotFoundException | NoSuchFieldException ignored) {}
        }
        throw new ClassNotFoundException("未找到 ForgeCapabilities 或 CapabilityEnergy");
    }

    /** 调用 blockEntity.getCapability(cap, null).orElse(null) */
    private static Object getEnergyStorage(Object blockEntity) throws Exception {
        Object cap = getEnergyCapability();
        Class<?> iCapClass = loadForgeClass("net.minecraftforge.common.capabilities.ICapabilityProvider");
        Class<?> capClass = loadForgeClass("net.minecraftforge.common.capabilities.Capability");

        // 尝试重载：getCapability(Capability, Direction), getCapability(Capability)
        Method getCapMethod = null;
        try {
            Class<?> dirClass = loadForgeClass("net.minecraft.core.Direction");
            getCapMethod = iCapClass.getMethod("getCapability", capClass, dirClass);
            Object lazyOpt = getCapMethod.invoke(blockEntity, cap, null);
            return orElseNull(lazyOpt);
        } catch (Exception ex) {
            try {
                getCapMethod = iCapClass.getMethod("getCapability", capClass);
                Object lazyOpt = getCapMethod.invoke(blockEntity, cap);
                return orElseNull(lazyOpt);
            } catch (Exception ex2) {
                throw new RuntimeException("未找到 getCapability", ex2);
            }
        }
    }

    private static Object orElseNull(Object lazyOpt) throws Exception {
        if (lazyOpt == null) return null;
        Method orElse = null;
        for (Method m : lazyOpt.getClass().getMethods()) {
            if (m.getName().equals("orElse") && m.getParameterCount() == 1) {
                orElse = m;
                break;
            }
        }
        if (orElse == null) throw new NoSuchMethodException("未找到 orElse");
        return orElse.invoke(lazyOpt, (Object) null);
    }

    // ---- 公共 API ----

    /**
     * 返回给定 BlockState 的 NMS IEnergyStorage，或 null。
     */
    public static Object getForgeEnergyStorage(BlockState state) {
        try {
            Object be = getNmsBlockEntity(state);
            if (be == null) return null;
            return getEnergyStorage(be);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasForgeEnergyCapability(BlockState state) {
        try {
            Object be = getNmsBlockEntity(state);
            if (be == null) return false;
            return getEnergyStorage(be) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static int simulateExtract(BlockState state, int amount) throws Exception {
        Object be = getNmsBlockEntity(state);
        Object storage = getEnergyStorage(be);
        if (storage == null) return 0;
        Class<?> iStorage = loadForgeClass("net.minecraftforge.energy.IEnergyStorage");
        Method m = iStorage.getMethod("extractEnergy", int.class, boolean.class);
        return (int) m.invoke(storage, amount, true);
    }

    public static void doExtract(BlockState state, int amount) throws Exception {
        Object be = getNmsBlockEntity(state);
        Object storage = getEnergyStorage(be);
        if (storage == null) return;
        Class<?> iStorage = loadForgeClass("net.minecraftforge.energy.IEnergyStorage");
        Method m = iStorage.getMethod("extractEnergy", int.class, boolean.class);
        m.invoke(storage, amount, false);
    }

    public static int simulateReceive(BlockState state, int amount) throws Exception {
        Object be = getNmsBlockEntity(state);
        Object storage = getEnergyStorage(be);
        if (storage == null) return 0;
        Class<?> iStorage = loadForgeClass("net.minecraftforge.energy.IEnergyStorage");
        Method m = iStorage.getMethod("receiveEnergy", int.class, boolean.class);
        return (int) m.invoke(storage, amount, true);
    }

    public static void doReceive(BlockState state, int amount) throws Exception {
        Object be = getNmsBlockEntity(state);
        Object storage = getEnergyStorage(be);
        if (storage == null) return;
        Class<?> iStorage = loadForgeClass("net.minecraftforge.energy.IEnergyStorage");
        Method m = iStorage.getMethod("receiveEnergy", int.class, boolean.class);
        m.invoke(storage, amount, false);
    }
}
