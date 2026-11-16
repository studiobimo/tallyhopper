package dev.bimo.tallyhopper.block;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.conversion.ClockConversion;
import dev.bimo.tallyhopper.credit.CreditReport;
import dev.bimo.tallyhopper.credit.Ledger;
import dev.bimo.tallyhopper.credit.StoredBacklog;
import dev.bimo.tallyhopper.gui.GuiState;
import dev.bimo.tallyhopper.measure.Measurement;
import dev.bimo.tallyhopper.measure.MeasurementClock;
import dev.bimo.tallyhopper.offline.Backlog;
import dev.bimo.tallyhopper.offline.Rate;
import dev.bimo.tallyhopper.offline.RateBounds;
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
import java.util.function.BooleanSupplier;
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

public final class TallyHopperBlockEntity extends HopperBlockEntity {

    private static final Component DEFAULT_NAME = Component.translatable("container.tallyhopper.tally_hopper");

    private static final int READY_CHECK_TICKS = 20;

    private static final String GUI_STATE_KEY = "tallyhopper_gui";
    private static final String SAPLING_KEY = TallyHopper.MOD_ID + "_sapling";
    private static final String PAID_KEY = TallyHopper.MOD_ID + "_calibration_paid";

    private static final int SYNC_TICKS = 20;

    private final Measurement measurement = new Measurement();
    private final Ledger ledger = new Ledger();

    private final SimpleContainer saplings = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            TallyHopperBlockEntity.this.setChanged();
        }
    };

    private boolean calibrationPaid;

    private boolean countingIntake = true;

    private GuiState clientState = GuiState.EMPTY;
    private int viewers;

    public TallyHopperBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

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
        if (!hopper.calibrationPaid) {
            hopper.payForCalibration();
        }
        HopperBlockEntity.pushItemsTick(level, pos, state, hopper);

        Instant now = MeasurementClock.now(level);
        if (hopper.isWatching()) {
            hopper.measurement.tick(now);
        }
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

    public void recordIntake(ItemStack stack, int count) {
        Level level = getLevel();
        if (level != null
                && !level.isClientSide()
                && countingIntake
                && isWatching()
                && Measurement.isCountable(stack)) {
            measurement.recordIntake(stack.getItem(), count, MeasurementClock.now(level));
            TallyHopperBlock.flash(level, getBlockPos());
        }
    }

    /**
     * Runs {@code intake} without measuring what it brings in, for a stack a player threw by hand.
     * Such a stack is still taken in, so the hopper behaves exactly as a vanilla one would; it just
     * isn't farm output, and crediting it offline would hand back items nothing ever made.
     *
     * @see dev.bimo.tallyhopper.mixin.HopperBlockEntityMixin
     */
    public boolean intakeWithoutCounting(BooleanSupplier intake) {
        boolean wasCounting = countingIntake;
        countingIntake = false;
        try {
            return intake.getAsBoolean();
        } finally {
            countingIntake = wasCounting;
        }
    }

    public boolean isMeasuring() {
        return ledger.backlog().isEmpty();
    }

    public boolean isWatching() {
        return calibrationPaid && isMeasuring();
    }

    public boolean isCalibrationPaid() {
        return calibrationPaid;
    }

    public SimpleContainer saplings() {
        return saplings;
    }

    /**
     * Measures again from nothing, for a player whose farm changed. Spends one sapling, and does
     * nothing without one.
     *
     * <p>It also does nothing while a backlog is draining. The hopper is not watching its farm then, so
     * the sapling would buy a run that cannot begin until the backlog is empty, and the screen would
     * show a bar that sits at zero for as long as that takes.
     *
     * @return whether a sapling was spent and the measurement restarted
     */
    public boolean recalibrate() {
        if (!isMeasuring() || saplings.getItem(0).isEmpty()) {
            return false;
        }
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

    public CreditReport credit(OfflineSession.Current session) {
        Map<Item, Long> earned = ledger.earn(session, feedsATallyHopper() ? Map.of() : creditedRates());
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

    public ItemSinks.@Nullable ItemSink terminalSink(ServerLevel level) {
        Direction facing = getBlockState().getValue(HopperBlock.FACING);
        BlockPos target = getBlockPos().relative(facing);
        if (level.getBlockEntity(target) instanceof HopperBlockEntity) {
            return null;
        }
        return Services.ITEM_SINKS.find(level, target, facing.getOpposite());
    }

    /**
     * Whether another Tally Hopper further down this line already measures the same items.
     *
     * <p>Both would extrapolate them, so a line of two would hand back one farm's output twice. The
     * hopper nearest the storage is the one that earns: it sees everything the line delivers, while
     * an upstream one only sees what it passes on. This one keeps measuring and keeps its screen, it
     * just earns nothing while the line is built this way.
     *
     * @see ClockConversion#feedsATallyHopper
     */
    public boolean feedsATallyHopper() {
        return getLevel() instanceof ServerLevel level && ClockConversion.feedsATallyHopper(level, getBlockPos());
    }

    public Map<Item, Rate> creditedRates() {
        return RateBounds.capTotal(measurement.effectiveRates(), ceiling());
    }

    public Map<Item, Rate> provenRates() {
        return RateBounds.capTotal(measurement.provenRates(), ceiling());
    }

    private long ceiling() {
        return getLevel() instanceof ServerLevel level
                ? level.getGameRules().get(TallyHopperGameRules.MAX_ITEMS_PER_HOUR)
                : RateBounds.HOPPER_ITEMS_PER_HOUR;
    }

    public GuiState guiState() {
        Level level = getLevel();
        if (level != null && level.isClientSide()) {
            return clientState;
        }
        Map<Item, Long> rates = new LinkedHashMap<>();
        creditedRates().forEach((item, rate) -> rates.put(item, Math.round(rate.itemsPerHour())));
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
                calibrationPaid,
                feedsATallyHopper());
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

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.store(GUI_STATE_KEY, GuiState.CODEC, guiState());
        return tag;
    }

    public Ledger ledger() {
        return ledger;
    }

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

    @Override
    public boolean isValidBlockState(BlockState state) {
        return TallyHopperContent.blockEntityType().isValid(state);
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }
}
