package dev.bimo.tallyhopper.block;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.credit.CreditReport;
import dev.bimo.tallyhopper.credit.Ledger;
import dev.bimo.tallyhopper.measure.Measurement;
import dev.bimo.tallyhopper.measure.MeasurementClock;
import dev.bimo.tallyhopper.offline.Backlog;
import dev.bimo.tallyhopper.offline.SaturatingMath;
import dev.bimo.tallyhopper.platform.Services;
import dev.bimo.tallyhopper.platform.services.ItemSinks;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import dev.bimo.tallyhopper.session.OfflineSession;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

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
    private final Ledger ledger = new Ledger();

    public TallyHopperBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    /**
     * Credits the session once, refills the visible slots from the backlog, runs the vanilla hopper
     * tick, then measures.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, TallyHopperBlockEntity hopper) {
        OfflineSession.Current session = OfflineSession.current();
        if (session != null && hopper.ledger.needsCredit(session)) {
            hopper.credit(session);
        }
        hopper.refillFromBacklog();
        HopperBlockEntity.pushItemsTick(level, pos, state, hopper);

        Instant now = MeasurementClock.now(level);
        if (hopper.isMeasuring()) {
            hopper.measurement.tick(now);
        }
        // Before a session starts there is no window to be eligible for, so the last tick waits too.
        if (session != null && hopper.ledger.ticked(now)) {
            hopper.setChanged();
        }
        if (level.getGameTime() % READY_CHECK_TICKS == 0) {
            hopper.updateClockFace(level, pos);
        }
    }

    /** Called for every automated insert; see {@code HopperBlockEntityMixin}. */
    public void recordIntake(ItemStack stack, int count) {
        Level level = getLevel();
        if (level != null && !level.isClientSide() && isMeasuring() && Measurement.isCountable(stack)) {
            measurement.recordIntake(stack.getItem(), count, MeasurementClock.now(level));
        }
    }

    /**
     * Whether the hopper is measuring its farm. While the backlog drains, backlogged items share the
     * slots with farm output and slow its intake, so measuring would understate the farm; the rate
     * measured before stays in place until the backlog is empty.
     */
    public boolean isMeasuring() {
        return ledger.backlog().isEmpty();
    }

    /**
     * Credits this hopper for {@code session}'s offline window at its effective rates. Called once per
     * session, on the hopper's first tick.
     *
     * <p>In terminal mode the credit, and any backlog left from before, goes straight into the storage
     * the hopper faces; the rest goes to the backlog.
     */
    public CreditReport credit(OfflineSession.Current session) {
        Map<Item, Long> earned = ledger.earn(session, measurement.effectiveRates());
        setChanged();
        if (earned.isEmpty()) {
            return CreditReport.NONE;
        }
        Backlog<Item> backlog = ledger.backlog();
        Map<Item, Long> remainder = earned;
        long delivered = 0;
        ItemSinks.@Nullable ItemSink sink = getLevel() instanceof ServerLevel level ? terminalSink(level) : null;
        if (sink != null) {
            backlog.contents().forEach((item, count) -> backlog.take(item, sink.insert(item, count)));
            remainder = new LinkedHashMap<>();
            for (Map.Entry<Item, Long> entry : earned.entrySet()) {
                long inserted = sink.insert(entry.getKey(), entry.getValue());
                delivered = SaturatingMath.add(delivered, inserted);
                if (inserted < entry.getValue()) {
                    remainder.put(entry.getKey(), entry.getValue() - inserted);
                }
            }
        }
        long left = 0;
        for (long count : remainder.values()) {
            left = SaturatingMath.add(left, count);
        }
        long refused = backlog.addAll(remainder);
        CreditReport report = new CreditReport(earned, delivered, left - refused, refused);
        TallyHopper.LOG.info("Tally Hopper at {} credited {}", getBlockPos().toShortString(), report);
        return report;
    }

    /**
     * The storage credit is bulk-filled into, or {@code null} in line mode.
     *
     * <p>A hopper that faces another hopper feeds a line, such as an item sorter. Filling that hopper
     * in bulk would jam the sorter's filters, so credit waits in the backlog and flows down the line at
     * vanilla speed. A hopper facing no storage at all works the same way.
     */
    public ItemSinks.@Nullable ItemSink terminalSink(ServerLevel level) {
        Direction facing = getBlockState().getValue(HopperBlock.FACING);
        BlockPos target = getBlockPos().relative(facing);
        if (level.getBlockEntity(target) instanceof HopperBlockEntity) {
            return null;
        }
        return Services.ITEM_SINKS.find(level, target, facing.getOpposite());
    }

    public Ledger ledger() {
        return ledger;
    }

    /**
     * Tops up the five visible slots from the backlog, so anything that pulls from or is pushed to by
     * this hopper drains it at vanilla speed. Only adds: slots holding other items are left alone.
     */
    private void refillFromBacklog() {
        Backlog<Item> backlog = ledger.backlog();
        NonNullList<ItemStack> items = getItems();
        boolean changed = false;
        for (int slot = 0; slot < items.size() && !backlog.isEmpty(); slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                Item next = Objects.requireNonNull(backlog.peek());
                ItemStack fresh = new ItemStack(next);
                fresh.setCount((int) backlog.take(next, getMaxStackSize(fresh)));
                items.set(slot, fresh);
                changed = true;
            } else if (Measurement.isCountable(stack)) {
                int room = getMaxStackSize(stack) - stack.getCount();
                long taken = room > 0 ? backlog.take(stack.getItem(), room) : 0;
                if (taken > 0) {
                    stack.grow((int) taken);
                    changed = true;
                }
            }
        }
        if (changed) {
            setChanged();
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
        ledger.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        measurement.save(output);
        ledger.save(output);
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
