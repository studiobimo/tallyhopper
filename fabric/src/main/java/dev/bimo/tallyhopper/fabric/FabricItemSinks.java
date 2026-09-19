package dev.bimo.tallyhopper.fabric;

import dev.bimo.tallyhopper.platform.services.ItemSinks;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/** Delivers through Fabric's Transfer API. */
public final class FabricItemSinks implements ItemSinks {

    @Override
    public @Nullable ItemSink find(ServerLevel level, BlockPos pos, Direction side) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, side);
        if (storage == null) {
            return null;
        }
        return (item, count) -> {
            try (Transaction transaction = Transaction.openOuter()) {
                long inserted = storage.insert(ItemVariant.of(item), count, transaction);
                transaction.commit();
                return inserted;
            }
        };
    }
}
