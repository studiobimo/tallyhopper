package dev.bimo.tallyhopper.block;

import dev.bimo.tallyhopper.registry.TallyHopperContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A vanilla hopper block entity under its own type.
 *
 * <p>Vanilla hopper logic is static and checks {@code instanceof HopperBlockEntity}: the transfer
 * cooldown handshake between hoppers, minecarts, comparators and item pickup all rely on it.
 * Extending it keeps every one of those behaviors identical. Vanilla hard-wires the hopper type in
 * the constructor, so the type accessors are overridden to report {@code tallyhopper:tally_hopper}
 * instead, which is what gets saved and synced.
 */
public final class TallyHopperBlockEntity extends HopperBlockEntity {

    private static final Component DEFAULT_NAME = Component.translatable("container.tallyhopper.tally_hopper");

    public TallyHopperBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public BlockEntityType<?> getType() {
        return TallyHopperContent.blockEntityType();
    }

    @Override
    public Holder<BlockEntityType<?>> typeHolder() {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.wrapAsHolder(TallyHopperContent.blockEntityType());
    }

    // Called from the super constructor, so it must not touch instance fields.
    @Override
    public boolean isValidBlockState(BlockState state) {
        return TallyHopperContent.blockEntityType().isValid(state);
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }
}
