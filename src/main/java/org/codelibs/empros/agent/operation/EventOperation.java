package org.codelibs.empros.agent.operation;

import java.util.List;
import java.util.Map;

public interface EventOperation {
    void excute(List<Map<String, String>> events);
}
