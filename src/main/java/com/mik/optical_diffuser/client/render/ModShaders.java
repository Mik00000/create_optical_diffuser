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

    public static void setProjectorMonochromeShader(ShaderInstance shader) {
        projectorMonochromeShader = shader;
        OpticalDiffuserMod.LOGGER.info("Registered Projector Monochrome shader successfully.");
    }

    public static ShaderInstance getProjectorMonochromeShader() {
        return projectorMonochromeShader;
    }

    private static final RenderStateShard.ShaderStateShard RENDERTYPE_PROJECTOR_MONOCHROME_SHADER =
            new RenderStateShard.ShaderStateShard(ModShaders::getProjectorMonochromeShader);

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

    public static RenderType getProjectorMonochrome(ResourceLocation texLoc) {
        if (projectorMonochromeShader == null) {
            return RenderType.entityTranslucentCull(texLoc);
        }
        return PROJECTOR_MONOCHROME.apply(texLoc);
    }
}

