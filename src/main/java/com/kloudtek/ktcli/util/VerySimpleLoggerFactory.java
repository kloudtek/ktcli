package com.kloudtek.ktcli.util;

import org.slf4j.Logger;

public class VerySimpleLoggerFactory implements org.slf4j.ILoggerFactory {
    @Override
    public Logger getLogger(String name) {
        return new VerySimpleLogger(name);
    }
}
