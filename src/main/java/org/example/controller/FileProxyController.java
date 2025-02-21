package org.example.controller;
import org.example.service.FileProxyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
public class FileProxyController {

    @Autowired
    private FileProxyService fileProxyService;

    @PostMapping("/download")
    public void downloadFile(@RequestBody RequestBody requestBody,
                             HttpServletRequest request,
                             HttpServletResponse response) {
        AsyncContext asyncContext = request.startAsync();
        asyncContext.setTimeout(60000); // 超时时间（毫秒）
        fileProxyService.handleAsyncDownload(requestBody, asyncContext, request, response);
    }
}