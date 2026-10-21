# NoMiss 1.2 backport validation — 2026-10-21

These 45 new production JARs passed exact-version API compilation, executed tests and production package/mixin audits. Full gameplay testing is incomplete. [Downloads, requirements and SHA-256 checksums](https://github.com/qemvix/NoMiss/releases/tag/v1.2).

The original 26.2/26.3 universal download is preserved. Root builds for both games passed 37 tests each; its original release asset remains 562,644 bytes with SHA-256 `74112f6d8b6d77abf94c6a50fa1f103eff03dfd29ec98db5f7e95d0e491e3aeb`.

| Minecraft | Loader | Minimum Java | Executed tests | Package/mixin audit | Client launched | Settings/picker UI | Full gameplay |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 26.1 | fabric | 25 | 4 | Pass | Yes | No | No |
| 26.1.2 | fabric | 25 | 4 | Pass | No | No | No |
| 1.21.11 | fabric | 21 | 4 | Pass | Yes | Yes | No |
| 1.21.10 | fabric | 21 | 4 | Pass | No | No | No |
| 1.21.9 | fabric | 21 | 4 | Pass | No | No | No |
| 1.21.8 | fabric | 21 | 4 | Pass | No | No | No |
| 1.21.7 | fabric | 21 | 4 | Pass | No | No | No |
| 1.21.6 | fabric | 21 | 4 | Pass | No | No | No |
| 1.21.5 | fabric | 21 | 4 | Pass | No | No | No |
| 1.21.4 | fabric | 21 | 4 | Pass | No | No | No |
| 1.21.3 | fabric | 21 | 4 | Pass | No | No | No |
| 1.21.2 | fabric | 21 | 4 | Pass | No | No | No |
| 1.21.1 | fabric | 21 | 4 | Pass | No | No | No |
| 1.21 | fabric | 21 | 4 | Pass | No | No | No |
| 1.20.6 | fabric | 21 | 4 | Pass | No | No | No |
| 1.20.5 | fabric | 21 | 4 | Pass | No | No | No |
| 1.20.4 | fabric | 17 | 4 | Pass | No | No | No |
| 1.20.3 | fabric | 17 | 4 | Pass | No | No | No |
| 1.20.2 | fabric | 17 | 4 | Pass | No | No | No |
| 1.20.1 | fabric | 17 | 4 | Pass | No | No | No |
| 1.20 | fabric | 17 | 4 | Pass | No | No | No |
| 1.19.4 | fabric | 17 | 4 | Pass | No | No | No |
| 1.19.2 | fabric | 17 | 4 | Pass | No | No | No |
| 1.19 | fabric | 17 | 4 | Pass | No | No | No |
| 1.8.9 | legacy-fabric | 17 | 7 | Pass | Yes | Yes | No |
| 1.8.8 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.8.7 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.8.6 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.8.5 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.8.4 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.8.3 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.8.2 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.8.1 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.8 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.7.10 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.7.8 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.7.7 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.7.6 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.7.5 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.7.4 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.7.3 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.7.2 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.6.4 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.6.2 | legacy-fabric | 17 | 7 | Pass | No | No | No |
| 1.6.1 | legacy-fabric | 17 | 7 | Pass | No | No | No |

The three launched clients used their listed minimum Java versions on Windows. UI checks rendered General, Weapons, Profiles and HUD pages, item icons and a narrowed search. Legacy 1.8.9 also passed actual exact-item signature checks for durability, repair cost and renaming. 26.1 received a load check, not the additional UI checks.

The remaining editions were compiled and audited but were not individually launched. No claim is made that every legacy game works on every newer JVM. End-to-end attack/mining, rebinding/typing, server transitions, HUD behavior in a world and import/export UI remain incompletely tested across the backports.

## Omitted

| Minecraft | Reason |
| --- | --- |
| 1.19.3 | Icon-rendering API adaptation failed compilation at the publication cutoff. |
| 1.19.1 | Validation unfinished when the user requested publication of completed builds. |
| 1.7.9 | Actual intermediary callback hierarchy conflicts remain; explicit build guard retained. |

Legacy editions adapt Accessibility/modern Mod Menu integration to the vanilla Options menu and a rebindable settings shortcut, with paginated native pages. Pre-1.9 cooldown controls are omitted and imported cooldown rules are ignored during attacks. Unavailable vanilla weapon categories/icons are excluded per edition. See [README.md](README.md) and [STATUS.md](STATUS.md).
