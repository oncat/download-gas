package org.example.service;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.RestTemplate;
import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Service
public class FileProxyService {

    private final RestTemplate restTemplate;

    public FileProxyService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Async
    public void handleAsyncDownload(RequestBody requestBody, AsyncContext asyncContext,
                                    HttpServletRequest request, HttpServletResponse response) {
        try {
            // Step 1: 调用应用 B 获取文件 URL
            String fileUrl = fetchFileUrlFromAppB(requestBody);

            // Step 2: 代理下载文件
            proxyDownload(fileUrl, asyncContext, request, response);
        } catch (Exception e) {
            handleError(asyncContext, response, e);
        }
    }

    private void proxyDownload(String fileUrl, AsyncContext asyncContext,
                               HttpServletRequest request, HttpServletResponse response) {
        try {
            // 解析前端请求的 Range 头
            String rangeHeader = request.getHeader("Range");

            // 设置请求头（透传 Range）
            HttpHeaders headers = new HttpHeaders();
            if (rangeHeader != null) {
                headers.set("Range", rangeHeader);
            }
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 发送 GET 请求（流式接收）
            ResponseEntity<InputStreamResource> responseEntity = restTemplate.exchange(
                    fileUrl,
                    HttpMethod.GET,
                    entity,
                    InputStreamResource.class
            );

            // 将应用 B 的响应头复制到前端响应
            copyHeaders(responseEntity, response);

            // 流式传输数据
            try (InputStream inputStream = responseEntity.getBody().getInputStream();
                 OutputStream outputStream = response.getOutputStream()) {
                StreamUtils.copy(inputStream, outputStream);
            }

            asyncContext.complete();
        } catch (Exception e) {
            handleError(asyncContext, response, e);
        }
    }

    private void copyHeaders(ResponseEntity<?> source, HttpServletResponse target) {
        // 状态码
        target.setStatus(source.getStatusCodeValue());

        // 关键头信息
        HttpHeaders headers = source.getHeaders();
        headers.forEach((key, values) -> {
            if (key.equalsIgnoreCase("Content-Type") ||
                    key.equalsIgnoreCase("Content-Length") ||
                    key.equalsIgnoreCase("Content-Range")) {
                target.setHeader(key, String.join(", ", values));
            }
        });

        // 支持断点续传
        target.setHeader("Accept-Ranges", "bytes");
    }

    private String fetchFileUrlFromAppB(RequestBody requestBody) {
        // 调用应用 B 的 API（示例返回固定值）
        return "http://app-b/files/123";
    }

    private void handleError(AsyncContext asyncContext, HttpServletResponse response, Exception e) {
        try {
            try {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } finally {
            asyncContext.complete();
        }
    }
}