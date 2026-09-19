package dev.bimo.tallyhopper.block;

import dev.bimo.tallyhopper.registry.TallyHopperContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TallyHopperBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? null
                : createTickerHelper(type, TallyHopperContent.blockEntityType(), HopperBlockEntity::pushItemsTick);
    }
}
