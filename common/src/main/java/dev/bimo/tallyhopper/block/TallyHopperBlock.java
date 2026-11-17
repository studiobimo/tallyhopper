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

public final class TallyHopperBlock extends HopperBlock {

    public static final BooleanProperty READY = BooleanProperty.create("ready");

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

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

    public static void flash(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TallyHopperBlock block && !state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, block, FLASH_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide() && by instanceof Player player && ClockConversion.feedsATallyHopper(level, pos)) {
            player.sendSystemMessage(Component.translatable("message.tallyhopper.placed_passthrough")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

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
