package dev.bimo.tallyhopper.neoforge;

import dev.bimo.tallyhopper.platform.services.ItemSinks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/** Delivers through NeoForge's item {@link ResourceHandler} capability. */
public final class NeoForgeItemSinks implements ItemSinks {

    @Override
    public @Nullable ItemSink find(ServerLevel level, BlockPos pos, Direction side) {
        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        if (handler == null) {
            return null;
        }
        return (item, count) -> {
            ItemResource resource = ItemResource.of(item);
            long inserted = 0;
            // The handler counts in ints; a credit can be larger.
            while (inserted < count) {
                int batch = (int) Math.min(Integer.MAX_VALUE, count - inserted);
                // Stacking fills matching stacks before empty slots, like a hopper does.
                int accepted = ResourceHandlerUtil.insertStacking(handler, resource, batch, null);
                inserted += accepted;
                if (accepted < batch) {
                    break;
                }
            }
            return inserted;
        };
    }
}
