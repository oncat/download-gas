package org.example.controller;

@RestController
public class FileProxyController {

    @Autowired
    private FileProxyService fileProxyService;

    @PostMapping("/download")
    public void downloadFile(@RequestBody RequestBody requestBody, HttpServletRequest request, HttpServletResponse response) {
        AsyncContext asyncContext = request.startAsync();
        // 设置异步超时时间（根据实际情况调整）
        asyncContext.setTimeout(60000);
        fileProxyService.handleAsyncDownload(requestBody, asyncContext, request, response);
    }
}