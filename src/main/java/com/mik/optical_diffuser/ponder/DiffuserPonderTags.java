package com.mik.optical_diffuser.ponder;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.OpticalDiffuserMod;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Ponder tags — consolidated into 2 main feature groups.
 */
public class DiffuserPonderTags {

    // Tag IDs
    public static final ResourceLocation DIFFUSER = rl("optical_diffuser");
    public static final ResourceLocation CINEMA   = rl("cinema_projectors");

    static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, path);
    }

    public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {

        // ── Group 1: Optical Diffuser & Beam Optics ──────────────────────────
        helper.registerTag(DIFFUSER)
                .title("Optical Diffuser & Optics")
                .description("Learn how the Optical Diffuser receives a laser beam and reshapes it using various beam and combat lenses.")
                .item(ModRegistry.OPTICAL_DIFFUSER_ITEM.get())
                .register();

        helper.addToTag(DIFFUSER)
                .add(key(ModRegistry.OPTICAL_DIFFUSER_ITEM.get()))
                .add(key(ModRegistry.FOCUSING_LENS.get()))
                .add(key(ModRegistry.DISPERSING_LENS.get()))
                .add(key(ModRegistry.THERMAL_IGNITION_LENS.get()))
                .add(key(ModRegistry.COLLIMATOR_LENS.get()))
                .add(key(ModRegistry.LEAD_GLASS_ITEM.get()));

        // ── Group 2: Cinema Projectors & Holograms ───────────────────────────
        helper.registerTag(CINEMA)
                .title("Cinema & Holograms")
                .description("Project video onto lead glass screens, emit synchronized audio via speakers, or float spinning 3D holograms.")
                .item(ModRegistry.SILENT_PROJECTOR_LENS.get())
                .register();

        helper.addToTag(CINEMA)
                .add(key(ModRegistry.OPTICAL_DIFFUSER_ITEM.get()))
                .add(key(ModRegistry.SILENT_PROJECTOR_LENS.get()))
                .add(key(ModRegistry.SOUND_PROJECTOR_LENS.get()))
                .add(key(ModRegistry.FILM_REEL.get()))
                .add(key(ModRegistry.SPEAKER_ITEM.get()))
                .add(key(ModRegistry.HOLOGRAM_LENS.get()));
    }

    /** Convert a registered item to its ResourceLocation registry key. */
    private static ResourceLocation key(net.minecraft.world.item.Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
    }

    private static ResourceLocation key(net.minecraft.world.level.ItemLike itemLike) {
        return key(itemLike.asItem());
    }
}
