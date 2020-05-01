/*
 * Copyright 2012-2020 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.empros.agent.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertiesUtil {
    private static final long ACCESS_INTERVAL = 60000;

    private static final Logger logger = LoggerFactory
            .getLogger(PropertiesUtil.class);

    private static final Map<String, PropData> propDataMap = new ConcurrentHashMap<>();

    public static String getAsString(final String path, final String key,
                                     final String defaultValue) {
        final Properties props = getProperties(path);
        final String value = props.getProperty(key);
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    public static long getAsLong(final String path, final String key,
                                 final long defaultValue) {
        final Properties props = getProperties(path);
        final String value = props.getProperty(key);
        if (value != null) {
            return Long.parseLong(value);
        }
        return defaultValue;
    }

    public static int getAsInt(final String path, final String key,
                               final int defaultValue) {
        final Properties props = getProperties(path);
        final String value = props.getProperty(key);
        if (value != null) {
            return Integer.parseInt(value);
        }
        return defaultValue;
    }

    private static Properties getProperties(final String path) {
        PropData propData = propDataMap.get(path);
        if (isAvailable(propData)) {
            return propData.properties;
        }

        synchronized (propDataMap) {
            propData = propDataMap.get(path);
            if (isAvailable(propData)) {
                return propData.properties;
            }

            final Properties props = new Properties();
            try (final InputStream in = ClassLoader.getSystemResourceAsStream(path)) {
                if (in != null) {
                    props.load(in);
                }
            } catch (final IOException e) {
                logger.warn("Could not read {}", path);
            }
            propDataMap.put(path,
                    new PropData(props, System.currentTimeMillis()));
            return props;
        }
    }

    private static boolean isAvailable(final PropData propData) {
        return propData != null
                && System.currentTimeMillis() - propData.lastAccessed < ACCESS_INTERVAL;
    }

    static class PropData {
        Properties properties;

        long lastAccessed;

        PropData(final Properties properties, final long lastModified) {
            this.properties = properties;
            lastAccessed = lastModified;
        }
    }

}
