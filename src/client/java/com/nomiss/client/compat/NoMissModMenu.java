package com.nomiss.client.compat;

import com.nomiss.client.gui.NoMissScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Loaded only by Mod Menu's optional entrypoint, never by the base client. */
public final class NoMissModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return NoMissScreen::new;
    }
}
