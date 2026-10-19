# NoMiss

Client-side Fabric mod for **Minecraft 26.2 and 26.3**. Prevent wasted left-click attacks with the items you choose: an entity must be under your crosshair and within the weapon's reach. Other items behave like vanilla.

## Install

Put `nomiss-1.1.1.jar` in your `mods` folder with **Fabric Loader 0.19.5+** and **Java 25**. Remove older NoMiss JARs first. The small Fabric API modules used by NoMiss are bundled; a separate Fabric API download is not required.

[Mod Menu](https://modrinth.com/mod/modmenu) is optional: use a 26.2-compatible Mod Menu build (20.0.3+) on Minecraft 26.2, or a 26.3-compatible build (21.0.0+) on Minecraft 26.3. When installed, its NoMiss configuration button opens the same settings screen.

## Settings and shortcuts

- **Accessibility Settings → NoMiss: ON/OFF** toggles the entire mod. The adjacent **NoMiss Settings** button opens its options.
- **`;` / `:`** (the semicolon key) opens settings while playing.
- **`'`** (apostrophe) toggles the mod while playing and shows its new state above the hotbar.
- **Add held item** and **Show/hide NoMiss status** have optional shortcuts, unbound by default.
- All four shortcuts can be changed under **Options → Controls → Key Binds → NoMiss**. They do not activate while typing in chat or other menus. The settings screen also has a **Key binds…** button that opens that menu.

Settings has **Weapon 1–20** slots, like Minecraft's keybind list. Each **Select item…** button opens a searchable picker with item icons, display names and registry IDs. Vanilla and installed mod items appear automatically. Click an item to assign it; **×** clears a slot. Each item type can occupy one slot. All variants with the same item ID (including enchanted/custom-named stacks) use that slot.

Defaults protect all seven sword variants, all seven spear variants, and the mace. Axes can be selected manually. **Default items** restores these 15 selections without resetting other options; **Clear selection** empties all slots.

Additional settings are available:

- **Wait for cooldown: OFF** by default. When enabled, selected items may attack entities only when attack strength is fully recharged. Blocked clicks are discarded; this never queues or automatically performs attacks.
- **Allow block breaking: ON** by default. When disabled, selected items require an entity target, including when holding left-click on blocks.

- **Show NoMiss status: ON** by default. A small ON/OFF indicator appears in the upper-left corner while playing. Its menu toggle and optional shortcut hide only the indicator; attack protection is independent.
- **Explain blocked attacks: OFF** by default. Briefly shows **Out of reach / no entity targeted**, **Cooldown**, or **Block breaking disabled** after a blocked click. This option is independent of the status indicator. Both indicators respect F1 and hide in menus.

Each setting has its own **Reset** button. It restores only that setting's default without changing other options, weapon slots, or keybinds. Reset shortcuts separately in Minecraft's Key Binds menu.

**Add held item** adds your main-hand item to the first empty weapon slot. It works with vanilla and modded items, never replaces a selection, and explains if your hand is empty, the item is already selected, or all 20 slots are full. Join a world to use it from the menu or assign its optional shortcut.

Changes apply and save immediately to `config/nomiss.json`. Empty slots and modded item IDs are retained across restarts. Unknown items remain visible in their slots so they can be cleared. Invalid JSON is backed up to `nomiss.broken-<timestamp>.json` before defaults are used. An existing `config/nomiss.off` file is migrated only when the JSON config does not yet exist.

## Development

Requires JDK 25. From the checkout:

```sh
bash gradlew build
```

The finished mod is `build/libs/nomiss-1.1.1.jar`, without the `-sources` suffix. `bash gradlew test` runs configuration, attack-rule, and universal JAR packaging tests.

```sh
bash gradlew runClient                                   # Minecraft 26.2
bash gradlew runClient -PwithModMenu                     # 26.2 with Mod Menu
bash gradlew runClient -Pminecraft_version=26.3          # Minecraft 26.3
bash gradlew runClient -Pminecraft_version=26.3 -PwithModMenu
```

The default development target is Minecraft 26.2. Both build targets produce the same universal packaging for 26.2 and 26.3. The JAR bundles the required Fabric library builds for each Minecraft version, plus dependency markers that select the correct set automatically. No server mod is required. NoMiss leaves vanilla attacks, damage, weapon reach and server rules in control.

For compatibility checks, build once and install that exact JAR into both Minecraft profiles. Rebuilding separately for each game can hide binary differences. Keyboard defaults resolve persistent key names at runtime because 26.2 uses GLFW keycodes and 26.3 uses SDL scancodes.
