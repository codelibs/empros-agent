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
package org.codelibs.empros.agent.operation.rest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.codelibs.core.io.CloseableUtil;
import org.codelibs.core.io.InputStreamUtil;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.empros.agent.event.Event;
import org.codelibs.empros.agent.exception.EmprosSystemException;
import org.codelibs.empros.agent.listener.OperationListener;
import org.codelibs.empros.agent.operation.Operation;
import org.codelibs.empros.agent.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestApiOperation implements Operation {

    private static final Logger logger = LoggerFactory.getLogger(RestApiOperation.class);

    private static final String EMPROSAPI_PROPERTIES = "emprosapi.properties";

    private final String url;

    private final int eventCapacity;

    private final int requestInterval;

    private final int maxRetryCount;

    private final CloseableHttpClient httpClient;

    private final ConnectionMonitor connectionMonitor;

    private final List<OperationListener> listenerList = new ArrayList<>();

    private final ApiMonitor apiMonitor;

    private final Timer apiMonitorTimer;

    private final long apiMonitorInterval;

    private final AtomicBoolean apiAvailable = new AtomicBoolean(false);

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
        apiMonitorInterval = PropertiesUtil.getAsLong(EMPROSAPI_PROPERTIES,
                "apiMonitorInterval", 1 * 60 * 1000L);

        final long connectionCheckInterval = PropertiesUtil.getAsLong(
                EMPROSAPI_PROPERTIES, "connectionCheckInterval", 5000L);
        final long idleConnectionTimeout = PropertiesUtil.getAsLong(
                EMPROSAPI_PROPERTIES, "idleConnectionTimeout", 60 * 1000L);

        final HttpClientBuilder builder = HttpClientBuilder.create();
        final PlainConnectionSocketFactory plainConnectionFactory = new PlainConnectionSocketFactory();
        final SSLConnectionSocketFactory sslConnectionFactory = buildSSLSocketFactory();
        builder.setSSLSocketFactory(sslConnectionFactory);
        final Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", plainConnectionFactory)
                .register("https", sslConnectionFactory)
                .build();
        final RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(10 * 1000)
                .setSocketTimeout(10 * 1000)
                .build();

        // TODO auth
        //final RegistryBuilder<AuthSchemeProvider> authSchemeProviderBuilder = RegistryBuilder.create();

        builder.setDefaultRequestConfig(requestConfig);

        final HttpClientConnectionManager clientConnectionManager = new PoolingHttpClientConnectionManager(
                registry, null, null, null,5, TimeUnit.MINUTES);

        builder.setConnectionManager(clientConnectionManager);

        httpClient = builder.build();

        connectionMonitor = new ConnectionMonitor(clientConnectionManager,
                connectionCheckInterval, idleConnectionTimeout);
        connectionMonitor.setDaemon(true);
        connectionMonitor.start();

        apiMonitor = new ApiMonitor();
        apiMonitorTimer = new Timer();
        apiMonitorTimer.schedule(apiMonitor, 0, apiMonitorInterval);
    }

    protected SSLConnectionSocketFactory buildSSLSocketFactory() {
        try {
            final SSLContextBuilder builder = new SSLContextBuilder();
            builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
            return new SSLConnectionSocketFactory(builder.build(), NoopHostnameVerifier.INSTANCE);
        } catch (final Exception e) {
            logger.warn("Failed to create TrustSelfSignedStrategy.", e);
        }
        return null;
    }

    @Override
    public void destroy() {
        connectionMonitor.shutdown();
        apiMonitorTimer.cancel();
        try {
            httpClient.close();
        } catch (final IOException e) {
            logger.warn("Failed to close HttpClient.", e);
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
        int start = 0;
        int retryCount = 0;
        while (true) {
            int end;
            if (start + eventCapacity < eventList.size()) {
                end = start + eventCapacity;
            } else {
                end = eventList.size();
            }

            HttpPost httpPost = null;
            HttpEntity httpEntity = null;
            try {
                httpPost = getHttpPost(eventList.subList(start, end));

                final HttpResponse response = httpClient.execute(httpPost);
                httpEntity = response.getEntity();
                final int status = response.getStatusLine().getStatusCode();
                if (logger.isDebugEnabled()) {
                    logger.debug("response: {}", response);
                }

                if (status == HttpStatus.SC_OK) {
                    if (end < eventList.size()) {
                        start = end;
                        retryCount = 0;
                    } else {
                        // finished
                        isSuccess = true;
                        break;
                    }
                } else {
                    final String content = getContentAsString(httpEntity);
                    logger.warn("HTTPRequest error. status code: {}, retry: {}, url: {}, content: {}", status, retryCount, url, content);
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

        if (isSuccess) {
            callbackResultSuccess(eventList);
        } else {
            callbackResultError(eventList);
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
        final Header[] headers = {new BasicHeader("Content-type",
                "application/json")};
        httpPost.setHeaders(headers);

        final String json = generateJson(eventList);
        httpPost.setEntity(new StringEntity(json, "UTF-8"));

        if (logger.isDebugEnabled()) {
            logger.debug("requestLine: {}", httpPost.getRequestLine());
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
                        .getValue().toString().replace("\'", "")));
                jsonBuf.append('\"');
            }
            jsonBuf.append('}');
        }

        jsonBuf.append(']');

        return jsonBuf.toString();
    }

    private void callbackResultSuccess(final List<Event> eventList) {
        if (!listenerList.isEmpty()) {
            for (OperationListener listener : listenerList) {
                listener.successHandler(eventList);
            }
        }
    }

    private void callbackResultError(final List<Event> eventList) {
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
            boolean before = apiAvailable.get();
            boolean after = isReachable();
            apiAvailable.set(after);
            if (!after) {
                if (after != before) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Api Monitoring. Server is not available. {}", url);
                    }
                }
            } else if (after != before) {
                if (logger.isInfoEnabled()) {
                    logger.info("Api Monitoring. Server was restored. {}", url);
                }
                callbackResoted();
            }

        }

        private boolean isReachable() {
            try {
                final HttpHead request = new HttpHead(url);
                final HttpResponse response = httpClient.execute(request);
                EntityUtils.consumeQuietly(response.getEntity());
                return true;
            } catch (final ConnectTimeoutException e) {
                // ignore
            } catch (final Exception e) {
                logger.warn("Failed to monitor api. {}", url);
            }
            return false;
        }
    }
}
