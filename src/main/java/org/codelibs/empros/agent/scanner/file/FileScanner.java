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
package org.codelibs.empros.agent.scanner.file;

import org.codelibs.core.lang.StringUtil;
import org.codelibs.empros.agent.event.Event;
import org.codelibs.empros.agent.event.EventManager;
import org.codelibs.empros.agent.scanner.Scanner;
import org.codelibs.empros.agent.util.PropertiesUtil;
import org.codelibs.empros.agent.watcher.file.FileWatchTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileScanner implements Scanner {
    private final Logger logger = LoggerFactory.getLogger(FileScanner.class);
    private static final String FILESCANNER_PROPERTIES = "filescanner.properties";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Queue<Path> targetDirs = new ConcurrentLinkedQueue<>();
    private final FileScanThread thread;
    private EventManager eventManager;
    private long timestamp = 0;

    public FileScanner() {
        thread = new FileScanThread();
        for (int count = 1; ; count++) {
            final String scanPath = PropertiesUtil.getAsString(
                FILESCANNER_PROPERTIES, "scanPath" + count, null);
            if (StringUtil.isBlank(scanPath)) {
                break;
            }
            Path path = Paths.get(scanPath);
            if (!Files.isDirectory(path)) {
                logger.warn("Not directory. {}", path.toAbsolutePath());
                continue;
            }
            logger.info("Add path:{}", path.toAbsolutePath());
            targetDirs.add(path);
        }
    }

    @Override
    public void setEventManager(final EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @Override
    public void start() {
        timestamp = System.currentTimeMillis();
        running.set(true);
        thread.start();
    }

    @Override
    public void stop() {
        running.set(false);
        thread.interrupt();
    }

    @Override
    public boolean isRunning() {
        if (!running.get()) {
            return false;
        }
        if (targetDirs.isEmpty() && !thread.executing.get()) {
            return false;
        }
        return true;
    }

    protected class FileScanThread extends Thread {
        protected final AtomicBoolean executing = new AtomicBoolean(false);

        @Override
        public void run() {
            executing.set(true);
            logger.info("Start FileScanThread");
            while (FileScanner.this.running.get()) {
                final Path targetDir = targetDirs.poll();
                if (targetDir == null) {
                    executing.set(false);
                    logger.info("Finished FileScanThread");
                    break;
                }
                if (!Files.isDirectory(targetDir)) {
                    logger.warn("Invalid directory. {}", targetDir);
                }
                try {
                    Files.walkFileTree(targetDir, new SimpleFileVisitor<Path>() {
                        int counter = 0;

                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                            return super.preVisitDirectory(dir, attrs);
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            if (exc != null) {
                                logger.warn("Failed to visit directory.", exc);
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return super.postVisitDirectory(dir, exc);
                        }


                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Event event = FileWatchTask.createEvent(FileWatchTask.CREATE, file, FileScanner.this.timestamp, Collections.emptyList());
                            eventManager.addEvent(event);
                            counter++;
                            if (counter > 1000) {
                                counter = 0;
                                eventManager.submit();
                            }
                            return super.visitFile(file, attrs);
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                            if (file == null) {
                                logger.warn("file is null");
                            }
                            if (exc != null) {
                                logger.warn("Failed to visit file.", exc);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                    eventManager.submit();
                } catch (final Throwable t) {
                    logger.warn("File scan error.", t);
                }
            }
        }
    }
}
