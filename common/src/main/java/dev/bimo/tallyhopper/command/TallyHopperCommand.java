package dev.bimo.tallyhopper.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.measure.Measurement;
import dev.bimo.tallyhopper.offline.Rate;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /tallyhopper info} and {@code /tallyhopper rate}, for players who want to see or steer what a
 * hopper measured. Nothing needs them: a hopper calibrates on its own.
 *
 * <p>A hopper is targeted by looking at it or by giving its position. Operators may target any loaded
 * hopper and set any rate. Other players must be within {@link #REACH} blocks, and may only set a rate
 * at or below the measured one, so an override can switch an item off but never mint items the farm
 * didn't make.
 */
public final class TallyHopperCommand {

    /** How far away a hopper can be targeted, by looking or, for non-operators, at all. */
    static final double REACH = 8;

    private static final SimpleCommandExceptionType NOT_LOOKING =
            new SimpleCommandExceptionType(Component.translatable("commands.tallyhopper.error.not_looking"));
    private static final SimpleCommandExceptionType NOT_A_TALLY_HOPPER =
            new SimpleCommandExceptionType(Component.translatable("commands.tallyhopper.error.not_a_tally_hopper"));
    private static final SimpleCommandExceptionType TOO_FAR =
            new SimpleCommandExceptionType(Component.translatable("commands.tallyhopper.error.too_far"));
    private static final SimpleCommandExceptionType NO_ACCESS =
            new SimpleCommandExceptionType(Component.translatable("commands.tallyhopper.error.no_access"));
    private static final SimpleCommandExceptionType PLAIN_ITEMS_ONLY =
            new SimpleCommandExceptionType(Component.translatable("commands.tallyhopper.error.plain_items"));
    private static final DynamicCommandExceptionType ABOVE_MEASURED = new DynamicCommandExceptionType(
            measured -> Component.translatable("commands.tallyhopper.error.above_measured", measured));
    private static final DynamicCommandExceptionType NO_OVERRIDE = new DynamicCommandExceptionType(
            item -> Component.translatable("commands.tallyhopper.error.no_override", item));

    private TallyHopperCommand() {}

    /** Finds the hopper a command is aimed at. */
    @FunctionalInterface
    private interface Target {
        BlockPos find(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    private static final Target LOOKED_AT = context -> lookedAt(context.getSource());
    private static final Target AT_POS = context -> BlockPosArgument.getLoadedBlockPos(context, "pos");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("tallyhopper")
                .then(Commands.literal("info")
                        .executes(c -> info(c, LOOKED_AT))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> info(c, AT_POS))))
                .then(Commands.literal("rate")
                        .then(Commands.literal("set")
                                .then(set(context, LOOKED_AT))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(set(context, AT_POS))))
                        .then(Commands.literal("clear")
                                .executes(c -> clearAll(c, LOOKED_AT))
                                .then(clear(context, LOOKED_AT))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(c -> clearAll(c, AT_POS))
                                        .then(clear(context, AT_POS))))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> set(CommandBuildContext context, Target target) {
        return Commands.argument("item", ItemArgument.item(context))
                .then(Commands.argument("perHour", LongArgumentType.longArg(0))
                        .executes(c -> set(
                                c, target, ItemArgument.getItem(c, "item"), LongArgumentType.getLong(c, "perHour"))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> clear(CommandBuildContext context, Target target) {
        return Commands.argument("item", ItemArgument.item(context))
                .executes(c -> clear(c, target, ItemArgument.getItem(c, "item")));
    }

    private static int info(CommandContext<CommandSourceStack> context, Target target) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos pos = target.find(context);
        Measurement measurement = hopper(source, pos, false).measurement();

        Component status = measurement.isReady()
                ? Component.translatable("commands.tallyhopper.info.ready")
                : Component.translatable(
                        "commands.tallyhopper.info.calibrating",
                        measurement.observed().toMinutes(),
                        Measurement.WARM_UP.toMinutes());
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.tallyhopper.info.header", pos.getX(), pos.getY(), pos.getZ(), status),
                false);

        Map<Item, Component> lines = new LinkedHashMap<>();
        measurement
                .measuredRates()
                .forEach((item, rate) -> lines.put(
                        item, Component.translatable("commands.tallyhopper.info.measured", name(item), perHour(rate))));
        // An override replaces the measured line for its item.
        measurement
                .overrides()
                .forEach((item, perHour) -> lines.put(
                        item, Component.translatable("commands.tallyhopper.info.override", name(item), perHour)));
        if (lines.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.tallyhopper.info.nothing"), false);
        }
        lines.values().forEach(line -> source.sendSuccess(() -> line, false));
        return lines.size();
    }

    private static int set(CommandContext<CommandSourceStack> context, Target target, ItemInput input, long perHour)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos pos = target.find(context);
        TallyHopperBlockEntity hopper = hopper(source, pos, true);
        if (!input.components().isEmpty()) {
            throw PLAIN_ITEMS_ONLY.create();
        }
        Item item = input.item().value();
        if (!isOperator(source)) {
            long measured = measuredPerHour(hopper.measurement(), item);
            if (perHour > measured) {
                throw ABOVE_MEASURED.create(measured);
            }
        }
        hopper.changeOverrides(measurement -> {
            measurement.setOverride(item, perHour);
            return perHour;
        });
        source.sendSuccess(() -> Component.translatable("commands.tallyhopper.rate.set", name(item), perHour), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context, Target target, ItemInput input)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        TallyHopperBlockEntity hopper = hopper(source, target.find(context), true);
        Item item = input.item().value();
        if (!hopper.changeOverrides(measurement -> measurement.clearOverride(item))) {
            throw NO_OVERRIDE.create(name(item));
        }
        source.sendSuccess(() -> Component.translatable("commands.tallyhopper.rate.cleared", name(item)), true);
        return 1;
    }

    private static int clearAll(CommandContext<CommandSourceStack> context, Target target)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        TallyHopperBlockEntity hopper = hopper(source, target.find(context), true);
        int removed = hopper.changeOverrides(Measurement::clearOverrides);
        source.sendSuccess(() -> Component.translatable("commands.tallyhopper.rate.cleared_all", removed), true);
        return removed;
    }

    private static BlockPos lookedAt(CommandSourceStack source) throws CommandSyntaxException {
        HitResult hit = source.getPlayerOrException().pick(REACH, 1.0F, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            return block.getBlockPos();
        }
        throw NOT_LOOKING.create();
    }

    private static TallyHopperBlockEntity hopper(CommandSourceStack source, BlockPos pos, boolean changing)
            throws CommandSyntaxException {
        if (!isOperator(source)) {
            ServerPlayer player = source.getPlayerOrException();
            if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH) {
                throw TOO_FAR.create();
            }
            // Spawn protection and the world border apply here just as they do to using the block.
            if (changing && !player.mayInteract(source.getLevel(), pos)) {
                throw NO_ACCESS.create();
            }
        }
        if (source.getLevel().getBlockEntity(pos) instanceof TallyHopperBlockEntity hopper) {
            return hopper;
        }
        throw NOT_A_TALLY_HOPPER.create();
    }

    private static boolean isOperator(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /** The whole items per hour this hopper would credit for {@code item} without overrides. */
    private static long measuredPerHour(Measurement measurement, Item item) {
        Rate rate = measurement.measuredRates().get(item);
        return measurement.isWarmedUp() && rate != null ? (long) Math.floor(rate.itemsPerHour()) : 0;
    }

    private static long perHour(Rate rate) {
        return Math.round(rate.itemsPerHour());
    }

    private static Component name(Item item) {
        return item.getDefaultInstance().getHoverName();
    }
}
