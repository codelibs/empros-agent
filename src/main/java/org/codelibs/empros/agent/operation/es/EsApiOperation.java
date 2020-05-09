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
package org.codelibs.empros.agent.operation.es;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.conn.HttpClientConnectionManager;
import org.codelibs.elasticsearch.client.HttpClient;
import org.codelibs.empros.agent.event.Event;
import org.codelibs.empros.agent.exception.EmprosSystemException;
import org.codelibs.empros.agent.listener.OperationListener;
import org.codelibs.empros.agent.operation.Operation;
import org.codelibs.empros.agent.util.PropertiesUtil;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EsApiOperation implements Operation {

    private static final Logger logger = LoggerFactory.getLogger(EsApiOperation.class);

    private static final String EMPROSAPI_PROPERTIES = "emprosapi.properties";

    private static final String ES_FIELD_ENCODING = StandardCharsets.UTF_8.toString();

    private final String esHosts;

    private final String esIndex;

    private final int requestInterval;

    private final Client client;

    private final List<OperationListener> listenerList = new ArrayList<>();

    private final ApiMonitor apiMonitor;

    private final Timer apiMonitorTimer;

    private final long apiMonitorInterval;

    private final AtomicBoolean apiAvailable = new AtomicBoolean(false);

    public EsApiOperation() {
        esHosts = PropertiesUtil.getAsString(EMPROSAPI_PROPERTIES, "esHosts", "");

        if (esHosts.isEmpty()) {
            throw new EmprosSystemException("esHosts is empty.");
        }

        esIndex = PropertiesUtil.getAsString(EMPROSAPI_PROPERTIES, "esIndex", "empros");
        //esType = PropertiesUtil.getAsString(EMPROSAPI_PROPERTIES, "esType", "event");

        requestInterval = PropertiesUtil.getAsInt(EMPROSAPI_PROPERTIES,
                "requestInterval", 100);
        apiMonitorInterval = PropertiesUtil.getAsLong(EMPROSAPI_PROPERTIES,
                "apiMonitorInterval", 10 * 1000L);

        client = createHttpClient(esHosts);

        apiMonitor = new ApiMonitor();
        apiMonitorTimer = new Timer();
        apiMonitorTimer.schedule(apiMonitor, 0, apiMonitorInterval);
    }

    protected Client createHttpClient(final String host) {
        final Settings.Builder builder = Settings.builder().putList("http.hosts", host.split(","))
                .put("processors", PropertiesUtil.getAsInt(EMPROSAPI_PROPERTIES, "availableProcessors", Runtime.getRuntime().availableProcessors()));
        return new HttpClient(builder.build(), null);
    }

    @Override
    public void destroy() {
        apiMonitorTimer.cancel();
        try {
            client.close();
        } catch (final ElasticsearchException e) {
            logger.warn("Failed to close Client: {}", client, e);
        }
    }

    @Override
    public void addOperationListener(OperationListener listener) {
        listenerList.add(listener);
    }

    @Override
    public void excute(final List<Event> eventList) {
        if (!apiAvailable.get()) {
            callbackResultError(eventList);
            return;
        }

        boolean isSuccess = false;

        try {
            final BulkRequestBuilder bulkRequest = client.prepareBulk();
            for (final Event event : eventList) {
                final XContentBuilder builder = jsonBuilder().startObject();
                for (final Map.Entry<String, Object> entry : event.entrySet()) {
                    builder.field(entry.getKey(),
                            new String(
                                    entry.getValue().toString()
                                            .getBytes(ES_FIELD_ENCODING),
                                    ES_FIELD_ENCODING));
                }
                builder.endObject();
                bulkRequest.add(new IndexRequest(esIndex).source(builder));
                if (logger.isDebugEnabled()) {
                    logger.debug("Event: {}", event);
                }
            }
            final BulkResponse bulkResponse = bulkRequest.execute().actionGet();

            if (bulkResponse.hasFailures()) {
                isSuccess = false;
                logger.warn("bulkResponse.buildFailureMessage: {}", bulkResponse.buildFailureMessage());
            } else {
                isSuccess = true;
            }
        } catch (final Exception e) {
            logger.warn("Could not put: {}:{}", esHosts, e);
            isSuccess = false;
        }

        sleep();

        if (isSuccess) {
            callbackResultSuccess(eventList);
        } else {
            callbackResultError(eventList);
        }
    }

    protected void sleep() {
        try {
            Thread.sleep(requestInterval);
        } catch (final InterruptedException e) {
            // ignore
        }
    }

    private void callbackResultSuccess(List<Event> eventList) {
        if (!listenerList.isEmpty()) {
            for (OperationListener listener : listenerList) {
                listener.successHandler(eventList);
            }
        }
    }

    private void callbackResultError(List<Event> eventList) {
        if (!listenerList.isEmpty()) {
            for (OperationListener listener : listenerList) {
                listener.errorHandler(eventList);
            }
        }
    }

    private void callbackResoted() {
        if (!listenerList.isEmpty()) {
            for (OperationListener listener : listenerList) {
                listener.restoredHandler();
            }
        }
    }

    public static class ConnectionMonitor extends Thread {

        private final HttpClientConnectionManager clientConnectionManager;

        private volatile boolean shutdown = false;

        private final long connectionCheckInterval;

        private final long idleConnectionTimeout;

        public ConnectionMonitor(
                final HttpClientConnectionManager clientConnectionManager,
                final long connectionCheckInterval,
                final long idleConnectionTimeout) {
            super();
            this.clientConnectionManager = clientConnectionManager;
            this.connectionCheckInterval = connectionCheckInterval;
            this.idleConnectionTimeout = idleConnectionTimeout;
        }

        @Override
        public void run() {
            while (!shutdown) {
                synchronized (this) {
                    try {
                        wait(connectionCheckInterval);
                        // Close expired connections
                        clientConnectionManager.closeExpiredConnections();
                        // Close idle connections
                        clientConnectionManager.closeIdleConnections(
                                idleConnectionTimeout, TimeUnit.MILLISECONDS);
                    } catch (final Exception e) {
                        logger.warn(
                                "A connection monitoring exception occurs.", e);
                    }
                }
            }
        }

        public void shutdown() {
            shutdown = true;
            synchronized (this) {
                notifyAll();
            }
        }
    }

    protected class ApiMonitor extends TimerTask {
        @Override
        public void run() {
            logger.info("monitoring");
            boolean before = apiAvailable.get();
            boolean after = isReachable();
            apiAvailable.set(after);
            if (!after) {
                if (after != before) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Api Monitoring. Server is not available. {}", esHosts);
                    }
                }
            } else if (after != before) {
                if (logger.isInfoEnabled()) {
                    logger.info("Api Monitoring. Server was restored. {}", esHosts);
                }
                callbackResoted();
            }

        }

        private boolean isReachable() {
            if (!client.prepareGet().setIndex(esIndex).execute().actionGet().isExists()) {
                logger.warn("Failed to monitor api. {}", esHosts);
                return false;
            }
            return true;
        }
    }

}
