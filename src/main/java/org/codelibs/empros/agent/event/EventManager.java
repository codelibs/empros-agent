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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

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

    Queue<Event> eventQueue = new ConcurrentLinkedQueue<Event>();

    private final AtomicBoolean running = new AtomicBoolean(false);

    private MonitoringThread monitoringThread;

    public EventManager(final int eventSizeInRequest, final int requestPoolSize) {
        this.eventSizeInRequest = eventSizeInRequest;
        maxPoolSize = requestPoolSize;
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
    }

    public void addEvent(final Event event) {
        if (event == null) {
            logger.warn("Added event is null.");
            return;
        }
        eventQueue.add(event);
    }

    public void submit() {
        synchronized (this) {
            notify();
        }
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
                    final Set<Event> eventSet = new LinkedHashSet<>();
                    while (eventSet.size() < eventSizeInRequest) {
                        final Event event = eventQueue.poll();
                        if (event == null) {
                            break;
                        }
                        // TODO filter

                        eventSet.add(event);
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

}
