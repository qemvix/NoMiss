# NoMiss

Client-side Fabric mod for **Minecraft 26.3**. Prevent wasted left-click attacks with the items you choose: an entity must be under your crosshair and within the weapon's reach. Other items behave like vanilla.

## Install

Put `nomiss-1.1.0.jar` in your `mods` folder with **Fabric Loader 0.19.5+** and **Java 25**. Remove older NoMiss JARs first. The small Fabric API modules used by NoMiss are bundled; a separate Fabric API download is not required.

[Mod Menu](https://modrinth.com/mod/modmenu) 21.0.0+ is optional. When installed, its NoMiss configuration button opens the same settings screen.

## Settings and shortcuts

- **Accessibility Settings → NoMiss: ON/OFF** toggles the entire mod. The adjacent **NoMiss Settings** button opens its options.
- **`;` / `:`** (the semicolon key) opens settings while playing.
- **`'`** (apostrophe) toggles the mod while playing and shows its new state above the hotbar.
- Both shortcuts can be changed under **Options → Controls → Key Binds → NoMiss**. They do not activate while typing in chat or other menus.

Settings has **Weapon 1–20** slots, like Minecraft's keybind list. Each **Select item…** button opens a searchable picker with item icons, display names and registry IDs. Vanilla and installed mod items appear automatically. Click an item to assign it; **×** clears a slot. Each item type can occupy one slot. All variants with the same item ID (including enchanted/custom-named stacks) use that slot.

Defaults protect all seven sword variants, all seven spear variants, and the mace. Axes can be selected manually. **Default items** restores these 15 selections without resetting other options; **Clear selection** empties all slots.

Two additional settings are available:

- **Wait for cooldown: OFF** by default. When enabled, selected items may attack entities only when attack strength is fully recharged. Blocked clicks are discarded; this never queues or automatically performs attacks.
- **Allow block breaking: ON** by default. When disabled, selected items require an entity target, including when holding left-click on blocks.

Changes apply and save immediately to `config/nomiss.json`. Empty slots and modded item IDs are retained across restarts. Unknown items remain visible in their slots so they can be cleared. Invalid JSON is backed up to `nomiss.broken-<timestamp>.json` before defaults are used. An existing `config/nomiss.off` file is migrated only when the JSON config does not yet exist.

## Development

Requires JDK 25. From the checkout:

```sh
bash gradlew build
```

The finished mod is `build/libs/nomiss-1.1.0.jar`, without the `-sources` suffix. `bash gradlew test` runs configuration and attack-rule regression tests.

```sh
bash gradlew runClient                  # test without Mod Menu
bash gradlew runClient -PwithModMenu    # test with Mod Menu
```

The default build targets Minecraft 26.3; this release does not claim compatibility with 26.2. No server mod is required. NoMiss leaves vanilla attacks, damage, weapon reach and server rules in control.
