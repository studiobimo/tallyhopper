package dev.bimo.tallyhopper.gametest;

import dev.bimo.tallyhopper.registry.TallyHopperContent;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/** The Tally Hopper recipe as the game loads it: hopper + clock + sapling, clock kept. */
public final class CraftingTests {

    private CraftingTests() {}

    private static Optional<RecipeHolder<CraftingRecipe>> find(GameTestHelper helper, ItemStack... grid) {
        CraftingInput input = CraftingInput.of(grid.length, 1, List.of(grid));
        return helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
    }

    /** Any sapling works; the clock comes back into its grid slot and nothing else does. */
    public static void keepsTheClock(GameTestHelper helper) {
        ItemStack[] grid = {new ItemStack(Items.HOPPER), new ItemStack(Items.CLOCK), new ItemStack(Items.CHERRY_SAPLING)
        };
        CraftingInput input = CraftingInput.of(3, 1, List.of(grid));
        CraftingRecipe recipe = find(helper, grid)
                .orElseThrow(
                        () -> helper.assertionException(Component.literal("hopper + clock + sapling should craft")))
                .value();

        helper.assertTrue(recipe.assemble(input).is(TallyHopperContent.item()), "result is a Tally Hopper");
        NonNullList<ItemStack> remaining = recipe.getRemainingItems(input);
        helper.assertTrue(remaining.get(0).isEmpty(), "the hopper is used");
        helper.assertTrue(remaining.get(1).is(Items.CLOCK), "the clock stays in the grid");
        helper.assertTrue(remaining.get(2).isEmpty(), "the sapling is used");
        helper.succeed();
    }

    /** No sapling, no Tally Hopper; and a Tally Hopper can't be crafted back into a hopper. */
    public static void noOtherRecipes(GameTestHelper helper) {
        helper.assertTrue(
                find(helper, new ItemStack(Items.HOPPER), new ItemStack(Items.CLOCK))
                        .isEmpty(),
                "hopper + clock alone should not craft");
        helper.assertTrue(
                find(helper, new ItemStack(TallyHopperContent.item())).isEmpty(), "a Tally Hopper should not un-craft");
        helper.succeed();
    }
}
