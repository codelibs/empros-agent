package org.codelibs.empros.agent.filewatcher;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.nio.file.WatchEvent.Kind;

import org.codelibs.empros.agent.event.EmprosEvent;
import org.codelibs.empros.agent.event.EventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWatchTask extends Thread {
    private Logger logger = LoggerFactory.getLogger(FileWatchTask.class);

    private Path watchPath;

    private EventManager manager;

    private WatchEvent.Modifier[] extModifiers = { com.sun.nio.file.ExtendedWatchEventModifier.FILE_TREE };

    private long timeout = 1000;

    private WatchService watcher = null;

    private WatchKey key = null;

    private final String CREATE = "create";

    private final String MODIFY = "modify";

    private final String DELETE = "delete";

    private final String FILE = "filepath";

    private final String KIND = "kind";

    public FileWatchTask(String watchPath, EventManager manager) {
        super();
        this.watchPath = new File(watchPath).toPath();
        this.manager = manager;
    }

    @Override
    public void run() {
        logger.info("===== Started watching >> " + watchPath + " =====");
        FileSystem fs = watchPath.getFileSystem();
        try {
            watcher = fs.newWatchService();

            key = watchPath.register(watcher, new Kind[] {
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.OVERFLOW }, extModifiers);
        } catch (Exception e) {
            logger.warn("Failed to create watcher.");
            return;
        }

        while (key.isValid()) {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    key.cancel();
                    watcher.close();
                    throw new InterruptedException();
                }

                WatchKey retrieveKey = watcher.poll(timeout,
                        TimeUnit.MILLISECONDS);
                if (retrieveKey == null) {
                    continue;
                }

                for (WatchEvent<?> event : retrieveKey.pollEvents()) {
                    String kind;
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        kind = CREATE;
                    } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        kind = MODIFY;
                    } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                        kind = DELETE;
                    } else {
                        logger.warn(event.kind().toString()
                                + " Failed to watch file.");
                        continue;
                    }

                    Path context = (Path) event.context();
                    File file = new File(watchPath.toString(),
                            context.toString());
                    if (logger.isDebugEnabled()) {
                        logger.debug(event.kind() + ": count=" + event.count()
                                + " " + file.getAbsolutePath());
                    }

                    EmprosEvent fileEvent = new EmprosEvent();
                    fileEvent.put(FILE, file.getAbsolutePath().replace("\\", "/"));
                    fileEvent.put(KIND, kind);

                    filteringEvent(fileEvent);
                }

                retrieveKey.reset();
            } catch (InterruptedException e) {
                logger.info("Interrupted.");
                break;
            } catch (Exception e) {
                logger.warn(e.getMessage());
            }
        }

        logger.info("===== Finished =====");
    }

    private void filteringEvent(EmprosEvent event) {
        synchronized (manager) {
            List<EmprosEvent> eventList = manager.getEvents(false);

            boolean isExsists = false;

            String newFile = event.get(FILE);
            int size = eventList.size();

            for (int i = 0; i < size; i++) {
                Map<String, String> lastEvent = eventList.get(i);
                if (lastEvent.get(FILE).equals(newFile)) {
                    isExsists = true;

                    String newKind = event.get(KIND);
                    String oldKind = lastEvent.get(KIND);

                    if (!newKind.equals(oldKind)) {
                        if (oldKind.equals(CREATE) && newKind.equals(DELETE)) {
                            manager.remove(i);

                        } else if ((oldKind.equals(MODIFY) && newKind
                                .equals(CREATE))
                                || (oldKind.equals(DELETE) && newKind
                                        .equals(CREATE))) {
                            manager.remove(i);
                            event.put(KIND, MODIFY);
                            manager.addEvent(event);

                        } else if (oldKind.equals(MODIFY)
                                || oldKind.equals(DELETE)) {
                            manager.remove(i);
                            manager.addEvent(event);
                        }
                    }

                    break;
                }
            }

            if (!isExsists) {
                manager.addEvent(event);
            }
        }
    }
}
