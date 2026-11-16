package dev.bimo.tallyhopper.platform;

import dev.bimo.tallyhopper.TallyHopper;
import dev.bimo.tallyhopper.platform.services.ItemSinks;
import dev.bimo.tallyhopper.platform.services.MenuTypes;
import dev.bimo.tallyhopper.platform.services.PlatformHelper;
import java.util.ServiceLoader;

public final class Services {

    public static final PlatformHelper PLATFORM = load(PlatformHelper.class);
    public static final ItemSinks ITEM_SINKS = load(ItemSinks.class);
    public static final MenuTypes MENUS = load(MenuTypes.class);

    private Services() {}

    public static <T> T load(Class<T> clazz) {
        T service = ServiceLoader.load(clazz, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No service implementation for " + clazz.getName()));
        TallyHopper.LOG.debug("Loaded {} for service {}", service, clazz);
        return service;
    }
}
