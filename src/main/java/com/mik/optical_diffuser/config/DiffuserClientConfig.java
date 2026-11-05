package com.mik.optical_diffuser.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class DiffuserClientConfig {
    public static final DiffuserClientConfig INSTANCE;
    public static final ModConfigSpec SPEC;

    public final ModConfigSpec.DoubleValue coreBrightness;
    public final ModConfigSpec.DoubleValue coreWhiteHotIntensity;
    public final ModConfigSpec.DoubleValue layerAlphaDecayStep;
    public final ModConfigSpec.DoubleValue beamAlphaMultiplier;
    public final ModConfigSpec.DoubleValue longitudinalFalloffExponent;
    public final ModConfigSpec.DoubleValue mieForwardScattering;
    public final ModConfigSpec.IntValue perceptualLumaFloor;
    public final ModConfigSpec.DoubleValue lensCapBrightness;

    public DiffuserClientConfig(ModConfigSpec.Builder builder) {
        builder.comment("Client-side visual rendering settings for Create Optical Diffuser").push("visuals");

        coreBrightness = builder
                .comment("Inner core brightness factor (default: 0.50, 30% softer than before).",
                         "Controls the intensity of the inner central laser/spotlight beam.")
                .defineInRange("coreBrightness", 0.50, 0.05, 3.0);

        coreWhiteHotIntensity = builder
                .comment("Core white-hot desaturation intensity (default: 0.20).",
                         "0.0 = pure dye color (no white), 1.0 = intense blinding incandescent white.")
                .defineInRange("coreWhiteHotIntensity", 0.20, 0.0, 1.0);

        layerAlphaDecayStep = builder
                .comment("Alpha decay step between volumetric layers (default: 0.010).",
                         "Smaller value = softer, smoother transition between concentric beam shells.")
                .defineInRange("layerAlphaDecayStep", 0.010, 0.001, 0.10);

        beamAlphaMultiplier = builder
                .comment("Overall volumetric beam opacity / brightness multiplier (default: 1.0).",
                         "Scales the entire volumetric spotlight cone brightness.")
                .defineInRange("beamAlphaMultiplier", 1.0, 0.10, 3.0);

        longitudinalFalloffExponent = builder
                .comment("Longitudinal distance transmission falloff exponent (default: 0.35).",
                         "Lower value = beam stays dense further; higher value = faster fade into distance.")
                .defineInRange("longitudinalFalloffExponent", 0.35, 0.10, 2.0);

        mieForwardScattering = builder
                .comment("Mie forward-scattering glare boost when looking towards the beacon (default: 0.40).",
                         "Extra radiance multiplier when facing the projector lens.")
                .defineInRange("mieForwardScattering", 0.40, 0.0, 2.0);

        perceptualLumaFloor = builder
                .comment("Perceptual luminance floor for dark dyes like Blue/Purple (default: 85).",
                         "Prevents dark wavelengths from being completely lost against the black night sky.")
                .defineInRange("perceptualLumaFloor", 85, 0, 255);

        lensCapBrightness = builder
                .comment("Brightness of the circular lens glass cap on the block face (default: 0.90).")
                .defineInRange("lensCapBrightness", 0.90, 0.10, 1.0);

        builder.pop();
    }

    static {
        Pair<DiffuserClientConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(DiffuserClientConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }
}
