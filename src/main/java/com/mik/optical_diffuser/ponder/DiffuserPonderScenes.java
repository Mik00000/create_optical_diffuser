package com.mik.optical_diffuser.ponder;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.OpticalDiffuserMod;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * All 16 ponder scene definitions for Create: Optical Diffuser.
 *
 * Scene ID → NBT path:
 *   "scene_id" → assets/create_optical_diffuser/ponder/scene_id.nbt
 *
 * Registration pattern (using ponder 1.0.82 API):
 *   helper.forComponents(itemKey).addStoryBoard("nbt_path", Scene::method, tagEntry -> tagEntry.highlightTag(TAG));
 */
public class DiffuserPonderScenes {

    static final String MOD = OpticalDiffuserMod.MOD_ID;

    static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD, path);
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {

        // ── Group 1: Optical Diffuser ─────────────────────────────────────────

        helper.forComponents(key(ModRegistry.OPTICAL_DIFFUSER_ITEM.get()))
                .addStoryBoard("diffuser/what_is_it",         DiffuserPonderScenes::whatIsIt,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER))
                .addStoryBoard("diffuser/beam_color",         DiffuserPonderScenes::beamColor,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER))
                .addStoryBoard("diffuser/carousel",           DiffuserPonderScenes::carousel,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER))
                .addStoryBoard("diffuser/redstone_control",   DiffuserPonderScenes::redstoneControl,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER))
                .addStoryBoard("diffuser/dual_shafts",        DiffuserPonderScenes::dualShafts,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER));

        // ── Group 2: Beam Shaping Lenses ─────────────────────────────────────

        helper.forComponents(key(ModRegistry.FOCUSING_LENS.get()))
                .addStoryBoard("lenses/focusing",             DiffuserPonderScenes::focusingLens,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER));

        helper.forComponents(key(ModRegistry.DISPERSING_LENS.get()))
                .addStoryBoard("lenses/dispersing",           DiffuserPonderScenes::dispersingLens,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER));

        // ── Group 3: Cinema Lenses & Speaker ─────────────────────────────────

        helper.forComponents(key(ModRegistry.FILM_REEL.get()))
                .addStoryBoard("cinema/film_reel",            DiffuserPonderScenes::filmReel,
                        e -> e.highlightTag(DiffuserPonderTags.CINEMA));

        helper.forComponents(key(ModRegistry.SILENT_PROJECTOR_LENS.get()))
                .addStoryBoard("cinema/silent_projector",     DiffuserPonderScenes::silentProjector,
                        e -> e.highlightTag(DiffuserPonderTags.CINEMA));

        helper.forComponents(key(ModRegistry.SOUND_PROJECTOR_LENS.get()))
                .addStoryBoard("cinema/sound_projector",      DiffuserPonderScenes::soundProjector,
                        e -> e.highlightTag(DiffuserPonderTags.CINEMA));

        helper.forComponents(key(ModRegistry.SPEAKER_ITEM.get()))
                .addStoryBoard("cinema/speaker_volume",       DiffuserPonderScenes::speakerVolume,
                        e -> e.highlightTag(DiffuserPonderTags.CINEMA));

        // ── Group 4: Hologram Lens ────────────────────────────────────────────

        helper.forComponents(key(ModRegistry.HOLOGRAM_LENS.get()))
                .addStoryBoard("hologram/basics",             DiffuserPonderScenes::hologramBasics,
                        e -> e.highlightTag(DiffuserPonderTags.CINEMA))
                .addStoryBoard("hologram/schematic",          DiffuserPonderScenes::hologramSchematic,
                        e -> e.highlightTag(DiffuserPonderTags.CINEMA));

        // ── Group 5: Combat Lenses ────────────────────────────────────────────

        helper.forComponents(key(ModRegistry.THERMAL_IGNITION_LENS.get()))
                .addStoryBoard("combat/thermal",              DiffuserPonderScenes::thermalLens,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER));

        helper.forComponents(key(ModRegistry.COLLIMATOR_LENS.get()))
                .addStoryBoard("combat/collimator",           DiffuserPonderScenes::collimatorLens,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER));

        // ── Group 6: Lead Glass ───────────────────────────────────────────────

        helper.forComponents(key(ModRegistry.LEAD_GLASS_ITEM.get()))
                .addStoryBoard("lead_glass/filter_and_screen",DiffuserPonderScenes::leadGlass,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 1.1 — What is the Diffuser?
    // ─────────────────────────────────────────────────────────────────────────
    public static void whatIsIt(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("diffuser_what_is_it", "The Optical Diffuser");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 2);
        BlockPos sourcePos   = new BlockPos(2, 1, 4); // glowstone placeholder for laser source

        scene.world().showSection(util.select().position(sourcePos),   Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(60)
                .text("The Optical Diffuser receives a laser from a Create: Optical source through its back face")
                .pointAt(util.vector().blockSurface(sourcePos, Direction.NORTH))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        scene.idle(70);

        scene.overlay().showText(60)
                .text("…and re-emits it as a shaped cone of light in front")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.SOUTH))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(70);

        scene.overlay().showText(50)
                .text("Without an incoming beam the diffuser produces no light — it is a receiver, not a source")
                .pointAt(util.vector().topOf(diffuserPos));
        scene.idle(60);

        scene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 1.2 — Beam Color via Stained Glass
    // ─────────────────────────────────────────────────────────────────────────
    public static void beamColor(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("diffuser_beam_color", "Coloring the Beam");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 2);
        BlockPos glassPos1   = new BlockPos(2, 1, 3);
        BlockPos glassPos2   = new BlockPos(2, 1, 1);

        scene.world().showSection(util.select().fromTo(new BlockPos(1, 1, 1), new BlockPos(3, 1, 4)), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(60)
                .text("Without any glass the beam uses the color of the upstream laser source")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        scene.idle(70);

        // Place red glass behind diffuser
        scene.world().setBlock(glassPos1, Blocks.RED_STAINED_GLASS.defaultBlockState(), false);
        scene.world().showSection(util.select().position(glassPos1), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(60)
                .text("Stained glass placed between the source and the diffuser tints the beam that color")
                .pointAt(util.vector().blockSurface(glassPos1, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        scene.idle(70);

        // Place blue glass in front
        scene.world().setBlock(glassPos2, Blocks.BLUE_STAINED_GLASS.defaultBlockState(), false);
        scene.world().showSection(util.select().position(glassPos2), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(60)
                .text("Glass in front also counts — both sides are scanned up to 12 blocks")
                .pointAt(util.vector().blockSurface(glassPos2, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.BLUE);
        scene.idle(70);

        scene.overlay().showText(50)
                .text("Multiple glass colors blend using subtractive optical mixing — just like real filters")
                .pointAt(util.vector().topOf(diffuserPos));
        scene.idle(60);

        scene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 1.3 — Carousel & Lens Slots
    // ─────────────────────────────────────────────────────────────────────────
    public static void carousel(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("diffuser_carousel", "The Lens Carousel");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 2);

        scene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(60)
                .text("The carousel on the left face holds up to 4 lenses — one per 90° slot")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        scene.idle(70);

        scene.overlay().showText(55)
                .text("Right-click any slot with a lens to install it. Right-click the slot again with an empty hand to retrieve it")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST));
        scene.idle(65);

        scene.overlay().showText(55)
                .text("The left kinetic shaft rotates the carousel — the slot pointing forward is always active")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.overlay().showText(55)
                .text("Use a wrench on the left face to step the carousel 90° by hand without a shaft")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.overlay().showText(50)
                .text("Right-click the front aperture with an empty hand to retrieve the currently active lens")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.NORTH));
        scene.idle(60);

        scene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 1.4 — Redstone Control
    // ─────────────────────────────────────────────────────────────────────────
    public static void redstoneControl(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("diffuser_redstone", "Redstone Control");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 2);
        BlockPos leverPos    = new BlockPos(3, 1, 2);

        scene.world().showSection(util.select().fromTo(new BlockPos(1, 1, 1), new BlockPos(4, 1, 3)), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(55)
                .text("With no redstone signal the diffuser operates at base intensity")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        scene.idle(65);

        scene.world().toggleRedstonePower(util.select().position(leverPos));
        scene.idle(5);

        scene.overlay().showText(60)
                .text("Signal 1–11 scales beam intensity from 10% to 100%")
                .pointAt(util.vector().topOf(leverPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(70);

        scene.overlay().showText(55)
                .text("Signals 12–14 overdrive the beam — wider cone and longer reach")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        scene.idle(65);

        scene.overlay().showText(55)
                .text("Signal 15 kills the beam completely — except for cinema and hologram modes, which ignore it")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        scene.idle(65);

        scene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 1.5 — Dual Shaft Roles
    // ─────────────────────────────────────────────────────────────────────────
    public static void dualShafts(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("diffuser_dual_shafts", "Two Independent Shafts");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 2);
        BlockPos leftShaft   = new BlockPos(1, 1, 2); // left cogwheel
        BlockPos bottomShaft = new BlockPos(2, 0, 2); // bottom cogwheel

        scene.world().showSection(util.select().fromTo(new BlockPos(0, 0, 1), new BlockPos(3, 2, 3)), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showOutline(net.createmod.ponder.api.PonderPalette.GREEN, "left",
                util.select().position(leftShaft), 60);
        scene.overlay().showText(60)
                .text("The left shaft rotates the lens carousel — cycling through installed lens slots")
                .pointAt(util.vector().blockSurface(leftShaft, Direction.WEST))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(70);

        scene.overlay().showOutline(net.createmod.ponder.api.PonderPalette.BLUE, "bottom",
                util.select().position(bottomShaft), 60);
        scene.overlay().showText(60)
                .text("The bottom shaft spins the beam around its own axis — controlling hologram rotation and polarization effects")
                .pointAt(util.vector().blockSurface(bottomShaft, Direction.DOWN))
                .colored(net.createmod.ponder.api.PonderPalette.BLUE);
        scene.idle(70);

        scene.overlay().showText(50)
                .text("Both shafts are independent — drive them from different sources for dynamic patterns")
                .pointAt(util.vector().topOf(diffuserPos));
        scene.idle(60);

        scene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 2.1 — Focusing Lens
    // ─────────────────────────────────────────────────────────────────────────
    public static void focusingLens(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("focusing_lens", "Focusing Lens");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 2);

        scene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(55)
                .text("By default the diffuser casts a broad 35° spotlight cone")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        scene.idle(65);

        scene.overlay().showText(60)
                .text("Insert a Focusing Lens — it narrows the cone based on shaft RPM, from 45° at rest down to 8° at speed")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(70);

        scene.overlay().showText(55)
                .text("Higher RPM = tighter beam, reaching further. Up to 64 blocks with a visible laser")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.overlay().showText(50)
                .text("A redstone signal compounds the effect — sharpening the cone even tighter on top of shaft speed")
                .pointAt(util.vector().topOf(diffuserPos));
        scene.idle(60);

        scene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 2.2 — Dispersing Lens
    // ─────────────────────────────────────────────────────────────────────────
    public static void dispersingLens(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("dispersing_lens", "Dispersing Lens");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 2);

        scene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(60)
                .text("The Dispersing Lens fans the beam wide — from 20° at rest up to 70° at full redstone power")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        scene.idle(70);

        scene.overlay().showText(55)
                .text("Coverage is maximized at the cost of range — ideal for large-area lighting: farms, warehouses, landing pads")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.overlay().showText(50)
                .text("Redstone signal 1–14 progressively widens the cone. Higher signal = wider fan")
                .pointAt(util.vector().topOf(diffuserPos));
        scene.idle(60);

        scene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 3.1 — Film Reel
    // ─────────────────────────────────────────────────────────────────────────
    public static void filmReel(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("film_reel", "Film Reel — Media Server");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos reelPos = new BlockPos(2, 1, 2);

        scene.world().showSection(util.select().position(reelPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(55)
                .text("The Film Reel block acts as a media server — it downloads and stores a video or audio file from a URL")
                .pointAt(util.vector().topOf(reelPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        scene.idle(65);

        scene.overlay().showText(55)
                .text("Right-click to open the GUI. Paste a direct URL and start the upload. The file is saved to the server — no client hosting needed")
                .pointAt(util.vector().blockSurface(reelPos, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.overlay().showText(50)
                .text("Break the block to retrieve the Film Reel item. It carries the uploaded media and can be inserted into a diffuser")
                .pointAt(util.vector().topOf(reelPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(60);

        scene.overlay().showText(50)
                .text("A comparator next to the Film Reel block outputs a signal proportional to playback progress")
                .pointAt(util.vector().blockSurface(reelPos, Direction.EAST));
        scene.idle(60);

        scene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 3.2 — Silent Projector Lens
    // ─────────────────────────────────────────────────────────────────────────
    public static void silentProjector(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("silent_projector_lens", "Silent Projector Lens");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(3, 1, 4);
        BlockPos reelPos     = new BlockPos(4, 1, 4); // right side of diffuser
        BlockPos screenStart = new BlockPos(1, 1, 1);
        BlockPos screenEnd   = new BlockPos(5, 3, 1);

        scene.world().showSection(util.select().fromTo(screenStart, screenEnd), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(60)
                .text("Hold the Silent Projector Lens and right-click one corner of a lead glass screen, then Shift+Right-click the opposite corner")
                .pointAt(util.vector().blockSurface(screenStart, Direction.EAST))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        scene.idle(70);

        scene.overlay().showText(55)
                .text("Insert the configured lens into the carousel. The lens now knows which screen to project onto")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.world().showSection(util.select().position(reelPos), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(55)
                .text("Place a Film Reel block on the right side of the diffuser, or insert a Film Reel item into the internal slot")
                .pointAt(util.vector().blockSurface(reelPos, Direction.EAST))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.overlay().showText(55)
                .text("With an incoming laser and a media source, video plays on the screen. No audio output")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        scene.idle(65);

        scene.overlay().showText(50)
                .text("Left shaft RPM controls playback speed. Stop the shaft to pause; reverse it to rewind")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST));
        scene.idle(60);

        scene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 3.3 — Sound Projector Lens + Speaker
    // ─────────────────────────────────────────────────────────────────────────
    public static void soundProjector(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("sound_projector_lens", "Sound Projector Lens");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(3, 1, 4);
        BlockPos speakerPos  = new BlockPos(0, 1, 2);
        BlockPos screenStart = new BlockPos(1, 1, 1);
        BlockPos screenEnd   = new BlockPos(5, 3, 1);

        scene.world().showSection(util.select().fromTo(screenStart, screenEnd), Direction.DOWN);
        scene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(55)
                .text("The Sound Projector Lens works identically to the silent version — with audio output enabled")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        scene.idle(65);

        scene.world().showSection(util.select().position(speakerPos), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(55)
                .text("Place a Speaker block anywhere. Right-click the diffuser while holding the Speaker item to link them")
                .pointAt(util.vector().blockSurface(speakerPos, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.overlay().showText(55)
                .text("Video plays on screen. Audio emits from the Speaker — spatially positioned for an immersive cinema setup")
                .pointAt(util.vector().topOf(speakerPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.overlay().showText(50)
                .text("Attach a Display Link to the Film Reel block to show filename and playback state on a Nixie Tube or Train Display")
                .pointAt(util.vector().topOf(diffuserPos));
        scene.idle(60);

        scene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 3.4 — Speaker Volume & Unlinking
    // ─────────────────────────────────────────────────────────────────────────
    public static void speakerVolume(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("speaker", "Speaker — Volume & Unlinking");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos speakerPos = new BlockPos(2, 1, 2);

        scene.world().showSection(util.select().position(speakerPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(55)
                .text("The linked Speaker emits audio positionally — players closer to the speaker hear it louder")
                .pointAt(util.vector().topOf(speakerPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        scene.idle(65);

        scene.overlay().showText(55)
                .text("Shift+Right-click the speaker with an empty hand to unlink it from its diffuser")
                .pointAt(util.vector().blockSurface(speakerPos, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.overlay().showText(55)
                .text("Breaking the speaker preserves the link in the dropped item — place it again to reconnect instantly")
                .pointAt(util.vector().topOf(speakerPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.overlay().showText(50)
                .text("To reset the link permanently: craft the Speaker item with an iron ingot — it becomes a fresh unlinked speaker")
                .pointAt(util.vector().topOf(speakerPos));
        scene.idle(60);

        scene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 4.1 — Hologram Basics
    // ─────────────────────────────────────────────────────────────────────────
    public static void hologramBasics(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("hologram_lens_basics", "Hologram Lens");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 2);

        scene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(60)
                .text("Without an imprinted target the Hologram Lens projects a navigational gyroscope. Its spin is driven by the bottom shaft")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        scene.idle(70);

        scene.overlay().showText(55)
                .text("To set a target: hold any item in your offhand and right-click the lens in your hotbar. The hologram switches to that object")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.overlay().showText(55)
                .text("With nothing in offhand, right-click the lens to cycle the projection distance: 2 → 4 → 6 → … → 32 blocks")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.overlay().showText(50)
                .text("Right-click while the lens is in your offhand to cycle scale: 0.5× → 1× → 1.5× → 2× → 3×")
                .pointAt(util.vector().topOf(diffuserPos));
        scene.idle(60);

        scene.overlay().showText(50)
                .text("Hold an item in offhand and right-click the lens again to clear the target — the gyroscope returns")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        scene.idle(60);

        scene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 4.2 — Hologram with Schematic
    // ─────────────────────────────────────────────────────────────────────────
    public static void hologramSchematic(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("hologram_lens_schematic", "Hologram — Schematic Preview");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 2);

        scene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(60)
                .text("Imprint a Create Schematic onto the Hologram Lens to project a rotating 3D preview of the entire structure")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        scene.idle(70);

        scene.overlay().showText(55)
                .text("The structure rotates continuously — driven by the bottom shaft. Useful for planning builds mid-construction")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.overlay().showText(50)
                .text("Bottom shaft speed directly controls hologram rotation speed — connect a faster source for faster spin")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.DOWN));
        scene.idle(60);

        scene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 5.1 — Thermal Ignition Lens
    // ─────────────────────────────────────────────────────────────────────────
    public static void thermalLens(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("thermal_ignition_lens", "Thermal Ignition Lens");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 5);
        BlockPos icePos      = new BlockPos(2, 1, 2);

        scene.world().showSection(util.select().fromTo(new BlockPos(1, 1, 1), new BlockPos(4, 1, 6)), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(55)
                .text("The Thermal Ignition Lens converts the beam into a focused heat ray")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        scene.idle(65);

        scene.overlay().showText(55)
                .text("Entities inside the cone are set on fire. Fire duration and damage scale with shaft RPM")
                .pointAt(util.vector().topOf(new BlockPos(2, 1, 3)))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        scene.idle(65);

        scene.overlay().showText(55)
                .text("Ice melts. Snow evaporates. Flammable blocks catch fire on sustained exposure")
                .pointAt(util.vector().topOf(icePos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.overlay().showText(55)
                .text("Items on belts or depots inside the cone are cooked — using Create's Fan Blasting/Smoking pipeline. High RPM blasts; low RPM smokes")
                .pointAt(util.vector().topOf(new BlockPos(3, 1, 3)));
        scene.idle(65);

        scene.overlay().showText(50)
                .text("Redstone narrows the cone from 28° to 12° — tighter focus for precision targeting")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(60);

        scene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 5.2 — Collimator Lens
    // ─────────────────────────────────────────────────────────────────────────
    public static void collimatorLens(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("collimator_lens", "Collimator Lens");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 7);
        BlockPos targetPos   = new BlockPos(2, 1, 1);

        scene.world().showSection(util.select().fromTo(new BlockPos(0, 1, 0), new BlockPos(4, 1, 8)), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(55)
                .text("The Collimator Lens produces a razor-sharp 2° needle beam — far narrower than any cone lens")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        scene.idle(65);

        scene.overlay().showText(55)
                .text("Range is dramatically extended — up to 96 blocks at high RPM. Ideal for long corridors or precision strikes")
                .pointAt(util.vector().topOf(new BlockPos(2, 1, 3)))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.overlay().showText(55)
                .text("The needle strikes a single narrow point. Entities beside the target remain unaffected")
                .pointAt(util.vector().topOf(targetPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        scene.idle(65);

        scene.overlay().showText(50)
                .text("Redstone sharpens it further — at signal 14 the beam is under 1° wide")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        scene.idle(60);

        scene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 6.1 — Lead Glass
    // ─────────────────────────────────────────────────────────────────────────
    public static void leadGlass(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("lead_glass", "Lead Glass — Filter & Screen");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 3);
        BlockPos glassWall   = new BlockPos(2, 1, 1);

        scene.world().showSection(util.select().fromTo(new BlockPos(1, 1, 1), new BlockPos(3, 2, 4)), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(55)
                .text("Lead Glass placed in the beam path serves a dual purpose — it is both an optical filter and a cinema screen")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        scene.idle(65);

        scene.world().setBlock(new BlockPos(2, 1, 2), Blocks.RED_STAINED_GLASS.defaultBlockState(), false);
        scene.idle(10);

        scene.overlay().showText(55)
                .text("Stained glass anywhere in the 12-block scan zone — in front or behind the diffuser — tints the beam")
                .pointAt(util.vector().blockSurface(new BlockPos(2, 1, 2), Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        scene.idle(65);

        scene.world().setBlock(new BlockPos(2, 2, 2), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), false);
        scene.idle(10);

        scene.overlay().showText(55)
                .text("Multiple glass colors blend subtractively: Red + Blue = Magenta. Red + Green = Yellow")
                .pointAt(util.vector().blockSurface(new BlockPos(2, 2, 2), Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.BLUE);
        scene.idle(65);

        scene.overlay().showText(50)
                .text("Lead glass also acts as the cinema screen for projector lenses — the video maps directly onto it")
                .pointAt(util.vector().blockSurface(glassWall, Direction.SOUTH));
        scene.idle(60);

        scene.markAsFinished();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static ResourceLocation key(net.minecraft.world.item.Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
    }

    private static ResourceLocation key(net.minecraft.world.level.ItemLike itemLike) {
        return key(itemLike.asItem());
    }
}
