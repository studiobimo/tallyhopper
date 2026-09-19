package dev.bimo.tallyhopper.registry;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.block.TallyHopperBlock;
import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.credit.StoredBacklog;
import dev.bimo.tallyhopper.gui.TallyHopperMenu;
import dev.bimo.tallyhopper.item.TallyHopperItem;
import dev.bimo.tallyhopper.platform.Services;
import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * The mod's block, item, block entity type and backlog component.
 *
 * <p>Each loader calls the {@code create…} methods from inside its registration phase, in the order
 * block, item, block entity type, and registers the result under the matching key. It also
 * registers {@link dev.bimo.tallyhopper.crafting.ShapelessKeepRecipe#SERIALIZER} under
 * {@link #SHAPELESS_KEEP_KEY}, and the backlog component from {@link #createBacklogComponent()} under
 * {@link #BACKLOG_COMPONENT_KEY}. Objects are
 * created there, not in static initializers, because NeoForge only allows registry objects to be
 * built while its registries are open.
 */
public final class TallyHopperContent {

    public static final Identifier TALLY_HOPPER = Identifier.fromNamespaceAndPath(TallyHopper.MOD_ID, "tally_hopper");
    public static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(Registries.BLOCK, TALLY_HOPPER);
    public static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, TALLY_HOPPER);
    public static final ResourceKey<BlockEntityType<?>> BLOCK_ENTITY_KEY =
            ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, TALLY_HOPPER);
    public static final ResourceKey<RecipeSerializer<?>> SHAPELESS_KEEP_KEY = ResourceKey.create(
            Registries.RECIPE_SERIALIZER,
            Identifier.fromNamespaceAndPath(TallyHopper.MOD_ID, "crafting_shapeless_keep"));
    public static final ResourceKey<DataComponentType<?>> BACKLOG_COMPONENT_KEY = ResourceKey.create(
            Registries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(TallyHopper.MOD_ID, "backlog"));

    public static final ResourceKey<MenuType<?>> MENU_KEY = ResourceKey.create(Registries.MENU, TALLY_HOPPER);

    /** Awarded the first time a hopper credits offline time. */
    public static final Identifier SLEEP_MODE_ADVANCEMENT =
            Identifier.fromNamespaceAndPath(TallyHopper.MOD_ID, "sleep_mode");

    /** Vanilla keeps its own copy of this key private. */
    public static final ResourceKey<CreativeModeTab> REDSTONE_BLOCKS_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("redstone_blocks"));

    private static @Nullable Block block;
    private static @Nullable Item item;
    private static @Nullable BlockEntityType<TallyHopperBlockEntity> blockEntityType;
    private static @Nullable DataComponentType<StoredBacklog> backlogComponent;
    private static @Nullable MenuType<TallyHopperMenu> menuType;

    private TallyHopperContent() {}

    /** Same properties as the vanilla hopper. */
    public static Block createBlock() {
        Block created = new TallyHopperBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .requiresCorrectToolForDrops()
                .strength(3.0F, 4.8F)
                .sound(SoundType.METAL)
                .noOcclusion()
                .setId(BLOCK_KEY));
        block = created;
        return created;
    }

    public static Item createItem() {
        BlockItem created = new TallyHopperItem(
                block(),
                new Item.Properties()
                        .component(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                        .useBlockDescriptionPrefix()
                        .setId(ITEM_KEY));
        // Vanilla does this in Items.registerItem; it makes block.asItem() work. Repeating it is harmless.
        created.registerBlocks(Item.BY_BLOCK, created);
        item = created;
        return created;
    }

    public static BlockEntityType<TallyHopperBlockEntity> createBlockEntityType() {
        BlockEntityType<TallyHopperBlockEntity> created =
                new BlockEntityType<>(TallyHopperBlockEntity::new, Set.of(block()));
        blockEntityType = created;
        return created;
    }

    public static DataComponentType<StoredBacklog> createBacklogComponent() {
        DataComponentType<StoredBacklog> created = DataComponentType.<StoredBacklog>builder()
                .persistent(StoredBacklog.CODEC)
                .networkSynchronized(StoredBacklog.STREAM_CODEC)
                .build();
        backlogComponent = created;
        return created;
    }

    /** Loader-specific, because the position has to travel with the screen; see {@code MenuTypes}. */
    public static MenuType<TallyHopperMenu> createMenuType() {
        MenuType<TallyHopperMenu> created = Services.MENUS.createMenuType();
        menuType = created;
        return created;
    }

    public static Block block() {
        return registered(block, "block");
    }

    public static Item item() {
        return registered(item, "item");
    }

    public static BlockEntityType<TallyHopperBlockEntity> blockEntityType() {
        return registered(blockEntityType, "block entity type");
    }

    public static MenuType<TallyHopperMenu> menuType() {
        return registered(menuType, "menu type");
    }

    public static DataComponentType<StoredBacklog> backlogComponent() {
        return registered(backlogComponent, "backlog component");
    }

    private static <T> T registered(@Nullable T value, String what) {
        if (value == null) {
            throw new IllegalStateException("Tally Hopper " + what + " used before registration");
        }
        return value;
    }
}
