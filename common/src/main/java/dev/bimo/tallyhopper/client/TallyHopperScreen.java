package dev.bimo.tallyhopper.client;

import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.conversion.HopperChain;
import dev.bimo.tallyhopper.gui.GuiState;
import dev.bimo.tallyhopper.gui.TallyHopperMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LockIconButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Tally Hopper's screen, built from the parts vanilla already uses: the villager screen's bar for
 * calibration, the brewing stand's fuel meter for the saplings it costs, and the padlock button.
 *
 * <p>The panel says only what a glance needs; the numbers behind it are a tooltip on the bar. Rates
 * are still set through the {@code /tallyhopper rate} command, so the server decides in one place who
 * may change what.
 */
public final class TallyHopperScreen extends AbstractContainerScreen<TallyHopperMenu> {

    private static final Identifier BACKGROUND =
            Identifier.fromNamespaceAndPath("tallyhopper", "textures/gui/container/tally_hopper.png");

    private static final Identifier BAR_BACKGROUND =
            Identifier.withDefaultNamespace("container/villager/experience_bar_background");

    /** The plain white bar, tinted while calibrating, and the green one vanilla fills a trade with. */
    private static final Identifier BAR_CALIBRATING =
            Identifier.withDefaultNamespace("container/villager/experience_bar_result");

    private static final Identifier BAR_READY =
            Identifier.withDefaultNamespace("container/villager/experience_bar_current");

    private static final Identifier FUEL = Identifier.withDefaultNamespace("container/brewing_stand/fuel_length");

    private static final int BAR_X = 34;
    private static final int BAR_Y = 32;
    private static final int BAR_WIDTH = 102;
    private static final int BAR_HEIGHT = 5;

    private static final int FUEL_X = 7;
    private static final int FUEL_Y = 46;
    private static final int FUEL_WIDTH = 18;
    private static final int FUEL_HEIGHT = 4;

    /** How many saplings fill the meter, as twenty blaze powder fill a brewing stand's. */
    private static final int FUEL_FULL = 16;

    private static final int LOCK_X = 140;
    private static final int LOCK_Y = 40;

    private static final int STATUS_X = 34;
    private static final int STATUS_Y = 20;

    private static final int TEXT_COLOR = 0xFF404040;
    private static final int CALIBRATING_TINT = 0xFFFFD83D;

    /** How many item rates the tooltip lists before the rest are counted. */
    private static final int LISTED_RATES = 4;

    /** A vanilla hopper moves one item every eight ticks, which is 2.5 a second. */
    private static final double ITEMS_PER_SECOND = 2.5;

    private @Nullable LockIconButton lock;

    public TallyHopperScreen(TallyHopperMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        LockIconButton button = new LockIconButton(leftPos + LOCK_X, topPos + LOCK_Y, press -> recalibrate());
        lock = button;
        addRenderableWidget(button);
    }

