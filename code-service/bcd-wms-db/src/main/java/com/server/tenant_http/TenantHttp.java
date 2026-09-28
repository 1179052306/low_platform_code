package com.server.tenant_http;

import com.alibaba.fastjson2.JSONObject;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.*;

/**
 * @author lw
 * @date: 2025/2/25
 * @description:
 **/
public class TenantHttp {


    public TenantHttp(String url) {
        this.url = url;
    }

    public TenantHttp(String url, Map<String, Object> param) {
        this.url = url;
        this.param = param;
        this.autoClose = true;
    }

    public TenantHttp(String url, boolean autoClose) {
        this.url = url;
        this.param = param;
        this.autoClose = autoClose;
    }

    public TenantHttp(String url, Map<String, Object> param, boolean autoClose) {
        this.url = url;
        this.param = param;
        this.autoClose = autoClose;
    }

    public String getUrl() {
        return url;
    }

    private String url;
    private Map<String, Object> param;
    private Map<String, String> headerParams;
    private int statusCode;
    private String content;
    private String xmlParam;
    private HttpPost httpPost;
    private String postBodyType = "url";
    private HttpPut httpPut;
    private HttpGet httpGet;
    private Boolean autoClose;
    private CloseableHttpClient httpClient = null;
    private String location;
    private CloseableHttpResponse httpResponse;
    private boolean isHttps;
    private static CookieStore cookieStore = null;

    public void initCookieStore() {
        if (cookieStore == null) {
            cookieStore = new BasicCookieStore();
            ;
        }

    }

    public String getPostBodyType() {
        return postBodyType;
    }

    public void setPostBodyType(String postBodyType) {
        this.postBodyType = postBodyType;
    }

    public HttpGet getHttpGet() {
        return httpGet;
    }

    public void setHttpGet(HttpGet httpGet) {
        this.httpGet = httpGet;
    }

    public HttpPut getHttpPut() {
        return httpPut;
    }

    public void setHttpPut(HttpPut httpPut) {
        this.httpPut = httpPut;
    }

    public HttpPost getHttpPost() {
        return httpPost;
    }

    public void setHttpPost(HttpPost httpPost) {
        this.httpPost = httpPost;
    }

    public void setUrl(String url) {
        this.url = url;

        if (httpPost != null) {
            httpPost.setURI(URI.create(url));
        }
        if (httpPut != null) {
            httpPut.setURI(URI.create(url));
        }
        if (httpGet != null) {
            httpGet.setURI(URI.create(url));
        }
    }


    public Boolean getAutoClose() {
        return autoClose;
    }

    public void setAutoClose(Boolean autoClose) {
        this.autoClose = autoClose;
    }


    public String getLocation() {
        return location;
    }


    public CloseableHttpResponse getHttpResponse() {
        return httpResponse;
    }

    public boolean isHttps() {
        return isHttps;
    }

    public void setHttps(boolean isHttps) {
        this.isHttps = isHttps;
    }

    public String getXmlParam() {
        return xmlParam;
    }

    public void setXmlParam(String xmlParam) {
        this.xmlParam = xmlParam;
    }

    public void setParameter(Map<String, Object> map) {
        param = map;
    }

    public void addParameter(String key, String value) {
        if (param == null)
            param = new HashMap<String, Object>();
        param.put(key, value);
    }

    public void setHeaderParams(Map<String, String> headerParams) {
        this.headerParams = headerParams;
    }

    public void addHeaderParams(String key, String value) {
        if (headerParams == null)
            headerParams = new HashMap<String, String>();
        headerParams.put(key, value);
    }


    public void post() throws ClientProtocolException, IOException {

        if (httpPost == null) {
            httpPost = new HttpPost(url);
        }

        setEntity(httpPost);
        execute(httpPost);
    }

    public void put() throws ClientProtocolException, IOException {
        if (httpPut == null) {
            httpPut = new HttpPut(url);
        }

        setEntity(httpPut);
        execute(httpPut);

    }

