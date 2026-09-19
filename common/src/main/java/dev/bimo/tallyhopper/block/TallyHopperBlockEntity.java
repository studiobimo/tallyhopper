package dev.bimo.tallyhopper.block;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.credit.CreditReport;
import dev.bimo.tallyhopper.credit.Ledger;
import dev.bimo.tallyhopper.credit.StoredBacklog;
import dev.bimo.tallyhopper.gui.GuiState;
import dev.bimo.tallyhopper.measure.Measurement;
import dev.bimo.tallyhopper.measure.MeasurementClock;
import dev.bimo.tallyhopper.offline.Backlog;
import dev.bimo.tallyhopper.offline.SaturatingMath;
import dev.bimo.tallyhopper.platform.Services;
import dev.bimo.tallyhopper.platform.services.ItemSinks;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import dev.bimo.tallyhopper.registry.TallyHopperGameRules;
import dev.bimo.tallyhopper.session.OfflineSession;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
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
import net.minecraft.world.phys.Vec3;
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

    private static final String GUI_STATE_KEY = "tallyhopper_gui";
    private static final String SAPLING_KEY = TallyHopper.MOD_ID + "_sapling";
    private static final String PAID_KEY = TallyHopper.MOD_ID + "_calibration_paid";

    /** How often an open screen is brought up to date. */
    private static final int SYNC_TICKS = 20;

    private final Measurement measurement = new Measurement();
    private final Ledger ledger = new Ledger();

    /**
     * What a calibration run costs, one sapling at a time. It is a container of its own rather than a
     * sixth hopper slot, so every piece of vanilla hopper logic still sees exactly five slots and can
     * never push a sapling out or pull one in.
     */
    private final SimpleContainer saplings = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            TallyHopperBlockEntity.this.setChanged();
        }
    };

    private boolean calibrationPaid;
    private GuiState clientState = GuiState.EMPTY;
    private int viewers;

    public TallyHopperBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    /**
     * Credits the session once, refills the visible slots from the backlog, runs the vanilla hopper
     * tick, then measures.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, TallyHopperBlockEntity hopper) {
        if (level instanceof ServerLevel server) {
            hopper.measurement.setWarmUp(
                    Duration.ofMinutes(server.getGameRules().get(TallyHopperGameRules.MIN_OBSERVATION_MINUTES)));
        }
        OfflineSession.Current session = OfflineSession.current();
        if (session != null && hopper.ledger.needsCredit(session)) {
            hopper.credit(session);
        }
        hopper.refillFromBacklog();
        // Paid for before the vanilla tick, so items taken in on this very tick are already counted.
        if (!hopper.calibrationPaid) {
            hopper.payForCalibration();
        }
        HopperBlockEntity.pushItemsTick(level, pos, state, hopper);

        Instant now = MeasurementClock.now(level);
        if (hopper.isWatching()) {
            hopper.measurement.tick(now);
        }
        // Before a session starts there is no window to be eligible for, so the last tick waits too.
        if (session != null && hopper.ledger.ticked(now)) {
            hopper.setChanged();
        }
        if (level.getGameTime() % READY_CHECK_TICKS == 0) {
            hopper.updateClockFace(level, pos);
        }
        if (hopper.viewers > 0 && level.getGameTime() % SYNC_TICKS == 0) {
            hopper.syncToClients(level, pos);
        }
    }

    /** Called for every automated insert; see {@code HopperBlockEntityMixin}. */
    public void recordIntake(ItemStack stack, int count) {
        Level level = getLevel();
        if (level != null && !level.isClientSide() && isWatching() && Measurement.isCountable(stack)) {
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
     * Whether the hopper is watching its farm right now: it has been paid for and nothing is draining
     * through it. A hopper keeps watching after it is ready, so its rate follows the farm instead of
     * staying at whatever the first few minutes happened to look like.
     */
    public boolean isWatching() {
        return calibrationPaid && isMeasuring();
    }

    /** Whether a sapling has been spent on the measurement this hopper is building. */
    public boolean isCalibrationPaid() {
        return calibrationPaid;
    }

    /** The sapling slot, shown on the screen. It is never part of the hopper's five slots. */
    public SimpleContainer saplings() {
        return saplings;
    }

    /**
     * Measures again from nothing, for a player whose farm changed. Spends one sapling, and does
     * nothing without one.
     *
     * @return whether a sapling was spent and the measurement restarted
     */
    public boolean recalibrate() {
        if (saplings.getItem(0).isEmpty()) {
            return false;
        }
        // The hopper has already paid for the measurement it is about to throw away, so it pays again.
        calibrationPaid = false;
        payForCalibration();
        measurement.restart();
        Level level = getLevel();
        if (level != null) {
            updateClockFace(level, getBlockPos());
            syncToClients(level, getBlockPos());
        }
        return true;
    }

    /** Takes one sapling for the current measurement, if one is there and none has been taken yet. */
    private boolean payForCalibration() {
        if (calibrationPaid) {
            return false;
        }
        ItemStack stack = saplings.getItem(0);
        if (stack.isEmpty()) {
            return false;
        }
        stack.shrink(1);
        saplings.setItem(0, stack.isEmpty() ? ItemStack.EMPTY : stack);
        calibrationPaid = true;
        setChanged();
        return true;
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
        if (getLevel() instanceof ServerLevel level) {
            backlog.setCap(level.getGameRules().get(TallyHopperGameRules.BACKLOG_CAP));
        }
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
        ledger.setLastCredit(delivered + left - refused);
        CreditReport report = new CreditReport(earned, delivered, left - refused, refused);
        TallyHopper.LOG.info("Tally Hopper at {} credited {}", getBlockPos().toShortString(), report);
        OfflineSession.reportCredit(getBlockPos(), report);
        if (getLevel() instanceof ServerLevel level) {
            Vec3 above = Vec3.atCenterOf(getBlockPos()).add(0, 0.5, 0);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, above.x, above.y, above.z, 8, 0.25, 0.25, 0.25, 0);
        }
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

    /** What an open screen shows. Built on the server, read on the client. */
    public GuiState guiState() {
        Level level = getLevel();
        if (level != null && level.isClientSide()) {
            return clientState;
        }
        Map<Item, Long> rates = new LinkedHashMap<>();
        measurement.effectiveRates().forEach((item, rate) -> rates.put(item, Math.round(rate.itemsPerHour())));
        return new GuiState(
                rates,
                List.copyOf(measurement.overrides().keySet()),
                (int) measurement.observed().toSeconds(),
                (int) measurement.warmUp().toSeconds(),
                measurement.isReady(),
                ledger.backlog().total(),
                ledger.lastCredit(),
                level instanceof ServerLevel server && terminalSink(server) != null,
                saplings.getItem(0).getCount(),
                calibrationPaid);
    }

    @Override
    public void startOpen(ContainerUser user) {
        super.startOpen(user);
        viewers++;
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            syncToClients(level, getBlockPos());
        }
    }

    @Override
    public void stopOpen(ContainerUser user) {
        super.stopOpen(user);
        viewers = Math.max(0, viewers - 1);
    }

    private void syncToClients(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Only the screen's summary travels, not the measurement behind it, which is much larger. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.store(GUI_STATE_KEY, GuiState.CODEC, guiState());
        return tag;
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

    /** The backlog as an item component, or {@code null} when there is none to carry. */
    public @Nullable StoredBacklog storedBacklog() {
        Backlog<Item> backlog = ledger.backlog();
        return backlog.isEmpty() ? null : new StoredBacklog(backlog.contents());
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        StoredBacklog stored = components.get(TallyHopperContent.backlogComponent());
        if (stored != null) {
            ledger.restoreBacklog(stored);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(TallyHopperContent.backlogComponent(), storedBacklog());
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        Ledger.discardBacklog(output);
    }

    /**
     * Vanilla spills the five slots for us. The sapling slot is not one of them, so it spills here:
     * nothing a player put into this block may be lost when it breaks.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        Level level = getLevel();
        if (level != null) {
            Containers.dropContents(level, pos, saplings);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        measurement.load(input);
        ledger.load(input);
        saplings.setItem(0, input.read(SAPLING_KEY, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        calibrationPaid = input.getBooleanOr(PAID_KEY, false);
        input.read(GUI_STATE_KEY, GuiState.CODEC).ifPresent(state -> clientState = state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        measurement.save(output);
        ledger.save(output);
        if (!saplings.getItem(0).isEmpty()) {
            output.store(SAPLING_KEY, ItemStack.OPTIONAL_CODEC, saplings.getItem(0));
        }
        output.putBoolean(PAID_KEY, calibrationPaid);
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
