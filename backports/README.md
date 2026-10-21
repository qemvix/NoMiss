# NoMiss 1.2 version ports

These projects keep version-specific adapters apart from the existing root build for Minecraft 26.2/26.3. **45 additional editions have completed real API builds, tests and production package/mixin audits.** Their downloads are collected in the [existing v1.2 release](https://github.com/qemvix/NoMiss/releases/tag/v1.2). Full gameplay testing is incomplete. See [VALIDATION.md](VALIDATION.md) for exact per-edition coverage and [STATUS.md](STATUS.md) for remaining work. **1.19.3, 1.19.1 and 1.7.9 are omitted.**

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

On Windows PowerShell, use `./gradlew.bat` and quote version properties:

```powershell
./gradlew.bat -p backports/fabric '-Pminecraft_version=1.21.11' clean build --no-daemon --max-workers=2
./gradlew.bat -p backports/legacy '-Pminecraft_version=1.8.9' clean build --no-daemon --max-workers=2
py backports/build_matrix.py 1.21.11 1.8.9 --java-home 'C:\path\to\jdk-25'
```

The projects use `py` on Windows and `python3` elsewhere. Override with `-Ppython_executable=/absolute/path/to/python` if needed. The matrix runner uses its own Python interpreter and selects the native Gradle wrapper. Source and configuration files are always read/written as UTF-8.

Generate sources without downloading Minecraft:

```sh
python3 backports/generate_fabric.py 1.19 backports/build/preview-1.19
python3 backports/generate_legacy.py 1.6.1 backports/build/preview-1.6.1
```

Run shared-rule tests and Java syntax checks for the complete requested matrix:

```sh
python3 backports/offline_check.py --java-home "$JAVA_HOME"
python3 -m unittest discover -s backports/tests -p 'test_*.py' -v
```

This command compiles only Minecraft-independent configuration/rules and parses the client sources. It **does not** validate Minecraft APIs, mappings, mixins, loader startup, or gameplay. First use downloads Gson and JUnit from Maven Central, verifying their published checksums. Results live under `backports/build/offline-check/`.

Build the ports and collect only JARs that pass unit tests and package/mixin audits (use `--output PATH` to keep independent run reports separate):

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

Builds need the usual Maven/Gradle hosts plus `maven.fabricmc.net`, `meta.fabricmc.net`, `piston-meta.mojang.com`, `piston-data.mojang.com`, `libraries.minecraft.net`, `maven.terraformersmc.com`, `maven.legacyfabric.net`, and `meta.legacyfabric.net`. Client smoke tests also need Minecraft assets. Check actual access on the build machine. The earlier cloud network-policy blocker does not apply to the current local PC, where these hosts are reachable.

## Release plan

Update master directly and retain the existing `v1.2` release. Once builds and client checks pass, collect the downloads under the title **1.2 - the updates update**, with notes focused on added version support, loader/Java requirements, and confirmed omissions. Preserve the existing 26.2/26.3 artifact. Do not move the release tag to an unverified checkpoint.

## Isolated Windows client checks

`launch_smoke.py` launches an audited production JAR in `backports/build/client-smoke/MINECRAFT`, with official checksum-verified libraries/assets and a specified Java runtime. It does not read account tokens or modify installed Minecraft profiles, mods, configuration or worlds. It opens a visible game window and closes that process after the specified interval.

```powershell
py backports/launch_smoke.py 1.8.9 path/to/nomiss-1.8.9-legacy-fabric-1.2.jar --java-home 'C:\path\to\java-17' --ui-jdk 'C:\path\to\jdk-25' --seconds 60
```

`--ui-jdk` compiles a separate test-only mod using `client_ui_smoke.py` and `tests/client/UiChecks.java`. It opens/renders all four settings pages, item icons and a narrowed search. Legacy checks also verify that durability/repair-cost changes retain the exact-item signature while renaming changes it. The test mod is never bundled into NoMiss downloads. These are client/UI checks, not full gameplay tests. A running process alone is not a gameplay pass.

## Upload the verified v1.2 editions to Modrinth from your PC

`publish_modrinth.py` publishes only the files pinned by `v1.2-SHA256SUMS.txt`: 45 additional editions and the unchanged original 26.2/26.3 JAR (46 files, 47 Minecraft releases). It does not rebuild Minecraft, launch a client, change the project description/moderation status, modify existing versions, or use GitHub credentials. Missing files are fetched from the existing GitHub v1.2 release and verified before use.

On Windows, double-click `backports/upload_modrinth.cmd`. A masked text box accepts Ctrl+V and shows dots as paste feedback. Click Continue to start. Or run:

```powershell
py backports/publish_modrinth.py --project nomiss --publish --token-dialog
```

Create a **Modrinth** personal access token in [Modrinth settings](https://modrinth.com/settings/pats) with **Create Versions** permission (`VERSION_CREATE`). For a private/draft project, also allow reading private projects/versions if the token settings offer those scopes. The account must have upload permission on the project. Enter the token only in the local masked dialog; it is not saved, printed, placed in command history, or sent to GitHub. An existing `MODRINTH_TOKEN` environment variable can be used instead of `--token-dialog` for unattended runs. `--prompt-token` remains available for a hidden terminal prompt; characters are invisible there even after a successful paste. Never commit a token or send it in chat.

A read-only plan needs no upload permission:

```powershell
py backports/publish_modrinth.py --project nomiss
# Authenticate to check duplicates inside a private project:
py backports/publish_modrinth.py --project nomiss --prompt-token
```

Without authentication, private project checks remain explicitly pending; a 404 does not mean a new project should be created. No upload occurs unless `--publish` is present. Reports are saved to `backports/build/modrinth/report.json`, never including credentials. A new report starts before validation/API calls, records the current phase, and preserves errors even when authentication fails early. The launcher also shows a final success/error dialog; errors are saved separately to `backports/build/modrinth/last-error.txt`.

Each exact Minecraft edition gets a separate version entry with the correct `fabric` or `legacy-fabric` tag. The original JAR has both `26.2` and `26.3` tags. Display names retain **1.2 - the updates update**, and version identifiers such as `1.2+mc1.21.11.fabric` distinguish downloads while every JAR's internal mod version stays **1.2**. The default channel is `release`, matching the GitHub release; `--channel beta` or `--channel alpha` changes the channel for newly uploaded entries. Changelogs include minimum Java/loader requirements, per-edition validation limits, SHA-256, and legacy UI/cooldown adaptations. Mod Menu is optional where supported; bundled APIs do not become unnecessary external requirements.

Before the first upload, the tool validates every local checksum, metadata/Java selector, accepted Modrinth compatibility tag, and existing version collision. Identical SHA-512 files with matching tags are skipped even if their existing version number is different. Conflicting files/version numbers stop the run without overwriting anything. Every successful upload is checked again using a separate API request. Repeating the same command resumes after interruption without reuploading already confirmed files. POST requests are not blindly retried after uncertain network failures.

This uploader does not turn compile/package/UI evidence into a gameplay claim. Full gameplay testing of the additions remains incomplete; 1.19.1, 1.19.3 and 1.7.9 remain omitted. Review [VALIDATION.md](VALIDATION.md) for the precise coverage.
