# 重力学 (Gravitation)

[English](README.en.md)

`Gravitation` 是一个基于 NeoForge 的 Minecraft 1.21.1 模组，聚焦于与 Sable/Simulated 物理系统相关的可玩机制。

当前核心内容是 **红石质能转换装置**：方块质量会随红石信号变化，并支持通过 CC: Tweaked 外设脚本控制“最大质量”。

## 功能特性

- 红石信号强度动态影响装置实际质量。
- 支持在游戏内滚轮调节装置最大质量。
- 支持配置文件限定可调节质量上下限。
- 可选集成 CC: Tweaked，支持电脑脚本读写最大质量。

## 依赖与兼容

### 基础环境

- Minecraft: `1.21.1`（版本范围见 `gradle.properties`: `[1.21.1,1.21.2)`）
- NeoForge: `21+`（版本范围见 `gradle.properties`: `[21,)`）
- Java: `21`

### 必需依赖（运行时）

- AnvilLib（util/registrum/config）
- Simulated 及其相关依赖（含 Sable、Flywheel、Offroad、Aeronautics）

### 可选依赖

- CC: Tweaked：启用外设控制能力（未安装时不会注册外设能力）
- JEI：仅用于配方/信息展示

## 安装（玩家）

1. 安装匹配版本的 NeoForge（`21.x`，Minecraft `1.21.1`）。
2. 将本模组与必需依赖放入 `mods` 文件夹。
3. 如需电脑控制功能，再加入 CC: Tweaked。

如果启动报错，优先检查依赖缺失或版本不匹配。

## 构建（开发）

### 前置条件

- JDK `21`
- 使用仓库内 Gradle Wrapper（`gradlew`/`gradlew.bat`）

### 常用命令

```powershell
./gradlew.bat build
./gradlew.bat runClient
./gradlew.bat runServer
./gradlew.bat runData
```

构建产物默认位于 `build/libs/`。

## 开发说明

- 主要源码目录：`src/main/java`、`src/main/resources`。
- 生成资源目录：`src/generated`。
- CC 外设能力通过 `RegisterCapabilitiesEvent` 注册，并在模组初始化时检测 `computercraft` 是否存在后再挂载。

## CC: Tweaked 外设 API

当安装 CC: Tweaked 后，红石质能转换装置会暴露外设类型：

- `redstone_mass_energy_converter`

### Lua 方法

- `getMaxMass()` -> `number`
  - 获取当前“最大质量”设置值。
- `setMaxMass(value)` -> `number`
  - 设置最大质量，并返回实际生效值。
  - 会被配置项上下限自动钳制。
- `getCurrentMass()` -> `number`
  - 获取当前实际质量（受红石强度和最大质量共同影响）。
- `getRange()` -> `{ min = number, max = number }`
  - 获取当前配置允许范围。

### Lua 示例

```lua
local p = peripheral.find("redstone_mass_energy_converter")
if not p then
  error("peripheral not found")
end

local range = p.getRange()
print("range:", range.min, range.max)
print("old max:", p.getMaxMass())

local applied = p.setMaxMass(64)
print("applied max:", applied)
print("current mass:", p.getCurrentMass())
```

## 配置项

配置类：`src/main/java/dev/dubhe/gravitation/GravitationConfig.java`

- `redstoneMassEnergyConverterMinMass`
  - 默认值：`1`
  - 范围：`1 - 1024`
- `redstoneMassEnergyConverterMaxMass`
  - 默认值：`100`
  - 范围：`1 - 1024`
- `physicsStaffAllowedMaxControlSize`
  - 默认值：`32768`
  - 范围：`4096 - 1073741824`

`min/max` 在代码中会自动归一化，避免错误配置导致上下限反转。

## 常见问题（FAQ）

- 看不到外设：确认已安装 CC: Tweaked，且目标方块已正常放置并加载。
- `setMaxMass` 看似无效：检查是否被配置上下限钳制。
- 质量变化异常：检查红石信号是否正确更新到方块。
- 启动崩溃：优先核对 Minecraft / NeoForge / 依赖版本是否一致。

## 许可证

- 代码：`GNU LGPL 3.0`（见 [`LICENSE`](./LICENSE)）
- 资源：默认保留所有权利，除非另有声明（见 [`ASSETS_LICENSE`](./ASSETS_LICENSE)）
