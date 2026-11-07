package com.mik.optical_diffuser.recipe;

import com.mik.optical_diffuser.ModRegistry;
import com.mik.optical_diffuser.item.SpeakerBlockItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class ClearSpeakerRecipe extends CustomRecipe {

    public ClearSpeakerRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack speaker = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (speaker != null) {
                return false;
            }
            if (stack.is(ModRegistry.SPEAKER_ITEM.get()) && SpeakerBlockItem.getLinkedDiffuserPos(stack) != null) {
                speaker = stack;
            } else {
                return false;
            }
        }
        return speaker != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return new ItemStack(ModRegistry.SPEAKER_ITEM.get());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRegistry.CLEAR_SPEAKER_RECIPE_SERIALIZER.get();
    }
}
