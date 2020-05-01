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
package org.codelibs.empros.agent.event;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.codelibs.empros.agent.listener.OperationListener;
import org.codelibs.empros.agent.operation.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventManager {
    private static final Logger logger = LoggerFactory
            .getLogger(EventManager.class);

    protected ExecutorService executorService;

    protected Operation operation;

    protected final int eventSizeInRequest;

    protected final int maxPoolSize;

    protected Queue<Event> eventQueue = new ConcurrentLinkedQueue<>();

    protected List<EventFilter> eventFilterList = new ArrayList<>();

    protected final AtomicBoolean running = new AtomicBoolean(false);

    protected final AtomicBoolean executing = new AtomicBoolean(false);

    protected MonitoringThread monitoringThread;

    protected final boolean backupAndRestore;

    protected final String backupDirectory;

    protected final long operationInterval;

    public EventManager(final int eventSizeInRequest, final int requestPoolSize, final boolean backupAndRestore,
                        final String backupDirectory, final long operationInterval) {
        this.eventSizeInRequest = eventSizeInRequest;
        maxPoolSize = requestPoolSize;
        this.backupAndRestore = backupAndRestore;
        if (backupDirectory.endsWith("/")) {
            this.backupDirectory = backupDirectory;
        } else {
            this.backupDirectory = backupDirectory + "/";
        }
        this.operationInterval = operationInterval;
    }

    public void start() {
        if (running.getAndSet(true)) {
            // already running
            return;
        }
        executorService = Executors.newFixedThreadPool(maxPoolSize);
        monitoringThread = new MonitoringThread();
        monitoringThread.start();
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            // stopped
            return;
        }
        executorService.shutdown();
    }

    public void setOperation(final Operation operation) {
        this.operation = operation;
        this.operation.addOperationListener(new ResultHandler());
        if (backupAndRestore) {
            this.operation.addOperationListener(new EventBackupListener());
        }
    }

    public void addEvent(final Event event) {
        if (event == null) {
            logger.warn("Added event is null.");
            return;
        }
        executing.set(true);
        eventQueue.remove(event);
        eventQueue.add(event);
    }

    public boolean isExecuting() {
        return executing.get();
    }

    public void submit() {
        synchronized (this) {
            notifyAll();
        }
    }

    public void addEventFilter(final EventFilter eventFilter) {
        eventFilterList.add(eventFilter);
    }

    protected Event convert(final Event event) {
        Event target = event;
        for (final EventFilter eventFilter : eventFilterList) {
            if (target == null) {
                return null;
            }
            target = eventFilter.convert(target);
        }
        return target;
    }

    protected class MonitoringThread extends Thread {

        @Override
        public void run() {
            while (running.get()) {
                if (eventQueue.isEmpty()) {
                    synchronized (EventManager.this) {
                        try {
                            EventManager.this.wait();
                        } catch (final InterruptedException e) {
                            // ignore
                        }
                    }
                } else {
                    if(operationInterval > 0) {
                        try {
                            sleep(operationInterval);
                        } catch(final InterruptedException e) {
                            // ignore
                        }
                    }

                    final Set<Event> eventSet = new LinkedHashSet<>();
                    while (eventSet.size() < eventSizeInRequest) {
                        final Event event = eventQueue.poll();
                        if (event == null) {
                            break;
                        }
                        Event convertedEvent = convert(event);
                        if(convertedEvent != null) {
                            eventSet.add(convertedEvent);
                        }
                    }

                    if (!eventSet.isEmpty()) {
                        executorService.execute(() ->
                                operation.excute(new ArrayList<>(eventSet))
                        );
                    }
                }
            }
        }
    }

    private class ResultHandler implements OperationListener {
        @Override
        public void successHandler(final List<Event> eventList) {
            if (eventQueue.isEmpty()) {
                executing.set(false);
            }
        }

        @Override
        public void errorHandler(List<Event> eventList) {
            // do nothing
        }

        @Override
        public void restoredHandler() {
            // do nothing
        }
    }

    private class EventBackupListener implements OperationListener {
        private String filePrefix = "evbk-";

        @Override
        public void successHandler(List<Event> eventList) {
            restoreEvents();
        }

        @Override
        public void restoredHandler() {
            restoreEvents();
        }

        @Override
        public void errorHandler(final List<Event> eventList) {
            final String fileName = backupDirectory
                    + filePrefix + (new Date()).getTime() + "-" + Thread.currentThread().getName();
            final File file = new File(fileName);
            if (logger.isDebugEnabled()) {
                logger.debug("Callded Error Handler.");
                for (final Event event : eventList) {
                    final StringBuilder logBuffer = new StringBuilder("Back up event-> ");
                    for (final Map.Entry<String, Object> entry : event.entrySet()) {
                        logBuffer.append(entry.getKey());
                        logBuffer.append(":");
                        logBuffer.append(entry.getValue().toString());
                        logBuffer.append(" ");
                    }
                    logger.debug(logBuffer.toString());
                }
                logger.debug("Event Backup to -> {}", file.getAbsolutePath());
            }

            try (final FileOutputStream outFile = new FileOutputStream(file);
                 final ObjectOutputStream outObject = new ObjectOutputStream(outFile)
            ) {
                outObject.writeObject(eventList);
            } catch (final Exception e) {
                logger.warn("Failed to export backup event.", e);
            }
        }

        private void restoreEvents() {
            final File dir = new File(backupDirectory);
            if (!dir.isDirectory()) {
                logger.warn("{} is not a directory.", dir.getAbsolutePath());
                return;
            }
            final List<Event> restoreEventList = new ArrayList<>();

            synchronized (this) {
                if (!exsitsBackupEvent(dir)) {
                    return;
                }

                File[] files = dir.listFiles();
                final List<File> bkFileList = new ArrayList<>();
                for (final File file : files) {
                    if (file.getName().startsWith(filePrefix)) {
                        bkFileList.add(file);
                    }
                }

                for (final File file : bkFileList) {
                    try (final FileInputStream inFile = new FileInputStream(file);
                         final ObjectInputStream inObject = new ObjectInputStream(inFile)
                    ) {
                        final List<Event> events = (List<Event>) inObject.readObject();
                        restoreEventList.addAll(events);
                    } catch (final Exception e) {
                        logger.warn("Failed to restore event.", e);
                    }

                    try {
                        Files.deleteIfExists(file.toPath());
                    } catch (final IOException e) {
                        logger.warn("Failed to delete file.", e);
                    }
                }
            }
            if (logger.isInfoEnabled()) {
                logger.info("Restored Event Num: {}", restoreEventList.size());
            }

            int eventCount = 0;
            for (final Event event : restoreEventList) {
                if (logger.isDebugEnabled()) {
                    final StringBuilder logBuffer = new StringBuilder("Restored event-> ");
                    for (final Map.Entry<String, Object> entry : event.entrySet()) {
                        logBuffer.append(entry.getKey());
                        logBuffer.append(":");
                        logBuffer.append(entry.getValue().toString());
                        logBuffer.append(" ");
                    }
                    logger.debug(logBuffer.toString());
                }

                addEvent(event);
                eventCount++;
                if (eventCount >= 100) {
                    submit();
                    eventCount = 0;
                }
            }
            submit();
        }

        private boolean exsitsBackupEvent(final File dir) {
            if (!dir.isDirectory()) {
                return false;
            }
            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                return false;
            }

            for (final File file : files) {
                if (file.getName().startsWith(filePrefix)) {
                    return true;
                }
            }
            return false;
        }
    }
}
