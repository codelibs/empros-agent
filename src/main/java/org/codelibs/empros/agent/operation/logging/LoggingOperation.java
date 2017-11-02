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
    private final List<OperationListener> listenerList = new ArrayList<OperationListener>();
    private final AtomicInteger eventCount = new AtomicInteger();

    @Override
    public void addOperationListener(OperationListener listener) {
        listenerList.add(listener);
    }

    @Override
    public void excute(List<Event> events) {
        for (final Event event: events ) {
            logger.info("count:{} Event:{} ", eventCount.incrementAndGet(), event);
        }
    }

    @Override
    public void destroy() {

    }

    private void callbackResultSuccess(List<Event> eventList) {
        if (listenerList.size() > 0) {
            for (OperationListener listener : listenerList) {
                listener.successHandler(eventList);
            }
        }
    }

    private void callbackResultError(List<Event> eventList) {
        if (listenerList.size() > 0) {
            for (OperationListener listener : listenerList) {
                listener.errorHandler(eventList);
            }
        }
    }
}
