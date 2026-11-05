package com.mik.optical_diffuser;

import com.mik.optical_diffuser.block.OpticalDiffuserBlock;
import com.mik.optical_diffuser.block.entity.OpticalDiffuserBlockEntity;
import com.mik.optical_diffuser.menu.OpticalDiffuserMenu;
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

    public static final DeferredItem<Item> FOCUSING_LENS = ITEMS.register("focusing_lens",
            () -> new Item(new Item.Properties().stacksTo(16))
    );

    public static final DeferredItem<Item> DISPERSING_LENS = ITEMS.register("dispersing_lens",
            () -> new Item(new Item.Properties().stacksTo(16))
    );

    public static final Supplier<BlockEntityType<OpticalDiffuserBlockEntity>> OPTICAL_DIFFUSER_BE = BLOCK_ENTITY_TYPES.register("optical_diffuser",
            () -> BlockEntityType.Builder.of(OpticalDiffuserBlockEntity::new, OPTICAL_DIFFUSER.get()).build(null)
    );

    public static final DeferredHolder<MenuType<?>, MenuType<OpticalDiffuserMenu>> OPTICAL_DIFFUSER_MENU = MENUS.register("optical_diffuser",
            () -> IMenuTypeExtension.create(OpticalDiffuserMenu::new)
    );
}
