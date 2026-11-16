package dev.bimo.tallyhopper.gametest;

import static dev.bimo.tallyhopper.gametest.CreditTests.HOPPER;
import static dev.bimo.tallyhopper.gametest.CreditTests.placeTallyHopper;
import static dev.bimo.tallyhopper.gametest.CreditTests.session;
import static dev.bimo.tallyhopper.gametest.CreditTests.setRate;

import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.credit.CreditReport;
import dev.bimo.tallyhopper.registry.TallyHopperStats;
import dev.bimo.tallyhopper.session.OfflineSession;
import dev.bimo.tallyhopper.session.RejoinSummary;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.Items;

public final class ChunkLoadingTests {

    private ChunkLoadingTests() {}

    private static final BlockPos FAR_HOPPER = new BlockPos(4, 2, 2);

    private static final int QUIET_TICKS = 3 * RejoinSummary.SETTLE_TICKS;

    private static int credited(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(TallyHopperStats.ITEMS_CREDITED));
    }

    @SuppressWarnings("removal")
    public static void aLateChunkGetsItsOwnSummary(GameTestHelper helper) {
        TallyHopperBlockEntity near = placeTallyHopper(helper, HOPPER, Direction.DOWN);
        TallyHopperBlockEntity far = placeTallyHopper(helper, FAR_HOPPER, Direction.DOWN);
        setRate(near, Items.COBBLESTONE, 120);
        setRate(far, Items.COBBLESTONE, 600);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AtomicReference<OfflineSession.Current> opened = new AtomicReference<>();
        AtomicInteger afterFirst = new AtomicInteger();

        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    opened.set(session(helper, Duration.ofHours(1)));
                    near.credit(opened.get());
                })
                .thenIdle(QUIET_TICKS)
                .thenExecute(() -> {
                    afterFirst.set(credited(player));
                    helper.assertTrue(afterFirst.get() >= 120, "the first summary counted " + afterFirst.get());

                    CreditReport late = far.credit(opened.get());
                    helper.assertTrue(!late.isEmpty(), "the late hopper earned " + late);
                })
                .thenIdle(QUIET_TICKS)
                .thenExecute(() -> {
                    helper.assertTrue(
                            credited(player) > afterFirst.get(),
                            "the late hopper was announced too, not credited in silence");
                    helper.getLevel().getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    public static void aSleepingHopperEarnsNothingWhenItWakes(GameTestHelper helper) {
        TallyHopperBlockEntity asleep = placeTallyHopper(helper, HOPPER, Direction.DOWN);
        setRate(asleep, Items.COBBLESTONE, 600);

        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    OfflineSession.Current opened = session(helper, Duration.ofMinutes(5), Duration.ofHours(8));
                    CreditReport woken = asleep.credit(opened);
                    helper.assertTrue(woken.isEmpty(), "a sleeping hopper earned " + woken);
                    helper.assertValueEqual(asleep.ledger().backlog().total(), 0L, "backlog");
                    helper.assertTrue(!asleep.ledger().needsCredit(opened), "the session is already settled");
                })
                .thenIdle(20)
                .thenExecute(() -> helper.assertValueEqual(
                        asleep.ledger().backlog().total(), 0L, "still nothing twenty ticks later"))
                .thenSucceed();
    }
}