    public void get() throws ClientProtocolException, IOException {
        if (param != null) {
            StringBuilder url = new StringBuilder(this.url);
            boolean isFirst = true;
            for (String key : param.keySet()) {
                if (isFirst)
                    url.append("?");
                else
                    url.append("&");
                url.append(key).append("=").append(param.get(key));
                isFirst = false;
            }
            this.url = url.toString();
        }
        if (httpGet == null) {
            httpGet = new HttpGet(url);
        } else {
            try {
                httpGet.setURI(new URI(url));
            } catch (Exception ex) {

            }

        }
        if (headerParams != null) {
            if (httpGet.getAllHeaders().length > 0) {
                List<String> headerNames = new ArrayList<>();
                for (int i = 0; i < httpGet.getAllHeaders().length; i++) {
                    headerNames.add(httpGet.getAllHeaders()[i].getName());

                }
                for (int i = 0; i < headerNames.size(); i++) {

                    httpGet.removeHeaders(headerNames.get(i));
                }
            }

            for (Map.Entry<String, String> entry : headerParams.entrySet()) {
                httpGet.addHeader(entry.getKey(), entry.getValue());
            }
        }
        execute(httpGet);
    }

    /**
     * set http post,put param
     */
    private void setEntity(HttpEntityEnclosingRequestBase http) {
        if (param != null) {
            if (postBodyType.equals("url")) {
                List<NameValuePair> nvps = new LinkedList<NameValuePair>();
                for (String key : param.keySet())
                    nvps.add(new BasicNameValuePair(key, param.get(key).toString())); // 参数
                http.setEntity(new UrlEncodedFormEntity(nvps, Consts.UTF_8)); // 设置参数
            }
            if (postBodyType.equals("raw")) {
                JSONObject paramJson = new JSONObject();

                for (String key : param.keySet()) {
                    paramJson.put(key, param.get(key));
                }
                StringEntity s = new StringEntity(paramJson.toString(), "utf-8");

                s.setContentEncoding(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));

                http.setEntity(s);
            }

        }
        if (xmlParam != null) {
            http.setEntity(new StringEntity(xmlParam, Consts.UTF_8));
        }
        if (headerParams != null) {
            if (http.getAllHeaders().length > 0) {
                List<String> headerNames = new ArrayList<>();
                for (int i = 0; i < http.getAllHeaders().length; i++) {
                    headerNames.add(http.getAllHeaders()[i].getName());

                }
                for (int i = 0; i < headerNames.size(); i++) {

                    http.removeHeaders(headerNames.get(i));
                }
            }

            for (Map.Entry<String, String> entry : headerParams.entrySet()) {
                http.addHeader(entry.getKey(), entry.getValue());
            }
        }
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(35000)// 连接主机服务超时时间
                .setConnectionRequestTimeout(35000)// 请求超时时间
                .setSocketTimeout(60000)// 数据读取超时时间
                .build();
        http.setConfig(requestConfig);
    }

    private void execute(HttpUriRequest http) throws ClientProtocolException,
            IOException {

        try {
            if (isHttps) {
                SSLContext sslContext = new SSLContextBuilder()
                        .loadTrustMaterial(null, new TrustStrategy() {
                            // 信任所有
                            public boolean isTrusted(X509Certificate[] chain,
                                                     String authType)
                                    throws CertificateException {
                                return true;
                            }
                        }).build();
                SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                        sslContext);
                if (httpClient == null) {
                    httpClient = HttpClients.custom().setSSLSocketFactory(sslsf)
                            .build();
                }
            } else {
                if (httpClient == null) {
                    httpClient = HttpClients.createDefault();
                }
            }
            if (cookieStore != null) {
                HttpClientContext httpClientContext = HttpClientContext.create();
                httpClientContext.setCookieStore(cookieStore);
                httpResponse = httpClient.execute(http, httpClientContext);
            } else {
                httpResponse = httpClient.execute(http);
            }
            try {
                if (httpResponse != null) {

                    if (httpResponse.getStatusLine() != null)
                        statusCode = httpResponse.getStatusLine().getStatusCode();
                    HttpEntity entity = httpResponse.getEntity();
                    // 响应内容
                    content = EntityUtils.toString(entity, Consts.UTF_8);
                    if (httpResponse.getHeaders("Location") != null) {
                        if (httpResponse.getHeaders("Location").length > 0) {

                            location = Arrays.stream(httpResponse.getHeaders("Location")).toArray()[0].toString();
                        }
                    }


                }
            } finally {
                if (autoClose) {
                    httpResponse.close();
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (autoClose) {
                httpClient.close();
            }

        }
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getContent() throws ParseException, IOException {
        return content;
    }

    public void close() throws ParseException, IOException {
        if (httpClient != null) {
            httpClient.close();
        }
        if (httpResponse != null) {
            httpResponse.close();
        }

    }

}
