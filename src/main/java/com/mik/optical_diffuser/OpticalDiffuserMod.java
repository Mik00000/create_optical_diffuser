package com.mik.optical_diffuser;

import com.mik.optical_diffuser.client.ClientModEvents;
import com.mik.optical_diffuser.network.UpdateDiffuserPayload;
import net.lpcamors.optical.COCreativeModeTabs;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(OpticalDiffuserMod.MOD_ID)
public class OpticalDiffuserMod {
    public static final String MOD_ID = "create_optical_diffuser";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public OpticalDiffuserMod(IEventBus modEventBus, net.neoforged.fml.ModContainer modContainer) {
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, com.mik.optical_diffuser.config.DiffuserClientConfig.SPEC);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.mik.optical_diffuser.config.DiffuserServerConfig.SPEC);

        ModRegistry.BLOCKS.register(modEventBus);
        ModRegistry.ITEMS.register(modEventBus);
        ModRegistry.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModRegistry.MENUS.register(modEventBus);
        ModRegistry.RECIPE_SERIALIZERS.register(modEventBus);
        ModRegistry.SOUND_EVENTS.register(modEventBus);

        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(com.mik.optical_diffuser.compat.create.display.ModDisplaySources::register);
        modEventBus.addListener(this::commonSetup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            try {
                org.watermedia.WaterMediaConfig.codecs.ffmpeg.hardwareAccel = false;
            } catch (Throwable ignored) {}
            // Register ponder plugin before PonderClient.init() runs in FMLClientSetupEvent
            net.createmod.ponder.foundation.PonderIndex.addPlugin(new com.mik.optical_diffuser.ponder.DiffuserPonderPlugin());
            modEventBus.addListener(ClientModEvents::clientSetup);
            modEventBus.addListener(ClientModEvents::registerScreens);
            modEventBus.addListener(ClientModEvents::registerRenderers);
            modEventBus.addListener(ClientModEvents::registerShaders);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new com.mik.optical_diffuser.client.DiffuserClientEffects());
        }

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.mik.optical_diffuser.server.ServerLightEventHandler.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.mik.optical_diffuser.server.ModCommands.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::onServerStopping);

        com.mik.optical_diffuser.compat.SableCompat.init();

        LOGGER.info("Create Optical Diffuser initialized!");
    }

    private void onServerStarting(net.neoforged.neoforge.event.server.ServerStartingEvent event) {
        java.io.File mediaDir = new java.io.File(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toFile(), "projector_media");
        int port = com.mik.optical_diffuser.config.DiffuserServerConfig.INSTANCE.httpPort.get();
        com.mik.optical_diffuser.server.media.MediaHttpServer.start(mediaDir, port);
    }

    private void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        com.mik.optical_diffuser.server.media.MediaHttpServer.stop();
    }

    private void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            com.mik.optical_diffuser.compat.create.display.ModDisplaySources.registerAssociations();
        });
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (COCreativeModeTabs.CO_BASE_CREATIVE_TAB.is(event.getTabKey())) {
            event.accept(ModRegistry.OPTICAL_DIFFUSER_ITEM.get());
            event.accept(ModRegistry.LEAD_GLASS_ITEM.get());
            event.accept(ModRegistry.FOCUSING_LENS.get());
            event.accept(ModRegistry.DISPERSING_LENS.get());
            event.accept(ModRegistry.FILM_REEL.get());
            event.accept(ModRegistry.FILM_STRIP.get());
            event.accept(ModRegistry.SPEAKER_ITEM.get());
            event.accept(ModRegistry.SILENT_PROJECTOR_LENS.get());
            event.accept(ModRegistry.SOUND_PROJECTOR_LENS.get());
            event.accept(ModRegistry.HOLOGRAM_LENS.get());
            event.accept(ModRegistry.THERMAL_IGNITION_LENS.get());
            event.accept(ModRegistry.COLLIMATOR_LENS.get());
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1.0.0");
        registrar.playToServer(
                UpdateDiffuserPayload.TYPE,
                UpdateDiffuserPayload.STREAM_CODEC,
                UpdateDiffuserPayload::handle
        );
        registrar.playToServer(
                com.mik.optical_diffuser.network.UpdateFilmReelPayload.TYPE,
                com.mik.optical_diffuser.network.UpdateFilmReelPayload.STREAM_CODEC,
                com.mik.optical_diffuser.network.UpdateFilmReelPayload::handle
        );
        registrar.playToServer(
                com.mik.optical_diffuser.network.UpdateFilmReelBlockPayload.TYPE,
                com.mik.optical_diffuser.network.UpdateFilmReelBlockPayload.STREAM_CODEC,
                com.mik.optical_diffuser.network.UpdateFilmReelBlockPayload::handle
        );
        registrar.playToServer(
                com.mik.optical_diffuser.network.UpdateFilmReelTimePayload.TYPE,
                com.mik.optical_diffuser.network.UpdateFilmReelTimePayload.STREAM_CODEC,
                com.mik.optical_diffuser.network.UpdateFilmReelTimePayload::handle
        );
        registrar.playToServer(
                com.mik.optical_diffuser.network.LinkSpeakerPayload.TYPE,
                com.mik.optical_diffuser.network.LinkSpeakerPayload.STREAM_CODEC,
                com.mik.optical_diffuser.network.LinkSpeakerPayload::handle
        );
        registrar.playToServer(
                com.mik.optical_diffuser.network.StartMediaUploadPayload.TYPE,
                com.mik.optical_diffuser.network.StartMediaUploadPayload.STREAM_CODEC,
                com.mik.optical_diffuser.network.StartMediaUploadPayload::handle
        );
        registrar.playToServer(
                com.mik.optical_diffuser.network.MediaUploadChunkPayload.TYPE,
                com.mik.optical_diffuser.network.MediaUploadChunkPayload.STREAM_CODEC,
                com.mik.optical_diffuser.network.MediaUploadChunkPayload::handle
        );
        registrar.playToServer(
                com.mik.optical_diffuser.network.FinishMediaUploadPayload.TYPE,
                com.mik.optical_diffuser.network.FinishMediaUploadPayload.STREAM_CODEC,
                com.mik.optical_diffuser.network.FinishMediaUploadPayload::handle
        );
        registrar.playToServer(
                com.mik.optical_diffuser.network.CancelMediaUploadPayload.TYPE,
                com.mik.optical_diffuser.network.CancelMediaUploadPayload.STREAM_CODEC,
                com.mik.optical_diffuser.network.CancelMediaUploadPayload::handle
        );
        registrar.playToClient(
                com.mik.optical_diffuser.network.MediaUploadStatusPayload.TYPE,
                com.mik.optical_diffuser.network.MediaUploadStatusPayload.STREAM_CODEC,
                com.mik.optical_diffuser.network.MediaUploadStatusPayload::handle
        );
    }
}
