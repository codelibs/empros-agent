package org.codelibs.empros.agent.task;

import java.util.List;
import java.util.Map;
import java.util.TimerTask;

import org.codelibs.empros.agent.manager.EventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RelayTask extends TimerTask {
    private Logger logger = LoggerFactory.getLogger(RelayTask.class);

    private EventManager manager;

    private ExcuteEventTask excuteEventTask;

    public RelayTask(EventManager manager, ExcuteEventTask excuteEventTask) {
        this.manager = manager;
        this.excuteEventTask = excuteEventTask;
    }

    @Override
    public void run() {
        if (manager.getEventNum() > 0) {
            List<Map<String, String>> events = manager.getEvents(true);
            if (logger.isDebugEnabled()) {
                logger.debug("eventNum:" + events.size());
            }

            if (events.size() > 0) {
                excuteEventTask.setEvents(events);
            }
        }
    }
}
