package dev.bimo.tallyhopper.fabric.gametest;

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

/**
 * Opens the Tally Hopper's screen in a real client and photographs it while calibrating and once
 * ready, which is the only way to see that it renders at all.
 */
public final class TallyHopperScreenClientTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getConnection().waitForChunksRender();

            AtomicReference<BlockPos> placed = new AtomicReference<>();
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                BlockPos pos = player.blockPosition().above(2);
                player.level().setBlockAndUpdate(pos, TallyHopperContent.block().defaultBlockState());
                placed.set(pos);
                if (player.level().getBlockEntity(pos) instanceof TallyHopperBlockEntity hopper) {
                    Services.MENUS.open(player, hopper);
                }
            });

            context.waitForScreen(TallyHopperScreen.class);
            context.waitTicks(5);
            context.takeScreenshot("tally_hopper_calibrating");

            BlockPos pos = placed.get();
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
        }
    }
}
