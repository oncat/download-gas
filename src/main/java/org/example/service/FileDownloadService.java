package org.example.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class FileDownloadService {

    private final RestTemplate restTemplate;
    private final ExecutorService executorService;

    @Autowired
    public FileDownloadService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.executorService = Executors.newFixedThreadPool(10); // 根据需求调整线程数
    }

    public void downloadFileWithProxy(String fileUrl, HttpServletResponse response) {
        executorService.submit(() -> {
            try {
                ResponseEntity<InputStreamResource> entity = restTemplate.getForEntity(
                        fileUrl, InputStreamResource.class
                );

                if (entity.getStatusCode() != HttpStatus.OK) {
                    throw new RuntimeException("文件下载失败，状态码：" + entity.getStatusCodeValue());
                }

                // 获取输入输出流
                InputStream inputStream = entity.getBody().getInputStream();
                BufferedOutputStream outputStream = new BufferedOutputStream(response.getOutputStream());

                byte[] buffer = new byte[8192]; // 8KB缓冲区
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                // 关键：确保流被及时关闭
                outputStream.flush();
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void shutdown() {
        executorService.shutdown();
    }
}