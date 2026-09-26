package dev.bimo.tallyhopper.block;

import dev.bimo.tallyhopper.conversion.ClockConversion;
import dev.bimo.tallyhopper.credit.StoredBacklog;
import dev.bimo.tallyhopper.platform.Services;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A hopper that measures what passes through it. Everything a hopper does is inherited unchanged.
 *
 * <p>{@link #READY} drives the clock face: calibrating until the rate is measured, then ready.
 * {@link #LIT} drives the observer's lamp beside it, which flashes for each item the hopper counts.
 */
public final class TallyHopperBlock extends HopperBlock {

    public static final BooleanProperty READY = BooleanProperty.create("ready");

    /** Vanilla's own {@code lit}, as a furnace or redstone lamp uses it. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** How long the lamp stays on: the observer's pulse, two game ticks. */
    public static final int FLASH_TICKS = 2;

    public TallyHopperBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(READY, false).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(READY, LIT);
    }

    /**
     * Lights the lamp for {@link #FLASH_TICKS}, the way an observer lights when it sees a change.
     *
     * <p>Kept as cheap as a lamp can be. The change goes to clients only: no neighbour updates, no
     * redstone signal and no world light, since the glow is the model's own {@code light_emission}.
     * A lamp already lit is left alone rather than extended, so a steady stream reads as separate
     * blinks and no hopper changes state more often than every other tick, whatever feeds it.
     */
    public static void flash(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TallyHopperBlock block && !state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, block, FLASH_TICKS);
        }
    }

    /** The end of a flash. Vanilla hoppers never schedule ticks, so this is the only one there is. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Tells a player who has just placed a hopper that feeds another Tally Hopper that it will not earn.
     *
     * <p>Placement is allowed rather than refused. The same arrangement is reachable by placing the two
     * in the other order, or by re-aiming a hopper afterwards, so refusing here would only make the
     * state harder to reach, not impossible — and a mod that quietly refuses to place a block a player
     * is holding is worse than one that explains itself. The chain is walked once, on this placement.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide() && by instanceof Player player && ClockConversion.feedsATallyHopper(level, pos)) {
            player.sendSystemMessage(Component.translatable("message.tallyhopper.placed_passthrough")
                    .withStyle(ChatFormatting.GOLD));
        }
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
