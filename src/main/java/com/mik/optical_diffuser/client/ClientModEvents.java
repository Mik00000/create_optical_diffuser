package com.mik.optical_diffuser.client;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.client.gui.OpticalDiffuserScreen;
import com.mik.optical_diffuser.client.render.OpticalDiffuserRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public class ClientModEvents {

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.OPTICAL_DIFFUSER_BE.get(), OpticalDiffuserRenderer::new);
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.OPTICAL_DIFFUSER_MENU.get(), OpticalDiffuserScreen::new);
    }
}
