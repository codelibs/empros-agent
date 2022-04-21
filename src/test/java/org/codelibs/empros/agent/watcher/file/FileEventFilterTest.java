package org.codelibs.empros.agent.watcher.file;

import org.codelibs.empros.agent.event.Event;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class FileEventFilterTest {
    @Test
    public void test_test() throws Exception {
        FileEventFilter fileEventFilter = new FileEventFilter();
        Event event = new Event();
        event.put(FileWatchTask.FILE, "/tmp/aaa.txt");
        assertNotNull(fileEventFilter.convert(event));
    }
}
