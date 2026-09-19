package dev.bimo.tallyhopper.credit;

import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

/**
 * A backlog carried on a Tally Hopper item, like a shulker box carries its contents. Breaking the
 * block puts it here and placing the item puts it back, so moving a hopper never loses credit.
 */
public record StoredBacklog(Map<Item, Long> counts) {

    public static final Codec<StoredBacklog> CODEC =
            Ledger.COUNTS_CODEC.xmap(StoredBacklog::new, StoredBacklog::counts);
    public static final StreamCodec<RegistryFriendlyByteBuf, StoredBacklog> STREAM_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, Item, Long, Map<Item, Long>>map(
                            LinkedHashMap::new, ByteBufCodecs.registry(Registries.ITEM), ByteBufCodecs.VAR_LONG)
                    .map(StoredBacklog::new, StoredBacklog::counts);

    public StoredBacklog {
        // Kept in order: the oldest item is handed out first.
        counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }
}
