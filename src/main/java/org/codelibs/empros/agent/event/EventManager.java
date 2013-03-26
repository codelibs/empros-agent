package org.codelibs.empros.agent.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventManager {
    private Logger logger = LoggerFactory.getLogger(EventManager.class);

    private List<EmprosEvent> eventList = Collections
            .synchronizedList(new ArrayList<EmprosEvent>());

    public synchronized void addEvent(EmprosEvent event) {
        if (event == null) {
            logger.warn("Added event is null.");
            return;
        }
        eventList.add(event);
    }

    public synchronized List<EmprosEvent> getEvents(boolean isClear) {
        List<EmprosEvent> list = Collections
                .synchronizedList(new ArrayList<EmprosEvent>(eventList));

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
