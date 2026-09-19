package dev.bimo.tallyhopper.platform.services;

import java.nio.file.Path;

/** Loader-provided information about the runtime environment. */
public interface PlatformHelper {

    /** Where the loader keeps mod configuration, usually {@code <game dir>/config}. */
    Path configDir();

    /** Human-readable loader name, e.g. {@code Fabric} or {@code NeoForge}. */
    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();
}
