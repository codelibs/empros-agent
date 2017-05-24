/*
 * Copyright 2013 the CodeLibs Project and the Others.
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

import org.codelibs.empros.agent.operation.es.EsApiOperation;
import org.codelibs.empros.agent.operation.rest.RestApiOperation;
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
        } else {
            logger.warn("Unexpected args: " + command);
        }
    }

    private static synchronized EmprosAgent getAgent() {
        if (agent == null) {
            // TODO DI?
            agent = new EmprosAgent();
            final String apiType = PropertiesUtil
                    .getAsString(EMPROSAPI_PROPERTIES, "apiType", "");
            if (apiType.equals("es")) {
                agent.setOperation(new EsApiOperation());
            } else {
                agent.setOperation(new RestApiOperation());
            }
            agent.setWatcher(new FileWatcher());
        }
        return agent;
    }

    private static synchronized void removeAgent() {
        agent = null;
    }
}
