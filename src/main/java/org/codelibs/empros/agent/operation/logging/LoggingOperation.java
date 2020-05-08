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
package org.codelibs.empros.agent.operation.logging;

import org.codelibs.empros.agent.event.Event;
import org.codelibs.empros.agent.listener.OperationListener;
import org.codelibs.empros.agent.operation.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LoggingOperation implements Operation {
    private static final Logger logger = LoggerFactory.getLogger(LoggingOperation.class);
    private final List<OperationListener> listenerList = new ArrayList<>();
    private final AtomicInteger eventCount = new AtomicInteger();

    @Override
    public void addOperationListener(OperationListener listener) {
        listenerList.add(listener);
    }

    @Override
    public void excute(final List<Event> events) {
        for (final Event event: events) {
            logger.info("count:{} Event:{} ", eventCount.incrementAndGet(), event);
        }

        callbackResultSuccess(events);
    }

    @Override
    public void destroy() {
        // do nothing
    }

    private void callbackResultSuccess(final List<Event> eventList) {
        if (!listenerList.isEmpty()) {
            for (final OperationListener listener : listenerList) {
                listener.successHandler(eventList);
            }
        }
    }

    private void callbackResultError(final List<Event> eventList) {
        if (!listenerList.isEmpty()) {
            for (final OperationListener listener : listenerList) {
                listener.errorHandler(eventList);
            }
        }
    }
}
