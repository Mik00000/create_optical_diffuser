package com.mik.optical_diffuser.client.media;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public class DynamicMediaTexture extends AbstractTexture {

    private final ResourceLocation location;
    private int glTextureId = -1;

    public DynamicMediaTexture(ResourceLocation location) {
        this.location = location;
    }

    public void updateTextureId(int id) {
        this.glTextureId = id;
    }

    @Override
    public int getId() {
        return Math.max(0, this.glTextureId);
    }

    @Override
    public void load(ResourceManager resourceManager) {
        // Dynamic OpenGL texture supplied directly by WATERMeDIA
    }

    @Override
    public void close() {
        // Managed by WATERMeDIA
    }

    @Override
    public void releaseId() {
        this.glTextureId = -1;
    }

    public ResourceLocation getLocation() {
        return location;
    }
}
