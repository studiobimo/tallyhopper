package dev.bimo.tallyhopper.item;

import dev.bimo.tallyhopper.credit.StoredBacklog;
import dev.bimo.tallyhopper.offline.SaturatingMath;
import dev.bimo.tallyhopper.registry.TallyHopperContent;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** The Tally Hopper item, which shows a carried backlog in its tooltip so it isn't lost by surprise. */
public final class TallyHopperItem extends BlockItem {

    /** At most this many item types are listed; the rest are summed up on one line. */
    private static final int LISTED_ITEMS = 4;

    public TallyHopperItem(Block block, Properties properties) {
        super(block, properties);
    }

    // Vanilla deprecated this in 26.3 while moving tooltips onto components, but it only builds tooltips
    // from the components it knows, and neither loader adds modded ones. This is still the hook it calls.
    @Override
    @Deprecated
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> builder,
            TooltipFlag flag) {
        StoredBacklog backlog = stack.get(TallyHopperContent.backlogComponent());
        if (backlog == null || backlog.counts().isEmpty()) {
            return;
        }
        long total = 0;
        for (long count : backlog.counts().values()) {
            total = SaturatingMath.add(total, count);
        }
        builder.accept(Component.translatable("item.tallyhopper.tally_hopper.backlog", number(total))
                .withStyle(ChatFormatting.GRAY));
        int listed = 0;
        for (Map.Entry<net.minecraft.world.item.Item, Long> entry :
                backlog.counts().entrySet()) {
            if (listed++ == LISTED_ITEMS) {
                builder.accept(Component.translatable(
                                "item.tallyhopper.tally_hopper.backlog_more",
                                number(backlog.counts().size() - (long) LISTED_ITEMS))
                        .withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            builder.accept(Component.translatable(
                            "item.tallyhopper.tally_hopper.backlog_line",
                            entry.getKey().getDefaultInstance().getHoverName(),
                            number(entry.getValue()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
