package com.mik.optical_diffuser.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DiffuserLogger {
    private static final Logger LOGGER = LogManager.getLogger("OpticalDiffuser/Media");

    public static void info(String message, Object... params) {
        LOGGER.info(message, params);
    }

    public static void warn(String message, Object... params) {
        LOGGER.warn(message, params);
    }

    public static void error(String message) {
        LOGGER.error(message);
    }

    public static void error(String message, Throwable t) {
        if (t != null) {
            LOGGER.error(message, t);
        } else {
            LOGGER.error(message);
        }
    }

    public static void debug(String message, Object... params) {
        LOGGER.debug(message, params);
    }
}
