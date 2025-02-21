package org.example.service;

@Service
public class FileProxyService {

    private final CloseableHttpAsyncClient asyncHttpClient;

    public FileProxyService() {
        // 配置异步 HTTP 客户端（连接池、超时时间）
        this.asyncHttpClient = HttpAsyncClients.custom()
                .setMaxConnTotal(200) // 最大连接数
                .setMaxConnPerRoute(50) // 每个路由最大连接数
                .build();
        this.asyncHttpClient.start();
    }

    @Async
    public void handleAsyncDownload(RequestBody requestBody, AsyncContext asyncContext, HttpServletRequest request, HttpServletResponse response) {
        try {
            // Step 1: 调用应用 B 获取文件 URL
            String fileUrl = fetchFileUrlFromAppB(requestBody);

            // Step 2: 代理下载文件
            proxyDownload(fileUrl, asyncContext, request, response);
        } catch (Exception e) {
            handleError(asyncContext, response, e);
        }
    }

    private String fetchFileUrlFromAppB(RequestBody requestBody) {
        // 使用同步 HTTP 客户端调用应用 B 的 API 获取文件 URL
        // 示例：RestTemplate 或 Apache HttpClient
        return "http://app-b/files/123";
    }

    private void proxyDownload(String fileUrl, AsyncContext asyncContext, HttpServletRequest request, HttpServletResponse response) {
        try {
            // 解析前端请求的 Range 头
            String rangeHeader = request.getHeader("Range");

            // 创建带 Range 头的 GET 请求
            HttpGet httpGet = new HttpGet(fileUrl);
            if (rangeHeader != null) {
                httpGet.setHeader("Range", rangeHeader);
            }

            // 异步执行请求
            asyncHttpClient.execute(httpGet, new FutureCallback<HttpResponse>() {
                @Override
                public void completed(HttpResponse httpResponse) {
                    try {
                        // 将应用 B 的响应头复制到前端响应
                        copyHeaders(httpResponse, response);

                        // 获取输入流并写入前端输出流
                        try (InputStream inputStream = httpResponse.getEntity().getContent();
                             OutputStream outputStream = response.getOutputStream()) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                        }
                        asyncContext.complete();
                    } catch (Exception e) {
                        handleError(asyncContext, response, e);
                    }
                }

                @Override
                public void failed(Exception e) {
                    handleError(asyncContext, response, e);
                }

                @Override
                public void cancelled() {
                    handleError(asyncContext, response, new RuntimeException("Request cancelled"));
                }
            });
        } catch (Exception e) {
            handleError(asyncContext, response, e);
        }
    }

    private void copyHeaders(HttpResponse source, HttpServletResponse target) {
        // 复制状态码
        target.setStatus(source.getStatusLine().getStatusCode());

        // 复制关键头信息（如 Content-Type、Content-Length、Content-Range）
        Header contentType = source.getFirstHeader("Content-Type");
        if (contentType != null) {
            target.setHeader("Content-Type", contentType.getValue());
        }

        Header contentLength = source.getFirstHeader("Content-Length");
        if (contentLength != null) {
            target.setHeader("Content-Length", contentLength.getValue());
        }

        Header contentRange = source.getFirstHeader("Content-Range");
        if (contentRange != null) {
            target.setHeader("Content-Range", contentRange.getValue());
        }

        // 支持断点续传
        target.setHeader("Accept-Ranges", "bytes");
    }

    private void handleError(AsyncContext asyncContext, HttpServletResponse response, Exception e) {
        try {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (IOException ex) {
            // 日志记录
        } finally {
            asyncContext.complete();
        }
    }
}