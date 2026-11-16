package dev.bimo.tallyhopper.platform.services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

public interface ItemSinks {

    @Nullable ItemSink find(ServerLevel level, BlockPos pos, Direction side);

    @FunctionalInterface
    interface ItemSink {

        long insert(Item item, long count);
    }
}
