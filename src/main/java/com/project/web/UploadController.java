package com.project.web;

import com.project.common.Result;
import com.project.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
public class UploadController {
    private final UploadService service;

    public UploadController(UploadService service) {
        this.service = service;
    }

    @Operation(summary = "上传物品照片，支持 5 MB 以内的 JPEG/PNG")
    @PostMapping(value = "/api/uploads", consumes = "multipart/form-data")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(Map.of("url", service.upload(file)));
    }
}
