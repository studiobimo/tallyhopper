package dev.bimo.tallyhopper.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

/**
 * A vanilla shapeless recipe that hands one ingredient back instead of using it up.
 *
 * <p>The Tally Hopper recipe needs the clock as a tool, not a cost: it stays in the grid after
 * crafting. The JSON is a normal {@code crafting_shapeless} recipe plus a {@code kept} ingredient.
 * Everything else, including how the recipe book shows it, is the wrapped vanilla recipe.
 */
public record ShapelessKeepRecipe(ShapelessRecipe base, Ingredient kept) implements CraftingRecipe {

    public static final MapCodec<ShapelessKeepRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    ShapelessRecipe.MAP_CODEC.forGetter(ShapelessKeepRecipe::base),
                    Ingredient.CODEC.fieldOf("kept").forGetter(ShapelessKeepRecipe::kept))
            .apply(i, ShapelessKeepRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShapelessKeepRecipe> STREAM_CODEC = StreamCodec.composite(
            ShapelessRecipe.STREAM_CODEC,
            ShapelessKeepRecipe::base,
            Ingredient.CONTENTS_STREAM_CODEC,
            ShapelessKeepRecipe::kept,
            ShapelessKeepRecipe::new);
    public static final RecipeSerializer<ShapelessKeepRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = base.getRemainingItems(input);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (!stack.isEmpty() && kept.test(stack)) {
                // A copy of the exact stack, so a renamed clock comes back renamed.
                remaining.set(slot, stack.copyWithCount(1));
            }
        }
        return remaining;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return base.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return base.assemble(input);
    }

    @Override
    public RecipeSerializer<ShapelessKeepRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public CraftingBookCategory category() {
        return base.category();
    }

    @Override
    public boolean showNotification() {
        return base.showNotification();
    }

    @Override
    public String group() {
        return base.group();
    }

    @Override
    public PlacementInfo placementInfo() {
        return base.placementInfo();
    }

    @Override
    public List<RecipeDisplay> display() {
        return base.display();
    }
}
