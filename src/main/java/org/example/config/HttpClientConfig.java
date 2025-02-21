package org.example.config;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class HttpClientConfig {

    @Bean
    public CloseableHttpClient httpClient() {
        // 创建连接池管理器
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(200); // 最大连接数
        connectionManager.setDefaultMaxPerRoute(20); // 单个路由最大连接
        connectionManager.setValidateAfterInactivity(3000); // 3秒检测空闲连接

        // 设置代理（替换为你的代理地址和端口）
        HttpHost proxy = new HttpHost("your.proxy.host", 8080);

        // 请求配置（超时时间）
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(5000) // 连接超时
                .setSocketTimeout(60000) // 读取超时
                .setProxy(proxy) // 设置代理
                .build();

        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager)
                .build();
    }
}