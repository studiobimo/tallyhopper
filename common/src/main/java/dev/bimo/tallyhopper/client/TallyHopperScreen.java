package dev.bimo.tallyhopper.client;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
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
 * The Tally Hopper's screen: the five slots, what it measured, and what it is holding.
 *
 * <p>Rates are set through the {@code /tallyhopper rate} command the box below sends, so the server
 * decides in one place who may change what.
 */
public final class TallyHopperScreen extends AbstractContainerScreen<TallyHopperMenu> {

    private static final Identifier BACKGROUND =
            Identifier.fromNamespaceAndPath("tallyhopper", "textures/gui/container/tally_hopper.png");

    private static final int TEXT_COLOR = 0xFF404040;
    private static final int FADED_COLOR = 0xFF707070;
    private static final int LINE_HEIGHT = 10;

    /** The first line of text, just below the five slots. */
    private static final int FIRST_LINE_Y = 42;

    /** How many lines of text the panel has room for between the slots and the box. */
    private static final int MAX_LINES = 6;

    /** The top of the override box, below the text and above the inventory label. */
    private static final int OVERRIDE_Y = FIRST_LINE_Y + MAX_LINES * LINE_HEIGHT + 2;

    /** How wide a line of text may be, the same as the box below it. */
    private static final int TEXT_WIDTH = 160;

    /** A vanilla hopper moves one item every eight ticks, which is 2.5 a second. */
    private static final double ITEMS_PER_SECOND = 2.5;

    private @Nullable EditBox override;

    public TallyHopperScreen(TallyHopperMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 216);
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        EditBox box = new EditBox(
                font,
                leftPos + 8,
                topPos + OVERRIDE_Y,
                160,
                14,
                Component.translatable("gui.tallyhopper.override.hint"));
        box.setHint(Component.translatable("gui.tallyhopper.override.hint"));
        box.setMaxLength(64);
        box.setResponder(text -> {});
        override = box;
        addRenderableWidget(box);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        EditBox box = override;
        if (box != null
                && box.isFocused()
                && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
            submitOverride(box);
            return true;
        }
        return super.keyPressed(event);
    }

    /**
     * Sends what was typed as a {@code /tallyhopper rate} command: {@code <item> <rate>} sets one,
     * {@code clear} removes them all. The server checks the player may do it and answers in chat.
     */
    private void submitOverride(EditBox box) {
        String typed = box.getValue().trim();
        BlockPos pos = menu.pos();
        String at = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        String command = typed.equalsIgnoreCase("clear")
                ? "tallyhopper rate clear " + at
                : "tallyhopper rate set " + at + " " + typed;
        if (!typed.isEmpty() && minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().sendCommand(command);
            box.setValue("");
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        GuiState state = state();
        int y = FIRST_LINE_Y;
        for (Component line : lines(state)) {
            graphics.text(font, fit(line), 8, y, TEXT_COLOR, false);
            y += LINE_HEIGHT;
        }
    }

    /**
     * Cuts a line down to the panel's width. Item names and translations are any length, so a line that
     * doesn't fit ends in an ellipsis rather than running off the edge.
     */
    private Component fit(Component line) {
        String text = line.getString();
        if (font.width(text) <= TEXT_WIDTH) {
            return line;
        }
        String cut = font.plainSubstrByWidth(text, TEXT_WIDTH - font.width("…"));
        return Component.literal(cut + "…").withStyle(line.getStyle());
    }

    private GuiState state() {
        Level level = minecraft == null ? null : minecraft.level;
        return level != null && level.getBlockEntity(menu.pos()) instanceof TallyHopperBlockEntity hopper
                ? hopper.guiState()
                : GuiState.EMPTY;
    }

    /**
     * What the panel says, at most {@link #MAX_LINES} lines: what the hopper is doing, then as many
     * item rates as the rest of the room allows, with the ones left over counted on the last line.
     */
    private List<Component> lines(GuiState state) {
        List<Component> lines = new ArrayList<>();
        lines.add(status(state));
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
        Optional<Component> hint = state.terminal() ? Optional.empty() : chainHint();
        addRates(lines, state, MAX_LINES - lines.size() - (hint.isPresent() ? 1 : 0));
        hint.ifPresent(lines::add);
        return lines;
    }

    /** Adds up to {@code budget} lines of item rates, spending the last one on what didn't fit. */
    private void addRates(List<Component> lines, GuiState state, int budget) {
        int total = state.ratesPerHour().size();
        if (total == 0 || budget <= 0) {
            return;
        }
        int listed = total <= budget ? total : budget - 1;
        int shown = 0;
        for (Map.Entry<Item, Long> rate : state.ratesPerHour().entrySet()) {
            if (shown++ == listed) {
                break;
            }
            lines.add(Component.translatable(
                    state.overridden().contains(rate.getKey())
                            ? "gui.tallyhopper.rate.override"
                            : "gui.tallyhopper.rate.measured",
                    rate.getKey().getDefaultInstance().getHoverName(),
                    number(rate.getValue())));
        }
        if (total > listed) {
            lines.add(Component.translatable("gui.tallyhopper.more_items", number(total - (long) listed))
                    .withColor(FADED_COLOR));
        }
    }

    private Component status(GuiState state) {
        if (state.isCalibrating()) {
            return Component.translatable(
                    "gui.tallyhopper.status.calibrating",
                    number(state.observedSeconds() / 60),
                    number(state.warmUpSeconds() / 60));
        }
        return state.ready() && !state.ratesPerHour().isEmpty()
                ? Component.translatable("gui.tallyhopper.status.ready")
                : Component.translatable("gui.tallyhopper.status.idle");
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
                                direction(start, end.pos()))
                        .withColor(FADED_COLOR));
            case HopperChain.Result.Loop<BlockPos> loop ->
                Optional.of(
                        Component.translatable("gui.tallyhopper.chain_loops").withColor(FADED_COLOR));
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
