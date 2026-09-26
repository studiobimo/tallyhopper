package dev.bimo.tallyhopper.fabric.gametest;

import dev.bimo.tallyhopper.block.TallyHopperBlock;
import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.client.TallyHopperScreen;
import dev.bimo.tallyhopper.platform.Services;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Opens the Tally Hopper's screen in a real client and photographs it in each state it has: waiting
 * for a sapling, calibrating, ready, and passthrough. Then photographs the block itself by day and by
 * night, one hopper calibrating and one ready, so the clock face can be checked by eye. Rendering is
 * the one thing only a real client can show.
 */
public final class TallyHopperScreenClientTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getConnection().waitForChunksRender();

            AtomicReference<BlockPos> placed = new AtomicReference<>();
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                BlockPos pos = player.blockPosition().above(3);
                player.level().setBlockAndUpdate(pos, TallyHopperContent.block().defaultBlockState());
                placed.set(pos);
                if (player.level().getBlockEntity(pos) instanceof TallyHopperBlockEntity hopper) {
                    Services.MENUS.open(player, hopper);
                }
            });

            context.waitForScreen(TallyHopperScreen.class);
            context.waitTicks(5);
            context.takeScreenshot("tally_hopper_needs_sapling");

            BlockPos pos = placed.get();
            // A sapling buys one calibration run; the hopper spends it on its next tick.
            singleplayer.getServer().runOnServer(server -> {
                if (server.overworld().getBlockEntity(pos) instanceof TallyHopperBlockEntity hopper) {
                    hopper.saplings().setItem(0, new ItemStack(Items.OAK_SAPLING, 8));
                }
            });
            context.waitFor(client -> client.level != null
                    && client.level.getBlockEntity(pos) instanceof TallyHopperBlockEntity hopper
                    && hopper.guiState().isCalibrating());
            context.waitTicks(5);
            context.takeScreenshot("tally_hopper_calibrating");

            singleplayer
                    .getServer()
                    .runCommand("tallyhopper rate set %d %d %d minecraft:cobblestone 600"
                            .formatted(pos.getX(), pos.getY(), pos.getZ()));
            // The hopper syncs to open screens once a second.
            context.waitFor(client -> client.level != null
                    && client.level.getBlockEntity(pos) instanceof TallyHopperBlockEntity hopper
                    && hopper.guiState().ready());
            context.waitTicks(5);
            context.takeScreenshot("tally_hopper_ready");

            // A second Tally Hopper under this one takes over earning, so this one becomes a
            // passthrough: the status word changes and the padlock is hidden.
            singleplayer
                    .getServer()
                    .runOnServer(server -> server.overworld()
                            .setBlockAndUpdate(
                                    pos.below(), TallyHopperContent.block().defaultBlockState()));
            context.waitFor(client -> client.level != null
                    && client.level.getBlockEntity(pos) instanceof TallyHopperBlockEntity hopper
                    && hopper.guiState().isPassthrough());
            context.waitTicks(5);
            context.takeScreenshot("tally_hopper_passthrough");

            context.setScreen(() -> null);
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                BlockPos base = player.blockPosition();
                BlockPos calibrating = base.offset(-1, 1, -3);
                BlockPos ready = base.offset(1, 1, -3);
                server.overworld()
                        .setBlockAndUpdate(
                                calibrating, TallyHopperContent.block().defaultBlockState());
                server.overworld()
                        .setBlockAndUpdate(
                                ready,
                                TallyHopperContent.block().defaultBlockState().setValue(TallyHopperBlock.READY, true));
                // An override keeps it ready, or its next clock-face check would set it back.
                if (server.overworld().getBlockEntity(ready) instanceof TallyHopperBlockEntity hopper) {
                    hopper.changeOverrides(measurement -> {
                        measurement.setOverride(Items.COBBLESTONE, 600);
                        return true;
                    });
                }
                player.connection.teleport(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 180, 12);
            });
            singleplayer.getServer().runCommand("time set noon");
            context.waitTicks(40);
            context.takeScreenshot("tally_hopper_block_day");
            singleplayer.getServer().runCommand("time set midnight");
            context.waitTicks(40);
            context.takeScreenshot("tally_hopper_block_night");
        }
    }
}
