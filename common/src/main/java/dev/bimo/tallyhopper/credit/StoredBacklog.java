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

public record StoredBacklog(Map<Item, Long> counts) {

    public static final Codec<StoredBacklog> CODEC =
            Ledger.COUNTS_CODEC.xmap(StoredBacklog::new, StoredBacklog::counts);
    public static final StreamCodec<RegistryFriendlyByteBuf, StoredBacklog> STREAM_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, Item, Long, Map<Item, Long>>map(
                            LinkedHashMap::new, ByteBufCodecs.registry(Registries.ITEM), ByteBufCodecs.VAR_LONG)
                    .map(StoredBacklog::new, StoredBacklog::counts);

    public StoredBacklog {
        counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }
}
