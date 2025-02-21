package org.example.controller;
import org.example.service.FileDownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
public class FileDownloadController {

    private final FileDownloadService fileDownloadService;

    @Autowired
    public FileDownloadController(FileDownloadService fileDownloadService) {
        this.fileDownloadService = fileDownloadService;
    }

    @GetMapping("/download/{url}")
    public ResponseEntity<String> downloadFile(@PathVariable String url, HttpServletResponse response) {
        try {
            fileDownloadService.downloadFileWithProxy(url, response);
            return ResponseEntity.status(HttpStatus.OK).body("下载开始");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("下载失败");
        }
    }
}