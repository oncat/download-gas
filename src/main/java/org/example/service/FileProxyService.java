import com.squareup.okhttp.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

@Service
public class FileProxyService {

    private final OkHttpClient okHttpClient;

    public FileProxyService() {
        // 配置 OkHttp 客户端（连接池、超时时间）
        this.okHttpClient = new OkHttpClient();
        this.okHttpClient.setConnectTimeout(30, TimeUnit.SECONDS);
        this.okHttpClient.setReadTimeout(60, TimeUnit.SECONDS);
        this.okHttpClient.setWriteTimeout(60, TimeUnit.SECONDS);
        this.okHttpClient.setConnectionPool(new ConnectionPool(200, 5, TimeUnit.MINUTES));
    }

    @Async
    public void handleAsyncDownload(RequestBody requestBody, AsyncContext asyncContext, HttpServletRequest request, HttpServletResponse response) {
        try {
            // Step 1: 调用应用 B 获取文件 URL（需实现）
            String fileUrl = fetchFileUrlFromAppB(requestBody);

            // Step 2: 代理下载文件
            proxyDownloadWithOkHttp(fileUrl, asyncContext, request, response);
        } catch (Exception e) {
            handleError(asyncContext, response, e);
        }
    }

    private void proxyDownloadWithOkHttp(String fileUrl, AsyncContext asyncContext, HttpServletRequest request, HttpServletResponse response) {
        // 解析前端请求的 Range 头
        String rangeHeader = request.getHeader("Range");

        // 创建请求
        Request.Builder requestBuilder = new Request.Builder().url(fileUrl);
        if (rangeHeader != null) {
            requestBuilder.addHeader("Range", rangeHeader);
        }

        // 异步执行请求
        okHttpClient.newCall(requestBuilder.build()).enqueue(new Callback() {
            @Override
            public void onResponse(Response okResponse) throws IOException {
                try (ResponseBody responseBody = okResponse.body()) {
                    if (!okResponse.isSuccessful()) {
                        throw new IOException("Unexpected code: " + okResponse);
                    }

                    // 将应用 B 的响应头复制到前端响应
                    copyHeaders(okResponse, response);

                    // 流式传输数据
                    try (InputStream inputStream = responseBody.byteStream();
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
            public void onFailure(Request request, IOException e) {
                handleError(asyncContext, response, e);
            }
        });
    }

    private void copyHeaders(Response source, HttpServletResponse target) {
        // 复制状态码
        target.setStatus(source.code());

        // 复制关键头信息
        String contentType = source.header("Content-Type");
        if (contentType != null) {
            target.setHeader("Content-Type", contentType);
        }

        String contentLength = source.header("Content-Length");
        if (contentLength != null) {
            target.setHeader("Content-Length", contentLength);
        }

        String contentRange = source.header("Content-Range");
        if (contentRange != null) {
            target.setHeader("Content-Range", contentRange);
        }

        target.setHeader("Accept-Ranges", "bytes");
    }

    private String fetchFileUrlFromAppB(RequestBody requestBody) {
        // 实现调用应用 B 的逻辑（示例返回固定值）
        return "http://app-b/files/123";
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