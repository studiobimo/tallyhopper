package dev.bimo.tallyhopper.conversion;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.block.TallyHopperBlock;
import dev.bimo.tallyhopper.block.TallyHopperBlockEntity;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Using a clock on a hopper turns it into a Tally Hopper in place.
 *
 * <ul>
 *   <li>Use: converts the clicked vanilla hopper.
 *   <li>Sneak-use: follows the chain the clicked hopper feeds and converts the hopper at its end.
 * </ul>
 *
 * <p>Contents, custom name, lock and facing are kept, and the clock is used up. Each loader calls
 * {@link #onUseBlock} from its right-click-block event, before vanilla handles the click.
 */
public final class ClockConversion {

    private ClockConversion() {}

    /** Returns {@link InteractionResult#PASS} when the click isn't a conversion, so vanilla handles it. */
    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        ItemStack clock = player.getItemInHand(hand);
        BlockPos clicked = hit.getBlockPos();
        BlockState state = level.getBlockState(clicked);
        boolean chain = player.isSecondaryUseActive();
        if (!clock.is(Items.CLOCK) || player.isSpectator() || !(state.getBlock() instanceof HopperBlock)) {
            return InteractionResult.PASS;
        }
        // A plain click on a Tally Hopper opens it as usual.
        if (!chain && state.getBlock() instanceof TallyHopperBlock) {
            return InteractionResult.PASS;
        }
        if (!player.mayUseItemAt(clicked, hit.getDirection(), clock)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!chain) {
            convert(serverLevel, clicked, player, clock);
            return InteractionResult.SUCCESS;
        }
        return switch (HopperChain.findEnd(clicked, pos -> nextHopper(serverLevel, pos))) {
            case HopperChain.Result.End<BlockPos> end -> convertChainEnd(serverLevel, end.pos(), player, clock);
            case HopperChain.Result.Loop<BlockPos> loop -> refuse(player, "message.tallyhopper.chain_loops");
            case HopperChain.Result.TooLong<BlockPos> tooLong -> refuse(player, "message.tallyhopper.chain_too_long");
        };
    }

    /** The hopper that the hopper at {@code pos} pushes into, or {@code null}. Never loads chunks. */
    static @Nullable BlockPos nextHopper(Level level, BlockPos pos) {
        BlockPos target = pos.relative(level.getBlockState(pos).getValue(HopperBlock.FACING));
        if (!level.isLoaded(target) || !(level.getBlockState(target).getBlock() instanceof HopperBlock)) {
            return null;
        }
        return target;
    }

    private static InteractionResult convertChainEnd(ServerLevel level, BlockPos end, Player player, ItemStack clock) {
        BlockState state = level.getBlockState(end);
        if (state.getBlock() instanceof TallyHopperBlock) {
            return refuse(player, "message.tallyhopper.chain_end_converted");
        }
        if (!state.is(Blocks.HOPPER) || !level.mayInteract(player, end)) {
            return refuse(player, "message.tallyhopper.chain_end_unsupported");
        }
        convert(level, end, player, clock);
        return InteractionResult.SUCCESS;
    }

    /** Replaces a vanilla hopper with a Tally Hopper, carrying over its saved data. */
    static void convert(ServerLevel level, BlockPos pos, Player player, ItemStack clock) {
        BlockState old = level.getBlockState(pos);
        if (!old.is(Blocks.HOPPER) || !(level.getBlockEntity(pos) instanceof HopperBlockEntity hopper)) {
            return;
        }
        CompoundTag data = hopper.saveWithoutMetadata(level.registryAccess());
        BlockState converted = TallyHopperContent.block()
                .defaultBlockState()
                .setValue(HopperBlock.FACING, old.getValue(HopperBlock.FACING))
                .setValue(HopperBlock.ENABLED, old.getValue(HopperBlock.ENABLED));
        // Skipping block entity side effects stops the old hopper from spilling its items.
        level.setBlock(pos, converted, Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        if (level.getBlockEntity(pos) instanceof TallyHopperBlockEntity tally) {
            try (ProblemReporter.ScopedCollector reporter =
                    new ProblemReporter.ScopedCollector(tally.problemPath(), TallyHopper.LOG)) {
                tally.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), data));
            }
            tally.setChanged();
        }
        clock.consume(1, player);
        level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
    }

    private static InteractionResult refuse(Player player, String messageKey) {
        player.sendOverlayMessage(Component.translatable(messageKey));
        return InteractionResult.FAIL;
    }
}
