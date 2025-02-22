package org.example.config;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;
import javax.net.ssl.SSLException;

@Configuration
public class ProxyConfig {

    @Bean
    public WebClient webClient(
            @Value("${proxy.host:}") String proxyHost,
            @Value("${proxy.port:8080}") int proxyPort,
            @Value("${proxy.username:}") String proxyUsername,
            @Value("${proxy.password:}") String proxyPassword) throws SSLException {

        HttpClient httpClient = HttpClient.create()
                .secure(spec -> spec.sslContext(
                        SslContextBuilder.forClient()
                                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                ));

        if (StringUtils.hasText(proxyHost)) {
            httpClient = httpClient.proxy(proxy -> {
                ProxyProvider.Builder proxyBuilder = proxy
                        .type(ProxyProvider.Proxy.HTTP)
                        .host(proxyHost)
                        .port(proxyPort);
                if (StringUtils.hasText(proxyUsername)) {
                    proxyBuilder.username(proxyUsername);
                    proxyBuilder.password(s -> proxyPassword);
                }
            });
        }

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}