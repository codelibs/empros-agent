package org.codelibs.empros.agent.operation;

import java.util.List;

import org.codelibs.empros.agent.event.EmprosEvent;

public interface EventOperation {
    void excute(List<EmprosEvent> events);
}
