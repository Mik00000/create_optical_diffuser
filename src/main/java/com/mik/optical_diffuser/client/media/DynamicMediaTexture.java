package com.mik.optical_diffuser.client.media;

import com.mik.optical_diffuser.util.DiffuserLogger;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;

public class DynamicMediaTexture extends AbstractTexture {

    private final ResourceLocation location;

    private boolean initialized = false;
    private int programId = 0;
    private int uTexLoc = -1;
    private int uMonoLoc = -1;
    private int vao = 0;
    private int vbo = 0;
    private int fboId = 0;
    private int targetTexId = 0;

    private int currentWidth = 0;
    private int currentHeight = 0;
    private boolean hasValidFrame = false;
    private boolean hasWarnedInvalid = false;

    public DynamicMediaTexture(ResourceLocation location) {
        this.location = location;
    }

    private void initGL() {
        if (initialized) return;
        initialized = true;

        String vertSrc = "#version 150\n" +
                "in vec2 inPos;\n" +
                "in vec2 inUV;\n" +
                "out vec2 texCoord;\n" +
                "void main() {\n" +
                "    gl_Position = vec4(inPos, 0.0, 1.0);\n" +
                "    texCoord = inUV;\n" +
                "}\n";

        String fragSrc = "#version 150\n" +
                "uniform sampler2D uTex;\n" +
                "uniform int uMonochrome;\n" +
                "in vec2 texCoord;\n" +
                "out vec4 fragColor;\n" +
                "void main() {\n" +
                "    vec4 col = texture(uTex, texCoord);\n" +
                "    if (uMonochrome == 1) {\n" +
                "        float luma = dot(col.rgb, vec3(0.2126, 0.7152, 0.0722));\n" +
                "        fragColor = vec4(luma * 0.95, luma * 0.92, luma * 0.86, col.a);\n" +
                "    } else {\n" +
                "        fragColor = col;\n" +
                "    }\n" +
                "}\n";

        int vs = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vs, vertSrc);
        GL20.glCompileShader(vs);
        if (GL20.glGetShaderi(vs, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            DiffuserLogger.error("Failed to compile blit vertex shader: " + GL20.glGetShaderInfoLog(vs), null);
        }

        int fs = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fs, fragSrc);
        GL20.glCompileShader(fs);
        if (GL20.glGetShaderi(fs, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            DiffuserLogger.error("Failed to compile blit fragment shader: " + GL20.glGetShaderInfoLog(fs), null);
        }

        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vs);
        GL20.glAttachShader(programId, fs);
        GL20.glBindAttribLocation(programId, 0, "inPos");
        GL20.glBindAttribLocation(programId, 1, "inUV");
        GL20.glLinkProgram(programId);
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            DiffuserLogger.error("Failed to link blit program: " + GL20.glGetProgramInfoLog(programId), null);
        }
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);

        uTexLoc = GL20.glGetUniformLocation(programId, "uTex");
        uMonoLoc = GL20.glGetUniformLocation(programId, "uMonochrome");

        float[] quadVertices = {
                -1.0f, -1.0f, 0.0f, 0.0f,
                 1.0f, -1.0f, 1.0f, 0.0f,
                 1.0f,  1.0f, 1.0f, 1.0f,
                -1.0f, -1.0f, 0.0f, 0.0f,
                 1.0f,  1.0f, 1.0f, 1.0f,
                -1.0f,  1.0f, 0.0f, 1.0f
        };
        FloatBuffer buf = BufferUtils.createFloatBuffer(quadVertices.length);
        buf.put(quadVertices).flip();

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        fboId = GL30.glGenFramebuffers();
        targetTexId = GL11.glGenTextures();
    }

    public void updateFromSource(int srcTexId, boolean isSilent) {
        if (!initialized) {
            initGL();
        }

        if (srcTexId <= 0 || !GL11.glIsTexture(srcTexId)) {
            if (!hasWarnedInvalid) {
                DiffuserLogger.warn("Prevented green flicker: source texture id={} is invalid. Holding last stable frame.", srcTexId);
                hasWarnedInvalid = true;
            }
            return;
        }
        hasWarnedInvalid = false;

        // Check source texture dimensions directly from OpenGL
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcTexId);
        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (width <= 0 || height <= 0) {
            return;
        }

        if (currentWidth != width || currentHeight != height) {
            currentWidth = width;
            currentHeight = height;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, targetTexId);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, targetTexId, 0);
            DiffuserLogger.info("Allocated stable projector texture: {}x{} for {}", width, height, location);
        }

        // Save active OpenGL state
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int prevActiveTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int prevTex2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        // Blit fullscreen quad from srcTexId to our targetTexId
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        GL11.glViewport(0, 0, currentWidth, currentHeight);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL20.glUseProgram(programId);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcTexId);
        GL20.glUniform1i(uTexLoc, 0);
        GL20.glUniform1i(uMonoLoc, isSilent ? 1 : 0);

        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        // Restore OpenGL state
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
        GL20.glUseProgram(prevProgram);
        GL30.glBindVertexArray(prevVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVbo);
        GL13.glActiveTexture(prevActiveTex);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex2D);

        if (prevDepthTest) GL11.glEnable(GL11.GL_DEPTH_TEST);
        if (prevBlend) GL11.glEnable(GL11.GL_BLEND);
        if (prevCull) GL11.glEnable(GL11.GL_CULL_FACE);

        this.hasValidFrame = true;
    }

    public boolean hasValidFrame() {
        return this.hasValidFrame && this.targetTexId > 0;
    }

    @Override
    public int getId() {
        return Math.max(0, this.targetTexId);
    }

    @Override
    public void load(ResourceManager resourceManager) {
    }

    @Override
    public void close() {
        if (fboId != 0) {
            GL30.glDeleteFramebuffers(fboId);
            fboId = 0;
        }
        if (targetTexId != 0) {
            GL11.glDeleteTextures(targetTexId);
            targetTexId = 0;
        }
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            vao = 0;
        }
        if (vbo != 0) {
            GL15.glDeleteBuffers(vbo);
            vbo = 0;
        }
        if (programId != 0) {
            GL20.glDeleteProgram(programId);
            programId = 0;
        }
        initialized = false;
        hasValidFrame = false;
        currentWidth = 0;
        currentHeight = 0;
    }

    @Override
    public void releaseId() {
        close();
    }

    public ResourceLocation getLocation() {
        return location;
    }
}
