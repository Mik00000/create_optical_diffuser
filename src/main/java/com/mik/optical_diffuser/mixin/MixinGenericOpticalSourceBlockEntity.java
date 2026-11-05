package com.mik.optical_diffuser.mixin;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import net.lpcamors.optical.blocks.optical_source.GenericOpticalSourceBlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(GenericOpticalSourceBlockEntity.class)
public class MixinGenericOpticalSourceBlockEntity {

    /**
     * Prevents glue entities (SuperGlueEntity, HoneyGlueEntity, etc.) and decorative hanging entities
     * from obstructing optical laser beams.
     */
    @Redirect(
        method = "addSection",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
        ),
        remap = false
    )
    private List<Entity> optical_diffuser$filterGlueEntities(Level level, Entity except, AABB aabb) {
        List<Entity> entities = level.getEntities(except, aabb);
        if (entities.isEmpty()) {
            return entities;
        }
        return entities.stream()
            .filter(e -> !(e instanceof SuperGlueEntity)
                    && !e.getClass().getName().contains("Glue")
                    && !(e instanceof HangingEntity))
            .toList();
    }
}
