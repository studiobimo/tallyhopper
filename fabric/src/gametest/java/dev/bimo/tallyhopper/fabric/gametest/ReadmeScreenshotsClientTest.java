package dev.bimo.tallyhopper.fabric.gametest;

import dev.bimo.tallyhopper.block.TallyHopperBlock;
import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.measure.MeasurementClock;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import dev.bimo.tallyhopper.session.RejoinSummary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;

/**
 * Takes the pictures in the README: two Tally Hoppers on chests, one ready with its lamp lit and one
 * still calibrating, and the chat message a player gets back after a night away.
 *
 * <p>The night away is real as far as the mod can tell: the world is closed, the heartbeat it saved is
 * moved eight hours back, and the world is opened again. Everything after that is the mod's own
 * rejoin path, so the message in the picture is the one players see.
 */
public final class ReadmeScreenshotsClientTest implements FabricClientGameTest {

    private static final Duration AWAY = Duration.ofHours(8);

    @Override
    public void runTest(ClientGameTestContext context) {
        // The hoppers run the first session in the past, so their last tick lines up with the rewound
        // heartbeat; the second session is back on the real clock, eight hours later.
        MeasurementClock.useClock(() -> Instant.now().minus(AWAY));
        TestWorldSave save;
        AtomicReference<BlockPos> ready = new AtomicReference<>();
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getConnection().waitForChunksRender();
            singleplayer.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                BlockPos base = player.blockPosition();
                BlockPos lit = base.offset(0, 1, -3);
                ready.set(lit);
                onAChest(level, lit)
                        .setBlockAndUpdate(
                                lit,
                                TallyHopperContent.block()
                                        .defaultBlockState()
                                        .setValue(TallyHopperBlock.READY, true)
                                        .setValue(TallyHopperBlock.LIT, true));
                // A hand-set rate, so it is ready now and credits in full when the world opens again.
                if (level.getBlockEntity(lit) instanceof TallyHopperBlockEntity hopper) {
                    hopper.changeOverrides(measurement -> {
                        measurement.setOverride(Items.COBBLESTONE, 600);
                        return true;
                    });
                }
                BlockPos calibrating = base.offset(-1, 1, -3);
                onAChest(level, calibrating)
                        .setBlockAndUpdate(
                                calibrating, TallyHopperContent.block().defaultBlockState());
                lookAt(player, base.getX() + 1.7, base.getY(), base.getZ() - 0.5, lit.getX(), lit);
            });
            singleplayer.getServer().runCommand("time set noon");
            context.waitTicks(40);
            context.getInput().pressKey(options -> options.keyToggleGui);
            context.waitTicks(2);
            context.takeScreenshot(TestScreenshotOptions.of("readme_hoppers").withSize(1280, 720));
            context.getInput().pressKey(options -> options.keyToggleGui);
            save = singleplayer.getWorldSave();
        }

        rewindHeartbeat(save.getSaveDirectory(), AWAY);
        MeasurementClock.useClock(InstantSource.system());

        try (TestSingleplayerContext singleplayer = save.open()) {
            singleplayer.getConnection().waitForChunksRender();
            // The hopper credits as its chunk loads; the summary waits for the others, then speaks.
            BlockPos chest = ready.get().below();
            for (int waited = 0;
                    !singleplayer
                            .getServer()
                            .computeOnServer(server -> server.overworld().getBlockEntity(chest) instanceof Container box
                                    && !box.isEmpty());
                    waited++) {
                if (waited > ClientGameTestContext.DEFAULT_TIMEOUT) {
                    throw new AssertionError("nothing was credited into the chest at " + chest);
                }
                context.waitTick();
            }
            context.waitTicks(RejoinSummary.SETTLE_TICKS + 20);
            context.takeScreenshot("readme_rejoin_message");
        } finally {
            MeasurementClock.useGameTime();
        }
    }

    /** Puts a chest facing the camera under {@code pos}, for a Tally Hopper to face into. */
    private static ServerLevel onAChest(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(
                pos.below(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        return level;
    }

    /** Stands the player at a spot, looking at the middle of the gap between the two hoppers. */
    private static void lookAt(ServerPlayer player, double x, double y, double z, double targetX, BlockPos target) {
        double dx = targetX - x;
        double dy = target.getY() + 0.1 - (y + player.getEyeHeight());
        double dz = target.getZ() + 0.5 - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.connection.teleport(x, y, z, yaw, pitch);
    }

    /** Moves the heartbeat a closed world saved back by {@code away}, as if it had been shut that long ago. */
    private static void rewindHeartbeat(Path world, Duration away) {
        try (Stream<Path> files = Files.walk(world)) {
            Path session = files.filter(file -> file.endsWith(Path.of("tallyhopper", "session.dat")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no saved session under " + world));
            CompoundTag root = NbtIo.readCompressed(session, NbtAccounter.unlimitedHeap());
            CompoundTag data =
                    holding(root, "last_heartbeat").orElseThrow(() -> new AssertionError("no heartbeat in " + root));
            data.putLong("last_heartbeat", Instant.now().minus(away).toEpochMilli());
            NbtIo.writeCompressed(root, session);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Optional<CompoundTag> holding(CompoundTag tag, String key) {
        if (tag.contains(key)) {
            return Optional.of(tag);
        }
        return tag.keySet().stream()
                .flatMap(name -> tag.getCompound(name).stream())
                .flatMap(child -> holding(child, key).stream())
                .findFirst();
    }
}
