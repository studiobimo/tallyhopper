package dev.bimo.tallyhopper.block;

import dev.bimo.tallyhopper.credit.StoredBacklog;
import dev.bimo.tallyhopper.platform.Services;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A hopper that measures what passes through it. Everything a hopper does is inherited unchanged.
 *
 * <p>{@link #READY} drives the clock face: calibrating until the rate is measured, then ready.
 */
public final class TallyHopperBlock extends HopperBlock {

    public static final BooleanProperty READY = BooleanProperty.create("ready");

    public TallyHopperBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(READY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(READY);
    }

    /**
     * In creative, breaking drops nothing, so a hopper with a backlog drops itself carrying it, the way
     * a shulker box with contents does. Its five slots spill as usual.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()
                && player.preventsBlockDrops()
                && level.getBlockEntity(pos) instanceof TallyHopperBlockEntity hopper) {
            StoredBacklog backlog = hopper.storedBacklog();
            if (backlog != null) {
                ItemStack stack = new ItemStack(this);
                stack.set(TallyHopperContent.backlogComponent(), backlog);
                stack.set(DataComponents.CUSTOM_NAME, hopper.getCustomName());
                ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                entity.setDefaultPickUpDelay();
                level.addFreshEntity(entity);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Opens the Tally Hopper's own screen instead of the vanilla hopper one. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof TallyHopperBlockEntity hopper) {
            Services.MENUS.open(serverPlayer, hopper);
            player.awardStat(Stats.INSPECT_HOPPER);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TallyHopperBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? null
                : createTickerHelper(type, TallyHopperContent.blockEntityType(), TallyHopperBlockEntity::serverTick);
    }
}
