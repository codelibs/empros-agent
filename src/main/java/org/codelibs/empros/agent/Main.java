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
package org.codelibs.empros.agent;

import org.codelibs.empros.agent.operation.Operation;
import org.codelibs.empros.agent.operation.es.EsApiOperation;
import org.codelibs.empros.agent.operation.logging.LoggingOperation;
import org.codelibs.empros.agent.operation.rest.RestApiOperation;
import org.codelibs.empros.agent.scanner.file.FileScanner;
import org.codelibs.empros.agent.util.PropertiesUtil;
import org.codelibs.empros.agent.watcher.file.FileWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static EmprosAgent agent;

    private static final String EMPROSAPI_PROPERTIES = "emprosapi.properties";

   public static void main(final String[] args) {
        String command = null;
        if (args.length > 0) {
            command = args[0];
        }

        if ("start".equals(command)) {
            logger.info("Starting Windows Service.");

            final EmprosAgent agent = getAgent();

            if (!agent.start()) {
                return;
            }

            agent.await();

            removeAgent();
            agent.destroy();

            logger.info("Application is finished.");
        } else if ("stop".equals(command)) {
            logger.info("Stopping Windows Service.");

            final EmprosAgent agent = getAgent();
            agent.stop();
        } else if ("scan".equals(command)) {
            EmprosAgent agent = new EmprosAgent();
            agent.setOperation(getOperation());
            agent.setScanner(new FileScanner());
            if (!agent.scan()) {
                System.out.println("Failed to start scan.");
                System.exit(1);
            }
            agent.destroy();
            logger.info("Application is finished.");
            System.exit(0);
        } else {
            logger.warn("Unexpected args: " + command);
        }
    }

    private static synchronized EmprosAgent getAgent() {
        if (agent == null) {
            // TODO DI?
            agent = new EmprosAgent();
            agent.setOperation(getOperation());
            agent.setWatcher(new FileWatcher());
        }
        return agent;
    }

    private static synchronized Operation getOperation() {
        final String apiType = PropertiesUtil
            .getAsString(EMPROSAPI_PROPERTIES, "apiType", "");
        Operation operation;
        if (apiType.equals("es")) {
            operation = new EsApiOperation();
        } else if(apiType.equals("logging")) {
            operation = new LoggingOperation();
        } else {
            operation = new RestApiOperation();
        }
        return operation;
    }

    private static synchronized void removeAgent() {
        agent = null;
    }
}
