package com.mik.optical_diffuser.ponder;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.OpticalDiffuserMod;
import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.lpcamors.optical.blocks.optical_source.BeamHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * All ponder scene definitions for Create: Optical Diffuser.
 * Each text instruction is registered with attachKeyFrame() for timeline navigation.
 */
public class DiffuserPonderScenes {

    static final String MOD = OpticalDiffuserMod.MOD_ID;

    static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD, path);
    }

    private static void lightDiffuser(CreateSceneBuilder cScene, SceneBuildingUtil util, BlockPos sourcePos, BlockPos diffuserPos, Vec3i color, float speed) {
        lightDiffuser(cScene, util, sourcePos, diffuserPos, color, speed, BeamHelper.BeamPolarization.RANDOM);
    }

    private static void lightDiffuser(CreateSceneBuilder cScene, SceneBuildingUtil util, BlockPos sourcePos, BlockPos diffuserPos, Vec3i color, float speed, BeamHelper.BeamPolarization polarization) {
        cScene.world().setKineticSpeed(util.select().position(sourcePos), speed);
        BeamHelper.BeamType type = BeamHelper.BeamType.getTypeBySpeed(speed);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setBeam(new BeamHelper.BeamProperties(
                    speed > 0 ? speed : 16f,
                    Direction.NORTH,
                    type,
                    polarization,
                    color != null ? color : new Vec3i(255, 255, 255),
                    java.util.List.of(),
                    false,
                    false,
                    false,
                    false
            ));
        });
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {

        // ── Group 1: Optical Diffuser & Beam Optics ──────────────────────────

        helper.forComponents(key(ModRegistry.OPTICAL_DIFFUSER_ITEM.get()))
                .addStoryBoard("diffuser/what_is_it",         DiffuserPonderScenes::whatIsIt,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER))
                .addStoryBoard("diffuser/beam_color",         DiffuserPonderScenes::beamColor,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER))
                .addStoryBoard("diffuser/carousel",           DiffuserPonderScenes::carousel,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER))
                .addStoryBoard("diffuser/dual_shafts",        DiffuserPonderScenes::bottomShaftRotation,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER));

        helper.forComponents(key(ModRegistry.FOCUSING_LENS.get()), key(ModRegistry.DISPERSING_LENS.get()))
                .addStoryBoard("lenses/beam_shaping",         DiffuserPonderScenes::beamShapingLenses,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER));

        helper.forComponents(key(ModRegistry.THERMAL_IGNITION_LENS.get()))
                .addStoryBoard("combat/thermal",              DiffuserPonderScenes::thermalLens,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER));

        helper.forComponents(key(ModRegistry.COLLIMATOR_LENS.get()))
                .addStoryBoard("combat/collimator",           DiffuserPonderScenes::collimatorLens,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER));

        helper.forComponents(key(ModRegistry.LEAD_GLASS_ITEM.get()))
                .addStoryBoard("lead_glass/radiation_shielding",DiffuserPonderScenes::leadGlassShielding,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER))
                .addStoryBoard("lead_glass/decor",              DiffuserPonderScenes::leadGlassDecor,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER));

        // ── Group 2: Cinema Projectors & Holograms ───────────────────────────

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

        helper.forComponents(key(ModRegistry.HOLOGRAM_LENS.get()))
                .addStoryBoard("hologram/basics",             DiffuserPonderScenes::hologramBasics,
                        e -> e.highlightTag(DiffuserPonderTags.CINEMA))
                .addStoryBoard("hologram/schematic",          DiffuserPonderScenes::hologramSchematic,
                        e -> e.highlightTag(DiffuserPonderTags.CINEMA));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 1.1 — What is the Diffuser?
    // ─────────────────────────────────────────────────────────────────────────
    public static void whatIsIt(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("diffuser_what_is_it", "The Optical Diffuser");
        cScene.configureBasePlate(0, 0, 5);
        cScene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 1);
        BlockPos sourcePos   = new BlockPos(2, 1, 3);
        BlockPos shaftPos    = new BlockPos(2, 1, 4);
        BlockPos leverPos    = new BlockPos(3, 1, 1);

        cScene.world().showSection(util.select().position(shaftPos),   Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos),   Direction.DOWN);
        cScene.idle(10);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        cScene.idle(15);

        cScene.overlay().showText(60)
                .attachKeyFrame()
                .text("The Optical Diffuser receives a laser from a Create: Optical source through its back face")
                .pointAt(util.vector().blockSurface(sourcePos, Direction.NORTH))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(70);

        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        cScene.idle(15);

        cScene.overlay().showText(60)
                .attachKeyFrame()
                .text("…and re-emits it as a shaped cone of light in front")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.SOUTH))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(70);

        // Emergency Shut-off via Level 15 Redstone
        cScene.world().showSection(util.select().position(leverPos), Direction.DOWN);
        cScene.idle(15);

        cScene.overlay().showText(65)
                .attachKeyFrame()
                .text("A maximum redstone signal (level 15) immediately shuts off the diffuser as an emergency stop")
                .pointAt(util.vector().topOf(leverPos))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        cScene.idle(20);

        cScene.world().toggleRedstonePower(util.select().position(leverPos));
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> be.onRedstonePowerChanged(15));
        cScene.idle(45);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("Without lenses, signals 0–14 do not affect the beam; only signal 15 disables it")
                .pointAt(util.vector().topOf(diffuserPos));
        cScene.idle(20);

        cScene.world().toggleRedstonePower(util.select().position(leverPos));
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> be.onRedstonePowerChanged(0));
        cScene.idle(45);

        // No input laser = no light
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 0f);
        cScene.world().setKineticSpeed(util.select().position(sourcePos), 0f);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, OpticalDiffuserBlockEntity::removeBeam);
        cScene.idle(10);

        cScene.overlay().showText(50)
                .attachKeyFrame()
                .text("Without an incoming beam the diffuser produces no light — it is a receiver, not a source")
                .pointAt(util.vector().topOf(diffuserPos));
        cScene.idle(60);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 1.2 — Beam Color via Stained Glass
    // ─────────────────────────────────────────────────────────────────────────
    public static void beamColor(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("diffuser_beam_color", "Coloring the Beam");
        cScene.configureBasePlate(0, 0, 5);
        cScene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 1);
        BlockPos glassPos1   = new BlockPos(2, 1, 2);
        BlockPos sourcePos   = new BlockPos(2, 1, 3);
        BlockPos shaftPos    = new BlockPos(2, 1, 4);

        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        cScene.idle(15);

        cScene.overlay().showText(60)
                .attachKeyFrame()
                .text("Without any glass the beam uses the color of the upstream laser source")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(70);

        cScene.world().setBlock(glassPos1, Blocks.RED_STAINED_GLASS.defaultBlockState(), true);
        cScene.world().showSection(util.select().position(glassPos1), Direction.DOWN);
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 50, 50), 64f);
        cScene.idle(10);

        cScene.overlay().showText(60)
                .attachKeyFrame()
                .text("Stained glass placed between the source and the diffuser tints the beam that color")
                .pointAt(util.vector().blockSurface(glassPos1, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        cScene.idle(70);

        BlockPos glassPos2 = new BlockPos(2, 1, 0);
        cScene.world().setBlock(glassPos2, Blocks.BLUE_STAINED_GLASS.defaultBlockState(), true);
        cScene.world().showSection(util.select().position(glassPos2), Direction.DOWN);
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(180, 50, 255), 64f);
        cScene.idle(10);

        cScene.overlay().showText(60)
                .attachKeyFrame()
                .text("Glass in front also counts — both sides are scanned up to 12 blocks")
                .pointAt(util.vector().blockSurface(glassPos2, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.BLUE);
        cScene.idle(70);

        cScene.overlay().showText(50)
                .attachKeyFrame()
                .text("Multiple glass colors blend using subtractive optical mixing — just like real filters")
                .pointAt(util.vector().topOf(diffuserPos));
        cScene.idle(60);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 1.3 — Carousel & Lens Slots
    // ─────────────────────────────────────────────────────────────────────────
    public static void carousel(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("diffuser_carousel", "The Lens Carousel");
        cScene.configureBasePlate(0, 0, 5);
        cScene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 1);
        BlockPos leftShaft   = new BlockPos(1, 1, 1);
        BlockPos sourcePos   = new BlockPos(2, 1, 3);
        BlockPos shaftPos    = new BlockPos(2, 1, 4);

        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        cScene.idle(15);

        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setCarouselLens(0, new ItemStack(ModRegistry.FOCUSING_LENS.get()));
            be.setCarouselLens(1, new ItemStack(ModRegistry.DISPERSING_LENS.get()));
            be.setCarouselLens(2, new ItemStack(ModRegistry.THERMAL_IGNITION_LENS.get()));
            be.setCarouselLens(3, new ItemStack(ModRegistry.COLLIMATOR_LENS.get()));
        });
        cScene.idle(10);

        cScene.overlay().showText(60)
                .attachKeyFrame()
                .text("The carousel on the left face holds up to 4 lenses — one per 90° slot")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(70);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("Right-click any slot with a lens to install it. Right-click the slot again with an empty hand to retrieve it")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST));
        cScene.idle(65);

        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> be.rotateCarousel(90f));
        cScene.idle(10);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("Use a wrench on the left face to step the carousel 90° by hand without a shaft")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(65);

        cScene.world().showSection(util.select().position(leftShaft), Direction.DOWN);
        cScene.world().setKineticSpeed(util.select().position(leftShaft), 16f);
        cScene.world().setKineticSpeed(util.select().position(diffuserPos), 16f);
        cScene.idle(15);

        cScene.overlay().showText(60)
                .attachKeyFrame()
                .text("The left kinetic shaft continuously rotates the carousel — cycling active lenses as it spins")
                .pointAt(util.vector().blockSurface(leftShaft, Direction.WEST))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(60);

        cScene.world().setKineticSpeed(util.select().position(leftShaft), 0f);
        cScene.world().setKineticSpeed(util.select().position(diffuserPos), 0f);
        cScene.idle(15);

        cScene.overlay().showText(50)
                .attachKeyFrame()
                .text("Right-click the front aperture with an empty hand to retrieve the currently active lens")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.NORTH));
        cScene.idle(60);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 1.4 — Bottom Shaft & Beam Rotation
    // ─────────────────────────────────────────────────────────────────────────
    public static void bottomShaftRotation(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("diffuser_bottom_shaft", "Bottom Shaft & Beam Rotation");
        cScene.configureBasePlate(0, 0, 5);
        cScene.showBasePlate();

        BlockPos diffuserPos    = new BlockPos(0, 1, 1);
        BlockPos sourcePos      = new BlockPos(0, 1, 3);
        BlockPos sourceShaftPos = new BlockPos(0, 1, 4);
        BlockPos bottomCog      = new BlockPos(0, 0, 1);

        cScene.world().showSection(util.select().position(sourceShaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f, BeamHelper.BeamPolarization.RANDOM);
        cScene.world().setKineticSpeed(util.select().position(sourceShaftPos), 64f);
        cScene.idle(15);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("By default the diffuser casts a round conical beam")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(65);

        // Switch to Horizontal Polarization
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f, BeamHelper.BeamPolarization.HORIZONTAL);
        cScene.idle(10);

        cScene.overlay().showText(65)
                .attachKeyFrame()
                .text("Changing beam polarization at the Optical Source flattens the cone into a wide horizontal fan")
                .pointAt(util.vector().topOf(sourcePos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(75);

        cScene.world().showSection(util.select().position(bottomCog), Direction.UP);
        cScene.idle(10);
        cScene.overlay().showOutline(net.createmod.ponder.api.PonderPalette.BLUE, "bottom",
                util.select().position(bottomCog), 60);
        cScene.overlay().showText(60)
                .attachKeyFrame()
                .text("Connecting rotational power to the bottom socket spins the beam around its axis")
                .pointAt(util.vector().topOf(bottomCog))
                .colored(net.createmod.ponder.api.PonderPalette.BLUE);
        cScene.idle(70);

        cScene.world().setKineticSpeed(util.select().position(bottomCog), 32f);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> be.setSpinSpeed(32f));
        cScene.idle(15);

        cScene.overlay().showText(65)
                .attachKeyFrame()
                .text("With horizontal polarization, the rotating fan sweeps around like a lighthouse")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(75);

        cScene.overlay().showText(60)
                .attachKeyFrame()
                .text("Rotation speed is proportional to bottom shaft RPM — ideal for searchlights and lighthouses")
                .pointAt(util.vector().topOf(diffuserPos));
        cScene.idle(70);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 2.1 — Beam Shaping: Focusing & Dispersing Lenses
    // ─────────────────────────────────────────────────────────────────────────
    public static void beamShapingLenses(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("lenses_beam_shaping", "Beam Shaping: Focusing & Dispersing");
        cScene.configureBasePlate(0, 0, 7);
        cScene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(3, 1, 2);
        BlockPos sourcePos   = new BlockPos(3, 1, 4);
        BlockPos shaftPos    = new BlockPos(3, 1, 5);
        BlockPos cogPos      = new BlockPos(2, 1, 2);
        BlockPos leverPos    = new BlockPos(4, 1, 2);

        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(cogPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(leverPos), Direction.DOWN);
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        cScene.idle(15);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("By default the diffuser casts a standard 35° light cone")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(65);

        // Part 1: Focusing Lens
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setCarouselLens(0, new ItemStack(ModRegistry.FOCUSING_LENS.get()));
        });
        cScene.world().setKineticSpeed(util.select().position(cogPos), 48f);
        cScene.world().setKineticSpeed(util.select().position(diffuserPos), 48f);
        cScene.idle(10);

        cScene.overlay().showText(65)
                .attachKeyFrame()
                .text("The Focusing Lens narrows the beam into a spotlight — down from 45° to 8° as shaft speed increases")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(75);

        cScene.world().toggleRedstonePower(util.select().position(leverPos));
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> be.onRedstonePowerChanged(14));
        cScene.idle(10);

        cScene.overlay().showText(60)
                .attachKeyFrame()
                .text("A redstone signal compounds the effect, focusing the beam even tighter for extreme range")
                .pointAt(util.vector().topOf(leverPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(70);

        cScene.world().toggleRedstonePower(util.select().position(leverPos));
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> be.onRedstonePowerChanged(0));
        cScene.idle(15);

        // Part 2: Dispersing Lens
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setCarouselLens(0, new ItemStack(ModRegistry.DISPERSING_LENS.get()));
        });
        cScene.idle(10);

        cScene.overlay().showText(65)
                .attachKeyFrame()
                .text("Swapping to a Dispersing Lens fans the beam out into a wide floodlight (from 20° up to 70°)")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(75);

        cScene.world().toggleRedstonePower(util.select().position(leverPos));
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> be.onRedstonePowerChanged(14));
        cScene.idle(10);

        cScene.overlay().showText(65)
                .attachKeyFrame()
                .text("Redstone signal 1–14 widens the cone up to 70° — maximizing coverage for large halls and fields")
                .pointAt(util.vector().topOf(leverPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(75);

        cScene.world().toggleRedstonePower(util.select().position(leverPos));
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> be.onRedstonePowerChanged(0));
        cScene.idle(15);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 3.1 — Film Reel
    // ─────────────────────────────────────────────────────────────────────────
    public static void filmReel(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("film_reel", "Film Reel — Media Server");
        cScene.configureBasePlate(0, 0, 5);
        cScene.showBasePlate();

        BlockPos reelPos = new BlockPos(2, 1, 2);

        cScene.world().showSection(util.select().position(reelPos), Direction.DOWN);
        cScene.idle(15);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("The Film Reel block acts as a media server — it downloads and stores a video or audio file from a URL")
                .pointAt(util.vector().topOf(reelPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(65);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("Right-click to open the GUI. Paste a direct URL and start the upload. The file is saved to the server — no client hosting needed")
                .pointAt(util.vector().blockSurface(reelPos, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(65);

        cScene.overlay().showText(50)
                .attachKeyFrame()
                .text("Break the block to retrieve the Film Reel item. It carries the uploaded media and can be inserted into a diffuser")
                .pointAt(util.vector().topOf(reelPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(60);

        cScene.overlay().showText(50)
                .attachKeyFrame()
                .text("A comparator next to the Film Reel block outputs a signal proportional to playback progress")
                .pointAt(util.vector().blockSurface(reelPos, Direction.EAST));
        cScene.idle(60);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 3.2 — Silent Projector Lens
    // ─────────────────────────────────────────────────────────────────────────
    public static void silentProjector(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("silent_projector_lens", "Silent Projector Lens");
        cScene.configureBasePlate(0, 0, 7);
        cScene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(3, 1, 2);
        BlockPos reelPos     = new BlockPos(4, 1, 2);
        BlockPos sourcePos   = new BlockPos(3, 1, 4);
        BlockPos shaftPos    = new BlockPos(3, 1, 5);
        BlockPos screenStart = new BlockPos(1, 1, 0);
        BlockPos screenEnd   = new BlockPos(5, 3, 0);

        cScene.world().showSection(util.select().fromTo(screenStart, screenEnd), Direction.DOWN);
        cScene.idle(10);
        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        cScene.idle(10);

        cScene.overlay().showText(60)
                .attachKeyFrame()
                .text("Hold the Silent Projector Lens and right-click one corner of a screen wall, then Shift+Right-click the opposite corner")
                .pointAt(util.vector().blockSurface(screenStart, Direction.EAST))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(70);

        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setCarouselLens(0, new ItemStack(ModRegistry.SILENT_PROJECTOR_LENS.get()));
        });
        cScene.idle(10);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("Insert the configured lens into the carousel. The lens now knows which screen to project onto")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(65);

        cScene.world().showSection(util.select().position(reelPos), Direction.DOWN);
        cScene.idle(10);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("Place a Film Reel block on the right side of the diffuser, or insert a Film Reel item into the internal slot")
                .pointAt(util.vector().blockSurface(reelPos, Direction.EAST))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(65);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("With an incoming laser and a media source, video plays on the screen. No audio output")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(65);

        cScene.overlay().showText(50)
                .attachKeyFrame()
                .text("Left shaft RPM controls playback speed. Stop the shaft to pause; reverse it to rewind")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST));
        cScene.idle(60);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 3.3 — Sound Projector Lens + Speaker
    // ─────────────────────────────────────────────────────────────────────────
    public static void soundProjector(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("sound_projector_lens", "Sound Projector Lens");
        cScene.configureBasePlate(0, 0, 7);
        cScene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(3, 1, 2);
        BlockPos speakerPos  = new BlockPos(0, 1, 2);
        BlockPos reelPos     = new BlockPos(4, 1, 2);
        BlockPos sourcePos   = new BlockPos(3, 1, 4);
        BlockPos shaftPos    = new BlockPos(3, 1, 5);
        BlockPos screenStart = new BlockPos(1, 1, 0);
        BlockPos screenEnd   = new BlockPos(5, 3, 0);

        cScene.world().showSection(util.select().fromTo(screenStart, screenEnd), Direction.DOWN);
        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(reelPos), Direction.DOWN);
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setCarouselLens(0, new ItemStack(ModRegistry.SOUND_PROJECTOR_LENS.get()));
        });
        cScene.idle(15);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("The Sound Projector Lens works identically to the silent version — with audio output enabled")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(65);

        cScene.world().showSection(util.select().position(speakerPos), Direction.DOWN);
        cScene.idle(10);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("Place a Speaker block anywhere. Right-click the diffuser while holding the Speaker item to link them")
                .pointAt(util.vector().blockSurface(speakerPos, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(65);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("Video plays on screen. Audio emits from the Speaker — spatially positioned for an immersive cinema setup")
                .pointAt(util.vector().topOf(speakerPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(65);

        cScene.overlay().showText(50)
                .attachKeyFrame()
                .text("Attach a Display Link to the Film Reel block to show filename and playback state on a Nixie Tube or Train Display")
                .pointAt(util.vector().topOf(diffuserPos));
        cScene.idle(60);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 3.4 — Speaker Volume & Unlinking
    // ─────────────────────────────────────────────────────────────────────────
    public static void speakerVolume(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("speaker", "Speaker — Volume & Unlinking");
        cScene.configureBasePlate(0, 0, 5);
        cScene.showBasePlate();

        BlockPos speakerPos = new BlockPos(2, 1, 2);

        cScene.world().showSection(util.select().position(speakerPos), Direction.DOWN);
        cScene.idle(15);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("The linked Speaker emits audio positionally — players closer to the speaker hear it louder")
                .pointAt(util.vector().topOf(speakerPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(65);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("Shift+Right-click the speaker with an empty hand to unlink it from its diffuser")
                .pointAt(util.vector().blockSurface(speakerPos, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(65);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("Breaking the speaker preserves the link in the dropped item — place it again to reconnect instantly")
                .pointAt(util.vector().topOf(speakerPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(65);

        cScene.overlay().showText(50)
                .attachKeyFrame()
                .text("To reset the link permanently: craft the Speaker item with an iron ingot — it becomes a fresh unlinked speaker")
                .pointAt(util.vector().topOf(speakerPos));
        cScene.idle(60);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 4.1 — Hologram Basics
    // ─────────────────────────────────────────────────────────────────────────
    public static void hologramBasics(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("hologram_lens_basics", "Hologram Lens");
        cScene.configureBasePlate(0, 0, 5);
        cScene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 1);
        BlockPos sourcePos   = new BlockPos(2, 1, 3);
        BlockPos shaftPos    = new BlockPos(2, 1, 4);
        BlockPos bottomShaft = new BlockPos(2, 0, 1);

        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(bottomShaft), Direction.UP);
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        cScene.world().setKineticSpeed(util.select().position(bottomShaft), 24f);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setCarouselLens(0, new ItemStack(ModRegistry.HOLOGRAM_LENS.get()));
            be.setSpinSpeed(24f);
        });
        cScene.idle(15);

        cScene.overlay().showText(60)
                .attachKeyFrame()
                .text("Without an imprinted target the Hologram Lens projects a navigational gyroscope. Its spin is driven by the bottom shaft")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(70);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("To set a target: hold any item in your offhand and right-click the lens in your hotbar. The hologram switches to that object")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(65);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("With nothing in offhand, right-click the lens to cycle the projection distance: 2 → 4 → 6 → … → 32 blocks")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(65);

        cScene.overlay().showText(50)
                .attachKeyFrame()
                .text("Right-click while the lens is in your offhand to cycle scale: 0.5× → 1× → 1.5× → 2× → 3×")
                .pointAt(util.vector().topOf(diffuserPos));
        cScene.idle(60);

        cScene.overlay().showText(50)
                .attachKeyFrame()
                .text("Hold an item in offhand and right-click the lens again to clear the target — the gyroscope returns")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        cScene.idle(60);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 4.2 — Hologram with Schematic
    // ─────────────────────────────────────────────────────────────────────────
    public static void hologramSchematic(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("hologram_lens_schematic", "Hologram — Schematic Preview");
        cScene.configureBasePlate(0, 0, 7);
        cScene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(3, 1, 2);
        BlockPos sourcePos   = new BlockPos(3, 1, 4);
        BlockPos shaftPos    = new BlockPos(3, 1, 5);
        BlockPos bottomShaft = new BlockPos(3, 0, 2);

        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(bottomShaft), Direction.UP);
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        cScene.world().setKineticSpeed(util.select().position(bottomShaft), 16f);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setCarouselLens(0, new ItemStack(ModRegistry.HOLOGRAM_LENS.get()));
            be.setSpinSpeed(16f);
        });
        cScene.idle(15);

        cScene.overlay().showText(60)
                .attachKeyFrame()
                .text("Imprint a Create Schematic onto the Hologram Lens to project a rotating 3D preview of the entire structure")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(70);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("The structure rotates continuously — driven by the bottom shaft. Useful for planning builds mid-construction")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(65);

        cScene.overlay().showText(50)
                .attachKeyFrame()
                .text("Bottom shaft speed directly controls hologram rotation speed — connect a faster source for faster spin")
                .pointAt(util.vector().centerOf(diffuserPos));
        cScene.idle(60);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 5.1 — Thermal Ignition Lens
    // ─────────────────────────────────────────────────────────────────────────
    public static void thermalLens(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("thermal_ignition_lens", "Thermal Ignition Lens");
        cScene.configureBasePlate(0, 0, 7);
        cScene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 3);
        BlockPos sourcePos   = new BlockPos(2, 1, 5);
        BlockPos shaftPos    = new BlockPos(2, 1, 6);
        BlockPos icePos      = new BlockPos(2, 1, 1);

        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(icePos), Direction.DOWN);
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 120, 50), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setCarouselLens(0, new ItemStack(ModRegistry.THERMAL_IGNITION_LENS.get()));
        });
        cScene.idle(15);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("The Thermal Ignition Lens converts the beam into a focused heat ray")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        cScene.idle(65);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("Entities inside the cone are set on fire. Fire duration and damage scale with shaft RPM")
                .pointAt(util.vector().topOf(new BlockPos(2, 1, 2)))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        cScene.idle(65);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("Ice melts. Snow evaporates. Flammable blocks catch fire on sustained exposure")
                .pointAt(util.vector().topOf(icePos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(65);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("Items on belts or depots inside the cone are cooked — using Create's Fan Blasting/Smoking pipeline. High RPM blasts; low RPM smokes")
                .pointAt(util.vector().topOf(new BlockPos(3, 1, 2)));
        cScene.idle(65);

        cScene.overlay().showText(50)
                .attachKeyFrame()
                .text("Redstone narrows the cone from 28° to 12° — tighter focus for precision targeting")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(60);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 5.2 — Collimator Lens
    // ─────────────────────────────────────────────────────────────────────────
    public static void collimatorLens(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("collimator_lens", "Collimator Lens");
        cScene.configureBasePlate(0, 0, 7);
        cScene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(2, 1, 3);
        BlockPos sourcePos   = new BlockPos(2, 1, 5);
        BlockPos shaftPos    = new BlockPos(2, 1, 6);
        BlockPos targetPos   = new BlockPos(2, 1, 1);

        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(targetPos), Direction.DOWN);
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setCarouselLens(0, new ItemStack(ModRegistry.COLLIMATOR_LENS.get()));
        });
        cScene.idle(15);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("The Collimator Lens produces a razor-sharp 2° needle beam — far narrower than any cone lens")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(65);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("Range is dramatically extended — up to 96 blocks at high RPM. Ideal for long corridors or precision strikes")
                .pointAt(util.vector().topOf(new BlockPos(2, 1, 2)))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(65);

        cScene.overlay().showText(55)
                .attachKeyFrame()
                .text("The needle strikes a single narrow point. Entities beside the target remain unaffected")
                .pointAt(util.vector().topOf(targetPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(65);

        cScene.overlay().showText(50)
                .attachKeyFrame()
                .text("Redstone sharpens it further — at signal 14 the beam is under 1° wide")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        cScene.idle(60);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 6.1 — Lead Glass: Radiation Shielding
    // ─────────────────────────────────────────────────────────────────────────
    public static void leadGlassShielding(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("lead_glass_shielding", "Lead Glass — Radiation Shielding");
        cScene.configureBasePlate(0, 0, 7);
        cScene.showBasePlate();

        BlockPos shaftPos    = new BlockPos(2, 1, 6);
        BlockPos sourcePos   = new BlockPos(2, 1, 5);
        BlockPos diffuserPos = new BlockPos(2, 1, 4);
        BlockPos glassPos    = new BlockPos(2, 1, 3);
        BlockPos depotPos    = new BlockPos(1, 1, 2);
        BlockPos icePos      = new BlockPos(3, 1, 2);
        Vec3 zombiePos       = util.vector().topOf(util.grid().at(2, 0, 1));
        Vec3 floorMeatPos    = util.vector().topOf(util.grid().at(2, 0, 2)).add(0, 0.05, 0);

        // Show machine and targets (depot, ice)
        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(depotPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(icePos), Direction.DOWN);
        cScene.idle(10);

        // Put raw beef on depot and drop raw beef on floor (zero gravity, resting flat)
        cScene.world().modifyBlockEntity(depotPos, DepotBlockEntity.class, be -> be.setHeldItem(new ItemStack(Items.BEEF)));
        ElementLink<EntityElement> floorMeat = cScene.world().createItemEntity(floorMeatPos, Vec3.ZERO, new ItemStack(Items.BEEF));
        cScene.world().modifyEntity(floorMeat, e -> {
            if (e instanceof ItemEntity ie) {
                ie.setNoGravity(true);
                ie.setDeltaMovement(Vec3.ZERO);
            }
        });
        cScene.idle(10);

        // Phase 1: Low RPM (Microwave: 8–32 RPM, white beam)
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 16f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 16f);
        cScene.idle(15);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("At low source speeds (8–32 RPM), the diffuser emits microwaves which cook food, melt snow and ice, and ignite mobs")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(20);

        // Cook food on depot and floor
        cScene.effects().emitParticles(util.vector().topOf(depotPos),
                cScene.effects().particleEmitterWithinBlockSpace(ParticleTypes.SMOKE, new Vec3(0, 0.05, 0)), 1.0f, 6);
        cScene.effects().emitParticles(floorMeatPos,
                cScene.effects().particleEmitterWithinBlockSpace(ParticleTypes.SMOKE, new Vec3(0, 0.05, 0)), 1.0f, 6);
        cScene.world().modifyBlockEntity(depotPos, DepotBlockEntity.class, be -> be.setHeldItem(new ItemStack(Items.COOKED_BEEF)));
        cScene.world().modifyEntity(floorMeat, e -> {
            if (e instanceof ItemEntity ie) {
                ie.setItem(new ItemStack(Items.COOKED_BEEF));
            }
        });

        // Melt ice
        cScene.effects().emitParticles(util.vector().topOf(icePos),
                cScene.effects().simpleParticleEmitter(ParticleTypes.SPLASH, Vec3.ZERO), 1.0f, 15);
        cScene.world().setBlock(icePos, Blocks.WATER.defaultBlockState(), true);
        cScene.idle(15);

        // Spawn zombie that catches fire from microwave radiation (stationary legs, no AI)
        ElementLink<EntityElement> zombie1 = cScene.world().createEntity(level -> {
            Zombie z = EntityType.ZOMBIE.create(level);
            if (z != null) {
                z.setPos(zombiePos.x, zombiePos.y, zombiePos.z);
                z.xo = zombiePos.x;
                z.yo = zombiePos.y;
                z.zo = zombiePos.z;
                z.setYRot(180f);
                z.yRotO = 180f;
                z.setNoAi(true);
                z.setDeltaMovement(Vec3.ZERO);
                z.setOnGround(true);
                z.walkAnimation.setSpeed(0.0f);
            }
            return z;
        });
        cScene.idle(10);

        // Continuous realistic fire: on fire texture + recurring damage flinches
        cScene.world().modifyEntity(zombie1, e -> {
            if (e instanceof LivingEntity le) {
                le.setRemainingFireTicks(300);
                le.setSharedFlagOnFire(true);
                le.handleDamageEvent(le.damageSources().inFire());
            }
        });
        cScene.effects().emitParticles(zombiePos.add(0, 0.5, 0),
                cScene.effects().particleEmitterWithinBlockSpace(ParticleTypes.SMOKE, new Vec3(0, 0.05, 0)), 1.0f, 6);
        cScene.idle(20);

        cScene.world().modifyEntity(zombie1, e -> {
            if (e instanceof LivingEntity le) {
                le.handleDamageEvent(le.damageSources().inFire());
            }
        });
        cScene.effects().emitParticles(zombiePos.add(0, 0.5, 0),
                cScene.effects().particleEmitterWithinBlockSpace(ParticleTypes.SMOKE, new Vec3(0, 0.05, 0)), 1.0f, 6);
        cScene.idle(20);

        cScene.world().modifyEntity(zombie1, e -> {
            if (e instanceof LivingEntity le) {
                le.handleDamageEvent(le.damageSources().inFire());
            }
        });
        cScene.idle(20);
        cScene.world().modifyEntity(zombie1, Entity::discard);
        cScene.idle(10);

        // Phase 2: High RPM (Gamma Radiation: >128 RPM, white beam)
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 256f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 256f);
        cScene.idle(15);

        cScene.overlay().showText(90)
                .attachKeyFrame()
                .text("At high speeds (>128 RPM), it emits lethal Gamma radiation inflicting Wither, Weakness, and heavy radiation damage")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.NORTH))
                .colored(net.createmod.ponder.api.PonderPalette.RED);

        // Spawn zombie in lethal gamma radiation with low HP (6.0 = 3 hearts)
        ElementLink<EntityElement> zombie2 = cScene.world().createEntity(level -> {
            Zombie z = EntityType.ZOMBIE.create(level);
            if (z != null) {
                z.setPos(zombiePos.x, zombiePos.y, zombiePos.z);
                z.xo = zombiePos.x;
                z.yo = zombiePos.y;
                z.zo = zombiePos.z;
                z.setYRot(180f);
                z.yRotO = 180f;
                z.setNoAi(true);
                z.setDeltaMovement(Vec3.ZERO);
                z.setOnGround(true);
                z.walkAnimation.setSpeed(0.0f);
                z.setHealth(6.0f);
            }
            return z;
        });
        cScene.idle(10);

        // Pulse 1: Enters radiation, gets irradiated (wither + glowing + damage flash)
        cScene.world().modifyEntity(zombie2, e -> {
            if (e instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(MobEffects.WITHER, 300, 1));
                le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0));
                le.setGlowingTag(true);
                le.handleDamageEvent(le.damageSources().magic());
            }
        });
        cScene.effects().emitParticles(zombiePos.add(0, 0.6, 0),
                cScene.effects().particleEmitterWithinBlockSpace(ParticleTypes.WITCH, new Vec3(0, 0.05, 0)), 1.5f, 10);
        cScene.idle(20);

        // Pulse 2: Second radiation tick
        cScene.world().modifyEntity(zombie2, e -> {
            if (e instanceof LivingEntity le) {
                le.handleDamageEvent(le.damageSources().magic());
            }
        });
        cScene.effects().emitParticles(zombiePos.add(0, 0.6, 0),
                cScene.effects().particleEmitterWithinBlockSpace(ParticleTypes.WITCH, new Vec3(0, 0.05, 0)), 1.5f, 10);
        cScene.idle(20);

        // Pulse 3: Fatal damage and death collapse (exact vanilla fall animation)
        cScene.world().modifyEntity(zombie2, e -> {
            if (e instanceof LivingEntity le) {
                le.handleDamageEvent(le.damageSources().magic());
                le.handleEntityEvent((byte) 3);
            }
        });
        cScene.idle(20); // 20 ticks for entity to smoothly tilt 90° onto the floor
        cScene.effects().emitParticles(zombiePos.add(0, 0.2, 0),
                cScene.effects().particleEmitterWithinBlockSpace(ParticleTypes.POOF, new Vec3(0, 0.05, 0)), 1.5f, 15);
        cScene.idle(10);
        cScene.world().modifyEntity(zombie2, Entity::discard);
        cScene.idle(10);

        // Phase 3: Lead Glass Shielding
        cScene.world().showSection(util.select().position(glassPos), Direction.DOWN);
        cScene.idle(15);

        cScene.overlay().showOutline(net.createmod.ponder.api.PonderPalette.GREEN, "glass",
                util.select().position(glassPos), 60);
        cScene.overlay().showText(65)
                .attachKeyFrame()
                .text("Placing Lead Glass in the beam path completely blocks harmful Gamma radiation, rendering the cone safe for personnel")
                .pointAt(util.vector().topOf(glassPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);

        // Spawn unharmed mob behind lead glass shield
        ElementLink<EntityElement> zombie3 = cScene.world().createEntity(level -> {
            Zombie z = EntityType.ZOMBIE.create(level);
            if (z != null) {
                z.setPos(zombiePos.x, zombiePos.y, zombiePos.z);
                z.xo = zombiePos.x;
                z.yo = zombiePos.y;
                z.zo = zombiePos.z;
                z.setYRot(180f);
                z.yRotO = 180f;
                z.setNoAi(true);
                z.setDeltaMovement(Vec3.ZERO);
                z.setOnGround(true);
                z.walkAnimation.setSpeed(0.0f);
            }
            return z;
        });
        cScene.idle(75);

        cScene.overlay().showText(50)
                .attachKeyFrame()
                .text("Visible light continues to pass through, allowing safe illumination without radiation hazards")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.NORTH));
        cScene.idle(60);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 6.2 — Lead Glass: Connected Windows & Decor
    // ─────────────────────────────────────────────────────────────────────────
    public static void leadGlassDecor(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("lead_glass_decor", "Lead Glass — Connected Windows");
        cScene.configureBasePlate(0, 0, 5);
        cScene.showBasePlate();

        Selection frame = util.select().fromTo(new BlockPos(0, 1, 2), new BlockPos(4, 3, 2))
                .substract(util.select().fromTo(new BlockPos(1, 1, 2), new BlockPos(3, 2, 2)));
        Selection window = util.select().fromTo(new BlockPos(1, 1, 2), new BlockPos(3, 2, 2));

        cScene.world().showSection(frame, Direction.DOWN);
        cScene.idle(15);
        cScene.world().showSection(window, Direction.DOWN);
        cScene.idle(20);

        cScene.overlay().showText(65)
                .attachKeyFrame()
                .text("Lead Glass features seamless connected textures, joining into large panoramic windowpanes without internal seams")
                .pointAt(util.vector().blockSurface(new BlockPos(2, 2, 2), Direction.SOUTH))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(75);

        cScene.overlay().showText(60)
                .attachKeyFrame()
                .text("Ideal for airship observation bridges, radiation containment chambers, and laboratory viewing ports")
                .pointAt(util.vector().blockSurface(new BlockPos(2, 1, 2), Direction.SOUTH))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(70);

        cScene.markAsFinished();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static ResourceLocation key(net.minecraft.world.item.Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
    }

    private static ResourceLocation key(net.minecraft.world.level.ItemLike itemLike) {
        return key(itemLike.asItem());
    }
}
