package org.codelibs.empros.agent.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.codelibs.empros.agent.operation.EventOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExcuteEventTask extends Thread {
    private Logger logger = LoggerFactory.getLogger(ExcuteEventTask.class);

    private CountDownLatch latch = null;

    private List<List<Map<String, String>>> requestEvents = Collections
            .synchronizedList(new ArrayList<List<Map<String, String>>>());

    private EventOperation operation;

    public ExcuteEventTask(EventOperation operation) {
        this.operation = operation;
    }

    private void excute(List<Map<String, String>> events) {
        operation.excute(events);
    }

    private synchronized List<Map<String, String>> getEvents()
            throws InterruptedException {
        if (requestEvents.size() == 0) {
            return null;
        }
        List<Map<String, String>> events = requestEvents.get(0);
        requestEvents.remove(0);
        return events;
    }

    public synchronized void setEvents(List<Map<String, String>> events) {
        requestEvents.add(events);
        if (latch != null && latch.getCount() > 0) {
            latch.countDown();
        }
    }

    @Override
    public void run() {
        logger.info("===== Running =====");

        while (true) {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                List<Map<String, String>> events = getEvents();
                if (events == null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Waiting for Event...");
                    }
                    synchronized (this) {
                        latch = new CountDownLatch(1);
                    }

                    // wait event
                    latch.await();

                    synchronized (this) {
                        latch = null;
                    }
                    if (logger.isDebugEnabled()) {
                        logger.debug("Restart");
                    }
                    continue;
                }
                excute(events);
            } catch (InterruptedException e) {
                logger.info("Interrupted. ");
                break;
            } catch (Exception e) {
                logger.warn("exception: " + e.getMessage());
                e.printStackTrace();
            }
        }

        logger.info("===== Finished =====");

    }
}
