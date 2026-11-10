package com.mik.optical_diffuser.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class DiffuserServerConfig {
    public static final DiffuserServerConfig INSTANCE;
    public static final ModConfigSpec SPEC;

    static {
        Pair<DiffuserServerConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(DiffuserServerConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    public final ModConfigSpec.IntValue httpPort;
    public final ModConfigSpec.ConfigValue<String> publicHost;
    public final ModConfigSpec.IntValue maxUploadMb;

    public DiffuserServerConfig(ModConfigSpec.Builder builder) {
        builder.comment("Server-side media streaming and upload settings for Create Optical Diffuser").push("server_media");

        httpPort = builder
                .comment("HTTP server port for serving uploaded projector media files (default: 25585).")
                .defineInRange("httpPort", 25585, 1024, 65535);

        publicHost = builder
                .comment("Optional public host or IP override for generated media URLs (leave empty to use the server address connecting clients use).")
                .define("publicHost", "");

        maxUploadMb = builder
                .comment("Maximum video file upload size allowed for server admins in MB (default: 500 MB).")
                .defineInRange("maxUploadMb", 500, 10, 4096);

        builder.pop();
    }
}

