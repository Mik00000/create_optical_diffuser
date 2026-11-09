package com.mik.optical_diffuser.compat.create.display;

import com.mik.optical_diffuser.block.entity.FilmReelBlockEntity;
import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mik.optical_diffuser.item.FilmReelBlockItem;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.trains.display.FlapDisplaySection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class FilmReelTimeDisplaySource extends SingleLineDisplaySource {

    public static final MutableComponent EMPTY_TIME = Component.literal("--:--");

    private FilmData getFilmData(BlockEntity be) {
        if (be instanceof FilmReelBlockEntity reel) {
            return new FilmData(reel.getUrl(), reel.getCurrentTimeMs(), reel.getEffectiveSpeed());
        } else if (be instanceof OpticalDiffuserBlockEntity diffuser) {
            FilmReelBlockEntity connected = diffuser.getConnectedFilmReel();
            if (connected != null) {
                return new FilmData(connected.getUrl(), connected.getCurrentTimeMs(), connected.getEffectiveSpeed());
            }
            ItemStack itemReel = diffuser.getInstalledFilmReelItem();
            if (!itemReel.isEmpty()) {
                String url = FilmReelBlockItem.getUrl(itemReel);
                long time = FilmReelBlockItem.getCurrentTimeMs(itemReel);
                float speed = diffuser.getSpeed();
                return new FilmData(url, time, speed);
            }
        }
        return null;
    }

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity be = context.getSourceBlockEntity();
        FilmData data = getFilmData(be);

        if (data == null) {
            return EMPTY_TIME;
        }

        long ms = data.timeMs();
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;

        if (min >= 60) {
            long hr = min / 60;
            min = min % 60;
            return Component.literal(String.format("%d:%02d:%02d", hr, min, sec));
        }
        return Component.literal(String.format("%02d:%02d", min, sec));
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 10; // Refresh twice a second for live playback timer updates
    }

    @Override
    protected String getFlapDisplayLayoutName(DisplayLinkContext context) {
        return "Instant";
    }

    @Override
    protected FlapDisplaySection createSectionForValue(DisplayLinkContext context, int size) {
        return new FlapDisplaySection(size * 7.0f, "instant", false, false);
    }
}
