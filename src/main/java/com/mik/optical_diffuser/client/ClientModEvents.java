package com.mik.optical_diffuser.client;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.OpticalDiffuserMod;
import com.mik.optical_diffuser.client.gui.OpticalDiffuserScreen;
import com.mik.optical_diffuser.client.render.FilmReelRenderer;
import com.mik.optical_diffuser.client.render.ModPartialModels;
import com.mik.optical_diffuser.client.render.ModShaders;
import com.mik.optical_diffuser.client.render.OpticalDiffuserRenderer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

public class ClientModEvents {

    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModRegistry.OPTICAL_DIFFUSER.get(), RenderType.cutoutMipped());
        });
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        ModPartialModels.init();
        event.registerBlockEntityRenderer(ModRegistry.OPTICAL_DIFFUSER_BE.get(), OpticalDiffuserRenderer::new);
        event.registerBlockEntityRenderer(ModRegistry.FILM_REEL_BE.get(), FilmReelRenderer::new);
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistry.OPTICAL_DIFFUSER_MENU.get(), OpticalDiffuserScreen::new);
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, "rendertype_projector_monochrome"),
                            DefaultVertexFormat.NEW_ENTITY
                    ),
                    ModShaders::setProjectorMonochromeShader
            );
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, "rendertype_projector_color"),
                            DefaultVertexFormat.NEW_ENTITY
                    ),
                    ModShaders::setProjectorColorShader
            );
        } catch (IOException e) {
            OpticalDiffuserMod.LOGGER.error("Failed to register projector shaders", e);
        }
    }
}
