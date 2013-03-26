package org.codelibs.empros.agent.filewatcher;

import java.io.File;

import java.util.ArrayList;
import java.util.List;


import org.apache.commons.lang.StringUtils;
import org.codelibs.empros.agent.event.EventManager;
import org.codelibs.empros.agent.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWatcher {
    private Logger logger = LoggerFactory.getLogger(FileWatcher.class);

    private List<String> watchPaths = new ArrayList<String>();

    private List<FileWatchTask> fileWatcherList;

    private boolean isStarted = false;

    public void start(EventManager manager) {
        if(isStarted()) {
            return;
        }

        int count = 1;
        while (true) {
            String tmp = PropertiesUtil.loadProperties("filewatcher.properties", "watchPath" + count);
            if (StringUtils.isEmpty(tmp)) {
                break;
            }

            count++;
            if (!(new File(tmp)).exists()) {
                continue;
            }

            watchPaths.add(tmp);
        }

        if (watchPaths.size() == 0) {
            logger.info("Cannot find path setting.");
            return;
        }

        fileWatcherList = new ArrayList<FileWatchTask>();
        for (String path : watchPaths) {
            FileWatchTask fw = new FileWatchTask(path, manager);
            fw.setPriority(Thread.MAX_PRIORITY);
            fw.start();
            fileWatcherList.add(fw);
        }

        isStarted = true;
    }

    public void stop() {
        if(!isStarted()) {
            return;
        }

        for (FileWatchTask fw : fileWatcherList) {
            fw.interrupt();
        }
        for (FileWatchTask fw : fileWatcherList) {
            try {
                fw.join();
            } catch (InterruptedException e) {

            }
        }
        fileWatcherList.clear();
        watchPaths.clear();
        isStarted = false;
    }

    public boolean isStarted() {
        return isStarted;
    }
}
