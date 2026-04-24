# Gravitation

[中文说明](README.md)

`Gravitation` is a NeoForge mod for Minecraft 1.21.1, focused on gameplay mechanics built around the Sable/Simulated physics ecosystem.

The current core feature is the **Redstone Mass-Energy Converter**: block mass changes with redstone power and its max mass can be controlled from CC: Tweaked computers.

## Features

- Dynamic effective mass driven by redstone signal strength.
- In-game max-mass tuning through the existing scroll behavior.
- Configurable min/max mass limits.
- Optional CC: Tweaked integration for scripted control.

## Dependencies and Compatibility

### Base environment

- Minecraft: `1.21.1` (range in `gradle.properties`: `[1.21.1,1.21.2)`)
- NeoForge: `21+` (range in `gradle.properties`: `[21,)`)
- Java: `21`

### Required runtime dependencies

- AnvilLib modules (util/registrum/config)
- Simulated stack (including Sable, Flywheel, Offroad, Aeronautics)

### Optional dependencies

- CC: Tweaked: enables peripheral control (capability is not registered when CC is absent)
- JEI: recipe/info display convenience

## Installation (Players)

1. Install a compatible NeoForge for Minecraft `1.21.1`.
2. Put this mod and all required dependencies into your `mods` folder.
3. Add CC: Tweaked if you want computer-side control.

If the game fails to start, check missing dependencies and version mismatch first.

## Build (Developers)

### Prerequisites

- JDK `21`
- Use the bundled Gradle Wrapper (`gradlew` / `gradlew.bat`)

### Common commands

```powershell
./gradlew.bat build
./gradlew.bat runClient
./gradlew.bat runServer
./gradlew.bat runData
```

Build artifacts are generated in `build/libs/`.

## Development Notes

- Main source directories: `src/main/java`, `src/main/resources`.
- Generated resources directory: `src/generated`.
- CC peripheral capability is registered via `RegisterCapabilitiesEvent`, gated by a `computercraft` load check during mod init.

## CC: Tweaked Peripheral API

With CC: Tweaked installed, the Redstone Mass-Energy Converter exposes this peripheral type:

- `redstone_mass_energy_converter`

### Lua methods

- `getMaxMass()` -> `number`
  - Returns the currently configured max mass.
- `setMaxMass(value)` -> `number`
  - Sets max mass and returns the applied value.
  - The value is clamped to configured min/max bounds.
- `getCurrentMass()` -> `number`
  - Returns the effective current mass (affected by redstone power and max mass).
- `getRange()` -> `{ min = number, max = number }`
  - Returns the configured allowed range.

### Lua example

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

## Configuration

Config class: `src/main/java/dev/dubhe/gravitation/GravitationConfig.java`

- `redstoneMassEnergyConverterMinMass`
  - Default: `1`
  - Range: `1 - 1024`
- `redstoneMassEnergyConverterMaxMass`
  - Default: `100`
  - Range: `1 - 1024`
- `physicsStaffAllowedMaxControlSize`
  - Default: `32768`
  - Range: `4096 - 1073741824`

`min/max` are normalized in code to prevent inverted bounds.

## FAQ

- Peripheral not found: ensure CC: Tweaked is installed and the target block is loaded.
- `setMaxMass` seems ignored: verify it is not clamped by config bounds.
- Unexpected mass behavior: verify redstone updates/power level.
- Startup crash: check Minecraft/NeoForge/dependency versions first.

## License

- Code: `GNU LGPL 3.0` (see [`LICENSE`](./LICENSE))
- Assets: all rights reserved unless explicitly stated (see [`ASSETS_LICENSE`](./ASSETS_LICENSE))
