package dev.bimo.tallyhopper;

import dev.bimo.tallyhopper.platform.Services;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loader-agnostic entry point and shared constants. */
public final class TallyHopper {

    public static final String MOD_ID = "tallyhopper";
    public static final String MOD_NAME = "Tally Hopper";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    private TallyHopper() {}

    /** Called once by each loader's entry point during mod construction. */
    public static void init() {
        LOG.info("{} initializing on {}", MOD_NAME, Services.PLATFORM.getPlatformName());
    }
}
