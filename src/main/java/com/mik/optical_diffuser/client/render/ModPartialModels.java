package com.mik.optical_diffuser.client.render;

import com.mik.optical_diffuser.OpticalDiffuserMod;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public class ModPartialModels {

    public static final PartialModel FILM_REEL_SPOOL = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, "block/film_reel_spool")
    );

    public static void init() {
        // Classloading trigger
    }
}
