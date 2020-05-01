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
package org.codelibs.empros.agent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.codelibs.core.lang.ClassUtil;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.empros.agent.event.EventFilter;
import org.codelibs.empros.agent.event.EventManager;
import org.codelibs.empros.agent.operation.Operation;
import org.codelibs.empros.agent.scanner.Scanner;
import org.codelibs.empros.agent.util.PropertiesUtil;
import org.codelibs.empros.agent.watcher.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmprosAgent {
    private static final String AGENT_PROPERTIES = "agent.properties";

    private static final Logger logger = LoggerFactory
            .getLogger(EmprosAgent.class);

    private final EventManager eventManager;

    private Operation operation;

    private Watcher watcher;

    private Scanner scanner;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final CountDownLatch latch = new CountDownLatch(1);

    public EmprosAgent() {
        eventManager = new EventManager(PropertiesUtil.getAsInt(
                AGENT_PROPERTIES, "eventSizeInRequest", 100),
                PropertiesUtil
                        .getAsInt(AGENT_PROPERTIES, "requestPoolSize", 10),
                Boolean.parseBoolean(PropertiesUtil
                        .getAsString(AGENT_PROPERTIES, "backupAndRestore", "false")),
                PropertiesUtil.getAsString(AGENT_PROPERTIES, "backupDirectory", ""),
                PropertiesUtil.getAsLong(AGENT_PROPERTIES, "operationInterval", 0));
        final String[] eventFilters = PropertiesUtil.getAsString(
                AGENT_PROPERTIES, "eventFilters", StringUtil.EMPTY).split(",");
        for (final String eventFilterClass : eventFilters) {
            if (StringUtil.isNotBlank(eventFilterClass)) {
                final EventFilter eventFilter = ClassUtil
                        .newInstance(eventFilterClass);
                eventManager.addEventFilter(eventFilter);
            }
        }
    }

    public boolean start() {
        if (operation == null) {
            logger.warn("EmprosAgent has no operation.");
            return false;
        }

        if (started.getAndSet(true)) {
            logger.warn("EmprosAgent is already started.");
            return false;
        }

        logger.info("EmprosAgent is started.");

        eventManager.setOperation(operation);
        eventManager.start();

        watcher.setEventManager(eventManager);
        watcher.start();

        return true;
    }

    public boolean stop() {
        if (!started.get()) {
            logger.warn("EmprosAgent is not running.");
            return false;
        }

        latch.countDown();

        return true;
    }

    public boolean scan() {
        if (operation == null) {
            logger.warn("EmprosAgent has no operation.");
            return false;
        }

        if (started.getAndSet(true)) {
            logger.warn("EmprosAgent is already started.");
            return false;
        }

        logger.info("EmprosAgent is started.");

        eventManager.setOperation(operation);
        eventManager.start();

        scanner.setEventManager(eventManager);

        final long start = System.currentTimeMillis();
        scanner.start();

        while (scanner.isRunning()) {
            try {
                Thread.sleep(10 * 1000);
            } catch (InterruptedException ignore) {
                // ignore
            }

        }

        while(eventManager.isExecuting()) {
            try {
                logger.info("waiting event process");
                Thread.sleep(10 * 1000);
            } catch (InterruptedException ignore) {
                // ignore
            }
        }

        logger.info("Scan is finished. took:{} min", (System.currentTimeMillis() - start) / (60 * 1000));
        return true;
    }

    public void destroy() {
        if (watcher != null) {
            watcher.stop();
        }
        if (scanner != null) {
            scanner.stop();
        }
        eventManager.stop();
        operation.destroy();
    }

    public void await() {
        try {
            latch.await();
        } catch (final InterruptedException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Interrupted. ", e);
            }
        }
    }

    public void setOperation(final Operation operation) {
        this.operation = operation;
    }

    public void setWatcher(final Watcher watcher) {
        this.watcher = watcher;
    }

    public void setScanner(final Scanner scanner) {
        this.scanner = scanner;
    }
}
