package org.codelibs.empros.agent.scanner;


import org.codelibs.empros.agent.event.EventManager;

public interface Scanner {
    void start();

    void stop();

    boolean isRunning();

    void setEventManager(EventManager eventManager);
}
