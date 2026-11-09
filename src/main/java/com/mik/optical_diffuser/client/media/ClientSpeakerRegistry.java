package com.mik.optical_diffuser.client.media;

import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mik.optical_diffuser.block.entity.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientSpeakerRegistry {

    private static final Set<SpeakerBlockEntity> ACTIVE_SPEAKERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void register(SpeakerBlockEntity speaker) {
        ACTIVE_SPEAKERS.add(speaker);
    }

    public static void unregister(SpeakerBlockEntity speaker) {
        ACTIVE_SPEAKERS.remove(speaker);
    }

    public static List<SpeakerBlockEntity> getSpeakersForDiffuser(OpticalDiffuserBlockEntity diffuser) {
        if (diffuser == null || diffuser.getLevel() == null) return Collections.emptyList();
        BlockPos diffuserPos = diffuser.getBlockPos();
        Level level = diffuser.getLevel();
        List<SpeakerBlockEntity> matches = new ArrayList<>();

        java.util.UUID diffuserSubId = null;
        if (net.neoforged.fml.ModList.get().isLoaded("sable")) {
            try {
                dev.ryanhcode.sable.sublevel.SubLevel sub = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, diffuserPos);
                if (sub != null && !sub.isRemoved()) {
                    diffuserSubId = sub.getUniqueId();
                }
            } catch (Throwable ignored) {}
        }

        for (SpeakerBlockEntity s : ACTIVE_SPEAKERS) {
            if (s.isRemoved() || s.getLevel() == null) continue;
            BlockPos linked = s.getLinkedDiffuserPos();
            if (linked == null) continue;

            // 1. Direct BlockPos match
            if (diffuserPos.equals(linked)) {
                matches.add(s);
                continue;
            }

            // 2. Sable assembly match:
            if (diffuserSubId != null) {
                try {
                    dev.ryanhcode.sable.sublevel.SubLevel sub = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, diffuserPos);
                    if (sub != null) {
                        Vec3 plotPos = sub.logicalPose().transformPositionInverse(Vec3.atCenterOf(linked));
                        if (diffuserPos.closerThan(BlockPos.containing(plotPos), 2.5)) {
                            matches.add(s);
                            continue;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return matches;
    }

    public static List<SpeakerBlockEntity> getSpeakersForDiffuser(BlockPos diffuserPos) {
        if (diffuserPos == null) return Collections.emptyList();
        List<SpeakerBlockEntity> matches = new ArrayList<>();
        for (SpeakerBlockEntity s : ACTIVE_SPEAKERS) {
            if (s.isRemoved() || s.getLevel() == null) continue;
            if (diffuserPos.equals(s.getLinkedDiffuserPos())) {
                matches.add(s);
            }
        }
        return matches;
    }
}

