# NoMiss 1.2

Client-side mod with the original universal Fabric build for **Minecraft 26.2 and 26.3**, plus exact-version Fabric/Legacy Fabric backports. Prevent wasted attacks with selected weapons, categories, or exact items. Vanilla damage, reach and server rules remain in control; attacks are never queued or performed automatically.

Additional version downloads are collected in the [existing v1.2 release](https://github.com/qemvix/NoMiss/releases/tag/v1.2). See the [backport requirements](backports/README.md) and [per-edition validation table](backports/VALIDATION.md) before choosing an edition. The backports have limited representative client/UI checks; full gameplay testing is incomplete.


## Install

Put **`nomiss-1.2.jar`** in your `mods` folder with **Fabric Loader 0.19.5+** and **Java 25**. Remove older NoMiss JARs first. The required Fabric API modules are bundled. No server-side mod is required.

[Mod Menu](https://modrinth.com/mod/modmenu) is optional. Use a Minecraft 26.2 build (20.0.3+) on 26.2, or a Minecraft 26.3 build (21.0.0+) on 26.3.

## Open settings and change shortcuts

Open **Accessibility Settings → NoMiss Settings**, use NoMiss's configuration button in Mod Menu, or press **`;` / `:`** while playing. Accessibility Settings also retains the full-mod ON/OFF button.

All five shortcuts can be changed under **Options → Controls → Key Binds → NoMiss**. General has a **Key binds…** button leading there.

| Action | Default |
| --- | --- |
| Open settings | Semicolon `;` |
| Toggle NoMiss | Apostrophe `'` |
| Add held item | Unbound |
| Show/hide status indicator | Unbound |
| Next profile | Unbound |

Shortcuts are drained while typing in chat and menus, so they cannot activate later from a queued keypress.

## General

The menu has **General, Weapons, Profiles and HUD** pages. Changes apply and save immediately.

- **NoMiss: ON/OFF** is global across profiles.
- **Wait for cooldown** and **Allow block breaking** are defaults for the active profile. A weapon slot can inherit or override them.
- **Cooldown protection starts OFF in every built-in preset and every newly created profile.** Turn it on manually if wanted. Duplicating a profile deliberately copies its settings; migration and import preserve settings you previously chose.
- Individual Reset buttons restore one setting without altering weapon selections or unrelated options.

## Weapons

Each profile has **20 numbered slots**. Click a slot to select its weapon and configure its rules. The searchable picker shows icons for vanilla and registered mod items, plus these categories:

- All swords
- All spears
- All axes
- All maces
- All pickaxes
- All shovels
- All hoes

A category takes **one slot**, regardless of the number of matching weapons. New profiles start with All swords, All spears and All maces: three slots instead of fifteen individual weapon tiers.

Compatible modded weapons are recognized using Minecraft's corresponding item tags and common `c:tools/sword`, `spear`, `axe`, `mace`, `pickaxe`, `shovel`, and `hoe` tags. Items with the piercing-weapon component also count as spears. Mods that do not identify their weapon types this way can still be selected individually in the picker. Categories use the running world's tags, including data-pack changes.

**Add held item** adds the main-hand item type to the first empty slot. **Exact** adds an exact-item selection instead. Neither replaces an occupied slot. Duplicates, empty hands and a full list are reported. The slot editor also has **Use exact held item**, which replaces only that slot's selection and preserves its rules.

### Per-slot attack rules

Every slot has **Wait for cooldown** and **Allow block breaking** controls:

- **Profile default** follows General.
- **ON** or **OFF** overrides the profile for that selection.
- **Reset both rules** returns that slot to inheriting the profile defaults.

When selections overlap, **exact item wins over individual item type, which wins over category**. This allows a general sword category with a different rule for a particular sword. If multiple categories match, the lowest numbered matching category slot wins. Identical selections cannot occupy multiple slots.

### Exact-item protection

Exact matching compares the item's type and serialized properties, including custom name, enchantments and other components. **Stack count, durability and repair cost are ignored**, so normal use and repair do not remove protection. Renaming, enchanting, or changing other properties requires capturing the item again. Identical copies also match; this is not a unique physical-stack identifier.

Broader selections still apply to other items. To protect only one named sword, replace All swords with the exact held sword, or remove other selections that also cover its type. Exact-item capture requires joining a world and serializable item properties. Only a fingerprint and display label are saved, not the item's component payload.

**Default categories** replaces the active profile's slots with the three defaults; **Clear selection** asks before emptying its slots. Existing 1.1.x selections are preserved on upgrade, including their original numbered slots and unknown modded item IDs. Use Default categories when you want to replace an older expanded list with compact groups.

Block breaking is permitted by default in Survival and Modpack. Disabling it prevents selected items from breaking blocks, including while holding left-click. Spears use vanilla's minimum/maximum reach and collision checks; spectators bypass attack protection.

## Profiles and presets

Three built-in starting profiles are provided, all with cooldown protection **OFF**:

| Profile | Categories | Block breaking |
| --- | --- | --- |
| Survival | Swords, spears, maces | ON |
| PvP | Swords, spears, maces | OFF |
| Modpack | Swords, spears, maces, axes | ON |

Create, duplicate, rename, select and delete profiles on the Profiles page. **Create from preset…** adds a fresh preset without overwriting existing profiles. At least one profile must remain; up to 32 profiles can be saved. Deleting a profile asks for confirmation and removes its server assignments. The optional **Next profile** key cycles through saved profiles.

### Automatic server profiles

**Automatic server profiles** is optional and **OFF by default**. When enabled, joining a multiplayer server loads its remembered profile. Selecting a profile while connected remembers that choice for that server. On leaving, your previous manual profile is restored. While connected, saving retains the manual profile as the startup selection, so restarting elsewhere does not inherit a server override.

You can also explicitly remember or forget the current server's assignment from Profiles. These controls require a multiplayer connection. Server addresses are stored only in the local NoMiss configuration, normalized for case and the default port; non-default ports remain separate. Single-player worlds do not create server assignments. Turning automatic profiles off keeps the current selection as your manual profile.

### Import/export

**Copy all settings to clipboard** exports profiles, selections, exact-item fingerprints, per-slot rules and HUD settings as JSON. Keybindings stay in Minecraft's own options. **Server addresses and assignments are excluded, and automatic server profiles are disabled in exports.**

Paste this JSON into a message or file to share it. On the destination installation, copy the JSON and choose **Import settings from clipboard…**. NoMiss validates it before showing a confirmation. Import replaces all current profiles and display settings, clears server assignments and disables automatic server profiles. A backup of the current configuration is created before applying it. Invalid or oversized data is rejected; cancelling the confirmation leaves the configuration untouched.

## HUD

Each visual indicator has its own toggle:

- **Show NoMiss status**: ON/OFF and active profile; ON by default.
- **Show held-item protection**: whether the main-hand item is currently protected, accounting for the enabled toggle and spectator mode; OFF by default.
- **Explain blocked attacks**: a short reason after a rejected click—out of reach/no entity targeted, cooldown, or block breaking disabled; OFF by default.

Choose any screen corner, a size from **50–200%**, and opacity from **20–100%**. Each position/size/opacity control has its own Reset button. Indicators hide with F1 and while menus are open. Hiding an indicator does not disable protection or other indicators.

## Configuration and upgrading

Settings are saved atomically to **`config/nomiss.json`**, using schema version 2. Upgrading imports the old enabled flag, display options, manually chosen attack defaults and weapon slots into a **Migrated settings** profile. The original file is backed up as `nomiss.before-1.2-<id>.json` before the migrated configuration is saved.

Imports create `nomiss.before-import-<id>.json` backups. Invalid configuration files are preserved as `nomiss.broken-<timestamp>.json` before defaults are used. If the original cannot be preserved, saving is disabled for that session. The legacy `nomiss.off` sentinel is read only when no JSON configuration exists. Unknown modded item IDs remain stored so they work again when that mod returns.

## Development

Requires **JDK 25**:

```sh
bash gradlew build
```

The finished universal mod is **`build/libs/nomiss-1.2.jar`**, without `-sources`. Tests cover migration, profiles, matching priority, per-slot rules, server assignments, import/export validation, configuration persistence and universal packaging.

```sh
bash gradlew runClient                                   # Minecraft 26.2
bash gradlew runClient -PwithModMenu                     # 26.2 + Mod Menu
bash gradlew runClient -Pminecraft_version=26.3          # Minecraft 26.3
bash gradlew runClient -Pminecraft_version=26.3 -PwithModMenu
```

Both targets package the required library versions for both games, with dependency markers selecting the correct set at runtime. Build once and test **the exact same JAR** in both game profiles. Numeric key constants differ between 26.2 (GLFW) and 26.3 (SDL), so default shortcuts resolve persistent key names at runtime.
