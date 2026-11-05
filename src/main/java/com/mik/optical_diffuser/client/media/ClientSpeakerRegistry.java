package com.mik.optical_diffuser.client.media;

import com.mik.optical_diffuser.block.entity.SpeakerBlockEntity;
import net.minecraft.core.BlockPos;

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

