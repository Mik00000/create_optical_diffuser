package com.mik.optical_diffuser.block.entity;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.block.OpticalDiffuserBlock;
import com.mik.optical_diffuser.compat.SableCompat;
import com.mik.optical_diffuser.config.DiffuserServerConfig;
import com.mik.optical_diffuser.menu.OpticalDiffuserMenu;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.lpcamors.optical.COUtils;
import net.lpcamors.optical.blocks.optical_source.BeamHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.lpcamors.optical.CODamageSources;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.ModList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class OpticalDiffuserBlockEntity extends KineticBlockEntity {

    public static final Set<OpticalDiffuserBlockEntity> CLIENT_INSTANCES = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public static final Set<OpticalDiffuserBlockEntity> ACTIVE_SERVER_DIFFUSERS = ConcurrentHashMap.newKeySet();

    public enum DiffuserModule {
        NONE,
        FOCUSER,
        CONDENSER,
        PROJECTOR_SILENT,
        PROJECTOR_SOUND,
        HOLOGRAM,
        THERMAL_IGNITER,
        COLLIMATOR
    }

    private final ItemStack[] carouselLenses = new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private float carouselAngle = 0.0f;
    private ItemStack installedFilmReel = ItemStack.EMPTY;
    private int redstonePower = 0;

    private boolean hasBeam = false;
    private boolean isPowered = false;
    private int red = 255;
    private int green = 255;
    private int blue = 255;
    private float intensity = 1.0f;

    // Polarization & Type from incoming Optical Source
    private BeamHelper.BeamPolarization polarization = BeamHelper.BeamPolarization.RANDOM;
    private BeamHelper.BeamType beamType = BeamHelper.BeamType.VISIBLE;

    // Configuration
    private float coneAngle = 35.0f;               // 10.0f to 75.0f
    private int coneRange = 48;                    // Dynamically scaled by RPM/Type (16 to 96)
    private float spinSpeed = 0.0f;                // 0.0f to 30.0f RPM
    private int redstoneMode = 0;

    // Illumination tracking (broad area coverage + zero-flicker diff)
    private final Set<BlockPos> currentLightPositions = new HashSet<>();
    private Level currentIlluminatedLevel = null;

    // Cached laser beam properties
    private int cachedLaserR = 255;
    private int cachedLaserG = 255;
    private int cachedLaserB = 255;
    private float cachedSpeed = 0.0f;

    // Visual client-side rotation
    private float currentRotation = 0.0f;
    private boolean isFiltered = false;

    // Volumetric shadow casting 2D obstacle grid (3 concentric rings x 32 angular sectors)
    public static final int NUM_RINGS = 3;
    public static final int NUM_SECTORS = 32;
    public static final float[] RING_RADII_RATIOS = {0.25f, 0.60f, 1.00f};

    private final float[][] obstacleGrid = new float[NUM_RINGS][NUM_SECTORS];
    private final float[] sectorDistances = new float[NUM_SECTORS];
    private float centerObstacleDist = 48.0f;

    public float getRawSectorObstacleDistance(int sector) {
        int s = (sector % NUM_SECTORS + NUM_SECTORS) % NUM_SECTORS;
        float d = this.sectorDistances[s];
        return (d > 0.1f) ? d : this.coneRange;
    }

    /**
     * Edge-preserving smoothing across adjacent sectors.
     * Smooths continuous terrain slopes while preserving sharp shadow edges of obstacles.
     */
    public float getSectorObstacleDistance(int sector) {
        int s = (sector % NUM_SECTORS + NUM_SECTORS) % NUM_SECTORS;
        float curr = getRawSectorObstacleDistance(s);
        float prev = getRawSectorObstacleDistance(s - 1);
        float next = getRawSectorObstacleDistance(s + 1);

        float p = (Math.abs(curr - prev) < 3.0f) ? prev : curr;
        float n = (Math.abs(curr - next) < 3.0f) ? next : curr;
        return (p + curr * 2.0f + n) * 0.25f;
    }

    public float getRawObstacleDistance(int ring, int sector) {
        return getRawSectorObstacleDistance(sector);
    }

    public float getObstacleDistance(int ring, int sector) {
        return getSectorObstacleDistance(sector);
    }

    public float getCenterObstacleDistance() {
        return (this.centerObstacleDist > 0.1f) ? this.centerObstacleDist : this.coneRange;
    }

    public float getSegmentDistance(int index) {
        return getSectorObstacleDistance(index);
    }

    public float getInnerSegmentDistance(int index) {
        return getSectorObstacleDistance(index);
    }

    private long lastHitUpdateTick = -1;

    public float[] getSectorDistances() {
        return sectorDistances;
    }

    public long getLastHitUpdateTick() {
        return lastHitUpdateTick;
    }

    public void setLastHitUpdateTick(long tick) {
        this.lastHitUpdateTick = tick;
    }

    public void setCenterObstacleDistance(float dist) {
        this.centerObstacleDist = dist;
    }

    public OpticalDiffuserBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.OPTICAL_DIFFUSER_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            if (level.isClientSide) {
                CLIENT_INSTANCES.add(this);
            } else {
                ACTIVE_SERVER_DIFFUSERS.add(this);
            }
        }
    }

    public void setBeam(BeamHelper.BeamProperties properties) {
        this.hasBeam = true;
        this.polarization = properties.polarization();
        this.beamType = properties.beamType();
        this.cachedSpeed = Math.abs(properties.getEffectiveSpeed());
        this.cachedLaserR = properties.color().getX();
        this.cachedLaserG = properties.color().getY();
        this.cachedLaserB = properties.color().getZ();

        updateColorAndSpeed();
        updateModuleAndRedstone();
        updateBeamingState();

        setChanged();
        syncToClient();
    }

    public void removeBeam() {
        this.hasBeam = false;
        if (level != null && !level.isClientSide) {
            clearIllumination();
        }
        updateBeamingState();
        setChanged();
        syncToClient();
    }

    public void setInstalledFilmReelItem(ItemStack reel) {
        this.installedFilmReel = reel;
        setChanged();
        syncToClient();
    }

    public ItemStack getInstalledFilmReelItem() {
        return this.installedFilmReel;
    }

    public ItemStack[] getCarouselLenses() {
        return this.carouselLenses;
    }

    public float getCarouselAngle() {
        return this.carouselAngle;
    }

    public void setCarouselAngle(float angle) {
        this.carouselAngle = angle;
    }

    public int getActiveCarouselSlot() {
        // Active slot is the one closest to the front face (90 deg relative to left side)
        // With 4 slots evenly spaced at 0, 90, 180, 270 deg:
        float normalized = ((carouselAngle % 360.0f) + 360.0f) % 360.0f;
        // Slot 1 is front at angle 0. As carousel turns by +angle:
        int offset = Math.round(normalized / 90.0f) % 4;
        return (1 - offset + 4) % 4;
    }

    public ItemStack getActiveLens() {
        int slot = getActiveCarouselSlot();
        if (slot >= 0 && slot < 4 && !this.carouselLenses[slot].isEmpty()) {
            return this.carouselLenses[slot];
        }
        return ItemStack.EMPTY;
    }

    public void rotateCarousel(float degrees) {
        this.carouselAngle = ((this.carouselAngle + degrees) % 360.0f + 360.0f) % 360.0f;
        updateModuleAndRedstone();
        updateBeamingState();
        setChanged();
        syncToClient();
    }

    public ItemStack getCarouselLens(int slot) {
        if (slot >= 0 && slot < 4) {
            return this.carouselLenses[slot];
        }
        return ItemStack.EMPTY;
    }

    public void setCarouselLens(int slot, ItemStack stack) {
        if (slot >= 0 && slot < 4) {
            this.carouselLenses[slot] = stack;
            updateModuleAndRedstone();
            updateBeamingState();
            setChanged();
            syncToClient();
        }
    }

    public ItemStack removeCarouselLens(int slot) {
        if (slot >= 0 && slot < 4 && !this.carouselLenses[slot].isEmpty()) {
            ItemStack removed = this.carouselLenses[slot];
            this.carouselLenses[slot] = ItemStack.EMPTY;
            updateModuleAndRedstone();
            updateBeamingState();
            setChanged();
            syncToClient();
            return removed;
        }
        return ItemStack.EMPTY;
    }

    public boolean insertCarouselLens(ItemStack stack) {
        for (int i = 0; i < 4; i++) {
            if (this.carouselLenses[i].isEmpty()) {
                this.carouselLenses[i] = stack;
                updateModuleAndRedstone();
                updateBeamingState();
                setChanged();
                syncToClient();
                return true;
            }
        }
        return false;
    }

    public ItemStack removeLastCarouselLens() {
        // Remove from current active slot if present, otherwise any non-empty slot
        int active = getActiveCarouselSlot();
        if (!this.carouselLenses[active].isEmpty()) {
            ItemStack removed = this.carouselLenses[active];
            this.carouselLenses[active] = ItemStack.EMPTY;
            updateModuleAndRedstone();
            updateBeamingState();
            setChanged();
            syncToClient();
            return removed;
        }
        for (int i = 3; i >= 0; i--) {
            if (!this.carouselLenses[i].isEmpty()) {
                ItemStack removed = this.carouselLenses[i];
                this.carouselLenses[i] = ItemStack.EMPTY;
                updateModuleAndRedstone();
                updateBeamingState();
                setChanged();
                syncToClient();
                return removed;
            }
        }
        return ItemStack.EMPTY;
    }

    public boolean hasAnyCarouselLens() {
        for (int i = 0; i < 4; i++) {
            if (!this.carouselLenses[i].isEmpty()) return true;
        }
        return false;
    }

    public DiffuserModule getActiveModule() {
        ItemStack active = getActiveLens();
        if (!active.isEmpty()) {
            String id = BuiltInRegistries.ITEM.getKey(active.getItem()).toString();
            if (id.equals("create_optical_diffuser:focusing_lens") || id.equals("create_optical:beam_focuser")) return DiffuserModule.FOCUSER;
            if (id.equals("create_optical_diffuser:dispersing_lens") || id.equals("create_optical:beam_condenser")) return DiffuserModule.CONDENSER;
            if (id.equals("create_optical_diffuser:silent_projector_lens")) return DiffuserModule.PROJECTOR_SILENT;
            if (id.equals("create_optical_diffuser:sound_projector_lens")) return DiffuserModule.PROJECTOR_SOUND;
            if (id.equals("create_optical_diffuser:hologram_lens") || active.getItem() instanceof com.mik.optical_diffuser.item.HologramLensItem) return DiffuserModule.HOLOGRAM;
            if (id.equals("create_optical_diffuser:thermal_ignition_lens")) return DiffuserModule.THERMAL_IGNITER;
            if (id.equals("create_optical_diffuser:collimator_lens")) return DiffuserModule.COLLIMATOR;
        }
        if (this.level != null) {
            Direction facing = getBlockState().getValue(OpticalDiffuserBlock.FACING);
            for (Direction dir : Direction.values()) {
                if (dir == facing || dir == facing.getOpposite()) continue;
                BlockState adjState = this.level.getBlockState(this.worldPosition.relative(dir));
                String blockId = BuiltInRegistries.BLOCK.getKey(adjState.getBlock()).toString();
                if (blockId.equals("create_optical:beam_focuser")) return DiffuserModule.FOCUSER;
                if (blockId.equals("create_optical:beam_condenser")) return DiffuserModule.CONDENSER;
            }
        }
        return DiffuserModule.NONE;
    }

    public float getRedstoneVolumeFactor() {
        if (this.redstonePower == 0) {
            return 1.0f; // Unpowered: 100% standard playback
        }
        if (this.redstonePower >= 15) {
            return 0.0f; // Signal 15: Mute
        }
        if (this.redstonePower <= 11) {
            return 0.10f + (this.redstonePower - 1) * (0.90f / 10.0f); // 1 -> 10% up to 11 -> 100%
        }
        return 1.0f + (this.redstonePower - 11) * 0.15f; // 12 -> 115%, 13 -> 130%, 14 -> 145%
    }

    public void onRedstonePowerChanged(int power) {
        this.redstonePower = power;
        this.isPowered = power > 0;
        updateModuleAndRedstone();
        updateBeamingState();
        if (level != null && !level.isClientSide) {
            if (!isLightEnabled()) {
                clearIllumination();
            } else {
                updateIllumination(level, worldPosition, getBlockState());
            }
        }
        setChanged();
        syncToClient();
    }

    public void onRedstoneChanged(boolean powered) {
        int power = (level != null) ? level.getBestNeighborSignal(worldPosition) : (powered ? 15 : 0);
        onRedstonePowerChanged(power);
    }

    public void updateModuleAndRedstone() {
        if (this.level != null && !this.level.isClientSide) {
            this.redstonePower = this.level.getBestNeighborSignal(this.worldPosition);
            this.isPowered = this.redstonePower > 0;
        }

        DiffuserModule module = getActiveModule();
        if (module == DiffuserModule.FOCUSER) {
            // Signal 0..14 focuses the beam
            float t = Math.min(14, this.redstonePower) / 14.0f;
            this.coneAngle = 45.0f - t * (45.0f - 8.0f); // 45° at 0 down to 8° at 14
        } else if (module == DiffuserModule.CONDENSER) {
            // Signal 0..14 disperses the beam
            float t = Math.min(14, this.redstonePower) / 14.0f;
            this.coneAngle = 20.0f + t * (70.0f - 20.0f); // 20° at 0 up to 70° at 14
        } else if (module == DiffuserModule.THERMAL_IGNITER) {
            // Thermal Ignition Lens: moderately focused cone 28° down to 12° with redstone
            float t = Math.min(14, this.redstonePower) / 14.0f;
            this.coneAngle = 28.0f - t * 16.0f;
        } else if (module == DiffuserModule.COLLIMATOR) {
            // Collimator: razor-sharp needle beam 2.0° down to 1.0° with redstone
            float t = Math.min(14, this.redstonePower) / 14.0f;
            this.coneAngle = 2.0f - t * 1.0f;
        } else {
            // Default without module: fixed standard angle
            this.coneAngle = 35.0f;
        }

        if (this.hasBeam) {
            float rpmRatio = Math.min(1.0f, Math.max(0.0f, this.cachedSpeed / 256.0f));
            int minRange = 6;
            int maxRange = switch (this.beamType) {
                case RADIO, MICROWAVE -> 40;
                case VISIBLE -> 64;
                case GAMMA -> 80;
            };
            int baseRange = (int) (minRange + (maxRange - minRange) * Math.pow(rpmRatio, 0.75));
            if (module == DiffuserModule.FOCUSER) {
                float t = Math.min(14, this.redstonePower) / 14.0f;
                this.coneRange = (int) (baseRange * (1.0f + t * 0.5f));
            } else if (module == DiffuserModule.CONDENSER) {
                float t = Math.min(14, this.redstonePower) / 14.0f;
                this.coneRange = Math.max(12, (int) (baseRange * (1.0f - t * 0.4f)));
            } else if (module == DiffuserModule.THERMAL_IGNITER) {
                float t = Math.min(14, this.redstonePower) / 14.0f;
                this.coneRange = (int) (baseRange * (1.2f + t * 0.4f));
            } else if (module == DiffuserModule.COLLIMATOR) {
                this.coneRange = Math.min(96, (int) (baseRange * 1.8f));
            } else {
                this.coneRange = baseRange;
            }
            this.intensity = Math.min(1.2f, Math.max(0.25f, 0.25f + rpmRatio * 0.95f));
        } else {
            this.coneRange = 16;
            this.intensity = 0.4f;
        }
    }

    private void updateBeamingState() {
        if (level != null && !level.isClientSide) {
            BlockState current = getBlockState();
            boolean shouldBeam = isLightEnabled();
            if (current.hasProperty(OpticalDiffuserBlock.BEAMING) && current.getValue(OpticalDiffuserBlock.BEAMING) != shouldBeam) {
                level.setBlock(worldPosition, current.setValue(OpticalDiffuserBlock.BEAMING, shouldBeam), 2);
            }
        }
    }

    public void updateSettings(float coneAngle, float spinSpeed, int redstoneMode) {
        updateModuleAndRedstone();
        updateBeamingState();
        if (level != null && !level.isClientSide) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            if (!isLightEnabled()) {
                clearIllumination();
            }
        }

        setChanged();
        syncToClient();
    }

    public static Vec3i getDyeCanonicalColor(DyeColor dye) {
        return switch (dye) {
            case WHITE -> new Vec3i(255, 255, 255);
            case ORANGE -> new Vec3i(255, 110, 0);
            case MAGENTA -> new Vec3i(240, 40, 240);
            case LIGHT_BLUE -> new Vec3i(60, 185, 255);
            case YELLOW -> new Vec3i(255, 230, 20);
            case LIME -> new Vec3i(100, 240, 25);
            case PINK -> new Vec3i(255, 120, 170);
            case GRAY -> new Vec3i(110, 110, 110);
            case LIGHT_GRAY -> new Vec3i(170, 170, 170);
            case CYAN -> new Vec3i(15, 220, 220);
            case PURPLE -> new Vec3i(150, 30, 230);
            case BLUE -> new Vec3i(30, 70, 255);
            case BROWN -> new Vec3i(130, 70, 25);
            case GREEN -> new Vec3i(25, 210, 35);
            case RED -> new Vec3i(255, 15, 15);
            case BLACK -> new Vec3i(30, 30, 30);
        };
    }

    public static float[] getDyeFilterProfile(DyeColor dye) {
        return switch (dye) {
            case WHITE -> new float[]{1.0f, 1.0f, 1.0f};
            case YELLOW -> new float[]{1.0f, 0.95f, 0.02f};
            case BLUE -> new float[]{0.02f, 0.20f, 1.0f};
            case LIGHT_BLUE -> new float[]{0.15f, 0.75f, 1.0f};
            case CYAN -> new float[]{0.02f, 0.95f, 0.95f};
            case RED -> new float[]{1.0f, 0.02f, 0.02f};
            case GREEN -> new float[]{0.02f, 1.0f, 0.02f};
            case LIME -> new float[]{0.45f, 1.0f, 0.05f};
            case MAGENTA -> new float[]{1.0f, 0.02f, 0.90f};
            case PURPLE -> new float[]{0.50f, 0.02f, 0.90f};
            case ORANGE -> new float[]{1.0f, 0.45f, 0.02f};
            case PINK -> new float[]{1.0f, 0.35f, 0.65f};
            case GRAY -> new float[]{0.5f, 0.5f, 0.5f};
            case LIGHT_GRAY -> new float[]{0.75f, 0.75f, 0.75f};
            case BROWN -> new float[]{0.6f, 0.30f, 0.10f};
            case BLACK -> new float[]{0.10f, 0.10f, 0.10f};
        };
    }

    public void updateColorAndSpeed() {
        if (level != null) {
            Direction facing = getBlockState().getValue(OpticalDiffuserBlock.FACING);

            List<DyeColor> dyes = new ArrayList<>();

            // 1. Scan behind diffuser (between optical source and diffuser, up to 12 blocks)
            Direction back = facing.getOpposite();
            for (int i = 1; i <= 12; i++) {
                BlockPos checkPos = worldPosition.relative(back, i);
                BlockState st = level.getBlockState(checkPos);
                if (st.getBlock() instanceof BeaconBeamBlock beacon) {
                    DyeColor dye = beacon.getColor();
                    if (dye != null) {
                        dyes.add(dye);
                    }
                } else if (!isTransparentForBeam(st, level, checkPos)) {
                    break;
                }
            }

            // 2. Scan in front of diffuser (along emitted light beam, up to 12 blocks)
            for (int i = 1; i <= 12; i++) {
                BlockPos checkPos = worldPosition.relative(facing, i);
                BlockState st = level.getBlockState(checkPos);
                if (st.getBlock() instanceof BeaconBeamBlock beacon) {
                    DyeColor dye = beacon.getColor();
                    if (dye != null) {
                        dyes.add(dye);
                    }
                } else if (!isTransparentForBeam(st, level, checkPos)) {
                    break;
                }
            }

            int newR, newG, newB;
            if (dyes.isEmpty()) {
                // No glass filters found: clean bright white spotlight (or laser color if tinted)
                newR = this.hasBeam ? this.cachedLaserR : 255;
                newG = this.hasBeam ? this.cachedLaserG : 255;
                newB = this.hasBeam ? this.cachedLaserB : 255;
                if ((newR == newG && newG == newB) || (Math.abs(newR - newG) < 15 && Math.abs(newG - newB) < 15)) {
                    newR = 255;
                    newG = 255;
                    newB = 255;
                }
            } else if (dyes.size() == 1) {
                // Exactly one glass block: use canonical saturated dye color directly
                Vec3i singleColor = getDyeCanonicalColor(dyes.get(0));
                newR = singleColor.getX();
                newG = singleColor.getY();
                newB = singleColor.getZ();
            } else {
                // Multiple glass blocks: true subtractive optical filter mixing
                float curR = 1.0f;
                float curG = 1.0f;
                float curB = 1.0f;
                for (DyeColor dye : dyes) {
                    float[] profile = getDyeFilterProfile(dye);
                    curR *= profile[0];
                    curG *= profile[1];
                    curB *= profile[2];
                }

                // Peak normalization for luminous spotlight beam
                float maxCh = Math.max(curR, Math.max(curG, curB));
                if (maxCh > 0.0001f) {
                    curR /= maxCh;
                    curG /= maxCh;
                    curB /= maxCh;
                }

                newR = Math.min(255, Math.max(0, (int) (curR * 255)));
                newG = Math.min(255, Math.max(0, (int) (curG * 255)));
                newB = Math.min(255, Math.max(0, (int) (curB * 255)));
            }

            // Perceptual luminance floor: prevent dark wavelengths (blue, purple, dark red)
            // from being completely swallowed by the dark night sky
            float floorThreshold = 85.0f;
            try {
                if (com.mik.optical_diffuser.config.DiffuserClientConfig.INSTANCE != null &&
                    com.mik.optical_diffuser.config.DiffuserClientConfig.INSTANCE.perceptualLumaFloor != null) {
                    floorThreshold = com.mik.optical_diffuser.config.DiffuserClientConfig.INSTANCE.perceptualLumaFloor.get().floatValue();
                }
            } catch (Throwable ignored) {}

            float luma = 0.2126f * newR + 0.7152f * newG + 0.0722f * newB;
            if (luma < floorThreshold && (newR > 0 || newG > 0 || newB > 0)) {
                float lift = ((floorThreshold - luma) / 255.0f) * 0.40f;
                newR = Math.min(255, (int) (newR + (255 - newR) * lift));
                newG = Math.min(255, (int) (newG + (255 - newG) * lift));
                newB = Math.min(255, (int) (newB + (255 - newB) * lift));
            }

            if (this.red != newR || this.green != newG || this.blue != newB) {
                this.red = newR;
                this.green = newG;
                this.blue = newB;
                if (!level.isClientSide) {
                    setChanged();
                    syncToClient();
                }
            }
        }

        if (this.hasBeam) {
            float rpmRatio = Math.min(1.0f, Math.max(0.0f, this.cachedSpeed / 256.0f));
            int minRange = 6;
            int maxRange = switch (this.beamType) {
                case RADIO, MICROWAVE -> 40;
                case VISIBLE -> 64;
                case GAMMA -> 80;
            };
            // Non-linear RPM scaling: 1 RPM gives ~7 blocks, 256 RPM gives maxRange
            this.coneRange = (int) (minRange + (maxRange - minRange) * Math.pow(rpmRatio, 0.75));
            this.intensity = Math.min(1.2f, Math.max(0.25f, 0.25f + rpmRatio * 0.95f));
        } else {
            this.coneRange = 16;
            this.intensity = 0.4f;
        }
    }

    public void updateObstacleGrid() {
        // Obstacle occlusion system disabled for smooth unified volumetric spotlight
    }

    public void updateSegmentDistances() {
        // Obstacle occlusion system disabled
    }

    @Override
    public void tick() {
        super.tick();

        float effectiveSpeed = getEffectiveSpinSpeed();
        if (this.isActive() && effectiveSpeed != 0.0f) {
            this.currentRotation += (effectiveSpeed * 6.0f) / 20.0f;
            if (this.currentRotation >= 360.0f) {
                this.currentRotation -= 360.0f;
            } else if (this.currentRotation < 0.0f) {
                this.currentRotation += 360.0f;
            }
        }

        // Kinetic shaft connected to the left face rotates the lens carousel wheel
        float shaftSpeed = getSpeed();
        if (shaftSpeed != 0.0f) {
            int prevSlot = getActiveCarouselSlot();
            this.carouselAngle += (shaftSpeed * 6.0f) / 20.0f;
            if (this.carouselAngle >= 360.0f) {
                this.carouselAngle -= 360.0f;
            } else if (this.carouselAngle < 0.0f) {
                this.carouselAngle += 360.0f;
            }
            int newSlot = getActiveCarouselSlot();
            if (prevSlot != newSlot) {
                updateModuleAndRedstone();
                updateBeamingState();
                setChanged();
                if (level != null && !level.isClientSide) {
                    syncToClient();
                }
            }
        }

        if (level != null && level.isClientSide) {
            if (this.isActive() && level.getGameTime() % 2 == 0) {
                updateObstacleGrid();
            }
            com.mik.optical_diffuser.client.media.DiffuserMediaManager.get(this).tick();
        }

        if (level != null && !level.isClientSide) {
            ACTIVE_SERVER_DIFFUSERS.add(this);
            if (level.getGameTime() % 4 == 0) {
                com.mik.optical_diffuser.compat.SableCompat.checkAndMigrateScreenCoords(this);
            }
            if (this.isLightEnabled()) {
                // High-frequency active beam interactions (Archimedes heat ray, Collimator needle laser, Gamma radiation)
                if (level.getGameTime() % 2 == 0) {
                    tickActiveBeamEffects(level, worldPosition, getBlockState());
                }

                if (level.getGameTime() % 20 == 0 || this.currentLightPositions.isEmpty()) {
                    updateModuleAndRedstone();
                    updateColorAndSpeed();
                    updateIllumination(level, worldPosition, getBlockState());
                }
            } else {
                clearIllumination();
            }
        }
    }

    public com.mik.optical_diffuser.block.entity.FilmReelBlockEntity getConnectedFilmReel() {
        if (level == null) return null;
        Direction facing = getBlockState().getValue(OpticalDiffuserBlock.FACING);
        Direction right = OpticalDiffuserBlock.getRightDirection(facing);
        BlockPos rightPos = getBlockPos().relative(right);
        BlockEntity be = level.getBlockEntity(rightPos);
        if (be instanceof com.mik.optical_diffuser.block.entity.FilmReelBlockEntity reel) {
            return reel;
        }
        return null;
    }

    private void addLightCandidate(Level targetLevel, BlockPos p, Map<BlockPos, Integer> positions, int lightLevel) {
        if (targetLevel == null || isServerStopping() || isChunkUnloaded()) {
            return;
        }
        try {
            if (!targetLevel.hasChunkAt(p)) {
                return;
            }
            BlockState st = targetLevel.getBlockState(p);
            boolean isWater = st.getFluidState().is(FluidTags.WATER);
            if (st.isAir() || isWater || st.is(Blocks.LIGHT) || !st.blocksMotion() || st.canBeReplaced()) {
                positions.merge(p.immutable(), lightLevel, Math::max);
            }
        } catch (Exception ignored) {}
    }

    private void addSurfaceLightCandidate(Level targetLevel, BlockPos hitBlock, Direction hitFace, Map<BlockPos, Integer> positions, int lightLevel) {
        if (targetLevel == null || isServerStopping() || isChunkUnloaded()) {
            return;
        }
        try {
            BlockPos lightPos = hitBlock.relative(hitFace);
            if (!targetLevel.hasChunkAt(lightPos)) {
                return;
            }
            BlockState st = targetLevel.getBlockState(lightPos);
            boolean isWater = st.getFluidState().is(FluidTags.WATER);
            if (st.isAir() || isWater || st.is(Blocks.LIGHT)) {
                positions.merge(lightPos.immutable(), lightLevel, Math::max);
                return;
            }
            // If the surface has vegetation (grass, tall flowers, snow layer), check lightPos.above()
            if (!st.blocksMotion() || st.canBeReplaced()) {
                BlockPos above = lightPos.above();
                if (targetLevel.hasChunkAt(above)) {
                    BlockState aboveSt = targetLevel.getBlockState(above);
                    if (aboveSt.isAir() || aboveSt.is(Blocks.LIGHT) || aboveSt.getFluidState().is(FluidTags.WATER)) {
                        positions.merge(above.immutable(), lightLevel, Math::max);
                        return;
                    }
                }
                positions.merge(lightPos.immutable(), lightLevel, Math::max);
            }
        } catch (Exception ignored) {}
    }

    private void illuminateGroundUnder(Level targetLevel, Vec3 pt, Vec3 start, Vec3 centerDir, Vec3 right, Vec3 up,
                                       double scaleU, double scaleV, double tanHalf, Map<BlockPos, Integer> newPositions) {
        if (targetLevel == null) return;
        try {
            int gx = Mth.floor(pt.x);
            int gz = Mth.floor(pt.z);
            BlockPos testPos = new BlockPos(gx, 0, gz);
            if (!targetLevel.hasChunkAt(testPos)) return;

            int gy = targetLevel.getHeight(Heightmap.Types.MOTION_BLOCKING, gx, gz);
            BlockPos groundBlock = new BlockPos(gx, gy - 1, gz);

            // Verify ground block is in front of the diffuser and within the cone envelope
            Vec3 v = Vec3.atCenterOf(groundBlock).subtract(start);
            double zProj = v.dot(centerDir);
            if (zProj > 1.0 && zProj <= this.coneRange + 4.0) {
                double dx = v.dot(right) / scaleU;
                double dy = v.dot(up) / scaleV;
                double allowedRadius = zProj * tanHalf + 2.5;
                if (dx * dx + dy * dy <= allowedRadius * allowedRadius) {
                    addSurfaceLightCandidate(targetLevel, groundBlock, Direction.UP, newPositions, 15);
                }
            }
        } catch (Exception ignored) {}
    }

    private java.util.List<double[]> getIlluminationRayOffsets(BeamHelper.BeamPolarization pol) {
        java.util.List<double[]> offsets = new java.util.ArrayList<>();
        if (pol == BeamHelper.BeamPolarization.HORIZONTAL) {
            for (double u = -1.0; u <= 1.0; u += 0.12) {
                offsets.add(new double[]{u, 0.0});
                offsets.add(new double[]{u * 0.9, 0.20});
                offsets.add(new double[]{u * 0.9, -0.20});
            }
        } else if (pol == BeamHelper.BeamPolarization.VERTICAL) {
            for (double v = -1.0; v <= 1.0; v += 0.12) {
                offsets.add(new double[]{0.0, v});
                offsets.add(new double[]{0.20, v * 0.9});
                offsets.add(new double[]{-0.20, v * 0.9});
            }
        } else {
            // Circular cone: center + 4 concentric rings (69 rays total for gap-free surface coverage)
            offsets.add(new double[]{0.0, 0.0}); // Center
            // Ring 1 (r = 0.25, 8 rays)
            for (int i = 0; i < 8; i++) {
                double a = i * (Math.PI / 4.0);
                offsets.add(new double[]{Math.cos(a) * 0.25, Math.sin(a) * 0.25});
            }
            // Ring 2 (r = 0.50, 16 rays)
            for (int i = 0; i < 16; i++) {
                double a = i * (Math.PI / 8.0) + (Math.PI / 16.0);
                offsets.add(new double[]{Math.cos(a) * 0.50, Math.sin(a) * 0.50});
            }
            // Ring 3 (r = 0.75, 20 rays)
            for (int i = 0; i < 20; i++) {
                double a = i * (Math.PI / 10.0);
                offsets.add(new double[]{Math.cos(a) * 0.75, Math.sin(a) * 0.75});
            }
            // Ring 4 (r = 1.00, 24 rays)
            for (int i = 0; i < 24; i++) {
                double a = i * (Math.PI / 12.0) + (Math.PI / 24.0);
                offsets.add(new double[]{Math.cos(a) * 1.00, Math.sin(a) * 1.00});
            }
        }
        return offsets;
    }

    private void tickActiveBeamEffects(Level level, BlockPos pos, BlockState state) {
        try {
            Direction facing = state.getValue(OpticalDiffuserBlock.FACING);
            DiffuserModule module = getActiveModule();

            BlockPos localFront = pos.relative(facing);
            BlockState localFrontState = level.getBlockState(localFront);
            if (!isTransparentForBeam(localFrontState, level, localFront)) {
                return;
            }

            SableCompat.RayContext rayCtx = SableCompat.getRayContext(level, pos, facing);
            Level targetLevel = rayCtx.level();
            Vec3 start = rayCtx.start();
            Vec3 centerDir = rayCtx.direction();

            // Check if Gamma radiation is blocked by Lead Glass
            boolean filtered = checkLeadGlassFilter(targetLevel, pos, facing, start, centerDir);
            if (this.isFiltered != filtered) {
                this.isFiltered = filtered;
                setChanged();
                syncToClient();
            }

            long gameTime = level.getGameTime();

            // Stage 2 Gamma radiation in cone
            if (this.beamType == BeamHelper.BeamType.GAMMA && !this.isFiltered) {
                if (gameTime % 4 == 0) {
                    applyGammaConeEffects(targetLevel, start, centerDir);
                    if (level != targetLevel) {
                        Vec3 localStart = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.50));
                        Vec3 localDir = Vec3.atLowerCornerOf(facing.getNormal());
                        applyGammaConeEffects(level, localStart, localDir);
                    }
                }
            }

            // Stage 3 Microwave thermal effects
            if (this.beamType == BeamHelper.BeamType.MICROWAVE) {
                if (gameTime % 4 == 0) {
                    applyMicrowaveConeEffects(targetLevel, start, centerDir);
                    if (level != targetLevel) {
                        Vec3 localStart = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.50));
                        Vec3 localDir = Vec3.atLowerCornerOf(facing.getNormal());
                        applyMicrowaveConeEffects(level, localStart, localDir);
                    }
                }
            }

            // Stage 4 Archimedes Thermal Death Ray
            if (module == DiffuserModule.THERMAL_IGNITER) {
                if (gameTime % 4 == 0) {
                    applyThermalIgniterConeEffects(targetLevel, start, centerDir, rayCtx.up(), rayCtx.right());
                    if (level != targetLevel) {
                        Vec3 localStart = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.50));
                        Vec3 localDir = Vec3.atLowerCornerOf(facing.getNormal());
                        Vec3 localUp = (facing.getAxis() == Direction.Axis.Y) ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
                        Vec3 localRight = localDir.cross(localUp).normalize();
                        applyThermalIgniterConeEffects(level, localStart, localDir, localUp, localRight);
                    }
                }
            }

            // Stage 5 Collimator needle laser
            if (module == DiffuserModule.COLLIMATOR) {
                if (gameTime % 4 == 0) {
                    applyCollimatorBeamEffects(targetLevel, start, centerDir);
                    if (level != targetLevel) {
                        Vec3 localStart = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.50));
                        Vec3 localDir = Vec3.atLowerCornerOf(facing.getNormal());
                        applyCollimatorBeamEffects(level, localStart, localDir);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void updateIllumination(Level level, BlockPos pos, BlockState state) {
        try {
            Direction facing = state.getValue(OpticalDiffuserBlock.FACING);
            DiffuserModule module = getActiveModule();

            // If a solid block is placed directly in front of the diffuser, beam is blocked right away
            BlockPos localFront = pos.relative(facing);
            BlockState localFrontState = level.getBlockState(localFront);
            if (!isTransparentForBeam(localFrontState, level, localFront)) {
                clearIllumination();
                return;
            }

            SableCompat.RayContext rayCtx = SableCompat.getRayContext(level, pos, facing);
            Level targetLevel = rayCtx.level();
            Vec3 start = rayCtx.start();
            Vec3 centerDir = rayCtx.direction();
            Vec3 up = rayCtx.up();
            Vec3 right = rayCtx.right();

            double tanHalf = Math.tan(Math.toRadians(this.coneAngle * 0.5));
            float roll = 0.0f;
            if (this.polarization == BeamHelper.BeamPolarization.DIAGONAL_POSITIVE) {
                roll = 45.0f;
            } else if (this.polarization == BeamHelper.BeamPolarization.DIAGONAL_NEGATIVE) {
                roll = -45.0f;
            }

            Map<BlockPos, Integer> newPositions = new HashMap<>();

            double rad = Math.toRadians(roll);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);

            double scaleU = 1.0;
            double scaleV = 1.0;
            if (this.polarization == BeamHelper.BeamPolarization.HORIZONTAL) {
                scaleU = 2.0;
                scaleV = 0.35;
            } else if (this.polarization == BeamHelper.BeamPolarization.VERTICAL) {
                scaleU = 0.35;
                scaleV = 2.0;
            } else if (this.polarization == BeamHelper.BeamPolarization.DIAGONAL_POSITIVE || this.polarization == BeamHelper.BeamPolarization.DIAGONAL_NEGATIVE) {
                scaleU = 1.6;
                scaleV = 0.45;
            }

            boolean doThermalMelt = (this.beamType == BeamHelper.BeamType.MICROWAVE);

            // 1. Dense surface raycast across the entire cone to light up all ground, cliff, and wall surfaces (Level 15)
            java.util.List<double[]> rayOffsets = getIlluminationRayOffsets(this.polarization);

            for (double[] uv : rayOffsets) {
                double u = (uv[0] * cos - uv[1] * sin) * scaleU;
                double v = (uv[0] * sin + uv[1] * cos) * scaleV;
                Vec3 rayDir = centerDir.add(right.scale(u * tanHalf)).add(up.scale(v * tanHalf)).normalize();
                Vec3 rayEnd = start.add(rayDir.scale(this.coneRange));
                BlockHitResult hit = raycastBeam(targetLevel, start, rayEnd);

                if (hit.getType() == HitResult.Type.BLOCK) {
                    double hitDist = hit.getLocation().distanceTo(start);
                    BlockPos hitBlock = hit.getBlockPos();
                    Direction hitFace = hit.getDirection();

                    // Full bright light (15) to guarantee no mob spawning
                    addSurfaceLightCandidate(targetLevel, hitBlock, hitFace, newPositions, 15);

                    if (doThermalMelt) {
                        meltAt(targetLevel, hitBlock);
                        if (ModList.get().isLoaded("sable")) {
                            SableCompat.meltAt(targetLevel, hit.getLocation());
                        }
                    }

                    // Multi-layer pass: if hit was an obstacle before the end, also illuminate ground behind it
                    if (hitDist < this.coneRange - 4.0) {
                        Vec3 pastStart = hit.getLocation().add(rayDir.scale(1.2));
                        BlockHitResult hit2 = raycastBeam(targetLevel, pastStart, rayEnd);
                        if (hit2.getType() == HitResult.Type.BLOCK) {
                            addSurfaceLightCandidate(targetLevel, hit2.getBlockPos(), hit2.getDirection(), newPositions, 15);
                            if (doThermalMelt) {
                                meltAt(targetLevel, hit2.getBlockPos());
                                if (ModList.get().isLoaded("sable")) {
                                    SableCompat.meltAt(targetLevel, hit2.getLocation());
                                }
                            }
                        }
                    }
                }
            }

            // 2. Full 3D volumetric air light grid and terrain surface projection
            // Spacing every 4.5 blocks guarantees block light level >= 12 everywhere (mob spawning impossible)
            double step = 4.5;
            for (double d = 2.5; d < this.coneRange; d += step) {
                Vec3 centerPt = start.add(centerDir.scale(d));
                double rAtD = d * tanHalf;

                // Center node (air and ground directly beneath)
                addLightCandidate(targetLevel, BlockPos.containing(centerPt), newPositions, 15);
                illuminateGroundUnder(targetLevel, centerPt, start, centerDir, right, up, scaleU, scaleV, tanHalf, newPositions);

                // Intermediate ring if cone is wider than 3.5 blocks
                if (rAtD >= 3.5) {
                    double rRing = rAtD * 0.60;
                    int count = Math.max(4, (int) (2 * Math.PI * rRing / 4.5));
                    for (int k = 0; k < count; k++) {
                        double a = k * (2.0 * Math.PI / count) + rad;
                        double u = Math.cos(a) * scaleU * rRing;
                        double v = Math.sin(a) * scaleV * rRing;
                        Vec3 pt = centerPt.add(right.scale(u)).add(up.scale(v));
                        addLightCandidate(targetLevel, BlockPos.containing(pt), newPositions, 15);
                        illuminateGroundUnder(targetLevel, pt, start, centerDir, right, up, scaleU, scaleV, tanHalf, newPositions);
                    }
                }

                // Outer ring if cone is wider than 7.0 blocks
                if (rAtD >= 7.0) {
                    double rRing = rAtD * 0.90;
                    int count = Math.max(6, (int) (2 * Math.PI * rRing / 4.5));
                    for (int k = 0; k < count; k++) {
                        double a = k * (2.0 * Math.PI / count) + (Math.PI / count) + rad;
                        double u = Math.cos(a) * scaleU * rRing;
                        double v = Math.sin(a) * scaleV * rRing;
                        Vec3 pt = centerPt.add(right.scale(u)).add(up.scale(v));
                        addLightCandidate(targetLevel, BlockPos.containing(pt), newPositions, 15);
                        illuminateGroundUnder(targetLevel, pt, start, centerDir, right, up, scaleU, scaleV, tanHalf, newPositions);
                    }
                }
            }

            // Extra wide thermal melting for microwave beam across cone
            if (doThermalMelt) {
                java.util.List<double[]> uvSamples = getUvSamples(this.polarization);
                for (double[] uv : uvSamples) {
                    double u = uv[0] * cos - uv[1] * sin;
                    double v = uv[0] * sin + uv[1] * cos;
                    Vec3 sideDir = centerDir.add(right.scale(u * tanHalf)).add(up.scale(v * tanHalf)).normalize();
                    Vec3 sideEnd = start.add(sideDir.scale(this.coneRange));
                    BlockHitResult sideHit = raycastBeam(targetLevel, start, sideEnd);
                    if (sideHit.getType() == HitResult.Type.BLOCK) {
                        meltAt(targetLevel, sideHit.getBlockPos());
                        if (ModList.get().isLoaded("sable")) {
                            SableCompat.meltAt(targetLevel, sideHit.getLocation());
                        }
                    }
                }
                if (level != targetLevel) {
                    meltAt(level, pos.relative(facing));
                }
            }

            // Zero-flicker diff update
            if (this.currentIlluminatedLevel != null) {
                for (BlockPos oldPos : this.currentLightPositions) {
                    if (!newPositions.containsKey(oldPos) || this.currentIlluminatedLevel != targetLevel) {
                        removeLightBlock(this.currentIlluminatedLevel, oldPos);
                    }
                }
            }

            for (Map.Entry<BlockPos, Integer> entry : newPositions.entrySet()) {
                BlockPos newPos = entry.getKey();
                int levelValue = entry.getValue();
                if (!this.currentLightPositions.contains(newPos) || this.currentIlluminatedLevel != targetLevel) {
                    placeLightBlock(targetLevel, newPos, levelValue);
                }
            }

            this.currentLightPositions.clear();
            this.currentLightPositions.addAll(newPositions.keySet());
            this.currentIlluminatedLevel = targetLevel;

        } catch (Exception ignored) {
        }
    }

    public static boolean isTransparentForBeam(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) return true;
        if (state.is(Blocks.LIGHT)) return true;
        if (state.is(ModRegistry.LEAD_GLASS.get())) return true;
        if (!state.blocksMotion() || state.canBeReplaced()) return true;
        if (state.getBlock() instanceof TransparentBlock || state.getBlock() instanceof IronBarsBlock) return true;
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS)) return true;
        if (state.propagatesSkylightDown(level, pos) || state.getLightBlock(level, pos) <= 1) return true;
        return false;
    }

    private BlockHitResult raycastBeam(Level targetLevel, Vec3 start, Vec3 rayEnd) {
        double totalDist = start.distanceTo(rayEnd);
        if (totalDist < 0.05) {
            return BlockHitResult.miss(rayEnd, Direction.UP, BlockPos.containing(rayEnd));
        }

        Vec3 currentStart = start;
        Vec3 dir = rayEnd.subtract(start).normalize();

        for (int step = 0; step < 16; step++) {
            BlockHitResult hit = targetLevel.clip(new ClipContext(
                    currentStart, rayEnd,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    CollisionContext.empty()
            ));

            SableCompat.SableBlockHit sableHit = null;
            if (ModList.get().isLoaded("sable")) {
                sableHit = SableCompat.raycastSubLevels(targetLevel, currentStart, rayEnd, false);
            }

            double worldDist = (hit.getType() == HitResult.Type.BLOCK) ? currentStart.distanceTo(hit.getLocation()) : Double.MAX_VALUE;
            double sableDist = (sableHit != null) ? sableHit.worldDistance() : Double.MAX_VALUE;

            if (sableDist < worldDist && sableHit != null) {
                if (isTransparentForBeam(sableHit.state(), sableHit.subLevel().getLevel(), sableHit.plotPos())) {
                    Vec3 forward = sableHit.worldHitPos().add(dir.scale(0.2));
                    currentStart = forward;
                    if (currentStart.distanceTo(start) >= totalDist) {
                        return BlockHitResult.miss(rayEnd, sableHit.worldFace(), BlockPos.containing(rayEnd));
                    }
                    continue;
                } else {
                    return new BlockHitResult(sableHit.worldHitPos(), sableHit.worldFace(), BlockPos.containing(sableHit.worldHitPos()), false);
                }
            }

            if (hit.getType() == HitResult.Type.MISS) {
                return hit;
            }

            BlockPos hitPos = hit.getBlockPos();
            BlockState hitState = targetLevel.getBlockState(hitPos);

            if (isTransparentForBeam(hitState, targetLevel, hitPos)) {
                // Step forward through this block
                Vec3 forward = hit.getLocation().add(dir.scale(0.15));
                int maxHops = 32;
                while (targetLevel.getBlockState(BlockPos.containing(forward)).is(hitState.getBlock())
                        && forward.distanceTo(start) < totalDist && --maxHops > 0) {
                    forward = forward.add(dir.scale(0.2));
                }
                currentStart = forward;
                if (currentStart.distanceTo(start) >= totalDist) {
                    return BlockHitResult.miss(rayEnd, hit.getDirection(), hitPos);
                }
            } else {
                return hit;
            }
        }
        return BlockHitResult.miss(rayEnd, Direction.UP, BlockPos.containing(rayEnd));
    }

    private BlockPos findWaterSurfaceAir(Level level, BlockPos startPos) {
        BlockPos.MutableBlockPos cursor = startPos.mutable();
        for (int i = 0; i < 32; i++) {
            BlockState st = level.getBlockState(cursor);
            if (st.isAir() || st.is(Blocks.LIGHT)) {
                return cursor.immutable();
            }
            if (!st.getFluidState().is(net.minecraft.world.level.material.Fluids.WATER) && !st.isAir() && !st.is(Blocks.LIGHT)) {
                return null;
            }
            cursor.move(Direction.UP);
        }
        return null;
    }

    private boolean isTooCloseToExisting(Set<BlockPos> positions, BlockPos candidate, double minDistanceSq) {
        for (BlockPos p : positions) {
            if (p.distSqr(candidate) < minDistanceSq) {
                return true;
            }
        }
        return false;
    }

    private boolean checkLeadGlassFilter(Level targetLevel, BlockPos pos, Direction facing, Vec3 start, Vec3 centerDir) {
        if (level != null) {
            // Check in front of the diffuser
            for (int i = 1; i <= 6; i++) {
                BlockPos localFront = pos.relative(facing, i);
                if (level.getBlockState(localFront).is(ModRegistry.LEAD_GLASS.get())) {
                    return true;
                }
            }
            // Check behind the diffuser (between optical source and diffuser)
            Direction back = facing.getOpposite();
            for (int i = 1; i <= 6; i++) {
                BlockPos localBack = pos.relative(back, i);
                if (level.getBlockState(localBack).is(ModRegistry.LEAD_GLASS.get())) {
                    return true;
                }
            }
        }
        if (targetLevel != null) {
            // Check forward in target level
            for (double d = 0.5; d <= 6.0; d += 0.8) {
                Vec3 pt = start.add(centerDir.scale(d));
                BlockPos worldCheck = BlockPos.containing(pt);
                if (targetLevel.getBlockState(worldCheck).is(ModRegistry.LEAD_GLASS.get()) || SableCompat.isLeadGlassAt(targetLevel, pt)) {
                    return true;
                }
            }
            // Check backward in target level
            for (double d = 0.5; d <= 6.0; d += 0.8) {
                Vec3 pt = start.subtract(centerDir.scale(d));
                BlockPos worldCheck = BlockPos.containing(pt);
                if (targetLevel.getBlockState(worldCheck).is(ModRegistry.LEAD_GLASS.get()) || SableCompat.isLeadGlassAt(targetLevel, pt)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isEntityInCone(LivingEntity entity, Vec3 rayStart, Vec3 centerDir, double tanHalf, Level targetLevel) {
        double entityRadius = entity.getBbWidth() * 0.5;
        Vec3[] testPoints = new Vec3[]{
                entity.getEyePosition(),
                entity.position().add(0, entity.getBbHeight() * 0.5, 0),
                entity.position().add(0, 0.2, 0)
        };

        for (Vec3 pt : testPoints) {
            Vec3 toPt = pt.subtract(rayStart);
            double distAlongCenter = toPt.dot(centerDir);
            if (distAlongCenter >= 0.0 && distAlongCenter <= this.coneRange) {
                Vec3 proj = centerDir.scale(distAlongCenter);
                double radialDist = toPt.subtract(proj).length();
                double allowedRadius = Math.max(0.30, distAlongCenter * tanHalf) + entityRadius;
                if (radialDist <= allowedRadius) {
                    BlockHitResult los = raycastBeam(targetLevel, rayStart, pt);
                    if (los.getType() == HitResult.Type.MISS || los.getLocation().distanceTo(rayStart) >= pt.distanceTo(rayStart) - 0.6) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void applyGammaConeEffects(Level targetLevel, Vec3 start, Vec3 centerDir) {
        if (targetLevel == null) return;
        double tanHalf = Math.tan(Math.toRadians(this.coneAngle * 0.5));
        double maxRadius = Math.max(1.0, this.coneRange * tanHalf);
        AABB coneAABB = new AABB(start, start.add(centerDir.scale(this.coneRange))).inflate(maxRadius + 3.0);
        List<LivingEntity> entities = targetLevel.getEntitiesOfClass(LivingEntity.class, coneAABB, e -> e.isAlive() && !e.isSpectator());
        Vec3 rayStart = start.add(centerDir.scale(0.12));

        for (LivingEntity entity : entities) {
            if (isEntityInCone(entity, rayStart, centerDir, tanHalf, targetLevel)) {
                applyGammaRadiation(targetLevel, entity);
            }
        }
    }

    private void applyMicrowaveConeEffects(Level targetLevel, Vec3 start, Vec3 centerDir) {
        if (targetLevel == null) return;
        double tanHalf = Math.tan(Math.toRadians(this.coneAngle * 0.5));
        double maxRadius = Math.max(1.0, this.coneRange * tanHalf);
        AABB coneAABB = new AABB(start, start.add(centerDir.scale(this.coneRange))).inflate(maxRadius + 3.0);
        List<LivingEntity> entities = targetLevel.getEntitiesOfClass(LivingEntity.class, coneAABB, e -> e.isAlive() && !e.isSpectator());
        Vec3 rayStart = start.add(centerDir.scale(0.12));

        float rpm = Math.max(this.cachedSpeed, Math.max(Math.abs(getSpeed()), Math.abs(getEffectiveSpinSpeed())));
        float rpmRatio = Math.min(1.0f, Math.max(0.0f, rpm / 256.0f));
        float damage = 1.0f + rpmRatio * 2.5f;

        for (LivingEntity entity : entities) {
            if (isEntityInCone(entity, rayStart, centerDir, tanHalf, targetLevel)) {
                Vec3 originalMotion = entity.getDeltaMovement();
                entity.invulnerableTime = 0;
                entity.hurt(targetLevel.damageSources().inFire(), damage);
                entity.setDeltaMovement(originalMotion);
                entity.hurtMarked = true;
                entity.igniteForSeconds((int) (3 + rpmRatio * 4));
            }
        }

        processItemsInCone(targetLevel, coneAABB, rayStart, centerDir, tanHalf, rpm);
    }

    private void applyThermalIgniterConeEffects(Level targetLevel, Vec3 start, Vec3 centerDir) {
        Vec3 up = Math.abs(centerDir.y) > 0.99 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        Vec3 right = centerDir.cross(up).normalize();
        applyThermalIgniterConeEffects(targetLevel, start, centerDir, up, right);
    }

    private void applyThermalIgniterConeEffects(Level targetLevel, Vec3 start, Vec3 centerDir, Vec3 up, Vec3 right) {
        if (targetLevel == null) return;
        double tanHalf = Math.tan(Math.toRadians(this.coneAngle * 0.5));
        double maxRadius = Math.max(1.0, this.coneRange * tanHalf);
        AABB coneAABB = new AABB(start, start.add(centerDir.scale(this.coneRange))).inflate(maxRadius + 3.0);
        Vec3 rayStart = start.add(centerDir.scale(0.12));

        float rpm = Math.max(this.cachedSpeed, Math.max(Math.abs(getSpeed()), Math.abs(getEffectiveSpinSpeed())));
        float rpmRatio = Math.min(1.0f, Math.max(0.0f, rpm / 256.0f));

        long gameTime = targetLevel.getGameTime();

        // Mob damage every 8 ticks
        if (gameTime % 8 == 0) {
            float damage = 1.0f + rpmRatio * 2.0f; // 1.0 to 3.0 damage (0.5 to 1.5 hearts)
            List<LivingEntity> entities = targetLevel.getEntitiesOfClass(LivingEntity.class, coneAABB, e -> e.isAlive() && !e.isSpectator());
            for (LivingEntity entity : entities) {
                if (isEntityInCone(entity, rayStart, centerDir, tanHalf, targetLevel)) {
                    Vec3 originalMotion = entity.getDeltaMovement();
                    entity.hurt(targetLevel.damageSources().inFire(), damage);
                    entity.setDeltaMovement(originalMotion);
                    entity.igniteForSeconds((int) (4 + rpmRatio * 6));
                }
            }
        }

        // Process dropped items in cone (cooking food / smelting)
        processItemsInCone(targetLevel, coneAABB, rayStart, centerDir, tanHalf, rpm);

        // Process blocks and depots in cone
        processArchimedesBlockThermalRay(targetLevel, start, centerDir, up, right, rpm, coneAABB);

        // Quiet ambient fire sound throttled to once per second
        if (gameTime % 20 == 0) {
            targetLevel.playSound(null, BlockPos.containing(start), SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.4f, 1.0f);
        }
    }

    private void applyCollimatorBeamEffects(Level targetLevel, Vec3 start, Vec3 centerDir) {
        if (targetLevel == null) return;
        Vec3 beamEnd = start.add(centerDir.scale(this.coneRange));
        AABB beamAABB = new AABB(start, beamEnd).inflate(1.2);
        List<LivingEntity> entities = targetLevel.getEntitiesOfClass(LivingEntity.class, beamAABB, e -> e.isAlive() && !e.isSpectator());
        Vec3 rayStart = start.add(centerDir.scale(0.12));

        float rpm = Math.max(this.cachedSpeed, Math.max(Math.abs(getSpeed()), Math.abs(getEffectiveSpinSpeed())));
        float rpmRatio = Math.min(1.0f, Math.max(0.0f, rpm / 256.0f));
        // Base damage increased by 1.5x: (3.5f + rpmRatio * 7.5f) * 1.5f
        float piercingDamage = (3.5f + rpmRatio * 7.5f) * 1.5f;

        for (LivingEntity entity : entities) {
            Vec3 toEntity = entity.position().add(0, entity.getBbHeight() * 0.5, 0).subtract(rayStart);
            double distAlong = toEntity.dot(centerDir);
            if (distAlong >= 0 && distAlong <= this.coneRange) {
                Vec3 closestOnLine = centerDir.scale(distAlong);
                double distToLine = toEntity.subtract(closestOnLine).length();
                if (distToLine <= entity.getBbWidth() * 0.5 + 0.35) {
                    BlockHitResult hit = raycastBeam(targetLevel, rayStart, entity.getBoundingBox().getCenter());
                    if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(rayStart) >= entity.position().distanceTo(rayStart) - 0.5) {
                        Vec3 originalMotion = entity.getDeltaMovement();
                        entity.invulnerableTime = 0;
                        if (this.beamType == BeamHelper.BeamType.GAMMA) {
                            entity.hurt(CODamageSources.gammaRay(targetLevel), piercingDamage * 1.5f);
                        } else {
                            entity.hurt(targetLevel.damageSources().magic(), piercingDamage);
                        }
                        entity.setDeltaMovement(originalMotion);
                        entity.hurtMarked = true;
                        entity.igniteForSeconds(2 + (int) (rpmRatio * 4));
                    }
                }
            }
        }

        // Cook dropped food and smelt items along the collimator needle beam
        List<ItemEntity> items = targetLevel.getEntitiesOfClass(ItemEntity.class, beamAABB, e -> e.isAlive());
        for (ItemEntity itemEntity : items) {
            Vec3 toItem = itemEntity.position().subtract(rayStart);
            double distAlong = toItem.dot(centerDir);
            if (distAlong >= 0 && distAlong <= this.coneRange) {
                Vec3 closestOnLine = centerDir.scale(distAlong);
                double distToLine = toItem.subtract(closestOnLine).length();
                if (distToLine <= 0.6) {
                    processItemCooking(itemEntity, rpm);
                }
            }
        }

        // Cook items on Create Depots & Belts hit along the needle beam
        BlockHitResult beamBlockHit = raycastBeam(targetLevel, rayStart, beamEnd);
        double maxNeedleDist = (beamBlockHit.getType() == HitResult.Type.BLOCK) ? beamBlockHit.getLocation().distanceTo(rayStart) : this.coneRange;
        Set<BlockPos> visitedCollimatorDepots = new HashSet<>();
        for (double d = 0.5; d <= maxNeedleDist; d += 1.0) {
            BlockPos p = BlockPos.containing(rayStart.add(centerDir.scale(d)));
            BlockPos[] cands = new BlockPos[]{p, p.below()};
            for (BlockPos cPos : cands) {
                if (visitedCollimatorDepots.add(cPos)) {
                    BlockState cState = targetLevel.getBlockState(cPos);
                    if (cState.hasBlockEntity()) {
                        TransportedItemStackHandlerBehaviour handler = BlockEntityBehaviour.get(targetLevel, cPos, TransportedItemStackHandlerBehaviour.TYPE);
                        if (handler != null) {
                            handler.handleProcessingOnAllItems(transported -> processTransportedCooking(transported, targetLevel, rpm));
                        }
                    }
                }
            }
        }
    }

    private void applyGammaRadiation(Level targetLevel, LivingEntity entity) {
        if (isProtectedFromRadiation(entity)) {
            return;
        }
        Vec3 originalMotion = entity.getDeltaMovement();
        entity.invulnerableTime = 0;
        float rpm = Math.max(this.cachedSpeed, Math.max(Math.abs(getSpeed()), Math.abs(getEffectiveSpinSpeed())));
        float rpmRatio = Math.min(1.0f, Math.max(0.0f, rpm / 256.0f));
        float damage = 1.0f + rpmRatio * 1.5f;
        boolean hurt = entity.hurt(CODamageSources.gammaRay(targetLevel), damage);
        if (!hurt) {
            entity.hurt(targetLevel.damageSources().magic(), damage);
        }
        entity.setDeltaMovement(originalMotion);
        entity.hurtMarked = true;
        entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0, false, false));
    }

    private boolean isProtectedFromRadiation(LivingEntity entity) {
        // Full copper diving suit (BOTH helmet AND chestplate/backtank) is required to shield against gamma radiation
        ItemStack helmet = entity.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (helmet.isEmpty() || chest.isEmpty()) {
            return false;
        }
        String helmetId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(helmet.getItem()).toString();
        String chestId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(chest.getItem()).toString();
        boolean hasHelmet = helmetId.contains("diving_helmet") || helmetId.contains("copper_helmet");
        boolean hasChest = chestId.contains("backtank") || chestId.contains("copper_chestplate");
        return hasHelmet && hasChest;
    }

    private java.util.List<double[]> getUvSamples(BeamHelper.BeamPolarization pol) {
        java.util.List<double[]> samples = new java.util.ArrayList<>();
        if (pol == BeamHelper.BeamPolarization.HORIZONTAL) {
            // Dense horizontal fan
            for (double u = -1.8; u <= 1.81; u += 0.12) {
                samples.add(new double[]{u, 0.0});
            }
            for (double u = -1.5; u <= 1.51; u += 0.15) {
                samples.add(new double[]{u, 0.15});
                samples.add(new double[]{u, -0.15});
            }
            for (double u = -1.1; u <= 1.11; u += 0.2) {
                samples.add(new double[]{u, 0.28});
                samples.add(new double[]{u, -0.28});
            }
        } else if (pol == BeamHelper.BeamPolarization.VERTICAL) {
            // Dense vertical slit: swap u and v
            for (double v = -1.8; v <= 1.81; v += 0.12) {
                samples.add(new double[]{0.0, v});
            }
            for (double v = -1.5; v <= 1.51; v += 0.15) {
                samples.add(new double[]{0.15, v});
                samples.add(new double[]{-0.15, v});
            }
            for (double v = -1.1; v <= 1.11; v += 0.2) {
                samples.add(new double[]{0.28, v});
                samples.add(new double[]{-0.28, v});
            }
        } else {
            // High-density circular cone (91 rays across 5 concentric rings for seamless coverage even at 75° wide angles)
            samples.add(new double[]{0.0, 0.0}); // Center
            // Ring 1 (r = 0.18, 6 samples)
            for (int i = 0; i < 6; i++) {
                double a = i * (Math.PI / 3.0);
                samples.add(new double[]{Math.cos(a) * 0.18, Math.sin(a) * 0.18});
            }
            // Ring 2 (r = 0.36, 12 samples)
            for (int i = 0; i < 12; i++) {
                double a = i * (Math.PI / 6.0) + 0.1;
                samples.add(new double[]{Math.cos(a) * 0.36, Math.sin(a) * 0.36});
            }
            // Ring 3 (r = 0.54, 18 samples)
            for (int i = 0; i < 18; i++) {
                double a = i * (Math.PI / 9.0) + 0.2;
                samples.add(new double[]{Math.cos(a) * 0.54, Math.sin(a) * 0.54});
            }
            // Ring 4 (r = 0.72, 24 samples)
            for (int i = 0; i < 24; i++) {
                double a = i * (Math.PI / 12.0) + 0.05;
                samples.add(new double[]{Math.cos(a) * 0.72, Math.sin(a) * 0.72});
            }
            // Ring 5 (r = 0.90, 30 samples)
            for (int i = 0; i < 30; i++) {
                double a = i * (Math.PI / 15.0);
                samples.add(new double[]{Math.cos(a) * 0.90, Math.sin(a) * 0.90});
            }
        }
        return samples;
    }

    public static int getCookingPassesForRpm(float rpm) {
        float rpmRatio = Math.max(0.0f, Math.min(1.0f, rpm / 256.0f));
        // Scales smoothly: at 0 RPM = 1 pass; at 16 RPM = 3 passes; at 128 RPM = 16 passes; at 256 RPM = 32 passes (~1s full cook)
        return Math.max(1, (int) (1 + rpmRatio * 31));
    }

    public static void processItemCooking(ItemEntity itemEntity, float rpm) {
        int passes = getCookingPassesForRpm(rpm);
        boolean isBlasting = rpm >= 128.0f;

        for (int pass = 0; pass < passes; pass++) {
            if (FanProcessing.canProcess(itemEntity, AllFanProcessingTypes.SMOKING)) {
                if (FanProcessing.applyProcessing(itemEntity, AllFanProcessingTypes.SMOKING)) {
                    break;
                }
            } else if (isBlasting && FanProcessing.canProcess(itemEntity, AllFanProcessingTypes.BLASTING)) {
                if (FanProcessing.applyProcessing(itemEntity, AllFanProcessingTypes.BLASTING)) {
                    break;
                }
            } else {
                break;
            }
        }
    }

    public static TransportedItemStackHandlerBehaviour.TransportedResult processTransportedCooking(
            TransportedItemStack transported, Level level, float rpm
    ) {
        int passes = getCookingPassesForRpm(rpm);
        boolean isBlasting = rpm >= 128.0f;

        for (int pass = 0; pass < passes; pass++) {
            if (AllFanProcessingTypes.SMOKING.canProcess(transported.stack, level)) {
                TransportedItemStackHandlerBehaviour.TransportedResult res =
                        FanProcessing.applyProcessing(transported, level, AllFanProcessingTypes.SMOKING);
                if (!res.doesNothing()) {
                    return res;
                }
            } else if (isBlasting && AllFanProcessingTypes.BLASTING.canProcess(transported.stack, level)) {
                TransportedItemStackHandlerBehaviour.TransportedResult res =
                        FanProcessing.applyProcessing(transported, level, AllFanProcessingTypes.BLASTING);
                if (!res.doesNothing()) {
                    return res;
                }
            } else {
                break;
            }
        }
        return TransportedItemStackHandlerBehaviour.TransportedResult.doNothing();
    }

    private void processItemsInCone(Level targetLevel, AABB aabb, Vec3 rayStart, Vec3 centerDir, double tanHalf, float rpm) {
        List<ItemEntity> items = targetLevel.getEntitiesOfClass(ItemEntity.class, aabb, e -> e.isAlive());
        for (ItemEntity itemEntity : items) {
            Vec3 pos = itemEntity.position();
            Vec3 toPt = pos.subtract(rayStart);
            double distAlong = toPt.dot(centerDir);
            if (distAlong >= 0 && distAlong <= this.coneRange) {
                Vec3 proj = centerDir.scale(distAlong);
                double allowedR = Math.max(0.5, distAlong * tanHalf) + 0.5;
                if (toPt.subtract(proj).length() <= allowedR) {
                    processItemCooking(itemEntity, rpm);
                }
            }
        }
    }

    public static void meltAt(Level lvl, BlockPos p) {
        try {
            BlockState st = lvl.getBlockState(p);
            if (st.is(BlockTags.SNOW) || st.is(Blocks.SNOW)) {
                lvl.levelEvent(2009, p, 0);
                lvl.removeBlock(p, false);
            } else if (st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.POWDER_SNOW)) {
                lvl.levelEvent(2009, p, 0);
                lvl.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
            } else if (st.is(Blocks.PACKED_ICE)) {
                lvl.levelEvent(2009, p, 0);
                lvl.setBlock(p, Blocks.ICE.defaultBlockState(), 3);
            } else if (st.is(Blocks.BLUE_ICE)) {
                lvl.levelEvent(2009, p, 0);
                lvl.setBlock(p, Blocks.PACKED_ICE.defaultBlockState(), 3);
            } else if (st.is(BlockTags.ICE) || st.is(Blocks.ICE) || st.is(Blocks.FROSTED_ICE)) {
                lvl.levelEvent(2009, p, 0);
                lvl.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
            } else {
                String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                if (id.contains("snow")) {
                    lvl.levelEvent(2009, p, 0);
                    lvl.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
                } else if (id.contains("ice")) {
                    lvl.levelEvent(2009, p, 0);
                    lvl.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
                }
            }
        } catch (Exception ignored) {}
    }

    private void processArchimedesBlockThermalRay(Level targetLevel, Vec3 start, Vec3 centerDir, Vec3 up, Vec3 right, float rpm, AABB coneAABB) {
        if (targetLevel == null) return;
        double tanHalf = Math.tan(Math.toRadians(this.coneAngle * 0.5));
        RandomSource random = targetLevel.getRandom();

        // Rays: Center + 6 inner + 10 outer = 17 rays (dense coverage without lag)
        List<double[]> uvList = new ArrayList<>(17);
        uvList.add(new double[]{0.0, 0.0});
        for (int i = 0; i < 6; i++) {
            double a = i * (Math.PI / 3.0);
            uvList.add(new double[]{Math.cos(a) * 0.40, Math.sin(a) * 0.40});
        }
        for (int i = 0; i < 10; i++) {
            double a = i * (Math.PI / 5.0) + (Math.PI / 10.0);
            uvList.add(new double[]{Math.cos(a) * 0.85, Math.sin(a) * 0.85});
        }

        List<dev.ryanhcode.sable.sublevel.SubLevel> sableCandidates = null;
        if (ModList.get().isLoaded("sable")) {
            sableCandidates = SableCompat.getIntersectingSubLevels(targetLevel, coneAABB);
        }

        Set<BlockPos> visitedWorldDepots = new HashSet<>();
        Set<BlockPos> visitedSableDepots = new HashSet<>();
        Set<BlockPos> visitedBlocks = new HashSet<>();
        double maxDist = this.coneRange;

        for (double[] uv : uvList) {
            Vec3 rayDir = centerDir.add(right.scale(uv[0] * tanHalf)).add(up.scale(uv[1] * tanHalf)).normalize();
            Vec3 curStart = start;
            double remDist = maxDist;

            // Penetrate up to 4 layers (e.g. leaves, bushes, ice)
            for (int hop = 0; hop < 4 && remDist > 0.5; hop++) {
                Vec3 rayEnd = curStart.add(rayDir.scale(remDist));

                // 1. Raycast world blocks
                BlockHitResult worldHit = targetLevel.clip(new ClipContext(
                        curStart, rayEnd,
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.ANY,
                        CollisionContext.empty()
                ));

                // 2. Raycast Sable sublevels
                SableCompat.SableBlockHit sableHit = null;
                if (sableCandidates != null && !sableCandidates.isEmpty()) {
                    sableHit = SableCompat.raycastSubLevels(targetLevel, curStart, rayEnd, true, ClipContext.Block.OUTLINE);
                }

                double worldD = (worldHit.getType() == HitResult.Type.BLOCK) ? curStart.distanceTo(worldHit.getLocation()) : Double.MAX_VALUE;
                double sableD = (sableHit != null) ? sableHit.worldDistance() : Double.MAX_VALUE;

                if (worldD == Double.MAX_VALUE && sableD == Double.MAX_VALUE) {
                    break;
                }

                if (sableD < worldD && sableHit != null) {
                    Level subLevel = sableHit.subLevel().getLevel();
                    BlockPos sPos = sableHit.plotPos();
                    Direction sFace = sableHit.worldFace();

                    // Cook items on ship depot/belt
                    BlockPos[] dCandidates = new BlockPos[]{sPos, sPos.below()};
                    for (BlockPos dPos : dCandidates) {
                        if (visitedSableDepots.add(dPos)) {
                            BlockState ds = subLevel.getBlockState(dPos);
                            if (ds.hasBlockEntity()) {
                                TransportedItemStackHandlerBehaviour handler = BlockEntityBehaviour.get(subLevel, dPos, TransportedItemStackHandlerBehaviour.TYPE);
                                if (handler != null) {
                                    handler.handleProcessingOnAllItems(transported -> processTransportedCooking(transported, subLevel, rpm));
                                }
                            }
                        }
                    }

                    boolean stop = processThermalBlock(subLevel, sPos, sFace, rpm, random);
                    if (stop) {
                        break;
                    }
                    double distTraveled = sableHit.worldHitPos().distanceTo(curStart) + 0.3;
                    remDist -= distTraveled;
                    curStart = sableHit.worldHitPos().add(rayDir.scale(0.3));
                } else {
                    BlockPos wPos = worldHit.getBlockPos();
                    Direction wFace = worldHit.getDirection();

                    // Cook items on world depot/belt
                    BlockPos[] dCandidates = new BlockPos[]{wPos, wPos.below()};
                    for (BlockPos dPos : dCandidates) {
                        if (visitedWorldDepots.add(dPos)) {
                            BlockState ds = targetLevel.getBlockState(dPos);
                            if (ds.hasBlockEntity()) {
                                TransportedItemStackHandlerBehaviour handler = BlockEntityBehaviour.get(targetLevel, dPos, TransportedItemStackHandlerBehaviour.TYPE);
                                if (handler != null) {
                                    handler.handleProcessingOnAllItems(transported -> processTransportedCooking(transported, targetLevel, rpm));
                                }
                            }
                        }
                    }

                    boolean stop = true;
                    if (visitedBlocks.add(wPos)) {
                        stop = processThermalBlock(targetLevel, wPos, wFace, rpm, random);
                    }
                    if (stop) {
                        break;
                    }
                    double distTraveled = worldHit.getLocation().distanceTo(curStart) + 0.3;
                    remDist -= distTraveled;
                    curStart = worldHit.getLocation().add(rayDir.scale(0.3));
                }
            }
        }
    }

    public static boolean isFlammableBlock(Level lvl, BlockPos p, BlockState st) {
        if (!st.blocksMotion()) {
            return false; // Non-solid blocks (grass, flowers, vines) must NEVER be ignition sources
        }

        // 1. Strict blacklist: terrain, minerals, stones, soils, ores, metals
        if (st.is(BlockTags.DIRT) || st.is(BlockTags.SAND) || st.is(BlockTags.BASE_STONE_OVERWORLD) ||
                st.is(BlockTags.BASE_STONE_NETHER) || st.is(BlockTags.STONE_BRICKS) ||
                st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT) || st.is(Blocks.COARSE_DIRT) ||
                st.is(Blocks.PODZOL) || st.is(Blocks.MYCELIUM) || st.is(Blocks.ROOTED_DIRT) ||
                st.is(Blocks.MUD) || st.is(Blocks.MUDDY_MANGROVE_ROOTS) || st.is(Blocks.FARMLAND) ||
                st.is(Blocks.DIRT_PATH) || st.is(Blocks.STONE) || st.is(Blocks.COBBLESTONE) ||
                st.is(Blocks.MOSSY_COBBLESTONE) || st.is(Blocks.SMOOTH_STONE) ||
                st.is(Blocks.DEEPSLATE) || st.is(Blocks.COBBLED_DEEPSLATE) ||
                st.is(Blocks.SAND) || st.is(Blocks.RED_SAND) || st.is(Blocks.GRAVEL) ||
                st.is(Blocks.BEDROCK) || st.is(Blocks.OBSIDIAN) || st.is(Blocks.CRYING_OBSIDIAN) ||
                st.is(Blocks.NETHERRACK) || st.is(Blocks.BASALT) || st.is(Blocks.SMOOTH_BASALT) ||
                st.is(Blocks.POLISHED_BASALT) || st.is(Blocks.BLACKSTONE) || st.is(Blocks.END_STONE) ||
                st.is(Blocks.TUFF) || st.is(Blocks.CALCITE) || st.is(Blocks.DRIPSTONE_BLOCK) ||
                st.is(Blocks.GRANITE) || st.is(Blocks.DIORITE) || st.is(Blocks.ANDESITE) ||
                st.is(Blocks.MAGMA_BLOCK) || st.is(Blocks.LAVA) || st.is(Blocks.WATER)) {
            return false;
        }

        // 2. Strict whitelist: only actual flammable wooden and fiber/organic materials
        if (st.is(BlockTags.LOGS) || st.is(BlockTags.PLANKS) || st.is(BlockTags.WOOL) ||
                st.is(BlockTags.WOODEN_FENCES) || st.is(BlockTags.WOODEN_SLABS) ||
                st.is(BlockTags.WOODEN_STAIRS) || st.is(BlockTags.WOODEN_DOORS) ||
                st.is(BlockTags.WOODEN_TRAPDOORS) || st.is(BlockTags.WOODEN_PRESSURE_PLATES) ||
                st.is(BlockTags.WOODEN_BUTTONS) || st.is(BlockTags.BAMBOO_BLOCKS) ||
                st.is(BlockTags.ALL_SIGNS) || st.is(BlockTags.ALL_HANGING_SIGNS) ||
                st.is(BlockTags.BANNERS) || st.is(BlockTags.WOOL_CARPETS) ||
                st.is(Blocks.HAY_BLOCK) || st.is(Blocks.TARGET) || st.is(Blocks.BOOKSHELF) ||
                st.is(Blocks.CHISELED_BOOKSHELF) || st.is(Blocks.LECTERN) ||
                st.is(Blocks.COMPOSTER) || st.is(Blocks.BEEHIVE) || st.is(Blocks.BEE_NEST) ||
                st.is(Blocks.BARREL) || st.is(Blocks.TNT)) {
            return true;
        }
        if (st.getBlock() instanceof net.minecraft.world.level.block.RotatedPillarBlock && st.is(BlockTags.LOGS)) {
            return true;
        }

        // 3. Check name/tag heuristics strictly for modded wood/planks/logs/wool/sails
        for (var tag : st.getTags().toList()) {
            String path = tag.location().getPath();
            if (path.contains("logs") || path.contains("planks") || path.contains("wooden") ||
                    path.contains("wool") || path.contains("thatch") || path.contains("sail")) {
                return true;
            }
        }
        String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
        if (id.contains("sail") || id.contains("plank") || id.contains("wood")) {
            return true;
        }
        return false;
    }

    public static boolean isPlantOrBush(BlockState st, Level lvl, BlockPos p) {
        if (st.is(BlockTags.FLOWERS) || st.is(BlockTags.CROPS) || st.is(BlockTags.SAPLINGS) ||
                st.is(BlockTags.TALL_FLOWERS) || st.is(BlockTags.REPLACEABLE_BY_TREES) ||
                st.is(BlockTags.SWORD_EFFICIENT) || st.is(BlockTags.MAINTAINS_FARMLAND)) {
            return true;
        }
        if (st.getBlock() instanceof net.minecraft.world.level.block.BushBlock ||
                st.getBlock() instanceof net.minecraft.world.level.block.GrowingPlantBlock ||
                st.getBlock() instanceof net.minecraft.world.level.block.FlowerBlock ||
                st.getBlock() instanceof net.minecraft.world.level.block.TallGrassBlock ||
                st.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock ||
                st.getBlock() instanceof net.minecraft.world.level.block.VineBlock ||
                st.getBlock() instanceof net.minecraft.world.level.block.SugarCaneBlock) {
            return true;
        }
        if (!st.blocksMotion() && (st.canBeReplaced() || st.is(BlockTags.FLOWERS) || st.is(BlockTags.CROPS))) {
            return true;
        }
        return false;
    }

    public static void igniteNeighborFace(Level lvl, BlockPos p, @Nullable Direction hitDir) {
        Direction[] tryDirs;
        if (hitDir != null) {
            tryDirs = new Direction[]{hitDir, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};
        } else {
            tryDirs = new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};
        }

        for (Direction dir : tryDirs) {
            BlockPos adj = p.relative(dir);
            BlockState adjState = lvl.getBlockState(adj);
            if (adjState.isAir() || adjState.canBeReplaced()) {
                if (lvl.getFluidState(adj).is(FluidTags.WATER) || adjState.is(Blocks.WATER)) {
                    continue; // Never place fire inside water
                }
                BlockState fireState = BaseFireBlock.getState(lvl, adj);
                if (fireState.canSurvive(lvl, adj)) {
                    lvl.setBlock(adj, fireState, 3);
                    return;
                }
            }
        }
    }

    public static boolean processThermalBlock(Level lvl, BlockPos p, @Nullable Direction hitDir, float rpm, RandomSource random) {
        try {
            BlockState st = lvl.getBlockState(p);
            if (st.isAir() || st.is(Blocks.LIGHT)) {
                return false;
            }
            if (st.is(ModRegistry.LEAD_GLASS.get())) {
                return true;
            }

            // 1. Water handling: steam puff (20% chance), absorbs beam
            if (st.is(Blocks.WATER) || st.getFluidState().is(FluidTags.WATER)) {
                if (random.nextFloat() < 0.20f) {
                    lvl.levelEvent(2009, p, 0); // Steam puff
                }
                return true; // Water absorbs beam in balanced mode, never turns to dry air holes
            }

            // 2. Ice and snow
            if (st.is(BlockTags.SNOW) || st.is(BlockTags.ICE) || st.is(Blocks.SNOW) || st.is(Blocks.ICE)) {
                meltAt(lvl, p);
                return false;
            }

            // 3. Bushes, plants, grass, crops, vines
            if (isPlantOrBush(st, lvl, p)) {
                if (rpm >= 100.0f && random.nextFloat() < 0.30f) {
                    BlockState fireState = BaseFireBlock.getState(lvl, p);
                    if (fireState.canSurvive(lvl, p)) {
                        lvl.setBlock(p, fireState, 3);
                        return false;
                    }
                }
                lvl.destroyBlock(p, false);
                lvl.levelEvent(2009, p, 0); // Subtle smoke puff
                return false; // Ray continues through cleared bush
            }

            // 4. Leaves (Trees!)
            boolean isLeaves = st.is(BlockTags.LEAVES) || st.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock;
            if (isLeaves) {
                igniteNeighborFace(lvl, p, hitDir);
                if (random.nextFloat() < 0.35f) {
                    BlockState fireState = BaseFireBlock.getState(lvl, p);
                    if (fireState.canSurvive(lvl, p)) {
                        lvl.setBlock(p, fireState, 3);
                    }
                }
                return false; // Ray penetrates deeper into the tree canopy
            }

            // 5. Solid flammable blocks: logs, planks, wool, fences, sails, etc.
            if (isFlammableBlock(lvl, p, st)) {
                igniteNeighborFace(lvl, p, hitDir);
                if (rpm >= 64.0f && random.nextFloat() < 0.30f) {
                    BlockState fireState = BaseFireBlock.getState(lvl, p);
                    if (fireState.canSurvive(lvl, p)) {
                        lvl.setBlock(p, fireState, 3);
                    }
                }
                if (rpm >= 200.0f && random.nextFloat() < 0.05f) {
                    if (st.is(BlockTags.LOGS)) {
                        lvl.setBlock(p, Blocks.COAL_BLOCK.defaultBlockState(), 3);
                    }
                }
                return true;
            }

            // 6. Smelting & Mineral transformations
            if (rpm >= 200.0f) {
                // High speed only (>= 200 RPM): Superheat stone/cobblestone into magma!
                if ((st.is(Blocks.COBBLESTONE) || st.is(Blocks.COBBLED_DEEPSLATE)) && random.nextFloat() < 0.20f) {
                    lvl.setBlock(p, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                    return true;
                } else if ((st.is(Blocks.STONE) || st.is(Blocks.ANDESITE) || st.is(Blocks.DIORITE) || st.is(Blocks.GRANITE)) && random.nextFloat() < 0.08f) {
                    lvl.setBlock(p, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                    return true;
                }
            }

            if (rpm >= 64.0f) {
                if (st.is(BlockTags.SAND)) {
                    lvl.setBlock(p, Blocks.GLASS.defaultBlockState(), 3);
                    return true;
                } else if (st.is(Blocks.CLAY)) {
                    lvl.setBlock(p, Blocks.TERRACOTTA.defaultBlockState(), 3);
                    return true;
                } else if (st.is(Blocks.WET_SPONGE)) {
                    lvl.setBlock(p, Blocks.SPONGE.defaultBlockState(), 3);
                    lvl.levelEvent(2009, p, 0);
                    return true;
                } else if (st.is(Blocks.RAW_IRON_BLOCK)) {
                    lvl.setBlock(p, Blocks.IRON_BLOCK.defaultBlockState(), 3);
                    return true;
                } else if (st.is(Blocks.RAW_COPPER_BLOCK)) {
                    lvl.setBlock(p, Blocks.COPPER_BLOCK.defaultBlockState(), 3);
                    return true;
                } else if (st.is(Blocks.RAW_GOLD_BLOCK)) {
                    lvl.setBlock(p, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                    return true;
                }
            }

            if (st.blocksMotion()) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isServerStopping() {
        if (level instanceof ServerLevel sl) {
            MinecraftServer s = sl.getServer();
            return s != null && (!s.isRunning() || s.isStopped());
        }
        return false;
    }

    private void placeLightBlock(Level targetLevel, BlockPos p, int level) {
        if (targetLevel == null || isServerStopping() || isChunkUnloaded()) {
            return;
        }
        try {
            if (!targetLevel.hasChunkAt(p)) {
                return;
            }
            int lvl = Math.max(1, Math.min(15, level));
            BlockState current = targetLevel.getBlockState(p);
            if (current.is(Blocks.LIGHT)) {
                if (current.getValue(LightBlock.LEVEL) != lvl) {
                    targetLevel.setBlock(p, current.setValue(LightBlock.LEVEL, lvl), 2);
                }
                return;
            }
            boolean isWater = current.getFluidState().is(FluidTags.WATER);
            if (current.isAir() || isWater || !current.blocksMotion() || current.canBeReplaced()) {
                BlockState lightState = Blocks.LIGHT.defaultBlockState()
                        .setValue(LightBlock.LEVEL, lvl)
                        .setValue(LightBlock.WATERLOGGED, isWater);
                targetLevel.setBlock(p, lightState, 2);
            }
        } catch (Exception ignored) {}
    }

    private void removeLightBlock(Level targetLevel, BlockPos p) {
        if (targetLevel == null) {
            return;
        }
        try {
            if (!targetLevel.hasChunkAt(p)) {
                return;
            }
            BlockState st = targetLevel.getBlockState(p);
            if (st.is(Blocks.LIGHT)) {
                if (st.hasProperty(LightBlock.WATERLOGGED) && st.getValue(LightBlock.WATERLOGGED)) {
                    targetLevel.setBlock(p, Blocks.WATER.defaultBlockState(), 2);
                } else {
                    targetLevel.removeBlock(p, false);
                }
            }
        } catch (Exception ignored) {}
    }

    public void clearIllumination() {
        Level lvl = (this.currentIlluminatedLevel != null) ? this.currentIlluminatedLevel : this.level;
        if (lvl != null) {
            for (BlockPos p : this.currentLightPositions) {
                removeLightBlock(lvl, p);
            }
        }
        this.currentLightPositions.clear();
        this.currentIlluminatedLevel = null;
    }

    @Override
    public void destroy() {
        super.destroy();
        handleCleanup();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        handleCleanup();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        handleCleanup();
    }

    private void handleCleanup() {
        if (level != null) {
            if (level.isClientSide) {
                CLIENT_INSTANCES.remove(this);
                com.mik.optical_diffuser.client.media.DiffuserMediaManager.remove(this);
            } else {
                ACTIVE_SERVER_DIFFUSERS.remove(this);
                clearIllumination();
            }
        }
    }

    public void syncToClient() {
        if (level != null && !level.isClientSide) {
            notifyUpdate();
        }
    }

    public boolean isRedstoneActive() {
        DiffuserModule mod = getActiveModule();
        if (mod == DiffuserModule.PROJECTOR_SOUND || mod == DiffuserModule.PROJECTOR_SILENT || mod == DiffuserModule.HOLOGRAM) {
            return true; // Cinema projector & Hologram stay active on signal 15
        }
        return this.redstonePower < 15;
    }

    public boolean isLightEnabled() {
        return this.hasBeam && isRedstoneActive();
    }

    public boolean isActive() {
        return isLightEnabled();
    }

    public int getRed() {
        return red;
    }

    public int getGreen() {
        return green;
    }

    public int getBlue() {
        return blue;
    }

    public float getIntensity() {
        return intensity;
    }

    public float getConeAngle() {
        return coneAngle;
    }

    public int getConeRange() {
        return coneRange;
    }

    public float getSpinSpeed() {
        return spinSpeed;
    }

    public int getRedstoneMode() {
        return redstoneMode;
    }

    public BeamHelper.BeamPolarization getPolarization() {
        return polarization;
    }

    public BeamHelper.BeamType getBeamType() {
        return beamType;
    }

    public float getCurrentRotation() {
        return currentRotation;
    }

    public float getEffectiveSpinSpeed() {
        if (hasSource() || hasNetwork() || Math.abs(getSpeed()) > 0.001f) {
            return getSpeed();
        }
        return this.spinSpeed;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("HasBeam", this.hasBeam);
        tag.putBoolean("IsPowered", this.isPowered);
        tag.putInt("Red", this.red);
        tag.putInt("Green", this.green);
        tag.putInt("Blue", this.blue);
        tag.putFloat("Intensity", this.intensity);

        tag.putString("Polarization", this.polarization.name());
        tag.putString("BeamType", this.beamType.name());

        tag.putFloat("ConeAngle", this.coneAngle);
        tag.putInt("ConeRange", this.coneRange);
        tag.putFloat("SpinSpeed", this.spinSpeed);
        tag.putInt("RedstoneMode", this.redstoneMode);
        tag.putInt("RedstonePower", this.redstonePower);
        tag.putFloat("CurrentRotation", this.currentRotation);
        tag.putFloat("CarouselAngle", this.carouselAngle);

        tag.putInt("CachedLaserR", this.cachedLaserR);
        tag.putInt("CachedLaserG", this.cachedLaserG);
        tag.putInt("CachedLaserB", this.cachedLaserB);
        tag.putFloat("CachedSpeed", this.cachedSpeed);
        tag.putBoolean("IsFiltered", this.isFiltered);

        for (int i = 0; i < 4; i++) {
            if (!this.carouselLenses[i].isEmpty()) {
                tag.put("CarouselLens" + i, this.carouselLenses[i].save(registries, new CompoundTag()));
            }
        }
        if (!this.installedFilmReel.isEmpty()) {
            tag.put("InstalledFilmReel", this.installedFilmReel.save(registries, new CompoundTag()));
        }

        if (!clientPacket) {
            long[] posArray = new long[this.currentLightPositions.size()];
            int idx = 0;
            for (BlockPos p : this.currentLightPositions) {
                posArray[idx++] = p.asLong();
            }
            tag.putLongArray("LightPositions", posArray);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.hasBeam = tag.getBoolean("HasBeam");
        if (tag.contains("IsPowered")) this.isPowered = tag.getBoolean("IsPowered");
        if (tag.contains("Red")) this.red = tag.getInt("Red");
        if (tag.contains("Green")) this.green = tag.getInt("Green");
        if (tag.contains("Blue")) this.blue = tag.getInt("Blue");
        if (tag.contains("Intensity")) this.intensity = tag.getFloat("Intensity");

        if (tag.contains("Polarization")) {
            try {
                this.polarization = BeamHelper.BeamPolarization.valueOf(tag.getString("Polarization"));
            } catch (Exception ignored) {}
        }
        if (tag.contains("BeamType")) {
            try {
                this.beamType = BeamHelper.BeamType.valueOf(tag.getString("BeamType"));
            } catch (Exception ignored) {}
        }

        if (tag.contains("ConeAngle")) this.coneAngle = tag.getFloat("ConeAngle");
        if (tag.contains("ConeRange")) this.coneRange = tag.getInt("ConeRange");
        if (tag.contains("SpinSpeed")) this.spinSpeed = tag.getFloat("SpinSpeed");
        if (tag.contains("RedstoneMode")) this.redstoneMode = tag.getInt("RedstoneMode");
        if (tag.contains("RedstonePower")) this.redstonePower = tag.getInt("RedstonePower");
        if (tag.contains("CurrentRotation")) this.currentRotation = tag.getFloat("CurrentRotation");
        if (tag.contains("CarouselAngle")) this.carouselAngle = tag.getFloat("CarouselAngle");

        if (tag.contains("CachedLaserR")) this.cachedLaserR = tag.getInt("CachedLaserR");
        if (tag.contains("CachedLaserG")) this.cachedLaserG = tag.getInt("CachedLaserG");
        if (tag.contains("CachedLaserB")) this.cachedLaserB = tag.getInt("CachedLaserB");
        if (tag.contains("CachedSpeed")) this.cachedSpeed = tag.getFloat("CachedSpeed");
        if (tag.contains("IsFiltered")) this.isFiltered = tag.getBoolean("IsFiltered");

        for (int i = 0; i < 4; i++) {
            if (tag.contains("CarouselLens" + i, 10)) {
                this.carouselLenses[i] = ItemStack.parse(registries, tag.getCompound("CarouselLens" + i)).orElse(ItemStack.EMPTY);
            } else {
                this.carouselLenses[i] = ItemStack.EMPTY;
            }
        }

        // Backward compatibility migration from ancient single InstalledModule tag
        if (tag.contains("InstalledModule", 10) && this.carouselLenses[0].isEmpty()) {
            this.carouselLenses[0] = ItemStack.parse(registries, tag.getCompound("InstalledModule")).orElse(ItemStack.EMPTY);
        }

        if (tag.contains("InstalledFilmReel", 10)) {
            this.installedFilmReel = ItemStack.parse(registries, tag.getCompound("InstalledFilmReel")).orElse(ItemStack.EMPTY);
        } else {
            this.installedFilmReel = ItemStack.EMPTY;
        }

        if (!clientPacket && tag.contains("LightPositions")) {
            long[] posArray = tag.getLongArray("LightPositions");
            this.currentLightPositions.clear();
            for (long l : posArray) {
                this.currentLightPositions.add(BlockPos.of(l));
            }
        }
    }

    public boolean hasBeam() {
        return this.hasBeam;
    }

    public float getCachedSpeed() {
        return this.cachedSpeed;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        tooltip.add(Component.literal("     ")
                .append(Component.translatable("gui.create_optical_diffuser.goggles.title").withStyle(ChatFormatting.GRAY)));

        tooltip.add(Component.literal("  ")
                .append(Component.translatable("gui.create_optical_diffuser.goggles.status").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("    ")
                .append(this.isActive()
                        ? Component.translatable("gui.create_optical_diffuser.goggles.status_active").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("gui.create_optical_diffuser.goggles.status_standby").withStyle(ChatFormatting.RED)));

        DiffuserModule mod = getActiveModule();
        tooltip.add(Component.literal("  ")
                .append(Component.translatable("gui.create_optical_diffuser.goggles.module").withStyle(ChatFormatting.GRAY)));
        ItemStack activeLens = getActiveLens();
        if (!activeLens.isEmpty()) {
            ChatFormatting col = switch (mod) {
                case FOCUSER -> ChatFormatting.GOLD;
                case CONDENSER -> ChatFormatting.AQUA;
                case PROJECTOR_SILENT -> ChatFormatting.WHITE;
                case PROJECTOR_SOUND -> ChatFormatting.LIGHT_PURPLE;
                case HOLOGRAM -> ChatFormatting.DARK_AQUA;
                case THERMAL_IGNITER -> ChatFormatting.RED;
                case COLLIMATOR -> ChatFormatting.DARK_RED;
                default -> ChatFormatting.YELLOW;
            };
            tooltip.add(Component.literal("    ")
                    .append(activeLens.getHoverName().copy().withStyle(col)));
        } else if (mod == DiffuserModule.FOCUSER) {
            tooltip.add(Component.literal("    ")
                    .append(Component.literal("Beam Focuser (Focusing)").withStyle(ChatFormatting.GOLD)));
        } else if (mod == DiffuserModule.CONDENSER) {
            tooltip.add(Component.literal("    ")
                    .append(Component.literal("Beam Condenser (Dispersing)").withStyle(ChatFormatting.AQUA)));
        } else {
            tooltip.add(Component.literal("    ")
                    .append(Component.translatable("gui.create_optical_diffuser.goggles.module_none").withStyle(ChatFormatting.DARK_GRAY)));
        }

        if (hasAnyCarouselLens()) {
            int activeSlot = getActiveCarouselSlot();
            tooltip.add(Component.literal("  ")
                    .append(Component.literal("Carousel Slot: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("#" + (activeSlot + 1) + " (Active)").withStyle(ChatFormatting.GOLD)));
        }

        com.mik.optical_diffuser.block.entity.FilmReelBlockEntity connectedReel = getConnectedFilmReel();
        if (connectedReel != null) {
            String url = connectedReel.getUrl();
            String disp = url.isEmpty() ? "Empty (Right-Click to edit)" : (url.length() > 28 ? url.substring(0, 25) + "..." : url);
            tooltip.add(Component.literal("  ")
                    .append(Component.literal("Film Reel: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(disp).withStyle(ChatFormatting.GOLD)));

            long timeMs = connectedReel.getCurrentTimeMs();
            if (timeMs > 0) {
                long seconds = (timeMs / 1000) % 60;
                long minutes = (timeMs / (1000 * 60)) % 60;
                long hours = timeMs / (1000 * 60 * 60);
                String timeStr = hours > 0
                        ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                        : String.format("%02d:%02d", minutes, seconds);
                tooltip.add(Component.literal("  ")
                        .append(Component.literal("Film Time: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(timeStr).withStyle(ChatFormatting.AQUA)));
            }
            tooltip.add(Component.literal("  ")
                    .append(Component.literal("Film Speed: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.0f RPM", connectedReel.getSpeed())).withStyle(ChatFormatting.YELLOW)));
        } else if (!this.installedFilmReel.isEmpty()) {
            String url = com.mik.optical_diffuser.item.FilmReelBlockItem.getUrl(this.installedFilmReel);
            String disp = url.isEmpty() ? "Empty" : (url.length() > 28 ? url.substring(0, 25) + "..." : url);
            tooltip.add(Component.literal("  ")
                    .append(Component.literal("Film Reel: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(disp).withStyle(ChatFormatting.GOLD)));
        }

        boolean isProjectorModule = (mod == DiffuserModule.PROJECTOR_SILENT || mod == DiffuserModule.PROJECTOR_SOUND);
        if (isProjectorModule && !activeLens.isEmpty()) {
            Direction facing = getBlockState().getValue(OpticalDiffuserBlock.FACING);
            com.mik.optical_diffuser.item.ProjectorLensItem.PlacementCheck check =
                    com.mik.optical_diffuser.item.ProjectorLensItem.checkPlacement(level, getBlockPos(), facing, activeLens);

            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("gui.create_optical_diffuser.goggles.projection_distance").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format(" %.1fm / 48m", check.distance()))
                            .withStyle(check.distance() >= 3.0 && check.distance() <= 48.0 ? ChatFormatting.AQUA : ChatFormatting.RED)));

            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("gui.create_optical_diffuser.goggles.beam_angle").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format(" %.1f° / 45°", check.coneAngleDeg()))
                            .withStyle(check.coneAngleDeg() <= 45.0 ? ChatFormatting.AQUA : ChatFormatting.RED)));

            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("gui.create_optical_diffuser.goggles.screen_tilt").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format(" %.1f° / 60°", check.tiltAngleDeg()))
                            .withStyle(check.tiltAngleDeg() <= 60.0 ? ChatFormatting.AQUA : ChatFormatting.RED)));

            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("gui.create_optical_diffuser.goggles.alignment").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" "))
                    .append(check.status().getDisplayMessage()));
        }

        if (mod == DiffuserModule.HOLOGRAM && !activeLens.isEmpty()) {
            int dist = com.mik.optical_diffuser.item.HologramLensItem.getFocalDistance(activeLens);
            float scale = com.mik.optical_diffuser.item.HologramLensItem.getHoloScale(activeLens);
            ItemStack target = level != null ? com.mik.optical_diffuser.item.HologramLensItem.getHoloTarget(activeLens, level.registryAccess()) : ItemStack.EMPTY;
            String targetName = !target.isEmpty() ? target.getHoverName().getString() : "Navigational Gyroscope";

            tooltip.add(Component.literal("  ")
                    .append(Component.literal("Hologram Mode: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(targetName).withStyle(ChatFormatting.AQUA)));
            tooltip.add(Component.literal("  ")
                    .append(Component.literal("Throw Distance: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(dist + "m").withStyle(ChatFormatting.GOLD)));
            tooltip.add(Component.literal("  ")
                    .append(Component.literal("Scale: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.1fx", scale)).withStyle(ChatFormatting.YELLOW)));
            tooltip.add(Component.literal("  ")
                    .append(Component.literal("Rotation: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.0f RPM", this.cachedSpeed)).withStyle(ChatFormatting.GREEN)));
        }

        if (getActiveModule() == DiffuserModule.PROJECTOR_SOUND) {
            if (this.redstonePower >= 15) {
                tooltip.add(Component.literal("  ")
                        .append(Component.translatable("gui.create_optical_diffuser.goggles.redstone").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(" 15/15 (Mute)").withStyle(ChatFormatting.RED)));
            } else if (this.redstonePower == 0) {
                tooltip.add(Component.literal("  ")
                        .append(Component.translatable("gui.create_optical_diffuser.goggles.redstone").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(" 0/15 (Volume: 100%)").withStyle(ChatFormatting.YELLOW)));
            } else {
                int vol = Math.round(getRedstoneVolumeFactor() * 100);
                tooltip.add(Component.literal("  ")
                        .append(Component.translatable("gui.create_optical_diffuser.goggles.redstone").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format(" %d/15 (Volume: %d%%)", this.redstonePower, vol)).withStyle(ChatFormatting.YELLOW)));
            }
        } else {
            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("gui.create_optical_diffuser.goggles.redstone").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" " + this.redstonePower + "/15").withStyle(this.redstonePower == 15 ? ChatFormatting.RED : ChatFormatting.YELLOW)));
        }

        if (this.hasBeam) {
            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("gui.create_optical_diffuser.goggles.beam_type").withStyle(ChatFormatting.GRAY)));
            tooltip.add(Component.literal("    ")
                    .append(Component.literal(this.beamType.name()).withStyle(ChatFormatting.AQUA)));

            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("gui.create_optical_diffuser.goggles.range").withStyle(ChatFormatting.GRAY)));
            tooltip.add(Component.literal("    ")
                    .append(Component.literal(this.coneRange + " blocks").withStyle(ChatFormatting.AQUA)));

            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("gui.create_optical_diffuser.goggles.polarization").withStyle(ChatFormatting.GRAY)));
            tooltip.add(Component.literal("    ")
                    .append(Component.literal(this.polarization.name()).withStyle(ChatFormatting.AQUA)));

            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("gui.create_optical_diffuser.goggles.spread").withStyle(ChatFormatting.GRAY)));
            tooltip.add(Component.literal("    ")
                    .append(Component.literal((int) this.coneAngle + "°").withStyle(ChatFormatting.AQUA)));

            if (this.beamType == BeamHelper.BeamType.GAMMA) {
                boolean shielded = this.isFiltered;
                if (!shielded && level != null) {
                    Direction facing = getBlockState().getValue(OpticalDiffuserBlock.FACING);
                    shielded = checkLeadGlassFilter(level, worldPosition, facing, Vec3.atCenterOf(worldPosition), Vec3.atLowerCornerOf(facing.getNormal()));
                }
                tooltip.add(Component.literal("  ")
                        .append(Component.translatable("gui.create_optical_diffuser.goggles.filter").withStyle(ChatFormatting.GRAY)));
                tooltip.add(Component.literal("    ")
                        .append(shielded
                                ? Component.translatable("gui.create_optical_diffuser.goggles.filter_active").withStyle(ChatFormatting.GREEN)
                                : Component.translatable("gui.create_optical_diffuser.goggles.filter_none").withStyle(ChatFormatting.RED)));
            }

            if (this.cachedSpeed > 0) {
                tooltip.add(Component.literal("  ")
                        .append(Component.translatable("gui.create_optical_diffuser.goggles.input_speed").withStyle(ChatFormatting.GRAY)));
                tooltip.add(Component.literal("    ")
                        .append(Component.literal((int) this.cachedSpeed + " RPM").withStyle(ChatFormatting.AQUA)));
            }
        } else {
            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("gui.create_optical_diffuser.goggles.no_beam").withStyle(ChatFormatting.DARK_GRAY)));
        }

        return true;
    }
}
