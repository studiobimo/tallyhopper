package dev.bimo.tallyhopper.fabric.datagen;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.crafting.ShapelessKeepRecipe;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.ImpossibleTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.RecipeUnlockAdvancementBuilder;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

public final class TallyHopperDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(Recipes::new);
        pack.addProvider(LootTables::new);
        pack.addProvider(Advancements::new);
        pack.addProvider(BlockTagProvider::new);
    }

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
                    ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, TallyHopperContent.TALLY_HOPPER);
                    ShapelessRecipe base = new ShapelessRecipe(
                            new Recipe.CommonInfo(true),
                            new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.REDSTONE, ""),
                            new ItemStackTemplate(TallyHopperContent.item()),
                            List.of(Ingredient.of(Items.HOPPER), Ingredient.of(Items.CLOCK), tag(ItemTags.SAPLINGS)));
                    RecipeUnlockAdvancementBuilder unlock = new RecipeUnlockAdvancementBuilder();
                    unlock.unlockedBy(getHasName(Items.HOPPER), has(Items.HOPPER));
                    output.accept(
                            key,
                            new ShapelessKeepRecipe(base, Ingredient.of(Items.CLOCK)),
                            unlock.build(output, key, RecipeCategory.REDSTONE));
                }
            };
        }

        @Override
        public String getName() {
            return "Tally Hopper recipes";
        }
    }

    private static final class Advancements extends FabricAdvancementProvider {

        Advancements(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        public void generateAdvancement(HolderLookup.Provider registries, Consumer<AdvancementHolder> consumer) {
            Advancement.Builder.advancement()
                    .rootDisplay(
                            TallyHopperContent.item(),
                            Component.translatable("advancements.tallyhopper.sleep_mode.title"),
                            Component.translatable("advancements.tallyhopper.sleep_mode.description"),
                            Identifier.withDefaultNamespace("textures/block/smooth_stone.png"),
                            AdvancementType.TASK,
                            true,
                            true,
                            false)
                    .addCriterion(
                            "credited",
                            new Criterion<>(CriteriaTriggers.IMPOSSIBLE, new ImpossibleTrigger.TriggerInstance()))
                    .save(consumer, TallyHopperContent.SLEEP_MODE_ADVANCEMENT);
        }
    }

    private static final class LootTables extends FabricBlockLootSubProvider {

        LootTables(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        public void generate() {
            Block block = TallyHopperContent.block();
            add(
                    block,
                    LootTable.lootTable()
                            .withPool(LootPool.lootPool()
                                    .setRolls(ContextIntProviders.exactly(1))
                                    .add(LootItem.lootTableItem(block)
                                            .apply(CopyComponentsFunction.copyComponentsFromBlockEntity(
                                                            LootContextParams.BLOCK_ENTITY)
                                                    .include(DataComponents.CUSTOM_NAME)
                                                    .include(TallyHopperContent.backlogComponent())))));
        }
    }

    private static final class BlockTagProvider extends FabricTagsProvider.BlockTagsProvider {

        BlockTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            builder(BlockTags.MINEABLE_WITH_PICKAXE).add(TallyHopperContent.BLOCK_KEY);
        }
    }
}
