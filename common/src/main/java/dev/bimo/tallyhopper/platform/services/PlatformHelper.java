package dev.bimo.tallyhopper.platform.services;

/** Loader-provided information about the runtime environment. */
public interface PlatformHelper {

    /** Human-readable loader name, e.g. {@code Fabric} or {@code NeoForge}. */
    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();
}
