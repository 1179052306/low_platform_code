package com.server.minio;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties properties;

    @Bean
    public MinioClient minioClient() throws Exception {
        log.info("Initializing MinIO Client for endpoint: {}", properties.getEndpoint());

        String endpoint = properties.getEndpoint();

        // 1. 处理 Endpoint 格式 (MinIO 8.x 自动根据 http/https 判断 secure)
        // 确保 endpoint 包含协议头，否则默认可能是 https 导致连接失败
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            // 如果 yml 里没写协议头，根据 secure 属性补全
            if (properties.isSecure()) {
                endpoint = "https://" + endpoint;
            } else {
                endpoint = "http://" + endpoint;
            }
        }

        // 2. 构建 OkHttpClient (用于处理 ignore-ssl 逻辑)
        OkHttpClient httpClient = createOkHttpClient(properties.isIgnoreSsl());

        // 3. 构建 MinioClient (MinIO 8.x 写法)
        return MinioClient.builder()
                .endpoint(endpoint) // 8.x 不需要 .secure()，自动识别
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .httpClient(httpClient) // 注入自定义的 httpClient 以支持忽略 SSL
                .build();
    }

    /**
     * 创建 OkHttpClient，支持忽略 SSL 证书验证
     */
    private OkHttpClient createOkHttpClient(boolean ignoreSsl) throws NoSuchAlgorithmException, KeyManagementException {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.MINUTES)
                .writeTimeout(10, TimeUnit.MINUTES)
                .readTimeout(30, TimeUnit.MINUTES);

        if (ignoreSsl) {
            log.warn("SSL verification is disabled. Creating insecure OkHttpClient.");
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                        @Override
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
                    }
            };

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            final javax.net.ssl.SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            clientBuilder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            clientBuilder.hostnameVerifier((hostname, session) -> true);
        }

        return clientBuilder.build();
    }
}