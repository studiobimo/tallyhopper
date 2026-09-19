package dev.bimo.tallyhopper.credit;

import java.util.Map;
import net.minecraft.world.item.Item;

/**
 * What one hopper's offline credit came to.
 *
 * @param earned the whole items earned per item type
 * @param delivered how many went straight into the container the hopper faces
 * @param backlogged how many went to the backlog
 * @param refused how many were not created because the backlog was at its cap
 */
public record CreditReport(Map<Item, Long> earned, long delivered, long backlogged, long refused) {

    public static final CreditReport NONE = new CreditReport(Map.of(), 0, 0, 0);

    public CreditReport {
        earned = Map.copyOf(earned);
    }

    public boolean isEmpty() {
        return earned.isEmpty();
    }
}
