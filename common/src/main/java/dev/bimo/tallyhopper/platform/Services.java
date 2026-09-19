package dev.bimo.tallyhopper.platform;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.platform.services.ItemSinks;
import dev.bimo.tallyhopper.platform.services.PlatformHelper;
import java.util.ServiceLoader;

/**
 * Resolves loader-specific implementations of common interfaces at runtime.
 *
 * <p>Each loader project registers its implementation in
 * {@code META-INF/services/<fully.qualified.InterfaceName>}.
 */
public final class Services {

    public static final PlatformHelper PLATFORM = load(PlatformHelper.class);
    public static final ItemSinks ITEM_SINKS = load(ItemSinks.class);

    private Services() {}

    public static <T> T load(Class<T> clazz) {
        T service = ServiceLoader.load(clazz, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No service implementation for " + clazz.getName()));
        TallyHopper.LOG.debug("Loaded {} for service {}", service, clazz);
        return service;
    }
}
