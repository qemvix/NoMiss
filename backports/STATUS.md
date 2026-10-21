# NoMiss 1.2 local PC release checkpoint — 2026-10-21

Development and testing ran locally in `C:\Users\elect\NoMiss`, freshly cloned from current master `e77e2f249d32689c5d0f2e7311da107e2d2ffc83`. No cloud environment was used. The previous local checkout was preserved as a recoverable backup.

## Release scope

The user requested publication of completed builds and omission of unfinished editions. **45 additional editions are staged for the existing v1.2 release**, titled exactly **1.2 - the updates update**. The original 26.2/26.3 artifact, release tag, license and copyright notices are preserved. Publication status is recorded below after upload verification.

See [VALIDATION.md](VALIDATION.md) for the exact Minecraft/loader/Java matrix and per-edition evidence. Full gameplay testing remains incomplete; compilation and package auditing are not presented as gameplay verification.

## Verified locally

- Existing root 26.2 and 26.3 builds each compiled against real game APIs and passed 37 tests (34 rule/configuration plus three universal-package tests).
- All 48 generated configurations passed 258 shared-rule checks, compiled with `javac --release 17` and executed on JDK 25. All 907 generated Java files syntax-parsed. These remain separate from game API builds.
- Twelve Python artifact/cache-guard regression tests passed.
- The 45 staged additions executed 243 tests across real API builds: four per modern edition, seven per legacy edition. Each final JAR passed metadata, exact version selector, minimum bytecode, bundled dependency, production mixin/signature and legacy Gson relocation audits.
- Production 26.1 launched on Java 25 and reached resource/audio/atlas initialization without a mod load failure.
- Production 1.21.11 on Java 21 and 1.8.9 on Java 17 passed all six test-only client render checks: General, Weapons, Profiles, HUD, picker icons and narrowed search.
- The final 1.8.9 JAR additionally passed in-client exact-item checks: durability/repair-cost changes retained the signature; renaming changed it. Empty ignored-only NBT is normalized, so a first repair-cost tag does not break matching.
- Original release asset verified: 562,644 bytes, SHA-256 `74112f6d8b6d77abf94c6a50fa1f103eff03dfd29ec98db5f7e95d0e491e3aeb`.

Only those three representative clients were launched. No backport is claimed fully gameplay-tested. Attack/mining behavior, binding persistence/menu typing, profile/server transitions, HUD interaction in a world and import/export UI were not all exercised end to end across the new editions.

## Compatibility fixes

- Native Windows wrapper/Python selection and explicit UTF-8 source/configuration generation.
- Strict production-namespace cache discovery, including the similar legacy development/production JAR names; ambiguous cache selections remain errors.
- Matrix checks reject missing/skipped test coverage and support separate report directories.
- Legacy option/keybinding packages, item labels/icons and native widget APIs corrected using actual compilation and bytecode inspection.
- 1.7.2–1.7.8 use their actual distinct screen/widget member identities; 1.7.2 uses the GameOptions-based scaled-window constructor. 1.6 uses pre-registry item constants.
- 1.19.4 item icons use its PoseStack renderer signature. Pre-1.19.3 accessibility integration injects the actually declared `createFooter`, rather than an inherited `init` that cannot be injected directly.

## Omitted editions

- **1.19.3:** icon-rendering API adaptation did not compile; excluded at the user's publication cutoff.
- **1.19.1:** validation unfinished at that cutoff; its in-progress build was stopped and no artifact is published.
- **1.7.9:** dedicated generation-1 and generation-2 Yarn builds are absent. Inspected actual Mojang client and intermediary mappings: the Screen `(ZI)V` callback uses `confirmResult`, while the consumer interface uses `method_21923`. A narrow 1.7.8-name/interface reconciliation still failed strict Minecraft remapping with source-name hierarchy conflicts across the real screen subclasses. No conflict checks were disabled; the normal explicit Gradle guard remains. No 1.7.10/1.7.8 game JAR is relabeled as 1.7.9.

## Publication

Prepared for the existing [v1.2 release](https://github.com/qemvix/NoMiss/releases/tag/v1.2). Upload verification and pushed source commits are recorded after publication completes.
