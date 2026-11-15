package com.mik.optical_diffuser;

import com.mik.optical_diffuser.block.OpticalDiffuserBlock;
import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mik.optical_diffuser.menu.OpticalDiffuserMenu;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(OpticalDiffuserMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OpticalDiffuserMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, OpticalDiffuserMod.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, OpticalDiffuserMod.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, OpticalDiffuserMod.MOD_ID);
    public static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, OpticalDiffuserMod.MOD_ID);

    public static final DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> CARAMELLDANSEN_SOUND = SOUND_EVENTS.register(
            "ponder_caramelldansen",
            () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(OpticalDiffuserMod.MOD_ID, "ponder_caramelldansen")
            )
    );

    public static final DeferredBlock<OpticalDiffuserBlock> OPTICAL_DIFFUSER = BLOCKS.register("optical_diffuser",
            () -> new OpticalDiffuserBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(3.0F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(OpticalDiffuserBlock.BEAMING) ? 15 : 0)
            )
    );

    public static final DeferredItem<BlockItem> OPTICAL_DIFFUSER_ITEM = ITEMS.registerSimpleBlockItem("optical_diffuser", OPTICAL_DIFFUSER);

    public static final DeferredBlock<com.mik.optical_diffuser.block.LeadGlassBlock> LEAD_GLASS = BLOCKS.register("lead_glass",
            () -> new com.mik.optical_diffuser.block.LeadGlassBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.5F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isValidSpawn((state, getter, pos, entityType) -> false)
                    .isRedstoneConductor((state, getter, pos) -> false)
                    .isSuffocating((state, getter, pos) -> false)
                    .isViewBlocking((state, getter, pos) -> false)
            )
    );

    public static final DeferredItem<BlockItem> LEAD_GLASS_ITEM = ITEMS.registerSimpleBlockItem("lead_glass", LEAD_GLASS);

    public static final DeferredBlock<com.mik.optical_diffuser.block.SpeakerBlock> SPEAKER = BLOCKS.register("speaker",
            () -> new com.mik.optical_diffuser.block.SpeakerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(2.0F)
                    .sound(SoundType.WOOD)
            )
    );

    public static final DeferredItem<com.mik.optical_diffuser.item.SpeakerBlockItem> SPEAKER_ITEM = ITEMS.register("speaker",
            () -> new com.mik.optical_diffuser.item.SpeakerBlockItem(SPEAKER.get(), new Item.Properties().stacksTo(64))
    );

    public static final DeferredItem<Item> FOCUSING_LENS = ITEMS.register("focusing_lens",
            () -> new Item(new Item.Properties().stacksTo(16))
    );

    public static final DeferredItem<Item> DISPERSING_LENS = ITEMS.register("dispersing_lens",
            () -> new Item(new Item.Properties().stacksTo(16))
    );

    public static final DeferredBlock<com.mik.optical_diffuser.block.FilmReelBlock> FILM_REEL_BLOCK = BLOCKS.register("film_reel",
            () -> new com.mik.optical_diffuser.block.FilmReelBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(2.0F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion()
            )
    );

    public static final DeferredItem<com.mik.optical_diffuser.item.FilmReelBlockItem> FILM_REEL = ITEMS.register("film_reel",
            () -> new com.mik.optical_diffuser.item.FilmReelBlockItem(FILM_REEL_BLOCK.get(), new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> FILM_STRIP = ITEMS.register("film_strip",
            () -> new Item(new Item.Properties().stacksTo(64))
    );

    public static final DeferredItem<com.mik.optical_diffuser.item.ProjectorLensItem> SILENT_PROJECTOR_LENS = ITEMS.register("silent_projector_lens",
            () -> new com.mik.optical_diffuser.item.ProjectorLensItem(new Item.Properties().stacksTo(1), false)
    );

    public static final DeferredItem<com.mik.optical_diffuser.item.ProjectorLensItem> SOUND_PROJECTOR_LENS = ITEMS.register("sound_projector_lens",
            () -> new com.mik.optical_diffuser.item.ProjectorLensItem(new Item.Properties().stacksTo(1), true)
    );

    public static final DeferredItem<com.mik.optical_diffuser.item.HologramLensItem> HOLOGRAM_LENS = ITEMS.register("hologram_lens",
            () -> new com.mik.optical_diffuser.item.HologramLensItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_FOCUSING_LENS = ITEMS.register("incomplete_focusing_lens",
            () -> new SequencedAssemblyItem(new Item.Properties().stacksTo(16))
    );

    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_DISPERSING_LENS = ITEMS.register("incomplete_dispersing_lens",
            () -> new SequencedAssemblyItem(new Item.Properties().stacksTo(16))
    );

    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_SILENT_PROJECTOR_LENS = ITEMS.register("incomplete_silent_projector_lens",
            () -> new SequencedAssemblyItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_SOUND_PROJECTOR_LENS = ITEMS.register("incomplete_sound_projector_lens",
            () -> new SequencedAssemblyItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_FILM_REEL = ITEMS.register("incomplete_film_reel",
            () -> new SequencedAssemblyItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_HOLOGRAM_LENS = ITEMS.register("incomplete_hologram_lens",
            () -> new SequencedAssemblyItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<Item> THERMAL_IGNITION_LENS = ITEMS.register("thermal_ignition_lens",
            () -> new Item(new Item.Properties().stacksTo(16))
    );

    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_THERMAL_IGNITION_LENS = ITEMS.register("incomplete_thermal_ignition_lens",
            () -> new SequencedAssemblyItem(new Item.Properties().stacksTo(16))
    );

    public static final DeferredItem<Item> COLLIMATOR_LENS = ITEMS.register("collimator_lens",
            () -> new Item(new Item.Properties().stacksTo(16))
    );

    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_COLLIMATOR_LENS = ITEMS.register("incomplete_collimator_lens",
            () -> new SequencedAssemblyItem(new Item.Properties().stacksTo(16))
    );

    public static final Supplier<BlockEntityType<OpticalDiffuserBlockEntity>> OPTICAL_DIFFUSER_BE = BLOCK_ENTITY_TYPES.register("optical_diffuser",
            () -> BlockEntityType.Builder.of(OpticalDiffuserBlockEntity::new, OPTICAL_DIFFUSER.get()).build(null)
    );

    public static final Supplier<BlockEntityType<com.mik.optical_diffuser.block.entity.FilmReelBlockEntity>> FILM_REEL_BE = BLOCK_ENTITY_TYPES.register("film_reel",
            () -> BlockEntityType.Builder.of(com.mik.optical_diffuser.block.entity.FilmReelBlockEntity::new, FILM_REEL_BLOCK.get()).build(null)
    );

    public static final Supplier<BlockEntityType<com.mik.optical_diffuser.block.entity.SpeakerBlockEntity>> SPEAKER_BE = BLOCK_ENTITY_TYPES.register("speaker",
            () -> BlockEntityType.Builder.of(com.mik.optical_diffuser.block.entity.SpeakerBlockEntity::new, SPEAKER.get()).build(null)
    );

    public static final DeferredHolder<MenuType<?>, MenuType<OpticalDiffuserMenu>> OPTICAL_DIFFUSER_MENU = MENUS.register("optical_diffuser",
            () -> IMenuTypeExtension.create(OpticalDiffuserMenu::new)
    );

    public static final Supplier<net.minecraft.world.item.crafting.RecipeSerializer<com.mik.optical_diffuser.recipe.ClearSpeakerRecipe>> CLEAR_SPEAKER_RECIPE_SERIALIZER =
            RECIPE_SERIALIZERS.register("clear_speaker", () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(com.mik.optical_diffuser.recipe.ClearSpeakerRecipe::new));
}
