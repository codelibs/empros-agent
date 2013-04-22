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
package org.codelibs.empros.agent.operation.rest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.codelibs.empros.agent.event.Event;
import org.codelibs.empros.agent.exception.EmprosSystemException;
import org.codelibs.empros.agent.operation.Operation;
import org.codelibs.empros.agent.util.PropertiesUtil;
import org.seasar.util.io.CloseableUtil;
import org.seasar.util.io.InputStreamUtil;
import org.seasar.util.lang.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestApiOperation implements Operation {

    private static final Logger logger = LoggerFactory
            .getLogger(RestApiOperation.class);

    private static final String EMPROSAPI_PROPERTIES = "emprosapi.properties";

    private static final String EVENT_BACKUPFILE = "empros-eventbackup";

    private final String url;

    private final int eventCapacity;

    private final int requestInterval;

    private final int maxRetryCount;

    private final HttpClient httpClient;

    private final ConnectionMonitor connectionMonitor;

    public RestApiOperation() {
        url = PropertiesUtil.getAsString(EMPROSAPI_PROPERTIES, "emprosUrl",
                null);

        if (StringUtil.isBlank(url)) {
            throw new EmprosSystemException("emprosUrl is empty.");
        }

        eventCapacity = PropertiesUtil.getAsInt(EMPROSAPI_PROPERTIES,
                "eventCapacity", 100);
        requestInterval = PropertiesUtil.getAsInt(EMPROSAPI_PROPERTIES,
                "requestInterval", 100);
        maxRetryCount = PropertiesUtil.getAsInt(EMPROSAPI_PROPERTIES,
                "maxRetryCount", 5);

        final long connectionCheckInterval = PropertiesUtil.getAsLong(
                EMPROSAPI_PROPERTIES, "connectionCheckInterval", 5000);
        final long idleConnectionTimeout = PropertiesUtil.getAsLong(
                EMPROSAPI_PROPERTIES, "idleConnectionTimeout", 60 * 1000);

        final SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory
                .getSocketFactory()));
        schemeRegistry.register(new Scheme("https", 443, SSLSocketFactory
                .getSocketFactory()));
        final ClientConnectionManager clientConnectionManager = new PoolingClientConnectionManager(
                schemeRegistry, 5, TimeUnit.MINUTES);

        httpClient = new DefaultHttpClient(clientConnectionManager);

        // TODO auth
        // TODO connection timeout
        // TODO socket timeout

        connectionMonitor = new ConnectionMonitor(clientConnectionManager,
                connectionCheckInterval, idleConnectionTimeout);
        connectionMonitor.setDaemon(true);
        connectionMonitor.start();
    }

    @Override
    public void destroy() {
        connectionMonitor.shutdown();
        httpClient.getConnectionManager().shutdown();
    }

    @Override
    public void excute(final List<Event> eventList) {
        File backupFile = new File(EVENT_BACKUPFILE);
        List<Event> events;
        if(backupFile.exists()) {
            events = restoreBackupEvent(eventList, backupFile);
            if(events == null) {
                return;
            }
        } else {
            events = eventList;
        }

        int start = 0;
        int retryCount = 0;
        while (true) {
            int end;
            if (start + eventCapacity < events.size()) {
                end = start + eventCapacity;
            } else {
                end = events.size();
            }

            HttpPost httpPost = null;
            HttpEntity httpEntity = null;
            try {
                httpPost = getHttpPost(events.subList(start, end));

                final HttpResponse response = httpClient.execute(httpPost);
                httpEntity = response.getEntity();
                final int status = response.getStatusLine().getStatusCode();
                if (logger.isDebugEnabled()) {
                    logger.debug("response: " + response.toString());
                }

                if (status == HttpStatus.SC_OK) {
                    if (end < events.size()) {
                        start = end;
                        retryCount = 0;
                    } else {
                        // finished
                        break;
                    }
                } else {
                    final String content = getContentAsString(httpEntity);
                    logger.warn("HTTPRequest error. status code:" + status
                            + " retry:" + retryCount + " url:" + url
                            + ", content: " + content);

                    if (retryCount < maxRetryCount) {
                        retryCount++;
                    } else {
                        // finished by an error
                        break;
                    }
                }
            } catch (final Exception e) {
                logger.warn("Could not access: " + url + " retry: "
                        + retryCount, e);
                if (retryCount < maxRetryCount) {
                    retryCount++;
                } else {
                    exportBackupFile(events, backupFile);
                    // finished by an error
                    break;
                }
                if (httpPost != null) {
                    httpPost.abort();
                }
            } finally {
                EntityUtils.consumeQuietly(httpEntity);
            }

            sleep();

        }
    }

    protected String getContentAsString(final HttpEntity httpEntity) {
        InputStream is = null;
        try {
            is = httpEntity.getContent();
            return new String(InputStreamUtil.getBytes(is), "UTF-8");
        } catch (final IOException e) {
            logger.warn("Failed to read a content.", e);
        } finally {
            CloseableUtil.close(is);
        }
        return StringUtil.EMPTY;
    }

    protected void sleep() {
        try {
            Thread.sleep(requestInterval);
        } catch (final InterruptedException e) {
            // ignore
        }
    }

    protected HttpPost getHttpPost(final List<Event> eventList)
            throws IOException {
        final HttpPost httpPost = new HttpPost(url);
        final Header[] headers = { new BasicHeader("Content-type",
                "application/json") };
        httpPost.setHeaders(headers);

        final String json = generateJson(eventList);
        httpPost.setEntity(new StringEntity(json, "UTF-8"));

        if (logger.isDebugEnabled()) {
            logger.debug("requestLine: " + httpPost.getRequestLine());
            logger.debug("requestBody: "
                    + EntityUtils.toString(httpPost.getEntity()));
        }
        return httpPost;
    }

    protected String generateJson(final List<Event> events) {
        final StringBuilder jsonBuf = new StringBuilder(1000);
        jsonBuf.append('[');
        for (int i = 0; i < events.size(); i++) {
            final Event event = events.get(i);

            if (i > 0) {
                jsonBuf.append(',');
            }
            jsonBuf.append('{');

            boolean isFirst = true;
            for (final Map.Entry<String, Object> entry : event.entrySet()) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    jsonBuf.append(',');
                }
                jsonBuf.append('\"');
                jsonBuf.append(StringEscapeUtils.escapeJavaScript(entry
                        .getKey()));
                jsonBuf.append("\":\"");
                jsonBuf.append(StringEscapeUtils.escapeJavaScript(entry
                        .getValue().toString()));
                jsonBuf.append('\"');
            }
            jsonBuf.append('}');
        }

        jsonBuf.append(']');

        return jsonBuf.toString();
    }

    private List<Event> restoreBackupEvent(List<Event> currentEventList, File backupFile) {
        if(!backupFile.exists()) {
            return new ArrayList<Event>(currentEventList);
        }

        List<Event> restoredEventList = null;
        int status = getServerStatus();
        if(status == HttpStatus.SC_OK) {
            restoredEventList = importBackupFile(backupFile);
            backupFile.delete();
            restoredEventList.addAll(currentEventList);
        } else {
            exportBackupFile(currentEventList, backupFile);
        }

        return restoredEventList;
    }

    private void exportBackupFile(List<Event> eventList, File file) {
        //TODO
        return;
    }

    private List<Event> importBackupFile(File file) {
        //TODO
        return null;
    }

    private int getServerStatus() {
        int status;
        try {
            //TODO リクエストヘッダ設定必要？
            HttpHead httpHead = new HttpHead(url);
            HttpResponse response = httpClient.execute(httpHead);
            status = response.getStatusLine().getStatusCode();
            EntityUtils.consumeQuietly(response.getEntity());
        } catch (ClientProtocolException e) {
            status = -1;
        } catch (IOException e) {
            status = -1;
        }
        return status;
    }

    public static class ConnectionMonitor extends Thread {

        private final ClientConnectionManager clientConnectionManager;

        private volatile boolean shutdown = false;

        private final long connectionCheckInterval;

        private final long idleConnectionTimeout;

        public ConnectionMonitor(
                final ClientConnectionManager clientConnectionManager,
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
}
