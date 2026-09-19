package dev.bimo.tallyhopper.platform.services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * Finds storage to deliver credit into, through the loader's transfer API: Fabric's {@code
 * ItemStorage.SIDED} or NeoForge's {@code Capabilities.Item.BLOCK}.
 *
 * <p>Both APIs already wrap every vanilla container, respecting its sided insertion rules and joining
 * the halves of a double chest, and they reach modded storage too, so there is no separate vanilla
 * path.
 */
public interface ItemSinks {

    /** The storage at {@code pos}, entered from {@code side}, or {@code null} if there is none. */
    @Nullable ItemSink find(ServerLevel level, BlockPos pos, Direction side);

    /** Storage that accepts items. It only ever inserts: nothing in it is moved, replaced or removed. */
    @FunctionalInterface
    interface ItemSink {

        /** Inserts up to {@code count} of {@code item} with default components; returns how many went in. */
        long insert(Item item, long count);
    }
}
