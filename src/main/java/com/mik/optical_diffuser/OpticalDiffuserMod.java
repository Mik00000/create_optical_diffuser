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

        ModRegistry.BLOCKS.register(modEventBus);
        ModRegistry.ITEMS.register(modEventBus);
        ModRegistry.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModRegistry.MENUS.register(modEventBus);

        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::addCreative);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ClientModEvents::registerScreens);
            modEventBus.addListener(ClientModEvents::registerRenderers);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new com.mik.optical_diffuser.client.DiffuserClientEffects());
        }

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.mik.optical_diffuser.server.ServerLightEventHandler.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.mik.optical_diffuser.server.ModCommands.class);

        LOGGER.info("Create Optical Diffuser initialized!");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (COCreativeModeTabs.CO_BASE_CREATIVE_TAB.is(event.getTabKey())) {
            event.accept(ModRegistry.OPTICAL_DIFFUSER_ITEM.get());
            event.accept(ModRegistry.LEAD_GLASS_ITEM.get());
            event.accept(ModRegistry.FOCUSING_LENS.get());
            event.accept(ModRegistry.DISPERSING_LENS.get());
            event.accept(ModRegistry.FILM_REEL.get());
            event.accept(ModRegistry.SILENT_PROJECTOR_LENS.get());
            event.accept(ModRegistry.SOUND_PROJECTOR_LENS.get());
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
    }
}
