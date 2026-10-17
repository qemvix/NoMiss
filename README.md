# No Miss

Client-side Fabric mod for Minecraft 26.2 and 26.3. While you hold a **sword, axe or mace**, left-click does
nothing unless an entity is under your crosshair and inside your reach. No more air swings, no more
wasted attack cooldown. Every other item behaves like vanilla.

## Build

Needs JDK 25 and an internet connection (Gradle downloads everything else).

First open `gradle.properties` and set `minecraft_version` to the version you play (`26.2` or `26.3`).

    ./gradlew build        # Windows: gradlew.bat build

The finished mod is `build/libs/nomiss-1.0.0.jar` (not the `-sources` one). Drop it in your
`mods` folder next to Fabric Loader 0.19.5+ (Fabric API is not required).

## Options

At the top of `src/client/java/com/nomiss/client/mixin/MinecraftMixin.java`:

- `ALLOW_BLOCK_BREAKING = true` (default): you can still left-click blocks with these weapons
  (chopping logs with an axe, cutting cobwebs with a sword).
- `ALLOW_BLOCK_BREAKING = false`: strict mode, the weapon only ever swings at an entity in reach.

## How it works

`Minecraft.startAttack()` is what runs when you press attack. The game's `hitResult` is only an
ENTITY when you're aiming at a hitbox within entity interaction range, so the mixin cancels the
attack whenever the result is anything else.

## If the build fails

The mixin uses Mojang names (`startAttack`, `hitResult`, `player`). If Mojang renamed one in a
newer snapshot, the compiler error will name the symbol: fix it in `MinecraftMixin.java`.
