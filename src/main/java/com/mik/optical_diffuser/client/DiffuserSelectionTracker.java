package com.mik.optical_diffuser.client;

import net.minecraft.core.BlockPos;

public class DiffuserSelectionTracker {

    private static BlockPos lastSelectedDiffuser = null;
    private static long selectTime = 0;

    public static void selectDiffuser(BlockPos pos) {
        lastSelectedDiffuser = pos;
        selectTime = System.currentTimeMillis();
    }

    public static BlockPos getLastSelectedDiffuser() {
        if (lastSelectedDiffuser == null) return null;
        // Expire selection after 5 minutes
        if (System.currentTimeMillis() - selectTime > 300_000) {
            lastSelectedDiffuser = null;
            return null;
        }
        return lastSelectedDiffuser;
    }

    public static void clear() {
        lastSelectedDiffuser = null;
    }
}

