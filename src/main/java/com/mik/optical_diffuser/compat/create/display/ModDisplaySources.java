package com.mik.optical_diffuser.compat.create.display;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.OpticalDiffuserMod;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;

public class ModDisplaySources {

    public static final FilmReelTimeDisplaySource FILM_REEL_TIME = new FilmReelTimeDisplaySource();
    public static final FilmReelInfoDisplaySource FILM_REEL_INFO = new FilmReelInfoDisplaySource();

    public static void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(CreateRegistries.DISPLAY_SOURCE)) {
            event.register(CreateRegistries.DISPLAY_SOURCE, helper -> {
                helper.register(ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, "film_reel_time"), FILM_REEL_TIME);
                helper.register(ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, "film_reel_info"), FILM_REEL_INFO);
            });
        }
    }

    public static void registerAssociations() {
        try {
            DisplaySource.BY_BLOCK_ENTITY.add(ModRegistry.FILM_REEL_BE.get(), FILM_REEL_TIME);
            DisplaySource.BY_BLOCK_ENTITY.add(ModRegistry.FILM_REEL_BE.get(), FILM_REEL_INFO);
            DisplaySource.BY_BLOCK_ENTITY.add(ModRegistry.OPTICAL_DIFFUSER_BE.get(), FILM_REEL_TIME);
            DisplaySource.BY_BLOCK_ENTITY.add(ModRegistry.OPTICAL_DIFFUSER_BE.get(), FILM_REEL_INFO);
            OpticalDiffuserMod.LOGGER.info("Registered Create Display Link sources for Film Reel and Optical Diffuser");
        } catch (Throwable t) {
            OpticalDiffuserMod.LOGGER.error("Failed to register Create Display Link sources", t);
        }
    }
}
