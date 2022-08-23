package com.rtm516.mcxboxbroadcast.bootstrap.geyser;

import com.rtm516.mcxboxbroadcast.core.Logger;


public class ExtensionLoggerImpl implements Logger {
    private org.slf4j.Logger logger;

    public ExtensionLoggerImpl(org.slf4j.Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warning(String message) {
        logger.warn(message);
    }

    @Override
    public void error(String message) {
        logger.error(message);
    }

    @Override
    public void error(String message, Throwable ex) {
        logger.error(message, ex);
    }

    @Override
    public void debug(String message) {
        logger.debug(message);
    }
}
