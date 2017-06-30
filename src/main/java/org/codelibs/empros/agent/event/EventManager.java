/*
 * Copyright 2013 the CodeLibs Project and the Others.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
        if (backupAndRestore) {
            this.operation.addOperationListener(new EventBackupListener());
        }
    }

    public void addEvent(final Event event) {
        if (event == null) {
            logger.warn("Added event is null.");
            return;
        }
        eventQueue.remove(event);
        eventQueue.add(event);
    }

    public void submit() {
        synchronized (this) {
            notify();
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
                        }
                    }
                } else {
                    if(operationInterval > 0) {
                        try {
                            sleep(operationInterval);
                        } catch(InterruptedException e) {
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
                        executorService.execute(new Runnable() {
                            @Override
                            public void run() {
                                operation.excute(new ArrayList<>(eventSet));
                            }
                        });
                    }
                }
            }
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
        public void errorHandler(List<Event> eventList) {
            String fileName = backupDirectory
                    + filePrefix + (new Date()).getTime() + "-" + Thread.currentThread().getName();
            File file = new File(fileName);
            if (logger.isDebugEnabled()) {
                logger.debug("Callded Error Handler.");
                for (Event event : eventList) {
                    StringBuilder logBuffer = new StringBuilder("Back up event-> ");
                    for (Map.Entry<String, Object> entry : event.entrySet()) {
                        logBuffer.append(entry.getKey());
                        logBuffer.append(":");
                        logBuffer.append(entry.getValue().toString());
                        logBuffer.append(" ");
                    }
                    logger.debug(logBuffer.toString());
                }
                logger.debug("Event Backup to -> " + file.getAbsolutePath());
            }

            FileOutputStream outFile = null;
            ObjectOutputStream outObject = null;
            try {
                outFile = new FileOutputStream(file);
                outObject = new ObjectOutputStream(outFile);
                outObject.writeObject(eventList);
            } catch (Exception e) {
                logger.warn("Failed to export backup event.", e);
            } finally {
                if (outObject != null) {
                    try {
                        outObject.close();
                    } catch (Exception e) {

                    }
                }
                if (outFile != null) {
                    try {
                        outFile.close();
                    } catch (Exception e) {

                    }
                }
            }
        }

        private void restoreEvents() {
            File dir = new File(backupDirectory);
            if (!dir.isDirectory()) {
                logger.warn(dir.getAbsolutePath() + " is not a directory.");
                return;
            }
            List<Event> restoreEventList = new ArrayList<Event>();

            synchronized (this) {
                if (!exsitsBackupEvent(dir)) {
                    return;
                }

                File[] files = dir.listFiles();
                List<File> bkFileList = new ArrayList<File>();
                for (File file : files) {
                    if (file.getName().startsWith(filePrefix)) {
                        bkFileList.add(file);
                    }
                }

                for (File file : bkFileList) {
                    FileInputStream inFile = null;
                    ObjectInputStream inObject = null;
                    try {
                        inFile = new FileInputStream(file);
                        inObject = new ObjectInputStream(inFile);
                        List<Event> events = (List<Event>) inObject.readObject();
                        restoreEventList.addAll(events);
                    } catch (Exception e) {
                        logger.warn("Failed to restore event.", e);
                    } finally {
                        if (inObject != null) {
                            try {
                                inObject.close();
                            } catch (Exception e) {

                            }
                        }
                        if (inFile != null) {
                            try {
                                inFile.close();
                            } catch (Exception e) {

                            }
                        }
                        file.delete();
                    }
                }
            }
            if (logger.isInfoEnabled()) {
                logger.info("Restored Event Num: " + restoreEventList.size());
            }

            int eventCount = 0;
            for (Event event : restoreEventList) {
                if (logger.isDebugEnabled()) {
                    StringBuilder logBuffer = new StringBuilder("Restored event-> ");
                    for (Map.Entry<String, Object> entry : event.entrySet()) {
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

        private boolean exsitsBackupEvent(File dir) {
            if (!dir.isDirectory()) {
                return false;
            }
            File[] files = dir.listFiles();
            if (files.length == 0) {
                return false;
            }

            boolean ret = false;
            for (File file : files) {
                if (file.getName().startsWith(filePrefix)) {
                    ret = true;
                    break;
                }
            }
            return ret;
        }
    }
}
