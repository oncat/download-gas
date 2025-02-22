package org.example.controller;

import lombok.Data;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final WebClient webClient;

    public FileController(WebClient webClient) {
        this.webClient = webClient;
    }

    @PostMapping("/download")
    public Mono<ResponseEntity<Flux<DataBuffer>>> downloadFile(
            @RequestBody DownloadRequest request,
            @RequestHeader(value = "Range", required = false) String rangeHeader) {

        WebClient streamingWebClient = webClient.mutate()
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(config -> config.defaultCodecs().maxInMemorySize(0))
                        .build())
                .build();

        return streamingWebClient.get()
                .uri(request.getTargetUrl())
                .headers(headers -> {
                    if (rangeHeader != null) {
                        headers.set(HttpHeaders.RANGE, rangeHeader);
                    }
                })
                .retrieve()
                .onStatus(HttpStatus::isError, response -> {
                    // 保留原始错误响应
                    return response.createException()
                            .flatMap(ex -> Mono.error(new ResponseStatusException(
                                    response.statusCode(),
                                    ex.getResponseBodyAsString(),
                                    ex)));
                })
                .toEntityFlux(DataBuffer.class)
                .timeout(Duration.ofSeconds(30))
                .map(responseEntity -> {
                    // 构建透传的响应头
                    HttpHeaders headers = new HttpHeaders();
                    responseEntity.getHeaders().forEach((key, values) -> {
                        if (!isSensitiveHeader(key)) {
                            headers.addAll(key, values);
                        }
                    });

                    // 强制流式Content-Type
                    if (!headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
                        headers.set(HttpHeaders.CONTENT_TYPE, "application/octet-stream");
                    }

                    // 添加资源清理逻辑
                    Flux<DataBuffer> body = responseEntity.getBody()
                            .doOnCancel(this::cleanupResources)
                            .doOnError(e -> this.cleanupResources());

                    return ResponseEntity.status(responseEntity.getStatusCode())
                            .headers(headers)
                            .body(body);
                })
                .onErrorResume(this::handleError);
    }

    private boolean isSensitiveHeader(String headerName) {
        return headerName.equalsIgnoreCase("X-Server-Info");
    }

    private void cleanupResources() {
        // 示例资源清理逻辑
        System.out.println("Cleaning up resources...");
    }

    private Mono<ResponseEntity<Flux<DataBuffer>>> handleError(Throwable ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex instanceof ConnectException) {
            status = HttpStatus.BAD_GATEWAY;
        } else if (ex instanceof SocketTimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT;
        } else if (ex instanceof WebClientResponseException) {
            // 透传原始错误状态码
            return Mono.just(ResponseEntity
                    .status(((WebClientResponseException) ex).getStatusCode())
                    .body(Flux.empty()));
        }
        return Mono.just(ResponseEntity
                .status(status)
                .body(Flux.empty()));
    }

    @Data
    public static class DownloadRequest {
        private String targetUrl;
    }
}