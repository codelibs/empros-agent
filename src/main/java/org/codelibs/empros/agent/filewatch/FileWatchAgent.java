package org.codelibs.empros.agent.filewatch;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Timer;

import org.apache.commons.lang.StringUtils;
import org.codelibs.empros.agent.EmprosAgent;
import org.codelibs.empros.agent.manager.EventManager;
import org.codelibs.empros.agent.operation.EventOperation;
import org.codelibs.empros.agent.task.ExcuteEventTask;
import org.codelibs.empros.agent.task.RelayTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWatchAgent extends EmprosAgent {
    private Logger logger = LoggerFactory.getLogger(FileWatchAgent.class);

    private List<String> watchPaths = new ArrayList<String>();

    private List<FileWatcher> fileWatcherList;

    @Override
    public void start(EventManager manager, EventOperation operation) {
        logger.info("FileEventAgent is started.");

        loadProperties();

        if (watchPaths.size() == 0) {
            logger.info("Cannot find path setting.");
            return;
        }

        excuteEventTask = new ExcuteEventTask(operation);
        excuteEventTask.setPriority(Thread.NORM_PRIORITY);
        excuteEventTask.start();

        fileWatcherList = new ArrayList<FileWatcher>();
        for (String path : watchPaths) {
            FileWatcher fw = new FileWatcher(path, manager);
            fw.setPriority(Thread.MAX_PRIORITY);
            fw.start();
            fileWatcherList.add(fw);
        }

        task = new RelayTask(manager, excuteEventTask);
        timer = new Timer();
        timer.schedule(task, poolInterval, poolInterval);
    }

    @Override
    public void stop() {
        super.stop();

        for (FileWatcher fw : fileWatcherList) {
            fw.interrupt();
        }
        for (FileWatcher fw : fileWatcherList) {
            try {
                fw.join();
            } catch (InterruptedException e) {

            }
        }
    }

    @Override
    protected void loadProperties() {
        Properties prop = new Properties();

        InputStream in = null;
        try {
            in = ClassLoader
                    .getSystemResourceAsStream("filewatch-agent.properties");
            prop.load(in);

            poolInterval = Long.parseLong(prop.getProperty("poolInterval"));

            int count = 1;
            while (true) {
                String tmp = prop.getProperty("watchPath" + count);
                if (StringUtils.isEmpty(tmp)) {
                    break;
                }

                count++;
                if (!(new File(tmp)).exists()) {
                    continue;
                }

                watchPaths.add(tmp);
            }

        } catch (IOException e) {
            logger.warn(e.getMessage());
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
