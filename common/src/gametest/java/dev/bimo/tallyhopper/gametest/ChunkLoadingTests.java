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

/**
 * What happens to a hopper whose chunk isn't loaded when the world opens.
 *
 * <p>A Tally Hopper only measures and only credits while its chunk ticks, so a chunk loader — vanilla
 * {@code /forceload}, the spawn chunks, or a mod's — decides which hoppers are awake. Nothing in the
 * mod loads a chunk itself: credit is lazy, and waits for the chunk to load on its own. See
 * {@code docs/compatibility.md}.
 */
public final class ChunkLoadingTests {

    private ChunkLoadingTests() {}

    private static final BlockPos FAR_HOPPER = new BlockPos(4, 2, 2);

    /** Long enough for every other test to finish and the shared rejoin summary to be sent. */
    private static final int QUIET_TICKS = 3 * RejoinSummary.SETTLE_TICKS;

    private static int credited(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(TallyHopperStats.ITEMS_CREDITED));
    }

    /**
     * A hopper whose chunk loads long after the world opened is still credited, and still announced.
     * The rejoin summary is sent once the hoppers that loaded together go quiet, so a chunk loading
     * later in the session gets its own line rather than being credited in silence.
     */
    @SuppressWarnings("removal")
    public static void aLateChunkGetsItsOwnSummary(GameTestHelper helper) {
        TallyHopperBlockEntity near = placeTallyHopper(helper, HOPPER, Direction.DOWN);
        TallyHopperBlockEntity far = placeTallyHopper(helper, FAR_HOPPER, Direction.DOWN);
        setRate(near, Items.COBBLESTONE, 120);
        setRate(far, Items.COBBLESTONE, 600);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AtomicReference<OfflineSession.Current> opened = new AtomicReference<>();
        AtomicInteger afterFirst = new AtomicInteger();

        // The summary is one piece of server state shared by every hopper, and the other tests credit
        // into it too. These waits are long enough for the suite to go quiet, so the only credit that
        // can still land in the second window is the late hopper's own.
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

                    // The player walks into the far chunk; only now does that hopper tick and credit.
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

    /**
     * A hopper that was already asleep in an unloaded chunk when the world closed earns nothing for
     * that session, and walking back to it later in the session doesn't produce a surprise payout.
     * Only hoppers a chunk loader kept running while you were away are eligible.
     */
    public static void aSleepingHopperEarnsNothingWhenItWakes(GameTestHelper helper) {
        TallyHopperBlockEntity asleep = placeTallyHopper(helper, HOPPER, Direction.DOWN);
        setRate(asleep, Items.COBBLESTONE, 600);

        helper.startSequence()
                .thenIdle(1)
                // Its chunk unloaded well before the world closed, so its last tick is long past.
                .thenExecute(() -> {
                    OfflineSession.Current opened = session(helper, Duration.ofMinutes(5), Duration.ofHours(8));
                    CreditReport woken = asleep.credit(opened);
                    helper.assertTrue(woken.isEmpty(), "a sleeping hopper earned " + woken);
                    helper.assertValueEqual(asleep.ledger().backlog().total(), 0L, "backlog");
                    // It keeps running from here, but this session is settled: no late payout arrives.
                    helper.assertTrue(!asleep.ledger().needsCredit(opened), "the session is already settled");
                })
                .thenIdle(20)
                .thenExecute(() -> helper.assertValueEqual(
                        asleep.ledger().backlog().total(), 0L, "still nothing twenty ticks later"))
                .thenSucceed();
    }
}
