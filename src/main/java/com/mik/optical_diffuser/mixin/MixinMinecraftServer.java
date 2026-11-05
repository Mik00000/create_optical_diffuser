package com.mik.optical_diffuser.mixin;

import com.mik.optical_diffuser.OpticalDiffuserMod;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;
import java.util.stream.Stream;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {

    @Unique
    private int optical_diffuser$stopServerWaitTicks = 0;

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void optical_diffuser$onStopServerStart(CallbackInfo ci) {
        this.optical_diffuser$stopServerWaitTicks = 0;
    }

    /**
     * Prevents infinite hangs on the "Saving world" screen caused by sub-level / custom mod chunk tickets
     * that never fully drain in the while(hasWork()) loop.
     * After 150 iterations, forces the loop to finish so saveAllChunks can cleanly save and close the world.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
        method = "stopServer",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/stream/Stream;anyMatch(Ljava/util/function/Predicate;)Z"
        )
    )
    private boolean optical_diffuser$limitChunkUnloadWait(Stream<?> stream, Predicate<?> predicate) {
        if (++this.optical_diffuser$stopServerWaitTicks > 150) {
            OpticalDiffuserMod.LOGGER.warn("Forcing server shutdown: chunk unload loop exceeded 150 ticks (preventing sub-level/ticket deadlock on 'Saving worlds'). Proceeding to save world files now.");
            return false;
        }
        return stream.anyMatch((Predicate) predicate);
    }
}
