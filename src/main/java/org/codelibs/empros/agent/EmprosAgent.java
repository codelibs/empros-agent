package org.codelibs.empros.agent;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Timer;

import org.codelibs.empros.agent.manager.EventManager;
import org.codelibs.empros.agent.operation.EventOperation;
import org.codelibs.empros.agent.task.ExcuteEventTask;
import org.codelibs.empros.agent.task.RelayTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmprosAgent {
    private Logger logger = LoggerFactory.getLogger(EmprosAgent.class);

    protected long poolInterval = 60000;

    protected RelayTask task;

    protected Timer timer;

    protected ExcuteEventTask excuteEventTask;

    public void start(EventManager manager, EventOperation operation) {
        logger.info("EmprosAgent is started.");

        loadProperties();

        excuteEventTask = new ExcuteEventTask(operation);
        excuteEventTask.start();

        task = new RelayTask(manager, excuteEventTask);
        timer = new Timer();
        timer.schedule(task, poolInterval, poolInterval);
    }

    public void stop() {
        task.cancel();
        timer.cancel();
        excuteEventTask.interrupt();

        // waiting for the stop of threads.
        try {
            excuteEventTask.join();
        } catch (InterruptedException e) {

        }
    }

    protected void loadProperties() {
        Properties prop = new Properties();

        InputStream in = null;
        try {
            in = ClassLoader.getSystemResourceAsStream("agent.properties");
            prop.load(in);

            if (prop.getProperty("poolInterval") != null) {
                poolInterval = Long.parseLong(prop.getProperty("poolInterval"));
            }
        } catch (IOException e) {
            logger.warn("Failed to load Property file.");
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e2) {
            }
        }
    }
}