    /**
     * Presses the menu's button, the way the lectern and the stonecutter do. The server spends the
     * sapling and starts the measurement again, so nothing here decides whether it may happen.
     */
    private void recalibrate() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TallyHopperMenu.RECALIBRATE_BUTTON);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        GuiState state = state();
        extractBar(graphics, state);
        extractFuel(graphics, state);
        LockIconButton button = lock;
        if (button != null) {
            button.setLocked(state.ready());
            button.active = menu.hasSapling();
        }
    }

    /** The villager screen's bar: white while it fills, green once the rate can be trusted. */
    private void extractBar(GuiGraphicsExtractor graphics, GuiState state) {
        int x = leftPos + BAR_X;
        int y = topPos + BAR_Y;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_BACKGROUND, x, y, BAR_WIDTH, BAR_HEIGHT);
        int filled = Math.round(BAR_WIDTH * state.progress());
        if (filled <= 0) {
            return;
        }
        if (state.ready()) {
            graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED, BAR_READY, BAR_WIDTH, BAR_HEIGHT, 0, 0, x, y, filled, BAR_HEIGHT);
        } else {
            graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    BAR_CALIBRATING,
                    BAR_WIDTH,
                    BAR_HEIGHT,
                    0,
                    0,
                    x,
                    y,
                    filled,
                    BAR_HEIGHT,
                    CALIBRATING_TINT);
        }
    }

    /** The brewing stand's fuel meter, counting saplings instead of blaze powder. */
    private void extractFuel(GuiGraphicsExtractor graphics, GuiState state) {
        int width = Math.round(FUEL_WIDTH * Math.min(1.0F, (float) state.saplings() / FUEL_FULL));
        if (width > 0) {
            graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    FUEL,
                    FUEL_WIDTH,
                    FUEL_HEIGHT,
                    0,
                    0,
                    leftPos + FUEL_X,
                    topPos + FUEL_Y,
                    width,
                    FUEL_HEIGHT);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        graphics.text(font, status(state()), STATUS_X, STATUS_Y, TEXT_COLOR, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (isOver(mouseX, mouseY, BAR_X, BAR_Y, BAR_WIDTH, BAR_HEIGHT)) {
            graphics.setComponentTooltipForNextFrame(font, details(state()), mouseX, mouseY);
        } else if (isOver(mouseX, mouseY, FUEL_X, FUEL_Y, FUEL_WIDTH, FUEL_HEIGHT)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(fuelLine(state())), mouseX, mouseY);
        } else if (lock != null && lock.isHovered()) {
            graphics.setComponentTooltipForNextFrame(font, lockLines(), mouseX, mouseY);
        }
    }

    private boolean isOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        int left = leftPos + x;
        int top = topPos + y;
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    private GuiState state() {
        Level level = minecraft == null ? null : minecraft.level;
        return level != null && level.getBlockEntity(menu.pos()) instanceof TallyHopperBlockEntity hopper
                ? hopper.guiState()
                : GuiState.EMPTY;
    }

    private static Component status(GuiState state) {
        if (state.needsSapling()) {
            return Component.translatable("gui.tallyhopper.status.needs_sapling");
        }
        if (state.isCalibrating()) {
            return Component.translatable("gui.tallyhopper.status.calibrating");
        }
        return state.ready()
                ? Component.translatable("gui.tallyhopper.status.ready")
                : Component.translatable("gui.tallyhopper.status.idle");
    }

    /** What the bar's tooltip says: the numbers that used to sit on the panel itself. */
    private List<Component> details(GuiState state) {
        List<Component> lines = new ArrayList<>();
        lines.add(status(state));
        if (state.isCalibrating()) {
            lines.add(Component.translatable(
                    "gui.tallyhopper.calibrating_minutes",
                    number(state.observedSeconds() / 60),
                    number(state.warmUpSeconds() / 60)));
        }
        addRates(lines, state);
        lines.add(
                state.terminal()
                        ? Component.translatable("gui.tallyhopper.mode.terminal")
                        : Component.translatable("gui.tallyhopper.mode.line"));
        if (state.backlog() > 0) {
            lines.add(Component.translatable(
                    "gui.tallyhopper.backlog", number(state.backlog()), drainTime(state.backlog())));
        } else if (state.lastCredit() > 0) {
            lines.add(Component.translatable("gui.tallyhopper.last_credit", number(state.lastCredit())));
        }
        if (!state.terminal()) {
            chainHint().ifPresent(lines::add);
        }
        return lines;
    }

    private void addRates(List<Component> lines, GuiState state) {
        int listed = 0;
        for (Map.Entry<Item, Long> rate : state.ratesPerHour().entrySet()) {
            if (listed++ == LISTED_RATES) {
                lines.add(Component.translatable(
                        "gui.tallyhopper.more_items",
                        number(state.ratesPerHour().size() - (long) LISTED_RATES)));
                return;
            }
            lines.add(Component.translatable(
                    state.overridden().contains(rate.getKey())
                            ? "gui.tallyhopper.rate.override"
                            : "gui.tallyhopper.rate.measured",
                    rate.getKey().getDefaultInstance().getHoverName(),
                    number(rate.getValue())));
        }
    }

    private Component fuelLine(GuiState state) {
        return state.saplings() > 0
                ? Component.translatable("gui.tallyhopper.saplings", number(state.saplings()))
                : Component.translatable("gui.tallyhopper.saplings.empty");
    }

    private List<Component> lockLines() {
        return menu.hasSapling()
                ? List.of(Component.translatable("gui.tallyhopper.recalibrate"))
                : List.of(Component.translatable("gui.tallyhopper.recalibrate.needs_sapling"));
    }

    /** Roughly how long the backlog takes to leave through the hopper, at vanilla speed. */
    private static Component drainTime(long backlog) {
        long seconds = Math.round(backlog / ITEMS_PER_SECOND);
        return seconds >= 60
                ? Component.translatable("gui.tallyhopper.drain.minutes", number(seconds / 60))
                : Component.translatable("gui.tallyhopper.drain.seconds", number(seconds));
    }

    /** Where this line of hoppers ends, so the player knows where instant filling would happen. */
    private Optional<Component> chainHint() {
        Level level = minecraft == null ? null : minecraft.level;
        if (level == null) {
            return Optional.empty();
        }
        BlockPos start = menu.pos();
        return switch (HopperChain.findEnd(start, pos -> nextHopper(level, pos))) {
            case HopperChain.Result.End<BlockPos> end
            when !end.pos().equals(start) ->
                Optional.of(Component.translatable(
                        "gui.tallyhopper.chain_end",
                        number(start.distManhattan(end.pos())),
                        direction(start, end.pos())));
            case HopperChain.Result.Loop<BlockPos> loop ->
                Optional.of(Component.translatable("gui.tallyhopper.chain_loops"));
            default -> Optional.empty();
        };
    }

    private static @Nullable BlockPos nextHopper(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HopperBlock)) {
            return null;
        }
        BlockPos target = pos.relative(state.getValue(HopperBlock.FACING));
        return level.getBlockEntity(target) instanceof HopperBlockEntity ? target : null;
    }

    private static Component direction(BlockPos from, BlockPos to) {
        BlockPos delta = to.subtract(from);
        Direction facing = Math.abs(delta.getX()) >= Math.abs(delta.getZ())
                ? (delta.getX() >= 0 ? Direction.EAST : Direction.WEST)
                : (delta.getZ() >= 0 ? Direction.SOUTH : Direction.NORTH);
        return Component.translatable("gui.tallyhopper.direction." + facing.getName());
    }

    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
