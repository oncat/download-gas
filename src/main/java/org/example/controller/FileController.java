package org.example.controller;

import lombok.Data;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

        return webClient.get()
                .uri(request.getTargetUrl())
                .headers(headers -> {
                    if (rangeHeader != null) {
                        headers.set(HttpHeaders.RANGE, rangeHeader);
                    }
                })
                .exchangeToMono(response -> {
                    HttpHeaders headers = new HttpHeaders();
                    response.headers().asHttpHeaders().forEach(headers::addAll);

                    return Mono.just(ResponseEntity
                            .status(response.statusCode())
                            .headers(headers)
                            .body(response.bodyToFlux(DataBuffer.class)));
                });
    }

    @Data
    public static class DownloadRequest {
        private String targetUrl;
    }
}