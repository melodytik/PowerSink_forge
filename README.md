# PowerSink v1.0

[English](#english) | [中文](#chinese)

---

<a name="english"></a>
## English

A Paper/Spigot 1.20.1 plugin that lets players sell or buy energy via in-game currency. Integrates with Vault for economy and supports multiple mod energy backends through Mohist/Arclight hybrid servers.

### Supported Energy Backends

| Backend | Detection |
|---------|-----------|
| Forge Energy (FE/RF) | Automatic via NMS reflection |
| Mekanism | Automatic if Mekanism mod is installed |
| Immersive Engineering | Automatic if IE mod is installed |

### Definitions

| Term | Meaning |
|------|---------|
| **Source** | Block from which energy is extracted — the player sells energy and earns money |
| **Sink** | Block to which energy is delivered — the player buys energy and pays money |
| **Node** | A block registered as either a Source or Sink |

### Usage

1. **Register a Source**: Left-click an energy storage block while holding **Redstone Dust** — energy will be extracted and you earn money.
2. **Register a Sink**: Left-click an energy storage block while holding **Glowstone Dust** — energy will be delivered and you pay money.
3. **Remove a Node**: Left-click a registered node while holding a **Lever**.

> Activation items and many other settings are configurable in `config.yml`.

### Commands

| Command | Description |
|---------|-------------|
| `/ps` or `/powersink` | Show help and subcommands |
| `/ps list [player]` | List nodes for a player (or yourself) |
| `/ps remove <id>` | Remove a node by its ID |
| `/ps reload` | Reload configuration |

### Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `powersink.setup.sink` | Register Sink nodes | Everyone |
| `powersink.setup.source` | Register Source nodes | Everyone |
| `powersink.list.self` | View own nodes | Everyone |
| `powersink.list.other` | View others' nodes | OP |
| `powersink.remove.self` | Delete own nodes | Everyone |
| `powersink.remove.other` | Delete others' nodes | OP |
| `powersink.limit.<group>` | Node limit group (see config) | Depends on group |

### Node Limits

You can define per-group limits on how many nodes each player can have. Limits are evaluated in order; the first matching group applies. The `default` group always acts as a fallback.

Players receive limits via the permission `powersink.limit.<group>`.

### Configuration (`config.yml`)

```yaml
powersink:
  tickInterval: 2           # Process nodes every N ticks (20 ticks = 1s)
  allowCreate:
    sink: false             # Allow players to create Sink nodes?
    source: true            # Allow players to create Source nodes?

activationItems:
  source: REDSTONE          # Item held to register a Source
  sink: GLOWSTONE_DUST      # Item held to register a Sink
  remove: LEVER             # Item held to remove a node

rates:
  maxEnergyTransaction: 1000000  # Max FE transferred per tick
  ratio: 0.05                   # Final multiplier (energy → money)
  function: log                 # Conversion function: "log" or "root"
  base: 100.0
  multiplier: 10.0
  shift: 10.0

limits:
  - group: admin
    sink: 1000
    source: 1000
  - group: default
    sink: 10
    source: 10
```

**Energy → Money Formula:**

```
money = ratio × (multiplier × f(energy) + shift)
```

- `f(energy)` = `log_base(energy)` when function is `"log"`
- `f(energy)` = `energy^(1/base)` when function is `"root"`

### Dependencies

- **Required**: a Paper/Spigot 1.20.1 server running on Mohist or Arclight (for Forge mod support)
- **Required**: [Vault](https://www.spigotmc.org/resources/vault.34315/) — economy abstraction
- **Optional**: Mekanism, Immersive Engineering

### Building

```bash
./gradlew clean build
```

The JAR will be at `build/libs/PowerSink-v1.0.jar`.

### Credits

- Original SpongeForge concept by voidstar
- MoneyCalculator borrows from [PowerMoney](https://github.com/AuraDevelopmentTeam/PowerMoney)
- 1.20.1 Paper port & mod compatibility by nyamura

---

<a name="chinese"></a>
## 中文

一款 Paper/Spigot 1.20.1 插件，允许玩家通过游戏内货币买卖能量。通过 Vault 接入经济系统，支持在 Mohist/Arclight 混合服务端上兼容多种模组能量后端。

### 支持的能量后端

| 后端 | 检测方式 |
|------|----------|
| Forge Energy (FE/RF) | NMS 反射自动检测 |
| Mekanism | 安装 Mekanism 模组后自动检测 |
| Immersive Engineering | 安装 IE 模组后自动检测 |

### 概念定义

| 术语 | 含义 |
|------|------|
| **输出节点（Source）** | 从中提取能量的方块 — 玩家出售能量获得金币 |
| **接收节点（Sink）** | 向其输送能量的方块 — 玩家购买能量支付金币 |
| **节点（Node）** | 注册为 Source 或 Sink 的方块统称 |

### 使用方式

1. **注册输出节点**：手持**红石粉**左键能量存储方块 — 能量将被提取，你获得金币。
2. **注册接收节点**：手持**萤石粉**左键能量存储方块 — 能量将被输送，你支付金币。
3. **删除节点**：手持**拉杆**左键已注册的节点即可删除。

> 激活物品和其他多项设置均可在 `config.yml` 中自定义。

### 命令

| 命令 | 说明 |
|------|------|
| `/ps` 或 `/powersink` | 显示帮助和子命令 |
| `/ps list [玩家名]` | 查看某玩家（或自己）的节点列表 |
| `/ps remove <ID>` | 按 ID 删除节点 |
| `/ps reload` | 重新加载配置文件 |

### 权限

| 权限节点 | 说明 | 默认 |
|----------|------|------|
| `powersink.setup.sink` | 注册接收节点 | 所有人 |
| `powersink.setup.source` | 注册输出节点 | 所有人 |
| `powersink.list.self` | 查看自己的节点 | 所有人 |
| `powersink.list.other` | 查看他人的节点 | OP |
| `powersink.remove.self` | 删除自己的节点 | 所有人 |
| `powersink.remove.other` | 删除他人的节点 | OP |
| `powersink.limit.<组名>` | 节点数量限制组（见配置） | 视组而定 |

### 节点数量限制

可为每个权限组定义节点数量上限。按配置顺序依次检查，首个匹配的组生效。`default` 组始终作为兜底。

通过权限 `powersink.limit.<组名>` 将玩家分配到对应限制组。

### 配置 (`config.yml`)

```yaml
powersink:
  tickInterval: 2           # 节点处理间隔（单位：tick，20 tick = 1秒）
  allowCreate:
    sink: false             # 是否允许玩家创建接收节点？
    source: true            # 是否允许玩家创建输出节点？

activationItems:
  source: REDSTONE          # 注册输出节点时手持的物品
  sink: GLOWSTONE_DUST      # 注册接收节点时手持的物品
  remove: LEVER             # 删除节点时手持的物品

rates:
  maxEnergyTransaction: 1000000  # 每次 tick 最大传输能量 (FE)
  ratio: 0.05                   # 最终倍率（能量 → 金钱）
  function: log                 # 转换函数："log" 或 "root"
  base: 100.0
  multiplier: 10.0
  shift: 10.0

limits:
  - group: admin
    sink: 1000
    source: 1000
  - group: default
    sink: 10
    source: 10
```

**能量 → 金钱转换公式：**

```
实际金钱 = ratio × (multiplier × f(能量) + shift)
```

- `f(能量)` = `log_base(能量)`（函数为 `"log"` 时）
- `f(能量)` = `能量^(1/base)`（函数为 `"root"` 时）

### 依赖

- **必需**：运行在 Mohist 或 Arclight 上的 Paper/Spigot 1.20.1 服务端（用于 Forge 模组兼容）
- **必需**：[Vault](https://www.spigotmc.org/resources/vault.34315/) — 经济系统抽象层
- **可选**：Mekanism、Immersive Engineering

### 构建

```bash
./gradlew clean build
```

JAR 文件位于 `build/libs/PowerSink-v1.0.jar`。

### 鸣谢

- 原 SpongeForge 概念：voidstar
- MoneyCalculator 借鉴自 [PowerMoney](https://github.com/AuraDevelopmentTeam/PowerMoney)
- 1.20.1 Paper 移植与模组兼容：nyamura
