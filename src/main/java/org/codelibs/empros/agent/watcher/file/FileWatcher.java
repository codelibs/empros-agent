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
package org.codelibs.empros.agent.watcher.file;

import java.io.File;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.StringUtils;
import org.codelibs.empros.agent.event.EventManager;
import org.codelibs.empros.agent.util.PropertiesUtil;
import org.codelibs.empros.agent.watcher.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWatcher implements Watcher {
    private final Logger logger = LoggerFactory.getLogger(FileWatcher.class);

    private static final String FILEWATCHER_PROPERTIES = "filewatcher.properties";

    private final List<FileWatchTask> fileWatcherList = new ArrayList<FileWatchTask>();

    private final AtomicBoolean started = new AtomicBoolean(false);

    private EventManager eventManager;

    /* (non-Javadoc)
     * @see org.codelibs.empros.agent.watcher.Watcher#start()
     */
    @Override
    @SuppressWarnings("restriction")
    public void start() {
        if (started.getAndSet(true)) {
            return;
        }

        int count = 0;
        while (true) {
            count++;

            final String key = "watchPath" + count;
            final String value = PropertiesUtil.getAsString(
                    FILEWATCHER_PROPERTIES, key, null);
            if (StringUtils.isEmpty(value)) {
                break;
            }

            final File file = new File(value);
            if (!file.isDirectory()) {
                continue;
            }

            final String kindStr = PropertiesUtil.getAsString(
                    FILEWATCHER_PROPERTIES + ".kinds", key,
                    "create,modify,delete,overflow");
            final String[] kinds = kindStr.split(",");
            final List<Kind<?>> kindList = new ArrayList<Kind<?>>(4);
            for (final String kind : kinds) {
                if (StringUtils.isNotBlank(kind)) {
                    switch (kind.trim()) {
                        case "create":
                            kindList.add(StandardWatchEventKinds.ENTRY_CREATE);
                            break;
                        case "modify":
                            kindList.add(StandardWatchEventKinds.ENTRY_MODIFY);
                            break;
                        case "delete":
                            kindList.add(StandardWatchEventKinds.ENTRY_DELETE);
                            break;
                        case "overflow":
                            kindList.add(StandardWatchEventKinds.OVERFLOW);
                            break;
                        default:
                            logger.warn("unknown kind: {}", kind);
                            break;
                    }
                }
            }

            final String modifierStr = PropertiesUtil.getAsString(
                    FILEWATCHER_PROPERTIES + ".modifiers", key, "");
            final String[] modifiers = modifierStr.split(",");
            final List<WatchEvent.Modifier> modifierList = new ArrayList<WatchEvent.Modifier>(
                    4);
            for (final String modifier : modifiers) {
                if (StringUtils.isNotBlank(modifier)) {
                    switch (modifier.trim()) {
                        case "fileTree":
                            modifierList
                                    .add(com.sun.nio.file.ExtendedWatchEventModifier.FILE_TREE);
                            break;
                        default:
                            logger.warn("unknown modifier: {}", modifier);
                            break;
                    }
                }
            }

            fileWatcherList.add(new FileWatchTask(eventManager, file.toPath(),
                    kindList.toArray(new Kind[kindList.size()]), modifierList
                    .toArray(new WatchEvent.Modifier[modifierList
                            .size()])));

        }

        if (fileWatcherList.isEmpty()) {
            logger.info("Cannot find path setting.");
            // TODO shutdown fook
            return;
        }

        for (final FileWatchTask fileWatchTask : fileWatcherList) {
            fileWatchTask.start();
        }

    }

    /* (non-Javadoc)
     * @see org.codelibs.empros.agent.watcher.Watcher#stop()
     */
    @Override
    public void stop() {
        if (!started.getAndSet(false)) {
            return;
        }

        for (final FileWatchTask fileWatchTask : fileWatcherList) {
            fileWatchTask.interrupt();
        }

        for (final FileWatchTask fileWatchTask : fileWatcherList) {
            try {
                fileWatchTask.join();
            } catch (final InterruptedException e) {
            }
        }
        fileWatcherList.clear();
    }

    /* (non-Javadoc)
     * @see org.codelibs.empros.agent.watcher.Watcher#setEventManager(org.codelibs.empros.agent.event.EventManager)
     */
    @Override
    public void setEventManager(final EventManager eventManager) {
        this.eventManager = eventManager;
    }

}
