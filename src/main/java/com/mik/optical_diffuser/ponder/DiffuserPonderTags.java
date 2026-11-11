package com.mik.optical_diffuser.ponder;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.OpticalDiffuserMod;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Ponder tags — one per guide tab.
 *
 * PonderTagRegistrationHelper<T> here is typed to ResourceLocation (item registry keys).
 * We build it with a keyFunction that maps ItemLike → ResourceLocation.
 */
public class DiffuserPonderTags {

    // Tag IDs
    static final ResourceLocation DIFFUSER   = rl("optical_diffuser");
    static final ResourceLocation BEAM       = rl("beam_lenses");
    static final ResourceLocation CINEMA     = rl("cinema_lenses");
    static final ResourceLocation HOLOGRAM   = rl("hologram_lens");
    static final ResourceLocation COMBAT     = rl("combat_lenses");
    static final ResourceLocation LEAD_GLASS = rl("lead_glass");

    static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, path);
    }

    public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {

        // ── Tab 1: Optical Diffuser ──────────────────────────────────────────
        helper.registerTag(DIFFUSER)
                .title("Optical Diffuser")
                .description("Learn how the Optical Diffuser receives a laser beam and reshapes it into a cone of light.")
                .item(ModRegistry.OPTICAL_DIFFUSER_ITEM.get())
                .register();

        helper.addToTag(DIFFUSER)
                .add(key(ModRegistry.OPTICAL_DIFFUSER_ITEM.get()));

        // ── Tab 2: Beam Shaping Lenses ───────────────────────────────────────
        helper.registerTag(BEAM)
                .title("Beam Shaping Lenses")
                .description("Focus the beam into a narrow long-range shaft, or fan it out for wide area coverage.")
                .item(ModRegistry.FOCUSING_LENS.get())
                .register();

        helper.addToTag(BEAM)
                .add(key(ModRegistry.FOCUSING_LENS.get()))
                .add(key(ModRegistry.DISPERSING_LENS.get()));

        // ── Tab 3: Cinema Lenses & Speaker ───────────────────────────────────
        helper.registerTag(CINEMA)
                .title("Cinema Lenses & Speaker")
                .description("Project video onto lead glass screens and emit synchronized audio via the Speaker block.")
                .item(ModRegistry.SILENT_PROJECTOR_LENS.get())
                .register();

        helper.addToTag(CINEMA)
                .add(key(ModRegistry.SILENT_PROJECTOR_LENS.get()))
                .add(key(ModRegistry.SOUND_PROJECTOR_LENS.get()))
                .add(key(ModRegistry.FILM_REEL.get()))
                .add(key(ModRegistry.SPEAKER_ITEM.get()));

        // ── Tab 4: Hologram Lens ─────────────────────────────────────────────
        helper.registerTag(HOLOGRAM)
                .title("Hologram Lens")
                .description("Float spinning 3D holograms of items, blocks, or entire Create schematics in the air.")
                .item(ModRegistry.HOLOGRAM_LENS.get())
                .register();

        helper.addToTag(HOLOGRAM)
                .add(key(ModRegistry.HOLOGRAM_LENS.get()));

        // ── Tab 5: Combat Lenses ─────────────────────────────────────────────
        helper.registerTag(COMBAT)
                .title("Combat Lenses")
                .description("Turn the beam into a focused heat ray or a razor-sharp needle laser.")
                .item(ModRegistry.THERMAL_IGNITION_LENS.get())
                .register();

        helper.addToTag(COMBAT)
                .add(key(ModRegistry.THERMAL_IGNITION_LENS.get()))
                .add(key(ModRegistry.COLLIMATOR_LENS.get()));

        // ── Tab 6: Lead Glass ────────────────────────────────────────────────
        helper.registerTag(LEAD_GLASS)
                .title("Lead Glass")
                .description("Use Lead Glass as an optical color filter or as a cinema projection screen.")
                .item(ModRegistry.LEAD_GLASS_ITEM.get())
                .register();

        helper.addToTag(LEAD_GLASS)
                .add(key(ModRegistry.LEAD_GLASS_ITEM.get()));
    }

    /** Convert a registered item to its ResourceLocation registry key. */
    private static ResourceLocation key(net.minecraft.world.item.Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
    }

    private static ResourceLocation key(net.minecraft.world.level.ItemLike itemLike) {
        return key(itemLike.asItem());
    }
}
