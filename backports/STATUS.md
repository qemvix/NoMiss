# Backport checkpoint — 2026-10-21

The root 26.2/26.3 implementation and existing v1.2 download are unchanged.

## Restored source

- Requested 48-release target matrix, isolated modern and Legacy Fabric Gradle projects, and reproducible source generators.
- Modern API adapters covering the requested 1.19–1.21.11 and 26.1/26.1.2 targets.
- Legacy client input, native settings/picker, profiles, server assignments, import/export, exact-item matching, HUD, and rebindable keys.
- Shared-rule regression tests, syntax checks, build collection and package/mixin auditing tools.

**All game-specific adapters remain unverified on this replacement machine.** Rebuilding from the lost environment's implementation notes is not evidence that these restored files compile or run.

## Verified locally

- 258 shared-rule regression checks passed across the 48 generated configurations (4 per modern edition, 7 per legacy edition), compiled with `javac --release 17` against Gson 2.8.9 and executed on JDK 25.
- Syntax parsing passed for all 907 generated Java files. This does not resolve Minecraft APIs.
- Seven artifact-auditor regression tests passed, including rejection of wrong Minecraft selectors, excessive Java bytecode levels, missing injection targets, mismatched injection parameters, missing dependencies and the wrong resource-loader generation.
- Before restoring the backport files, the 34 existing Minecraft-independent root tests passed.
- The existing 562,644-byte 26.2/26.3 release JAR was retrieved and its SHA-256 verified against `74112f6d8b6d77abf94c6a50fa1f103eff03dfd29ec98db5f7e95d0e491e3aeb`.
- No new installable JARs have been built or uploaded from this checkpoint.

## Blocking full validation

The live cloud network policy still lacks the custom Minecraft/Fabric domains. Actual requests to Fabric, Legacy Fabric and Mojang metadata fail with proxy `403 Forbidden`. The environment's saved draft contains the required domains, but the draft has not been applied to the live environment. Full Gradle builds require publishing/applying that configuration through the environment settings. A real 26.1 matrix build was attempted and failed during Fabric Loom plugin resolution before Minecraft compilation.

Minecraft 1.7.9 additionally has no published generation-1 Yarn mapping in the selected family. Reusing 1.7.10 names previously encountered a callback-method hierarchy conflict. Its Gradle target deliberately fails with an explicit error pending a correct mapping solution; do not remove the guard just to produce a JAR. Investigate generation-2 mappings or reconcile the actual intermediary hierarchy, then compile and launch that edition.

## Work remaining

1. Apply the saved environment network configuration.
2. Compile each generated client against its actual Minecraft/loader dependencies and fix API/mapping errors.
3. Resolve the 1.7.9 mapping issue.
4. Run root builds for both 26.2 and 26.3; run all port tests and audits.
5. Launch representative editions across GUI/input/API families and test attack blocking, permitted mining, profiles, bindings, item selection, exact matching, and HUD.
6. Push tested changes to master, then update the existing v1.2 release title/notes/downloads. Report any confirmed feature omissions honestly.

Source generation and shared configuration tests are completed checks; release compatibility is still pending.
