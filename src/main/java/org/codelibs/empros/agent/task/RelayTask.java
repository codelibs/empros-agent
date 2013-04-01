package org.codelibs.empros.agent.task;

import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.codelibs.empros.agent.event.EmprosEvent;
import org.codelibs.empros.agent.event.EventManager;
import org.codelibs.empros.agent.operation.EventOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RelayTask extends TimerTask {
    private Logger logger = LoggerFactory.getLogger(RelayTask.class);

    private EventManager manager;

    private EventOperation operation;

    private ExecutorService pool;

    private int maxPoolSize = 10;

    public RelayTask(EventManager manager, EventOperation operation) {
        this.manager = manager;
        this.operation = operation;
        this.pool = Executors.newFixedThreadPool(maxPoolSize);
    }

    @Override
    public void run() {
        if (manager.getEventNum() > 0) {
            List<EmprosEvent> events = manager.getEvents(true);
            if (logger.isDebugEnabled()) {
                logger.debug("eventNum:" + events.size());
            }

            if (events.size() > 0) {
                ExcuteEventTask excuteEventTask = new ExcuteEventTask(operation);
                excuteEventTask.setEvents(events);
                pool.execute(excuteEventTask);
            }
        }
    }

    @Override
    public boolean cancel() {
        boolean ret = super.cancel();
        try {
            pool.shutdown();
            if (!pool.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                logger.info("Cancel RelayTask 1");
                pool.shutdownNow();
            } else {
                logger.info("Cancel RelayTask 2");
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            logger.info("Cancel RelayTask 3");
        }
        return ret;
    }
}
