package dev.bimo.tallyhopper.fabric.datagen;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;

/** Generates the mod's recipes, loot table and tags into {@code common/src/generated}. */
public final class TallyHopperDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(Recipes::new);
        pack.addProvider(LootTables::new);
        pack.addProvider(BlockTagProvider::new);
    }

    // This entrypoint lives in a separate datagen mod; generate files for the real one.
    @Override
    public String getEffectiveModId() {
        return TallyHopper.MOD_ID;
    }

    private static final class Recipes extends FabricRecipeProvider {

        Recipes(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(
                HolderLookup.Provider registries,
                BootstrapContext<Recipe<?>> recipes,
                BootstrapContext<Advancement> advancements) {
            return new RecipeProvider(recipes, advancements) {
                @Override
                public void buildRecipes() {
                    shapeless(RecipeCategory.REDSTONE, TallyHopperContent.item())
                            .requires(Items.HOPPER)
                            .requires(Items.CLOCK)
                            .unlockedBy(getHasName(Items.HOPPER), has(Items.HOPPER))
                            .save(output);
                    // The clock comes back as the Tally Hopper's crafting remainder.
                    shapeless(RecipeCategory.REDSTONE, Items.HOPPER)
                            .requires(TallyHopperContent.item())
                            .unlockedBy(getHasName(TallyHopperContent.item()), has(TallyHopperContent.item()))
                            .save(
                                    output,
                                    ResourceKey.create(
                                            Registries.RECIPE,
                                            Identifier.fromNamespaceAndPath(
                                                    TallyHopper.MOD_ID, "hopper_from_tally_hopper")));
                }
            };
        }

        @Override
        public String getName() {
            return "Tally Hopper recipes";
        }
    }

    private static final class LootTables extends FabricBlockLootSubProvider {

        LootTables(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        // Same as the vanilla hopper: drops itself and keeps a custom name. Contents spill on break.
        @Override
        public void generate() {
            add(TallyHopperContent.block(), createNameableBlockEntityTable(TallyHopperContent.block()));
        }
    }

    private static final class BlockTagProvider extends FabricTagsProvider.BlockTagsProvider {

        BlockTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            // The block requires the correct tool, so without this it would never drop.
            builder(BlockTags.MINEABLE_WITH_PICKAXE).add(TallyHopperContent.BLOCK_KEY);
        }
    }
}
