package org.codelibs.empros.agent.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventManager {
    private Logger logger = LoggerFactory.getLogger(EventManager.class);

    private List<Map<String, String>> eventList = Collections
            .synchronizedList(new ArrayList<Map<String, String>>());

    public synchronized void addEvent(ConcurrentHashMap<String, String> event) {
        if (event == null) {
            logger.warn("Added event is null.");
            return;
        }
        eventList.add(event);
    }

    public synchronized List<Map<String, String>> getEvents(boolean isClear) {
        List<Map<String, String>> list = Collections
                .synchronizedList(new ArrayList<Map<String, String>>(eventList));

        if (isClear) {
            // clear events
            clear();
        }

        return list;
    }

    public synchronized int getEventNum() {
        return eventList.size();
    }

    public synchronized void clear() {
        eventList.clear();
    }

    public synchronized void remove(int index) {
        eventList.remove(index);
    }
}
