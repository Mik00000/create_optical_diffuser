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

    public void setTextureId(int id) {
        this.glTextureId = id;
        this.id = id;
    }

    @Override
    public int getId() {
        return Math.max(0, this.glTextureId);
    }

    @Override
    public void load(ResourceManager resourceManager) {
    }

    @Override
    public void close() {
        this.glTextureId = -1;
        this.id = -1;
    }

    @Override
    public void releaseId() {
        // Do not delete OpenGL texture ID here: WATERMeDIA manages its own texture lifecycle!
        this.glTextureId = -1;
        this.id = -1;
    }

    public ResourceLocation getLocation() {
        return location;
    }
}
