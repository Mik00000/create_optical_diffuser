package com.mik.optical_diffuser.client.render;

import com.mik.optical_diffuser.OpticalDiffuserMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public class ModShaders {
    private static ShaderInstance projectorMonochromeShader;
    private static ShaderInstance projectorColorShader;

    public static void setProjectorMonochromeShader(ShaderInstance shader) {
        projectorMonochromeShader = shader;
        OpticalDiffuserMod.LOGGER.info("Registered Projector Monochrome shader successfully.");
    }

    public static ShaderInstance getProjectorMonochromeShader() {
        return projectorMonochromeShader;
    }

    public static void setProjectorColorShader(ShaderInstance shader) {
        projectorColorShader = shader;
        OpticalDiffuserMod.LOGGER.info("Registered Projector Color shader successfully.");
    }

    public static ShaderInstance getProjectorColorShader() {
        return projectorColorShader;
    }

    private static final RenderStateShard.ShaderStateShard RENDERTYPE_PROJECTOR_MONOCHROME_SHADER =
            new RenderStateShard.ShaderStateShard(ModShaders::getProjectorMonochromeShader);

    private static final RenderStateShard.ShaderStateShard RENDERTYPE_PROJECTOR_COLOR_SHADER =
            new RenderStateShard.ShaderStateShard(ModShaders::getProjectorColorShader);

    public static final Function<ResourceLocation, RenderType> PROJECTOR_MONOCHROME = Util.memoize(texLoc -> {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_PROJECTOR_MONOCHROME_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texLoc, false, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .createCompositeState(true);
        return RenderType.create(
                "projector_monochrome",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                true,
                true,
                state
        );
    });

    public static final Function<ResourceLocation, RenderType> PROJECTOR_COLOR = Util.memoize(texLoc -> {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_PROJECTOR_COLOR_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texLoc, false, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .createCompositeState(true);
        return RenderType.create(
                "projector_color",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                true,
                true,
                state
        );
    });

    public static RenderType getProjectorMonochrome(ResourceLocation texLoc) {
        if (projectorMonochromeShader == null) {
            return RenderType.entityTranslucentCull(texLoc);
        }
        return PROJECTOR_MONOCHROME.apply(texLoc);
    }

    public static RenderType getProjectorColor(ResourceLocation texLoc) {
        if (projectorColorShader == null) {
            return RenderType.entityTranslucentCull(texLoc);
        }
        return PROJECTOR_COLOR.apply(texLoc);
    }
}
