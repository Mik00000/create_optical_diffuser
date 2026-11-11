package com.mik.optical_diffuser.ponder;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.OpticalDiffuserMod;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers all Create: Optical Diffuser ponder scenes and tags.
 * Activated via PonderClient.addPlugin() during FMLClientSetupEvent.
 */
public class DiffuserPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return OpticalDiffuserMod.MOD_ID;
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        DiffuserPonderTags.register(helper);
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        DiffuserPonderScenes.register(helper);
    }
}
