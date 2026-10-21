# Local PC checkpoint — 2026-10-21

Work is now running locally in `C:\Users\elect\NoMiss`, freshly cloned from master `e77e2f249d32689c5d0f2e7311da107e2d2ffc83`. The previous local checkout was preserved as a recoverable backup. No cloud environment is used.

The existing root implementation, license, release tag and published 26.2/26.3 download are preserved. No new downloads have been published, and no new edition is yet claimed as gameplay verified.

## Verified on this PC

- Root builds for **both 26.2 and 26.3** succeeded against actual Minecraft APIs. Each ran 37 tests (34 configuration/rule tests plus three universal-package tests), with zero failures/errors.
- The 48 generated configurations passed all **258 shared-rule checks**, compiled with `javac --release 17` and executed on JDK 25. All **907 generated Java sources** syntax-parsed. These checks alone do not validate Minecraft compatibility.
- All 12 Python regression tests passed: seven artifact-auditor guards plus five production-cache selection checks.
- Full builds, four tests per edition, and production-JAR package/mixin audits passed for **26.1, 26.1.2, 1.21.11, 1.21.10 and 1.21.8**.
- **Legacy Fabric 1.8.9 and 1.7.10** compiled and produced remapped JARs, ran seven tests each, and passed production-JAR audits (including all seven injections/accessors and relocated Gson).
- No representative client has been launched or gameplay-tested yet. Build/audit success is not a support claim.

## Implemented in this checkpoint

- Native Windows Gradle-wrapper execution and configurable Python launcher; avoid broken Microsoft Store `python3` aliases.
- Explicit UTF-8 generation/configuration I/O to avoid Windows code-page failures and corrupted labels.
- Production Minecraft cache selection recognizes Windows paths and rejects the similarly named legacy development JAR. Ambiguous caches still fail closed.
- Matrix test-report checks require all seven legacy tests (four on modern editions), excluding skipped tests.
- Correct legacy option/keybinding packages, Options screen target, native widget visibility, item display names and icon renderer calls, based on real compilation and `javap` inspection. The 1.6 family uses pre-registry item constants.

## Work continuing

1. Complete real API builds and strict package/mixin audits for all 48 requested targets; inspect `backports/build/matrix/` for local logs/results/checksums.
2. Resolve 1.7.9's actual intermediary callback hierarchy. Official generation-1 and generation-2 Yarn metadata were queried on this PC; neither publishes a dedicated 1.7.9 Yarn build. Its explicit guard remains in place pending a correct solution.
3. Launch representative production editions on their intended Java versions and test attack/mining rules, settings/picker/icons/search, bindings, profiles/server restoration, exact-item matching and HUD controls.
4. Publish only verified downloads in the existing v1.2 release, with the exact title **1.2 - the updates update**. Preserve its existing artifact and tag.

The old cloud proxy 403 failures are historical: Fabric, Legacy Fabric and Mojang are reachable from this PC. JDK 25 and Python 3.14 are installed locally. Client minimum-Java behavior remains to be tested separately.
