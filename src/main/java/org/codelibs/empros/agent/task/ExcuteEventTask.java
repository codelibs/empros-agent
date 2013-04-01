package org.codelibs.empros.agent.task;

import java.util.ArrayList;
import java.util.List;

import org.codelibs.empros.agent.event.EmprosEvent;
import org.codelibs.empros.agent.operation.EventOperation;

public class ExcuteEventTask implements Runnable {
    private List<EmprosEvent> requestEvents = null;

    private EventOperation operation;

    public ExcuteEventTask(EventOperation operation) {
        this.operation = operation;
    }

    private void excute(List<EmprosEvent> events) {
        operation.excute(events);
    }

    public void setEvents(List<EmprosEvent> events) {
        requestEvents = new ArrayList<EmprosEvent>(events);
    }

    @Override
    public void run() {
        excute(requestEvents);
    }
}
