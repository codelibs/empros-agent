package org.codelibs.empros.agent.operation.impl;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.codelibs.empros.agent.operation.EventOperation;
import org.codelibs.empros.agent.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.codelibs.empros.agent.event.EmprosEvent;

public class EmprosRestApiOperation implements EventOperation {
    private Logger logger = LoggerFactory
            .getLogger(EmprosRestApiOperation.class);

    private String url = "";

    private int eventCapacity = 1000;

    private int requestInterval = 100;

    public EmprosRestApiOperation() {
        super();
        url = PropertiesUtil.loadProperties("emprosapi.properties", "empros.url");
    }

    @Override
    public void excute(List<EmprosEvent> events) {
        HttpClient httpClient = null;
        try {
            int start = 0;
            int retryCnt = 0;
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }

                httpClient = new DefaultHttpClient();

                HttpPost httpPost = new HttpPost(url);
                Header[] headers = { new BasicHeader("Content-type",
                        "application/json") };
                httpPost.setHeaders(headers);

                int end;
                if (start + eventCapacity < events.size()) {
                    end = start + eventCapacity;
                } else {
                    end = events.size();
                }

                String json = generateJson(events.subList(start, end));
                httpPost.setEntity(new StringEntity(json, "UTF-8"));

                if (logger.isDebugEnabled()) {
                    logger.debug("requestLine: " + httpPost.getRequestLine());
                    logger.debug("requestBody: "
                            + EntityUtils.toString(httpPost.getEntity()));
                }

                HttpResponse response = null;
                int status = -1;
                try {
                    response = httpClient.execute(httpPost);
                    status = response.getStatusLine().getStatusCode();
                    if (logger.isDebugEnabled()) {
                        logger.debug("response: " + response.toString());
                    }
                }catch(IOException e) {

                }

                httpClient.getConnectionManager().shutdown();
                httpClient = null;

                if(status != HttpStatus.SC_OK) {
                    logger.warn("HTTPRequest error. status code:" + status + " retry:" + retryCnt + " url:" + url);
                    if(retryCnt < 5) {
                        retryCnt++;
                        Thread.sleep(requestInterval);
                        continue;
                    }
                }
                retryCnt = 0;

                if (end < events.size()) {
                    start = end;
                    Thread.sleep(requestInterval);
                } else {
                    break;
                }

            }

        } catch (UnsupportedEncodingException e) {
            logger.warn("UnsupportedEncodingException");
        } catch (IOException e) {
            logger.warn("IOException");
        } catch (InterruptedException e) {

        } finally {
            if (httpClient != null) {
                httpClient.getConnectionManager().shutdown();
            }
        }
    }

    private String generateJson(List<EmprosEvent> events) {
        StringBuilder jsonBuf = new StringBuilder();
        jsonBuf.append("[");
        for (int i = 0; i < events.size(); i++) {
            EmprosEvent event = events.get(i);

            if (i > 0) {
                jsonBuf.append(",");
            }
            jsonBuf.append("{");
            Iterator<Map.Entry<String, String>> it = event.entrySet()
                    .iterator();

            boolean isFirst = true;
            while (it.hasNext()) {
                if (!isFirst) {
                    jsonBuf.append(",");
                }
                isFirst = false;
                Entry<String, String> entry = it.next();
                jsonBuf.append("\"");
                jsonBuf.append(StringEscapeUtils.escapeJavaScript(entry
                        .getKey()));
                jsonBuf.append("\":\"");
                jsonBuf.append(StringEscapeUtils.escapeJavaScript(entry
                        .getValue()));
                jsonBuf.append("\"");
            }
            jsonBuf.append("}");
        }

        jsonBuf.append("]");

        return jsonBuf.toString();
    }
}
