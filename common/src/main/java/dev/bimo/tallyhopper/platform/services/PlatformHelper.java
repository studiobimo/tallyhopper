package dev.bimo.tallyhopper.platform.services;

import java.nio.file.Path;

public interface PlatformHelper {

    Path configDir();

    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();
}
