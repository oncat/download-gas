package org.example.controller;

import lombok.Data;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

        // 配置WebClient使用零内存缓冲模式
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
                .toEntityFlux(DataBuffer.class)
                .map(responseEntity -> {
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

                    // 添加容错处理：当客户端断开连接时主动释放资源
                    Flux<DataBuffer> body = responseEntity.getBody()
                            .doOnCancel(() -> cleanupResources())
                            .doOnError(e -> cleanupResources());

                    return ResponseEntity
                            .status(responseEntity.getStatusCode())
                            .headers(headers)
                            .body(body);
                });
    }

    // 过滤不需要透传的Header
    private boolean isSensitiveHeader(String headerName) {
        return headerName.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)
                || headerName.equalsIgnoreCase("X-Server-Info");
    }

    // 资源清理逻辑（如需要）
    private void cleanupResources() {
        // 可添加日志记录或资源释放操作
    }

    @Data
    public static class DownloadRequest {
        private String targetUrl;
    }
}