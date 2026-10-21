# NoMiss 1.2 version ports

These projects keep version-specific adapters apart from the existing root build for Minecraft 26.2/26.3. **The backports are work in progress, not supported downloads yet.** See [STATUS.md](STATUS.md) for validation and blockers.

`targets.py` records all 48 additional requested releases: every official release from 1.19 through 1.21.11, 26.1 and 26.1.2, and every official release from 1.6 through 1.8.9. The latter range begins at 1.6.1: there was no final release named simply 1.6 or 1.7.

| Edition | Loader | Minimum Java |
| --- | --- | --- |
| 26.2 / 26.3, existing root build | Fabric | 25 |
| 26.1 / 26.1.2 | Fabric | 25 |
| 1.20.5 through 1.21.11 | Fabric | 21 |
| 1.19 through 1.20.4 | Fabric | 17 |
| 1.6.1 through 1.8.9 | Legacy Fabric | 17 |

The build tools run with JDK 25. Generated classes target the minimum Java version in the table. A minimum bytecode level alone does not prove every newer JVM can launch that old Minecraft edition.

## Generate and build

Use the root Gradle wrapper; no extra wrappers or branches are required. Python 3 is required for source generation.

```sh
export JAVA_HOME=/path/to/jdk-25
export PATH="$JAVA_HOME/bin:$PATH"
bash gradlew -p backports/fabric -Pminecraft_version=1.21.11 clean build --no-daemon --max-workers=2
bash gradlew -p backports/legacy -Pminecraft_version=1.8.9 clean build --no-daemon --max-workers=2
```

Generate sources without downloading Minecraft:

```sh
python3 backports/generate_fabric.py 1.19 backports/build/preview-1.19
python3 backports/generate_legacy.py 1.6.1 backports/build/preview-1.6.1
```

Run shared-rule tests and Java syntax checks for the complete requested matrix:

```sh
python3 backports/offline_check.py --java-home "$JAVA_HOME"
```

This command compiles only Minecraft-independent configuration/rules and parses the client sources. It **does not** validate Minecraft APIs, mappings, mixins, loader startup, or gameplay. First use downloads Gson and JUnit from Maven Central, verifying their published checksums. Results live under `backports/build/offline-check/`.

Build the ports and collect only JARs that pass unit tests and package/mixin audits:

```sh
python3 backports/build_matrix.py --java-home "$JAVA_HOME"
# Or select targets:
python3 backports/build_matrix.py 1.21.11 1.8.9 --java-home "$JAVA_HOME"
```

Reports, logs, checksums and downloads are written to `backports/build/matrix/`. This command never publishes a release. It stops at the first failure unless `--keep-going` is provided. Failed or untested ports must not be uploaded or advertised as supported. Follow with client launch/gameplay checks across the relevant API families before publication.

## Compatibility behavior

- All new profiles and built-in presets start with cooldown protection **OFF**. Modern imports and duplicates retain manual choices.
- Pre-1.9 editions have no cooldown controls and ignore imported cooldown rules during attacks.
- Presets contain swords only before 1.21; swords and maces from 1.21; swords, spears and maces from 1.21.11. The Modpack preset adds axes using the next available slot.
- Older item pickers omit unavailable vanilla weapon categories and icons.
- Modern modded weapon categories use Minecraft/common tags and, where available, tool classes or piercing components. Legacy editions use compatible tool subclasses because those games have no item tags.
- Legacy editions use the vanilla Options menu and the rebindable settings shortcut. Standard modern Mod Menu and Accessibility menus do not exist there.
- Legacy settings use paginated native widgets and a searchable item picker with icons. All five shortcuts can be rebound in the NoMiss menu, including editions with limited native Controls layouts.
- Exact matching ignores count, durability and repair cost; it retains names, enchantments and other properties. Legacy metadata is retained for items whose damage value chooses a variant.
- Legacy Gson is shaded into NoMiss's own namespace so the game's older Gson is not replaced.

## Network requirements

Builds need the usual Maven/Gradle hosts plus `maven.fabricmc.net`, `meta.fabricmc.net`, `piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net`, `maven.terraformersmc.com`, `maven.legacyfabric.net`, and `meta.legacyfabric.net`. Client smoke tests also need Minecraft assets. Publish the environment's saved network configuration through its supported settings workflow; saving a draft does not apply it to a running machine.

## Release plan

Update master directly and retain the existing `v1.2` release. Once builds and client checks pass, collect the downloads under the title **1.2 - the updates update**, with notes focused on added version support, loader/Java requirements, and confirmed omissions. Preserve the existing 26.2/26.3 artifact. Do not move the release tag to an unverified checkpoint.
