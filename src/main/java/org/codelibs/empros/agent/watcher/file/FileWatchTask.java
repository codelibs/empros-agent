/*
 * Copyright 2013 the CodeLibs Project and the Others.
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

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Date;

import org.codelibs.empros.agent.event.Event;
import org.codelibs.empros.agent.event.Event.EventComparator;
import org.codelibs.empros.agent.event.EventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWatchTask extends Thread {
    private static final Logger logger = LoggerFactory
            .getLogger(FileWatchTask.class);

    public static final String CREATE = "create";

    public static final String MODIFY = "modify";

    public static final String DELETE = "delete";

    public static final String OVERFLOW = "overflow";

    public static final String FILE = "filepath";

    public static final String KIND = "kind";

    public static final String TIMESTAMP = "timestamp";

    private static final EventComparator EVENT_COMPARATOR = new FileEventComerator();

    private final Path watchPath;

    private final EventManager manager;

    private final Kind<?>[] kinds;

    private final WatchEvent.Modifier[] modifiers;

    public FileWatchTask(final EventManager manager, final Path watchPath,
                         final Kind<?>[] kinds, final WatchEvent.Modifier[] modifiers) {
        super();
        setPriority(Thread.MAX_PRIORITY);

        this.manager = manager;
        this.watchPath = watchPath;
        this.kinds = kinds;
        this.modifiers = modifiers;
    }

    @Override
    public void run() {

        if (logger.isInfoEnabled()) {
            logger.info("===== Started watching >> " + watchPath + " =====");
        }

        WatchService watcher;
        WatchKey watchKey;

        final FileSystem fs = watchPath.getFileSystem();
        try {
            watcher = fs.newWatchService();
            watchKey = watchPath.register(watcher, kinds, modifiers);
        } catch (final Exception e) {
            logger.warn("Failed to create watcher.", e);
            return;
        }

        try {
            while (watchKey.isValid()) {
                try {
                    final WatchKey retrieveKey = watcher.take();

                    final long timestamp = (new Date()).getTime();
                    for (final WatchEvent<?> event : retrieveKey.pollEvents()) {
                        String kind;
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            kind = CREATE;
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            kind = MODIFY;
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            kind = DELETE;
                        } else if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            kind = OVERFLOW;
                        } else {
                            logger.warn("unknown kind: {}", event.kind()
                                    .toString());
                            continue;
                        }

                        final Path context = (Path) event.context();
                        final Path path = watchPath.resolve(context);
                        if (logger.isDebugEnabled()) {
                            logger.debug("{}: count={} {}", event.kind(), event.count(), path);
                        }

                        final Event fileEvent = createEvent(kind, path, timestamp);
                        manager.addEvent(fileEvent);
                    }

                    manager.submit();
                    retrieveKey.reset();
                } catch (final InterruptedException e) {
                    watchKey.cancel();
                    break;
                } catch (final Exception e) {
                    logger.warn("Monitoring exception.", e);
                }
            }
        } finally {
            try {
                watcher.close();
            } catch (final IOException e) {
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("===== Finished =====");
        }
    }

    public static Event createEvent(final String kind, final Path path, final long timestamp) {
        final Event fileEvent = new Event();
        fileEvent.put(FILE, path.toString().replace("\\", "/"));
        fileEvent.put(KIND, kind);
        fileEvent.put(TIMESTAMP, timestamp);
        fileEvent.setEventComparator(EVENT_COMPARATOR);
        return fileEvent;
    }

    public static class FileEventComerator implements EventComparator, Serializable {
        @Override
        public int hashCode(final Event self) {
            final Object path = self.get(FILE);
            if (path != null) {
                return path.hashCode();
            }
            logger.warn("A value of {} is not found.", FILE);
            return self.hashCode(false);
        }

        @Override
        public boolean equals(final Event self, final Object target) {
            if (target instanceof Event) {
                final Object selfPath = self.get(FILE);
                return selfPath != null
                        && selfPath.equals(((Event) target).get(FILE));
            }
            return false;
        }
    }

}
