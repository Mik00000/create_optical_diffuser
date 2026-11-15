package com.mik.optical_diffuser.ponder;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.OpticalDiffuserMod;
import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mik.optical_diffuser.item.HologramLensItem;
import com.mik.optical_diffuser.item.ProjectorLensItem;
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
import net.minecraft.world.entity.monster.warden.Warden;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import net.createmod.catnip.math.Pointing;
import net.minecraft.nbt.CompoundTag;
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

    private static void setPonderSignal(CreateSceneBuilder cScene, SceneBuildingUtil util,
                                        BlockPos leverPos, BlockPos nixiePos, BlockPos diffuserPos, int signal) {
        cScene.effects().indicateRedstone(leverPos);
        cScene.world().modifyBlockEntityNBT(util.select().position(leverPos), AnalogLeverBlockEntity.class,
                tag -> tag.putInt("State", signal), true);
        cScene.world().modifyBlockEntity(leverPos, AnalogLeverBlockEntity.class, be -> {
            while (be.getState() < signal) be.changeState(false);
            while (be.getState() > signal) be.changeState(true);
            try {
                java.lang.reflect.Field f = AnalogLeverBlockEntity.class.getDeclaredField("clientState");
                f.setAccessible(true);
                net.createmod.catnip.animation.LerpedFloat lf = (net.createmod.catnip.animation.LerpedFloat) f.get(be);
                if (lf != null) {
                    lf.setValue(signal);
                }
            } catch (Exception ignored) {}
            be.notifyUpdate();
        });
        cScene.world().modifyBlockEntity(nixiePos, NixieTubeBlockEntity.class, be -> {
            be.updateRedstoneStrength(signal);
            be.updateDisplayedStrings();
        });
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.onRedstonePowerChanged(signal);
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

        helper.forComponents(key(ModRegistry.FOCUSING_LENS.get()))
                .addStoryBoard("lenses/focusing",             DiffuserPonderScenes::focusingLens,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER))
                .addStoryBoard("lenses/dispersing",           DiffuserPonderScenes::dispersingLens,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER));

        helper.forComponents(key(ModRegistry.DISPERSING_LENS.get()))
                .addStoryBoard("lenses/dispersing",           DiffuserPonderScenes::dispersingLens,
                        e -> e.highlightTag(DiffuserPonderTags.DIFFUSER))
                .addStoryBoard("lenses/focusing",             DiffuserPonderScenes::focusingLens,
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
                .addStoryBoard("hologram/rotation",           DiffuserPonderScenes::hologramRotation,
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
        cScene.idle(15);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        cScene.idle(20);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("The Optical Diffuser receives a laser from a Create: Optical source through its back face")
                .pointAt(util.vector().blockSurface(sourcePos, Direction.NORTH))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(85);

        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        cScene.idle(20);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("…and re-emits it as a shaped cone of light in front")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.SOUTH))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(85);

        // Emergency Shut-off via Level 15 Redstone
        cScene.world().showSection(util.select().position(leverPos), Direction.DOWN);
        cScene.idle(20);

        cScene.overlay().showText(80)
                .attachKeyFrame()
                .text("A maximum redstone signal (level 15) immediately shuts off the diffuser as an emergency stop")
                .pointAt(util.vector().topOf(leverPos))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        cScene.idle(25);

        cScene.world().toggleRedstonePower(util.select().position(leverPos));
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> be.onRedstonePowerChanged(15));
        cScene.idle(60);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Without lenses, signals 0–14 do not affect the beam; only signal 15 disables it")
                .pointAt(util.vector().topOf(diffuserPos));
        cScene.idle(25);

        cScene.world().toggleRedstonePower(util.select().position(leverPos));
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> be.onRedstonePowerChanged(0));
        cScene.idle(60);

        // No input laser = no light
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 0f);
        cScene.world().setKineticSpeed(util.select().position(sourcePos), 0f);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, OpticalDiffuserBlockEntity::removeBeam);
        cScene.idle(15);

        cScene.overlay().showText(70)
                .attachKeyFrame()
                .text("Without an incoming beam the diffuser produces no light — it is a receiver, not a source")
                .pointAt(util.vector().topOf(diffuserPos));
        cScene.idle(80);

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
        cScene.idle(20);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Without any glass the beam uses the color of the upstream laser source")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(85);

        cScene.world().setBlock(glassPos1, Blocks.RED_STAINED_GLASS.defaultBlockState(), true);
        cScene.world().showSection(util.select().position(glassPos1), Direction.DOWN);
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 50, 50), 64f);
        cScene.idle(15);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Stained glass placed between the source and the diffuser tints the beam that color")
                .pointAt(util.vector().blockSurface(glassPos1, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        cScene.idle(85);

        BlockPos glassPos2 = new BlockPos(2, 1, 0);
        cScene.world().setBlock(glassPos2, Blocks.BLUE_STAINED_GLASS.defaultBlockState(), true);
        cScene.world().showSection(util.select().position(glassPos2), Direction.DOWN);
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(180, 50, 255), 64f);
        cScene.idle(15);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Glass in front also counts — both sides are scanned up to 12 blocks")
                .pointAt(util.vector().blockSurface(glassPos2, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.BLUE);
        cScene.idle(85);

        cScene.overlay().showText(70)
                .attachKeyFrame()
                .text("Multiple glass colors blend using subtractive optical mixing — just like real filters")
                .pointAt(util.vector().topOf(diffuserPos));
        cScene.idle(80);

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
        cScene.idle(20);

        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setCarouselLens(0, new ItemStack(ModRegistry.FOCUSING_LENS.get()));
            be.setCarouselLens(1, new ItemStack(ModRegistry.DISPERSING_LENS.get()));
            be.setCarouselLens(2, new ItemStack(ModRegistry.THERMAL_IGNITION_LENS.get()));
            be.setCarouselLens(3, new ItemStack(ModRegistry.COLLIMATOR_LENS.get()));
        });
        cScene.idle(15);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("The carousel on the left face holds up to 4 lenses — one per 90° slot")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(85);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Right-click any slot with a lens to install it. Right-click the slot again with an empty hand to retrieve it")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST));
        cScene.idle(85);

        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> be.rotateCarousel(90f));
        cScene.idle(15);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Use a wrench on the left face to step the carousel 90° by hand without a shaft")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.WEST))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(85);

        cScene.world().showSection(util.select().position(leftShaft), Direction.DOWN);
        cScene.world().setKineticSpeed(util.select().position(leftShaft), 16f);
        cScene.world().setKineticSpeed(util.select().position(diffuserPos), 16f);
        cScene.idle(20);

        cScene.overlay().showText(80)
                .attachKeyFrame()
                .text("The left kinetic shaft continuously rotates the carousel — cycling active lenses as it spins")
                .pointAt(util.vector().blockSurface(leftShaft, Direction.WEST))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(90);

        cScene.world().setKineticSpeed(util.select().position(leftShaft), 0f);
        cScene.world().setKineticSpeed(util.select().position(diffuserPos), 0f);
        cScene.idle(20);

        cScene.overlay().showText(70)
                .attachKeyFrame()
                .text("Right-click the front aperture with an empty hand to retrieve the currently active lens")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.NORTH));
        cScene.idle(80);

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
        cScene.idle(20);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("By default the diffuser casts a round conical beam")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(85);

        // Switch to Horizontal Polarization
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f, BeamHelper.BeamPolarization.HORIZONTAL);
        cScene.idle(15);

        cScene.overlay().showText(80)
                .attachKeyFrame()
                .text("Changing beam polarization at the Optical Source flattens the cone into a wide horizontal fan")
                .pointAt(util.vector().topOf(sourcePos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(95);

        // Hole in the floor under diffuser
        cScene.overlay().showText(80)
                .attachKeyFrame()
                .text("Connecting rotational power to the bottom socket spins the beam around its axis")
                .pointAt(util.vector().topOf(bottomCog))
                .colored(net.createmod.ponder.api.PonderPalette.BLUE);
        cScene.idle(25);

        // Place the cogwheel into the hole with placement particles!
        cScene.world().setBlock(bottomCog, com.simibubi.create.AllBlocks.COGWHEEL.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.Y), true);
        cScene.idle(15);
        cScene.overlay().showOutline(net.createmod.ponder.api.PonderPalette.BLUE, "bottom",
                util.select().position(bottomCog), 75);
        cScene.idle(55);

        cScene.world().setKineticSpeed(util.select().position(bottomCog), 32f);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> be.setSpinSpeed(32f));
        cScene.idle(20);

        cScene.overlay().showText(85)
                .attachKeyFrame()
                .text("With horizontal polarization, the rotating fan sweeps around like a lighthouse")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(115);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Rotation speed is proportional to bottom shaft RPM — ideal for searchlights and lighthouses")
                .pointAt(util.vector().topOf(diffuserPos));
        cScene.idle(90);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 2.1 — Focusing Lens: Spotlight & Signal Modulation
    // ─────────────────────────────────────────────────────────────────────────
    public static void focusingLens(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("lenses_focusing", "Focusing Lens — Spotlight");
        cScene.configureBasePlate(0, 0, 7);
        cScene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(3, 1, 2);
        BlockPos sourcePos   = new BlockPos(3, 1, 4);
        BlockPos shaftPos    = new BlockPos(3, 1, 5);
        BlockPos leverPos    = new BlockPos(3, 2, 2);
        BlockPos casingPos   = new BlockPos(4, 1, 2);
        BlockPos nixiePos    = new BlockPos(4, 2, 2);

        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(casingPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(nixiePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(leverPos), Direction.DOWN);

        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        setPonderSignal(cScene, util, leverPos, nixiePos, diffuserPos, 0);
        cScene.idle(20);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("By default, the diffuser casts a wide 35° cone of light")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(85);

        // Install Focusing Lens directly into the active front slot (slot 1)
        cScene.overlay().showControls(util.vector().blockSurface(diffuserPos, Direction.NORTH), Pointing.DOWN, 25)
                .rightClick().withItem(new ItemStack(ModRegistry.FOCUSING_LENS.get()));
        cScene.idle(10);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setCarouselLens(1, new ItemStack(ModRegistry.FOCUSING_LENS.get()));
            be.onRedstonePowerChanged(0);
        });
        cScene.effects().emitParticles(util.vector().blockSurface(diffuserPos, Direction.NORTH),
                cScene.effects().simpleParticleEmitter(ParticleTypes.CRIT, Vec3.ZERO), 1.0f, 10);
        cScene.idle(15);

        cScene.overlay().showText(85)
                .attachKeyFrame()
                .text("The Focusing Lens concentrates the beam into a spotlight, narrowing the cone down to 45°")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.NORTH))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(95);

        cScene.overlay().showText(90)
                .attachKeyFrame()
                .text("Right-click the Analog Lever to increase redstone power, tightening the spotlight down to 8°")
                .pointAt(util.vector().topOf(leverPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(30);

        // Show player right-clicking the lever to step up signal
        cScene.overlay().showControls(util.vector().topOf(leverPos), Pointing.DOWN, 80).rightClick();
        cScene.idle(10);

        // Slowly step signal UP: 0 -> 3 -> 6 -> 9 -> 12 -> 14
        int[] upSignals = {3, 6, 9, 12, 14};
        for (int sig : upSignals) {
            setPonderSignal(cScene, util, leverPos, nixiePos, diffuserPos, sig);
            cScene.idle(16);
        }
        cScene.idle(40);

        // Show player shift-right-clicking the lever to step down signal
        cScene.overlay().showControls(util.vector().topOf(leverPos), Pointing.DOWN, 65).rightClick().whileSneaking();
        cScene.idle(10);

        // Slowly step signal DOWN: 14 -> 10 -> 6 -> 2 -> 0
        int[] downSignals = {10, 6, 2, 0};
        for (int sig : downSignals) {
            setPonderSignal(cScene, util, leverPos, nixiePos, diffuserPos, sig);
            cScene.idle(16);
        }
        cScene.idle(30);

        cScene.overlay().showText(80)
                .attachKeyFrame()
                .text("At signal 15, the diffuser completely turns off (redstone cut-off)")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        setPonderSignal(cScene, util, leverPos, nixiePos, diffuserPos, 15);
        cScene.idle(40);

        // Restore signal to 0
        setPonderSignal(cScene, util, leverPos, nixiePos, diffuserPos, 0);
        cScene.idle(30);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 2.2 — Dispersing Lens: Floodlight Coverage
    // ─────────────────────────────────────────────────────────────────────────
    public static void dispersingLens(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("lenses_dispersing", "Dispersing Lens — Floodlight");
        cScene.configureBasePlate(0, 0, 7);
        cScene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(3, 1, 2);
        BlockPos sourcePos   = new BlockPos(3, 1, 4);
        BlockPos shaftPos    = new BlockPos(3, 1, 5);
        BlockPos leverPos    = new BlockPos(3, 2, 2);
        BlockPos casingPos   = new BlockPos(4, 1, 2);
        BlockPos nixiePos    = new BlockPos(4, 2, 2);

        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(casingPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(nixiePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(leverPos), Direction.DOWN);

        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        setPonderSignal(cScene, util, leverPos, nixiePos, diffuserPos, 0);
        cScene.idle(20);

        // Install Dispersing Lens into active front slot (slot 1)
        cScene.overlay().showControls(util.vector().blockSurface(diffuserPos, Direction.NORTH), Pointing.DOWN, 25)
                .rightClick().withItem(new ItemStack(ModRegistry.DISPERSING_LENS.get()));
        cScene.idle(10);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setCarouselLens(1, new ItemStack(ModRegistry.DISPERSING_LENS.get()));
            be.onRedstonePowerChanged(0);
        });
        cScene.effects().emitParticles(util.vector().blockSurface(diffuserPos, Direction.NORTH),
                cScene.effects().simpleParticleEmitter(ParticleTypes.END_ROD, Vec3.ZERO), 1.0f, 10);
        cScene.idle(20);

        cScene.overlay().showText(85)
                .attachKeyFrame()
                .text("The Dispersing Lens spreads the beam outwards into a wide floodlight, starting at 20°")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.NORTH))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(95);

        cScene.overlay().showText(90)
                .attachKeyFrame()
                .text("Increasing the redstone signal widens the cone up to 70° for maximum room coverage")
                .pointAt(util.vector().topOf(leverPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(30);

        // Show player right-clicking the lever
        cScene.overlay().showControls(util.vector().topOf(leverPos), Pointing.DOWN, 80).rightClick();
        cScene.idle(10);

        // Step signal UP: 0 -> 3 -> 6 -> 9 -> 12 -> 14
        int[] upSignals = {3, 6, 9, 12, 14};
        for (int sig : upSignals) {
            setPonderSignal(cScene, util, leverPos, nixiePos, diffuserPos, sig);
            cScene.idle(16);
        }
        cScene.idle(40);

        // Show player shift-right-clicking the lever
        cScene.overlay().showControls(util.vector().topOf(leverPos), Pointing.DOWN, 65).rightClick().whileSneaking();
        cScene.idle(10);

        // Step signal DOWN: 14 -> 10 -> 6 -> 2 -> 0
        int[] downSignals = {10, 6, 2, 0};
        for (int sig : downSignals) {
            setPonderSignal(cScene, util, leverPos, nixiePos, diffuserPos, sig);
            cScene.idle(16);
        }
        cScene.idle(35);

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
        cScene.idle(20);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("The Film Reel block acts as a media server — it downloads and stores a video or audio file from a URL")
                .pointAt(util.vector().topOf(reelPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(85);

        cScene.overlay().showText(80)
                .attachKeyFrame()
                .text("Right-click to open the GUI. Paste a direct URL and start the upload. The file is saved to the server — no client hosting needed")
                .pointAt(util.vector().blockSurface(reelPos, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(90);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Break the block to retrieve the Film Reel item. It carries the uploaded media and can be inserted into a diffuser")
                .pointAt(util.vector().topOf(reelPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(85);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("A comparator next to the Film Reel block outputs a signal proportional to playback progress")
                .pointAt(util.vector().blockSurface(reelPos, Direction.EAST));
        cScene.idle(85);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 3.2 — Silent Projector Lens
    // ─────────────────────────────────────────────────────────────────────────
    public static void silentProjector(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("silent_projector_lens", "Silent Projector Lens");
        cScene.configureBasePlate(0, 0, 9);
        cScene.showBasePlate();

        BlockPos shaftPos    = new BlockPos(4, 1, 1);
        BlockPos sourcePos   = new BlockPos(4, 1, 2);
        BlockPos diffuserPos = new BlockPos(4, 1, 4);
        BlockPos reelPos     = new BlockPos(3, 1, 4);
        BlockPos reelShaft1  = new BlockPos(2, 1, 4);
        BlockPos reelShaft2  = new BlockPos(1, 1, 4);
        BlockPos screenStart = new BlockPos(2, 1, 8);
        BlockPos screenEnd   = new BlockPos(6, 3, 8);

        cScene.world().showSection(util.select().fromTo(screenStart, screenEnd), Direction.DOWN);
        cScene.idle(15);
        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        cScene.idle(15);

        cScene.overlay().showText(80)
                .attachKeyFrame()
                .text("Hold the Silent Projector Lens and right-click one corner of a screen wall, then Shift+Right-click the opposite corner")
                .pointAt(util.vector().blockSurface(screenStart, Direction.NORTH))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(90);

        ItemStack configuredLens = new ItemStack(ModRegistry.SILENT_PROJECTOR_LENS.get());
        ProjectorLensItem.setPos1(configuredLens, screenStart);
        ProjectorLensItem.setPos2(configuredLens, screenEnd);

        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setCarouselLens(1, configuredLens);
        });
        cScene.idle(15);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Insert the configured lens into the carousel. The lens now knows which screen to project onto")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.EAST))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(85);

        cScene.world().showSection(util.select().position(reelPos), Direction.DOWN);
        cScene.idle(15);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Place a Film Reel block on the right side of the diffuser, or insert a Film Reel item into the internal slot")
                .pointAt(util.vector().blockSurface(reelPos, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(85);

        cScene.world().showSection(util.select().fromTo(reelShaft2, reelShaft1), Direction.DOWN);
        cScene.idle(10);
        cScene.world().setKineticSpeed(util.select().fromTo(reelShaft2, reelPos), 16f);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("With an incoming laser and a spinning Film Reel, vintage animation plays smoothly on the screen")
                .pointAt(util.vector().centerOf(new BlockPos(4, 2, 8)))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(85);

        // Demonstrate acceleration
        cScene.world().setKineticSpeed(util.select().fromTo(reelShaft2, reelPos), 64f);
        cScene.overlay().showText(70)
                .attachKeyFrame()
                .text("Higher RPM speeds up playback. Speed is directly proportional to the Film Reel rotation")
                .pointAt(util.vector().blockSurface(reelShaft1, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(80);

        // Demonstrate pause
        cScene.world().setKineticSpeed(util.select().fromTo(reelShaft2, reelPos), 0f);
        cScene.overlay().showText(60)
                .attachKeyFrame()
                .text("Stop the kinetic shaft to freeze and pause playback")
                .pointAt(util.vector().centerOf(new BlockPos(4, 2, 8)))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        cScene.idle(70);

        // Demonstrate reverse/rewind
        cScene.world().setKineticSpeed(util.select().fromTo(reelShaft2, reelPos), -32f);
        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Reverse rotation direction to rewind the film backwards")
                .pointAt(util.vector().blockSurface(reelPos, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(85);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 3.3 — Sound Projector Lens + Speaker
    // ─────────────────────────────────────────────────────────────────────────
    public static void soundProjector(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("sound_projector_lens", "Sound Projector Lens");
        cScene.configureBasePlate(0, 0, 9);
        cScene.showBasePlate();

        BlockPos shaftPos     = new BlockPos(4, 1, 1);
        BlockPos sourcePos    = new BlockPos(4, 1, 2);
        BlockPos diffuserPos  = new BlockPos(4, 1, 4);
        BlockPos reelPos      = new BlockPos(3, 1, 4);
        BlockPos reelShaft1   = new BlockPos(2, 1, 4);
        BlockPos reelShaft2   = new BlockPos(1, 1, 4);
        BlockPos speakerPos   = new BlockPos(7, 1, 5);
        BlockPos linkPos         = new BlockPos(4, 2, 4);
        BlockPos boardStandStart = new BlockPos(1, 1, 2);
        BlockPos boardStandEnd   = new BlockPos(3, 1, 2);
        BlockPos boardStart      = new BlockPos(1, 2, 2);
        BlockPos boardEnd        = new BlockPos(3, 2, 2);
        BlockPos boardController = new BlockPos(3, 2, 2);
        BlockPos nixieTube1      = new BlockPos(1, 1, 1);
        BlockPos nixieTube2      = new BlockPos(2, 1, 1);
        BlockPos screenStart     = new BlockPos(2, 1, 8);
        BlockPos screenEnd       = new BlockPos(6, 3, 8);

        cScene.world().showSection(util.select().fromTo(screenStart, screenEnd), Direction.DOWN);
        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(reelPos), Direction.DOWN);
        cScene.world().showSection(util.select().fromTo(reelShaft2, reelShaft1), Direction.DOWN);
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        cScene.world().setKineticSpeed(util.select().fromTo(reelShaft2, reelPos), 16f);

        ItemStack configuredLens = new ItemStack(ModRegistry.SOUND_PROJECTOR_LENS.get());
        ProjectorLensItem.setPos1(configuredLens, screenStart);
        ProjectorLensItem.setPos2(configuredLens, screenEnd);

        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setCarouselLens(1, configuredLens);
        });
        cScene.idle(10);

        // Start distant projector audio when playback begins
        cScene.addInstruction(sceneInst -> {
            PonderSoundHelper.startCaramelldansen();
        });
        cScene.idle(10);

        cScene.overlay().showText(65)
                .attachKeyFrame()
                .text("Soundtrack Cinema Lens projects vivid full-colour animations and synchronizes spatial audio")
                .pointAt(util.vector().centerOf(new BlockPos(4, 2, 8)))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(70);

        cScene.world().showSection(util.select().position(speakerPos), Direction.DOWN);
        cScene.idle(10);

        cScene.overlay().showText(65)
                .attachKeyFrame()
                .text("Place a Speaker block anywhere. Right-click the diffuser while holding the Speaker item to link them")
                .pointAt(util.vector().blockSurface(speakerPos, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(70);

        // Instant switch to clear and loud speaker audio
        cScene.addInstruction(sceneInst -> {
            PonderSoundHelper.boostToSpeaker();
        });

        cScene.overlay().showText(65)
                .attachKeyFrame()
                .text("Audio emits directly from the Speaker — with spatial positioning and authentic acoustics")
                .pointAt(util.vector().topOf(speakerPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);

        // Emit musical notes from the speaker
        for (int i = 0; i < 3; i++) {
            cScene.effects().emitParticles(
                    util.vector().topOf(speakerPos).add(0, 0.2, 0),
                    cScene.effects().simpleParticleEmitter(ParticleTypes.NOTE, new Vec3(0, 0.1, 0)),
                    1,
                    1
            );
            cScene.idle(20);
        }

        // Show Display Link, 3-wide Display Board, and Nixie Tubes
        cScene.world().showSection(util.select().fromTo(boardStandStart, boardStandEnd), Direction.DOWN);
        cScene.world().showSection(util.select().fromTo(boardStart, boardEnd), Direction.DOWN);
        cScene.world().showSection(util.select().fromTo(nixieTube1, nixieTube2), Direction.DOWN);
        cScene.world().showSection(util.select().position(linkPos), Direction.DOWN);
        cScene.world().setKineticSpeed(util.select().fromTo(boardStart, boardEnd), 32f);
        cScene.idle(15);

        cScene.world().flashDisplayLink(linkPos);
        updateProjectorDisplay(cScene, boardController, nixieTube1, nixieTube2, "00:48");

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Attach a Display Link directly to the Optical Diffuser to transmit media info to Display Boards and Nixie Tubes")
                .pointAt(util.vector().blockSurface(linkPos, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);

        // Dynamic time increments every second (20 ticks) matching the audio track
        int currentSec = 48;
        for (int i = 0; i < 8; i++) {
            cScene.idle(20);
            currentSec++;
            String secStr = String.format("%02d", currentSec);
            updateProjectorDisplay(cScene, boardController, nixieTube1, nixieTube2, "00:" + secStr);
        }

        // Smooth fade-out precisely ending with the scene
        cScene.addInstruction(sceneInst -> {
            PonderSoundHelper.fadeOutAndStop();
        });
        cScene.idle(25);

        cScene.markAsFinished();
    }

    private static void updateProjectorDisplay(CreateSceneBuilder cScene, BlockPos boardController, BlockPos nixie1, BlockPos nixie2, String timeStr) {
        cScene.world().setDisplayBoardText(boardController, 0, net.minecraft.network.chat.Component.literal("  CARAMELLDANSEN  "));
        cScene.world().setDisplayBoardText(boardController, 1, net.minecraft.network.chat.Component.literal("  " + timeStr + "  [PLAY]  "));
        String min = timeStr.substring(0, 2);
        String sec = timeStr.substring(3, 5);
        String fullDigits = min + sec;
        String json = "{\"text\":\"" + fullDigits + "\"}";
        cScene.world().modifyBlockEntity(nixie1, com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity.class, be -> {
            be.displayCustomText(json, 0);
            be.updateDisplayedStrings();
        });
        cScene.world().modifyBlockEntity(nixie2, com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity.class, be -> {
            be.displayCustomText(json, 1);
            be.updateDisplayedStrings();
        });
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
        cScene.idle(20);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("The linked Speaker emits audio positionally — players closer to the speaker hear it louder")
                .pointAt(util.vector().topOf(speakerPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(85);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Shift+Right-click the speaker with an empty hand to unlink it from its diffuser")
                .pointAt(util.vector().blockSurface(speakerPos, Direction.UP))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(85);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Breaking the speaker preserves the link in the dropped item — place it again to reconnect instantly")
                .pointAt(util.vector().topOf(speakerPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(85);

        cScene.overlay().showText(70)
                .attachKeyFrame()
                .text("To reset the link permanently: craft the Speaker item with an iron ingot — it becomes a fresh unlinked speaker")
                .pointAt(util.vector().topOf(speakerPos));
        cScene.idle(80);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 4.1 — Hologram Basics
    // ─────────────────────────────────────────────────────────────────────────
    public static void hologramBasics(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("hologram_lens_basics", "Hologram Lens");
        cScene.configureBasePlate(0, 0, 7);
        cScene.showBasePlate();

        BlockPos diffuserPos  = new BlockPos(3, 2, 3);
        BlockPos mirrorPos    = new BlockPos(3, 1, 3);
        BlockPos sourcePos    = new BlockPos(3, 1, 5);
        BlockPos shaftPos     = new BlockPos(3, 1, 6);

        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(mirrorPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);

        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);

        // Diffuser starts with Hologram Lens installed, default gyro (stationary)
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            ItemStack lens = new ItemStack(ModRegistry.HOLOGRAM_LENS.get());
            HologramLensItem.setFocalDistance(lens, 2);
            HologramLensItem.setHoloScale(lens, 1.0f);
            be.setCarouselLens(1, lens);
            be.setSpinSpeed(0f);
        });
        cScene.idle(25);

        // Text 1: Default navigational gyro
        cScene.overlay().showText(85)
                .attachKeyFrame()
                .text("Without an imprinted target the Hologram Lens projects a 3D navigational gyroscope")
                .pointAt(util.vector().topOf(diffuserPos).add(0, 2.0, 0))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(95);

        // Text 2: Imprint target (Grass Block)
        cScene.overlay().showText(85)
                .attachKeyFrame()
                .text("To set a target: hold any item in your offhand and right-click the lens in your hotbar. The hologram switches to that object")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(20);

        // Show player right-clicking with Grass Block
        cScene.overlay().showControls(util.vector().topOf(diffuserPos), Pointing.DOWN, 35)
                .rightClick().withItem(new ItemStack(Items.GRASS_BLOCK));
        cScene.idle(15);

        // Transform into 3D Grass Block!
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            ItemStack lens = be.getActiveLens();
            HologramLensItem.setHoloTarget(lens, new ItemStack(Items.GRASS_BLOCK), be.getLevel().registryAccess());
            HologramLensItem.setFocalDistance(lens, 2);
            HologramLensItem.setHoloScale(lens, 1.0f);
            be.setCarouselLens(1, lens);
        });
        cScene.effects().emitParticles(util.vector().topOf(diffuserPos).add(0, 2.5, 0),
                cScene.effects().simpleParticleEmitter(ParticleTypes.END_ROD, Vec3.ZERO), 1.0f, 15);
        cScene.idle(80);

        // Text 3: Cycle projection distance (2 -> 4 -> 6 -> 2)
        cScene.overlay().showText(90)
                .attachKeyFrame()
                .text("With nothing in offhand, right-click the lens to cycle the projection distance: 2 → 4 → 6 → … → 32 blocks")
                .pointAt(util.vector().topOf(diffuserPos).add(0, 2.5, 0))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(25);

        // Player right clicks lens: distance -> 4
        cScene.overlay().showControls(util.vector().topOf(diffuserPos), Pointing.DOWN, 25).rightClick();
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            ItemStack lens = be.getActiveLens();
            HologramLensItem.setFocalDistance(lens, 4);
            be.setCarouselLens(1, lens);
        });
        cScene.idle(35);

        // Player right clicks lens: distance -> 6
        cScene.overlay().showControls(util.vector().topOf(diffuserPos), Pointing.DOWN, 25).rightClick();
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            ItemStack lens = be.getActiveLens();
            HologramLensItem.setFocalDistance(lens, 6);
            be.setCarouselLens(1, lens);
        });
        cScene.idle(40);

        // Return distance -> 2
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            ItemStack lens = be.getActiveLens();
            HologramLensItem.setFocalDistance(lens, 2);
            be.setCarouselLens(1, lens);
        });
        cScene.idle(25);

        // Text 4: Cycle scale (0.5x -> 1.0x -> 1.5x -> 2.0x -> 1.0x)
        cScene.overlay().showText(90)
                .attachKeyFrame()
                .text("Right-click while the lens is in your offhand to cycle scale: 0.5× → 1× → 1.5× → 2× → 3×")
                .pointAt(util.vector().topOf(diffuserPos).add(0, 2.5, 0));
        cScene.idle(25);

        // Scale -> 0.5x
        cScene.overlay().showControls(util.vector().topOf(diffuserPos), Pointing.DOWN, 20).rightClick().whileSneaking();
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            ItemStack lens = be.getActiveLens();
            HologramLensItem.setHoloScale(lens, 0.5f);
            be.setCarouselLens(1, lens);
        });
        cScene.idle(25);

        // Scale -> 1.5x
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            ItemStack lens = be.getActiveLens();
            HologramLensItem.setHoloScale(lens, 1.5f);
            be.setCarouselLens(1, lens);
        });
        cScene.idle(25);

        // Scale -> 2.0x
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            ItemStack lens = be.getActiveLens();
            HologramLensItem.setHoloScale(lens, 2.0f);
            be.setCarouselLens(1, lens);
        });
        cScene.idle(35);

        // Reset scale -> 1.0x
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            ItemStack lens = be.getActiveLens();
            HologramLensItem.setHoloScale(lens, 1.0f);
            be.setCarouselLens(1, lens);
        });
        cScene.idle(20);

        // Text 5: Clear target — returns to gyroscope
        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Hold an item in offhand and right-click the lens again to clear the target — the gyroscope returns")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        cScene.idle(20);

        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            ItemStack lens = be.getActiveLens();
            HologramLensItem.setHoloTarget(lens, ItemStack.EMPTY, be.getLevel().registryAccess());
            be.setCarouselLens(1, lens);
        });
        cScene.effects().emitParticles(util.vector().topOf(diffuserPos).add(0, 2.5, 0),
                cScene.effects().simpleParticleEmitter(ParticleTypes.POOF, Vec3.ZERO), 1.0f, 10);
        cScene.idle(75);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 4.2 — Hologram Rotation (Bottom Shaft)
    // ─────────────────────────────────────────────────────────────────────────
    public static void hologramRotation(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("hologram_lens_rotation", "Hologram — Kinetic Rotation");
        cScene.configureBasePlate(0, 0, 7);
        cScene.showBasePlate();

        BlockPos diffuserPos  = new BlockPos(3, 2, 3);
        BlockPos mirrorPos    = new BlockPos(3, 1, 3);
        BlockPos spinShaftPos = new BlockPos(3, 2, 4);
        BlockPos sourcePos    = new BlockPos(3, 1, 5);
        BlockPos shaftPos     = new BlockPos(3, 1, 6);

        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(mirrorPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);

        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);

        // Starts with Grass Block, stationary (0 RPM)
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            ItemStack lens = new ItemStack(ModRegistry.HOLOGRAM_LENS.get());
            HologramLensItem.setHoloTarget(lens, new ItemStack(Items.GRASS_BLOCK), be.getLevel().registryAccess());
            HologramLensItem.setFocalDistance(lens, 2);
            HologramLensItem.setHoloScale(lens, 1.0f);
            be.setCarouselLens(1, lens);
            be.setSpinSpeed(0f);
        });
        cScene.idle(25);

        // Text 1: Stationary by default
        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("By default, the projected hologram remains stationary")
                .pointAt(util.vector().topOf(diffuserPos).add(0, 2.5, 0))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(85);

        // Text 2: Connecting kinetic rotational power
        cScene.overlay().showText(85)
                .attachKeyFrame()
                .text("Connecting kinetic rotational power to the diffuser's bottom socket spins the hologram")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.SOUTH))
                .colored(net.createmod.ponder.api.PonderPalette.BLUE);
        cScene.idle(25);

        // Place shaft into bottom socket
        cScene.world().showSection(util.select().position(spinShaftPos), Direction.DOWN);
        cScene.idle(15);
        cScene.world().setKineticSpeed(util.select().position(spinShaftPos), 16f);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> be.setSpinSpeed(16f));
        cScene.idle(75);

        // Text 3: Faster RPM = faster spin
        cScene.overlay().showText(80)
                .attachKeyFrame()
                .text("Rotation speed is directly proportional to shaft RPM — faster power drives faster spin")
                .pointAt(util.vector().topOf(spinShaftPos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(20);

        // Speed up to 64 RPM
        cScene.world().setKineticSpeed(util.select().position(spinShaftPos), 64f);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> be.setSpinSpeed(64f));
        cScene.idle(85);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 4.3 — Hologram with Schematic
    // ─────────────────────────────────────────────────────────────────────────
    public static void hologramSchematic(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("hologram_lens_schematic", "Hologram — Schematic Preview");
        cScene.configureBasePlate(0, 0, 7);
        cScene.showBasePlate();

        BlockPos diffuserPos  = new BlockPos(3, 2, 3);
        BlockPos mirrorPos    = new BlockPos(3, 1, 3);
        BlockPos spinShaftPos = new BlockPos(3, 2, 4);
        BlockPos sourcePos    = new BlockPos(3, 1, 5);
        BlockPos shaftPos     = new BlockPos(3, 1, 6);

        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(mirrorPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(spinShaftPos), Direction.DOWN);

        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        cScene.world().setKineticSpeed(util.select().position(spinShaftPos), 16f);

        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            ItemStack lens = new ItemStack(ModRegistry.HOLOGRAM_LENS.get());
            HologramLensItem.setHoloTarget(lens, com.simibubi.create.AllItems.SCHEMATIC.asStack(), be.getLevel().registryAccess());
            HologramLensItem.setFocalDistance(lens, 2);
            HologramLensItem.setHoloScale(lens, 1.0f);
            be.setCarouselLens(1, lens);
            be.setSpinSpeed(16f);
        });
        cScene.idle(20);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Imprint a Create Schematic onto the Hologram Lens to project a rotating 3D preview of the entire structure")
                .pointAt(util.vector().topOf(diffuserPos).add(0, 2.5, 0))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(85);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("The structure rotates continuously — driven by the bottom shaft. Useful for planning builds mid-construction")
                .pointAt(util.vector().topOf(diffuserPos).add(0, 2.5, 0))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(85);

        cScene.overlay().showText(70)
                .attachKeyFrame()
                .text("Bottom shaft speed directly controls hologram rotation speed — connect a faster source for faster spin")
                .pointAt(util.vector().topOf(spinShaftPos));

        // Speed up rotation: 16 -> 64 RPM!
        cScene.world().setKineticSpeed(util.select().position(spinShaftPos), 64f);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> be.setSpinSpeed(64f));
        cScene.idle(80);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 5.1 — Thermal Ignition Lens
    // ─────────────────────────────────────────────────────────────────────────
    public static void thermalLens(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("thermal_ignition_lens", "Thermal Ignition Lens — Heat Ray");
        cScene.configureBasePlate(0, 0, 7);
        cScene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(3, 1, 3);
        BlockPos sourcePos   = new BlockPos(3, 1, 5);
        BlockPos shaftPos    = new BlockPos(3, 1, 6);
        BlockPos icePos      = new BlockPos(3, 1, 1);
        BlockPos depotPos    = new BlockPos(4, 1, 0);
        Vec3 zombiePos       = util.vector().topOf(util.grid().at(2, 0, 0));

        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(icePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(depotPos), Direction.DOWN);

        // Put raw beef on depot
        cScene.world().modifyBlockEntity(depotPos, DepotBlockEntity.class, be -> be.setHeldItem(new ItemStack(Items.BEEF)));
        cScene.idle(15);

        // Install Thermal Ignition Lens into active front slot (slot 1) with item click overlay
        cScene.overlay().showControls(util.vector().blockSurface(diffuserPos, Direction.NORTH), Pointing.DOWN, 25)
                .rightClick().withItem(new ItemStack(ModRegistry.THERMAL_IGNITION_LENS.get()));
        cScene.idle(10);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setCarouselLens(1, new ItemStack(ModRegistry.THERMAL_IGNITION_LENS.get()));
        });
        cScene.effects().emitParticles(util.vector().blockSurface(diffuserPos, Direction.NORTH),
                cScene.effects().simpleParticleEmitter(ParticleTypes.FLAME, Vec3.ZERO), 1.0f, 10);
        cScene.idle(15);

        // Turn on thermal heat ray beam (standard white laser beam color)
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 64f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 64f);
        cScene.idle(20);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("The Thermal Ignition Lens converts the beam into an Archimedes heat ray (28° cone)")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        cScene.idle(85);

        // Ice melts into water
        cScene.effects().emitParticles(util.vector().topOf(icePos),
                cScene.effects().simpleParticleEmitter(ParticleTypes.SPLASH, Vec3.ZERO), 1.0f, 15);
        cScene.world().setBlock(icePos, Blocks.WATER.defaultBlockState(), true);

        // Raw beef on depot cooks into cooked steak
        cScene.effects().emitParticles(util.vector().topOf(depotPos),
                cScene.effects().particleEmitterWithinBlockSpace(ParticleTypes.SMOKE, new Vec3(0, 0.05, 0)), 1.0f, 8);
        cScene.world().modifyBlockEntity(depotPos, DepotBlockEntity.class, be -> be.setHeldItem(new ItemStack(Items.COOKED_BEEF)));
        cScene.idle(15);

        cScene.overlay().showText(80)
                .attachKeyFrame()
                .text("Ice and snow melt immediately, and foods on Depots or Belts are roasted")
                .pointAt(util.vector().topOf(icePos))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(90);

        // Spawn a zombie in the heat cone
        ElementLink<EntityElement> zombie = cScene.world().createEntity(level -> {
            Zombie z = new Zombie(EntityType.ZOMBIE, level) {
                @Override
                public boolean isAlive() {
                    return !this.isRemoved();
                }

                @Override
                public void tick() {
                    super.tick();
                    this.setNoGravity(true);
                    this.setDeltaMovement(Vec3.ZERO);
                    this.setPos(zombiePos.x, zombiePos.y, zombiePos.z);
                    this.walkAnimation.setSpeed(0.0f);
                    this.walkAnimation.position(0.0f);
                    this.yBodyRot = 180f;
                    this.yBodyRotO = 180f;
                    this.yHeadRot = 180f;
                    this.yHeadRotO = 180f;
                    if (this.deathTime > 0 && this.deathTime < 20) {
                        this.deathTime++;
                    }
                }
            };
            z.setPos(zombiePos.x, zombiePos.y, zombiePos.z);
            z.xo = zombiePos.x;
            z.yo = zombiePos.y;
            z.zo = zombiePos.z;
            z.setYRot(180f);
            z.yRotO = 180f;
            z.yBodyRot = 180f;
            z.yBodyRotO = 180f;
            z.yHeadRot = 180f;
            z.yHeadRotO = 180f;
            z.setNoAi(true);
            z.setNoGravity(true);
            z.setDeltaMovement(Vec3.ZERO);
            z.setOnGround(true);
            z.walkAnimation.setSpeed(0.0f);
            z.setHealth(6.0f);
            return z;
        });
        cScene.idle(15);

        cScene.overlay().showText(85)
                .attachKeyFrame()
                .text("Entities inside the heat cone ignite and suffer continuous burn damage")
                .pointAt(zombiePos.add(0, 1.0, 0))
                .colored(net.createmod.ponder.api.PonderPalette.RED);

        // Continuous realistic fire: on fire flag + damage flinches
        cScene.world().modifyEntity(zombie, e -> {
            if (e instanceof LivingEntity le) {
                le.setRemainingFireTicks(300);
                le.setSharedFlagOnFire(true);
                le.handleDamageEvent(le.damageSources().inFire());
            }
        });
        cScene.effects().emitParticles(zombiePos.add(0, 0.5, 0),
                cScene.effects().particleEmitterWithinBlockSpace(ParticleTypes.SMOKE, new Vec3(0, 0.05, 0)), 1.0f, 6);
        cScene.idle(25);

        cScene.world().modifyEntity(zombie, e -> {
            if (e instanceof LivingEntity le) {
                le.handleDamageEvent(le.damageSources().inFire());
            }
        });
        cScene.effects().emitParticles(zombiePos.add(0, 0.5, 0),
                cScene.effects().particleEmitterWithinBlockSpace(ParticleTypes.SMOKE, new Vec3(0, 0.05, 0)), 1.0f, 6);
        cScene.idle(25);

        // Fatal hit: smooth vanilla 20-tick death tilt and poof
        cScene.world().modifyEntity(zombie, e -> {
            if (e instanceof LivingEntity le) {
                le.handleDamageEvent(le.damageSources().inFire());
                le.handleEntityEvent((byte) 3);
                le.deathTime = 1;
            }
        });
        cScene.idle(20);
        cScene.effects().emitParticles(zombiePos.add(0, 0.2, 0),
                cScene.effects().particleEmitterWithinBlockSpace(ParticleTypes.POOF, new Vec3(0, 0.05, 0)), 1.5f, 15);
        cScene.world().modifyEntity(zombie, Entity::discard);
        cScene.idle(25);

        cScene.markAsFinished();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scene 5.2 — Collimator Lens
    // ─────────────────────────────────────────────────────────────────────────
    public static void collimatorLens(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder cScene = new CreateSceneBuilder(scene);
        cScene.title("collimator_lens", "Collimator Lens — Needle Laser");
        cScene.configureBasePlate(0, 0, 7);
        cScene.showBasePlate();

        BlockPos diffuserPos = new BlockPos(3, 1, 3);
        BlockPos sourcePos   = new BlockPos(3, 1, 5);
        BlockPos shaftPos    = new BlockPos(3, 1, 6);
        Vec3 wardenPos       = util.vector().topOf(util.grid().at(3, 0, 1));

        cScene.world().showSection(util.select().position(shaftPos), Direction.DOWN);
        cScene.world().showSection(util.select().position(sourcePos), Direction.DOWN);
        cScene.world().showSection(util.select().position(diffuserPos), Direction.DOWN);
        cScene.idle(15);

        // Install Collimator Lens into active front slot (slot 1) with item click overlay
        cScene.overlay().showControls(util.vector().blockSurface(diffuserPos, Direction.NORTH), Pointing.DOWN, 25)
                .rightClick().withItem(new ItemStack(ModRegistry.COLLIMATOR_LENS.get()));
        cScene.idle(10);
        cScene.world().modifyBlockEntity(diffuserPos, OpticalDiffuserBlockEntity.class, be -> {
            be.setCarouselLens(1, new ItemStack(ModRegistry.COLLIMATOR_LENS.get()));
        });
        cScene.effects().emitParticles(util.vector().blockSurface(diffuserPos, Direction.NORTH),
                cScene.effects().simpleParticleEmitter(ParticleTypes.CRIT, Vec3.ZERO), 1.0f, 10);
        cScene.idle(15);

        // Turn on Needle Beam at 256 RPM (max speed)
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 256f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 256f);
        cScene.idle(20);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("The Collimator Lens focuses light into an ultra-sharp 2.0° needle laser with extreme range")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(85);

        // Spawn Warden directly in the laser path at (2, 0, 1)
        ElementLink<EntityElement> warden = cScene.world().createEntity(level -> {
            Warden w = new Warden(EntityType.WARDEN, level) {
                @Override
                public boolean isAlive() {
                    return !this.isRemoved();
                }

                @Override
                public void tick() {
                    super.tick();
                    this.setNoGravity(true);
                    this.setDeltaMovement(Vec3.ZERO);
                    this.setPos(wardenPos.x, wardenPos.y, wardenPos.z);
                    this.walkAnimation.setSpeed(0.0f);
                    this.walkAnimation.position(0.0f);
                    this.yBodyRot = 180f;
                    this.yBodyRotO = 180f;
                    this.yHeadRot = 180f;
                    this.yHeadRotO = 180f;
                    if (this.deathTime > 0 && this.deathTime < 20) {
                        this.deathTime++;
                    }
                }
            };
            w.setPos(wardenPos.x, wardenPos.y, wardenPos.z);
            w.xo = wardenPos.x;
            w.yo = wardenPos.y;
            w.zo = wardenPos.z;
            w.setYRot(180f);
            w.yRotO = 180f;
            w.yBodyRot = 180f;
            w.yBodyRotO = 180f;
            w.yHeadRot = 180f;
            w.yHeadRotO = 180f;
            w.setNoAi(true);
            w.setNoGravity(true);
            w.setDeltaMovement(Vec3.ZERO);
            w.setOnGround(true);
            w.walkAnimation.setSpeed(0.0f);
            w.setHealth(500.0f);
            return w;
        });
        cScene.idle(20);

        cScene.overlay().showText(100)
                .attachKeyFrame()
                .text("At 256 RPM, it deals 16.5 damage every 4 ticks ignoring immunity frames — melting a Warden (500 HP) in 6 seconds!")
                .pointAt(wardenPos.add(0, 2.0, 0))
                .colored(net.createmod.ponder.api.PonderPalette.RED);
        cScene.idle(20);

        // Loop of damage pulses exactly matching game logic:
        // Every 4 ticks, Warden takes 16.5 damage (total 30 hits = 120 ticks = 6.0 seconds)
        for (int hit = 1; hit <= 29; hit++) {
            cScene.world().modifyEntity(warden, e -> {
                if (e instanceof LivingEntity le) {
                    le.invulnerableTime = 0;
                    le.handleDamageEvent(le.damageSources().magic());
                    le.setHealth(Math.max(1.0f, le.getHealth() - 16.5f));
                }
            });
            cScene.effects().emitParticles(wardenPos.add(0, 1.5, 0),
                    cScene.effects().simpleParticleEmitter(ParticleTypes.CRIT, new Vec3(0.15, 0.15, 0.15)), 1.0f, 4);
            cScene.idle(4);
        }

        // Fatal 30th hit: triggers death animation
        cScene.world().modifyEntity(warden, e -> {
            if (e instanceof LivingEntity le) {
                le.invulnerableTime = 0;
                le.handleDamageEvent(le.damageSources().magic());
                le.handleEntityEvent((byte) 3);
                le.deathTime = 1;
                le.setHealth(0.0f);
            }
        });
        cScene.idle(20); // Exact vanilla death animation duration

        // Poof + sculk soul burst on death
        cScene.effects().emitParticles(wardenPos.add(0, 1.0, 0),
                cScene.effects().particleEmitterWithinBlockSpace(ParticleTypes.POOF, new Vec3(0, 0.1, 0)), 2.0f, 25);
        cScene.effects().emitParticles(wardenPos.add(0, 1.0, 0),
                cScene.effects().simpleParticleEmitter(ParticleTypes.SCULK_SOUL, new Vec3(0, 0.15, 0)), 1.5f, 20);
        cScene.world().modifyEntity(warden, Entity::discard);
        cScene.idle(30);

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
        cScene.idle(15);

        // Put raw beef on depot and drop raw beef on floor (zero gravity, resting flat)
        cScene.world().modifyBlockEntity(depotPos, DepotBlockEntity.class, be -> be.setHeldItem(new ItemStack(Items.BEEF)));
        ElementLink<EntityElement> floorMeat = cScene.world().createItemEntity(floorMeatPos, Vec3.ZERO, new ItemStack(Items.BEEF));
        cScene.world().modifyEntity(floorMeat, e -> {
            if (e instanceof ItemEntity ie) {
                ie.setNoGravity(true);
                ie.setDeltaMovement(Vec3.ZERO);
            }
        });
        cScene.idle(15);

        // Phase 1: Low RPM (Microwave: 8–32 RPM, white beam)
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 16f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 16f);
        cScene.idle(20);

        cScene.overlay().showText(85)
                .attachKeyFrame()
                .text("At low source speeds (8–32 RPM), the diffuser emits microwaves which cook food, melt snow and ice, and ignite mobs")
                .pointAt(util.vector().topOf(diffuserPos))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(25);

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
        cScene.idle(20);

        // Spawn zombie that catches fire from microwave radiation (stationary legs, no AI, custom death animation)
        ElementLink<EntityElement> zombie1 = cScene.world().createEntity(level -> {
            Zombie z = new Zombie(EntityType.ZOMBIE, level) {
                @Override
                public boolean isAlive() {
                    return !this.isRemoved();
                }

                @Override
                public void tick() {
                    super.tick();
                    this.setNoGravity(true);
                    this.setDeltaMovement(Vec3.ZERO);
                    this.setPos(zombiePos.x, zombiePos.y, zombiePos.z);
                    this.walkAnimation.setSpeed(0.0f);
                    this.walkAnimation.position(0.0f);
                    this.yBodyRot = 180f;
                    this.yBodyRotO = 180f;
                    this.yHeadRot = 180f;
                    this.yHeadRotO = 180f;
                    if (this.deathTime > 0 && this.deathTime < 20) {
                        this.deathTime++;
                    }
                }
            };
            z.setPos(zombiePos.x, zombiePos.y, zombiePos.z);
            z.xo = zombiePos.x;
            z.yo = zombiePos.y;
            z.zo = zombiePos.z;
            z.setYRot(180f);
            z.yRotO = 180f;
            z.yBodyRot = 180f;
            z.yBodyRotO = 180f;
            z.yHeadRot = 180f;
            z.yHeadRotO = 180f;
            z.setNoAi(true);
            z.setNoGravity(true);
            z.setDeltaMovement(Vec3.ZERO);
            z.setOnGround(true);
            z.walkAnimation.setSpeed(0.0f);
            z.setHealth(6.0f);
            return z;
        });
        cScene.idle(15);

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
        cScene.idle(25);

        cScene.world().modifyEntity(zombie1, e -> {
            if (e instanceof LivingEntity le) {
                le.handleDamageEvent(le.damageSources().inFire());
            }
        });
        cScene.effects().emitParticles(zombiePos.add(0, 0.5, 0),
                cScene.effects().particleEmitterWithinBlockSpace(ParticleTypes.SMOKE, new Vec3(0, 0.05, 0)), 1.0f, 6);
        cScene.idle(25);

        // Pulse 3: Fatal fire damage and smooth death fall
        cScene.world().modifyEntity(zombie1, e -> {
            if (e instanceof LivingEntity le) {
                le.handleDamageEvent(le.damageSources().inFire());
                le.handleEntityEvent((byte) 3);
                le.deathTime = 1;
            }
        });
        cScene.idle(20);
        cScene.effects().emitParticles(zombiePos.add(0, 0.2, 0),
                cScene.effects().particleEmitterWithinBlockSpace(ParticleTypes.POOF, new Vec3(0, 0.05, 0)), 1.5f, 15);
        cScene.world().modifyEntity(zombie1, Entity::discard);
        cScene.idle(15);

        // Phase 2: High RPM (Gamma Radiation: >128 RPM, white beam)
        lightDiffuser(cScene, util, sourcePos, diffuserPos, new Vec3i(255, 255, 255), 256f);
        cScene.world().setKineticSpeed(util.select().position(shaftPos), 256f);
        cScene.idle(20);

        cScene.overlay().showText(100)
                .attachKeyFrame()
                .text("At high speeds (>128 RPM), it emits lethal Gamma radiation inflicting Wither, Weakness, and heavy radiation damage")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.NORTH))
                .colored(net.createmod.ponder.api.PonderPalette.RED);

        // Spawn dying zombie in lethal gamma radiation with overridden isAlive() & tick() so death animation plays
        ElementLink<EntityElement> zombie2 = cScene.world().createEntity(level -> {
            Zombie z = new Zombie(EntityType.ZOMBIE, level) {
                @Override
                public boolean isAlive() {
                    return !this.isRemoved();
                }

                @Override
                public void tick() {
                    super.tick();
                    this.setNoGravity(true);
                    this.setDeltaMovement(Vec3.ZERO);
                    this.setPos(zombiePos.x, zombiePos.y, zombiePos.z);
                    this.walkAnimation.setSpeed(0.0f);
                    this.walkAnimation.position(0.0f);
                    this.yBodyRot = 180f;
                    this.yBodyRotO = 180f;
                    this.yHeadRot = 180f;
                    this.yHeadRotO = 180f;
                    if (this.deathTime > 0 && this.deathTime < 20) {
                        this.deathTime++;
                    }
                }
            };
            z.setPos(zombiePos.x, zombiePos.y, zombiePos.z);
            z.xo = zombiePos.x;
            z.yo = zombiePos.y;
            z.zo = zombiePos.z;
            z.setYRot(180f);
            z.yRotO = 180f;
            z.yBodyRot = 180f;
            z.yBodyRotO = 180f;
            z.yHeadRot = 180f;
            z.yHeadRotO = 180f;
            z.setNoAi(true);
            z.setNoGravity(true);
            z.setDeltaMovement(Vec3.ZERO);
            z.setOnGround(true);
            z.walkAnimation.setSpeed(0.0f);
            z.setHealth(6.0f);
            return z;
        });
        cScene.idle(15);

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
        cScene.idle(25);

        // Pulse 2: Second radiation tick
        cScene.world().modifyEntity(zombie2, e -> {
            if (e instanceof LivingEntity le) {
                le.handleDamageEvent(le.damageSources().magic());
            }
        });
        cScene.effects().emitParticles(zombiePos.add(0, 0.6, 0),
                cScene.effects().particleEmitterWithinBlockSpace(ParticleTypes.WITCH, new Vec3(0, 0.05, 0)), 1.5f, 10);
        cScene.idle(25);

        // Pulse 3: Fatal damage and smooth death fall
        cScene.world().modifyEntity(zombie2, e -> {
            if (e instanceof LivingEntity le) {
                le.handleDamageEvent(le.damageSources().magic());
                le.handleEntityEvent((byte) 3);
                le.deathTime = 1;
            }
        });
        cScene.idle(20);
        cScene.effects().emitParticles(zombiePos.add(0, 0.2, 0),
                cScene.effects().particleEmitterWithinBlockSpace(ParticleTypes.POOF, new Vec3(0, 0.05, 0)), 1.5f, 15);
        cScene.world().modifyEntity(zombie2, Entity::discard);
        cScene.idle(15);

        // Phase 3: Lead Glass Shielding
        cScene.world().showSection(util.select().position(glassPos), Direction.DOWN);
        cScene.idle(20);

        cScene.overlay().showOutline(net.createmod.ponder.api.PonderPalette.GREEN, "glass",
                util.select().position(glassPos), 70);
        cScene.overlay().showText(80)
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
                z.yBodyRot = 180f;
                z.yBodyRotO = 180f;
                z.setNoAi(true);
                z.setNoGravity(true);
                z.setDeltaMovement(Vec3.ZERO);
                z.setOnGround(true);
                z.walkAnimation.setSpeed(0.0f);
            }
            return z;
        });
        cScene.idle(95);

        cScene.overlay().showText(70)
                .attachKeyFrame()
                .text("Visible light continues to pass through, allowing safe illumination without radiation hazards")
                .pointAt(util.vector().blockSurface(diffuserPos, Direction.NORTH));
        cScene.idle(80);

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
        cScene.idle(20);
        cScene.world().showSection(window, Direction.DOWN);
        cScene.idle(25);

        cScene.overlay().showText(80)
                .attachKeyFrame()
                .text("Lead Glass features seamless connected textures, joining into large panoramic windowpanes without internal seams")
                .pointAt(util.vector().blockSurface(new BlockPos(2, 2, 2), Direction.SOUTH))
                .colored(net.createmod.ponder.api.PonderPalette.WHITE);
        cScene.idle(90);

        cScene.overlay().showText(75)
                .attachKeyFrame()
                .text("Ideal for airship observation bridges, radiation containment chambers, and laboratory viewing ports")
                .pointAt(util.vector().blockSurface(new BlockPos(2, 1, 2), Direction.SOUTH))
                .colored(net.createmod.ponder.api.PonderPalette.GREEN);
        cScene.idle(85);

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
