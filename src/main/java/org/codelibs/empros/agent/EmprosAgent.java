package org.codelibs.empros.agent;

import java.util.Timer;

import org.codelibs.empros.agent.event.EventManager;
import org.codelibs.empros.agent.operation.EventOperation;
import org.codelibs.empros.agent.task.ExcuteEventTask;
import org.codelibs.empros.agent.task.RelayTask;
import org.codelibs.empros.agent.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmprosAgent {
    private Logger logger = LoggerFactory.getLogger(EmprosAgent.class);

    private long poolInterval = 60000;

    private RelayTask task;

    private Timer timer;

    private ExcuteEventTask excuteEventTask;

    private EventManager manager = new EventManager();

    private boolean isStarted = false;

    public EventManager getEventManager() {
        return manager;
    }

    public void start(EventOperation operation) {
        logger.info("EmprosAgent is started.");
        if(operation == null) {
            return;
        }
        if(isStarted()) {
            return;
        }

        poolInterval = Long.parseLong(PropertiesUtil.loadProperties("agent.properties", "poolInterval"));

        excuteEventTask = new ExcuteEventTask(operation);
        excuteEventTask.start();

        task = new RelayTask(manager, excuteEventTask);
        timer = new Timer();
        timer.schedule(task, poolInterval, poolInterval);

        isStarted = true;
    }

    public void stop() {
        if(!isStarted()) {
            return;
        }

        task.cancel();
        timer.cancel();
        excuteEventTask.interrupt();

        // waiting for the stop of threads.
        try {
            excuteEventTask.join();
        } catch (InterruptedException e) {

        }

        task = null;
        timer = null;
        excuteEventTask = null;
        isStarted = false;
    }

    public boolean isStarted() {
        return isStarted;
    }

}
