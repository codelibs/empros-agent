package org.codelibs.empros.agent;

import java.util.concurrent.CountDownLatch;

import org.codelibs.empros.agent.event.EventManager;
import org.codelibs.empros.agent.filewatcher.FileWatcher;
import org.codelibs.empros.agent.operation.EventOperation;
import org.codelibs.empros.agent.operation.impl.EmprosRestApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static CountDownLatch latch = null;

    public static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger(Main.class);

        if (args.length == 0 || args[0].equals("start")) {
            logger.info("Windows Service start.");
            if (latch != null) {
                return;
            }
            EmprosAgent agent = new EmprosAgent();
            EventOperation operation = new EmprosRestApiOperation();
            agent.start(operation);

            EventManager manager = agent.getEventManager();
            FileWatcher watcher = new FileWatcher();
            watcher.start(manager);

            latch = new CountDownLatch(1);
            try {
                latch.await();
            } catch (InterruptedException e) {

            }
            agent.stop();
            latch = null;

            logger.info("Application is finished.");
        } else if (args[0].equals("stop")) {
            logger.info("Windows Service stop.");
            if (latch != null) {
                latch.countDown();
            } else {
                logger.warn("CountDownLatch is null.");
            }
        } else {
            logger.warn("Unexpected args: " + args[0]);
        }
    }
}