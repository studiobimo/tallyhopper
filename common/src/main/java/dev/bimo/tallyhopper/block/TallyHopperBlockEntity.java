package dev.bimo.tallyhopper.block;

import dev.bimo.tallyhopper.measure.Measurement;
import dev.bimo.tallyhopper.measure.MeasurementClock;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A vanilla hopper block entity under its own type.
 *
 * <p>Vanilla hopper logic is static and checks {@code instanceof HopperBlockEntity}: the transfer
 * cooldown handshake between hoppers, minecarts, comparators and item pickup all rely on it.
 * Extending it keeps every one of those behaviors identical. Vanilla hard-wires the hopper type in
 * the constructor, so the type accessors are overridden to report {@code tallyhopper:tally_hopper}
 * instead, which is what gets saved and synced.
 */
public final class TallyHopperBlockEntity extends HopperBlockEntity {

    private static final Component DEFAULT_NAME = Component.translatable("container.tallyhopper.tally_hopper");

    /** How often the clock face is brought up to date. */
    private static final int READY_CHECK_TICKS = 20;

    private final Measurement measurement = new Measurement();

    public TallyHopperBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    /** Runs the vanilla hopper tick, then measures. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, TallyHopperBlockEntity hopper) {
        HopperBlockEntity.pushItemsTick(level, pos, state, hopper);
        hopper.measurement.tick(MeasurementClock.now(level));
        if (level.getGameTime() % READY_CHECK_TICKS == 0) {
            hopper.updateClockFace(level, pos);
        }
    }

    /** Called for every automated insert; see {@code HopperBlockEntityMixin}. */
    public void recordIntake(ItemStack stack, int count) {
        Level level = getLevel();
        if (level != null && !level.isClientSide() && Measurement.isCountable(stack)) {
            measurement.recordIntake(stack.getItem(), count, MeasurementClock.now(level));
        }
    }

    public Measurement measurement() {
        return measurement;
    }

    /** Changes the overrides, then saves and updates the clock face straight away. */
    public <T> T changeOverrides(Function<Measurement, T> change) {
        T result = change.apply(measurement);
        setChanged();
        Level level = getLevel();
        if (level != null) {
            updateClockFace(level, getBlockPos());
        }
        return result;
    }

    private void updateClockFace(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean ready = measurement.isReady();
        if (state.hasProperty(TallyHopperBlock.READY) && state.getValue(TallyHopperBlock.READY) != ready) {
            level.setBlock(pos, state.setValue(TallyHopperBlock.READY, ready), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        measurement.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        measurement.save(output);
    }

    @Override
    public BlockEntityType<?> getType() {
        return TallyHopperContent.blockEntityType();
    }

    @Override
    public Holder<BlockEntityType<?>> typeHolder() {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.wrapAsHolder(TallyHopperContent.blockEntityType());
    }

    // Called from the super constructor, so it must not touch instance fields.
    @Override
    public boolean isValidBlockState(BlockState state) {
        return TallyHopperContent.blockEntityType().isValid(state);
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }
}
