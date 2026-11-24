package com.project.service;

import com.project.auth.UserContext;
import com.project.common.BizException;
import com.project.mapper.UploadMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class UploadService {
    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000;
    private static final String PREFIX = "/uploads/";

    private final UploadMapper mapper;
    private final Path directory;

    public UploadService(UploadMapper mapper, @Value("${app.upload.directory:.local/uploads}") String directory) {
        this.mapper = mapper;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public String upload(MultipartFile file) {
        Long ownerId = UserContext.require().getId();
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw BizException.of400("请选择不超过 5 MB 的 JPEG 或 PNG 图片");
        }
        Path stored = null;
        try (var stream = file.getInputStream(); ImageInputStream input = ImageIO.createImageInputStream(stream)) {
            if (input == null) {
                throw BizException.of400("无法读取图片");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw BizException.of400("图片内容无效，仅支持 JPEG 和 PNG");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) {
                    throw BizException.of400("仅支持 JPEG 和 PNG 图片");
                }
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_PIXELS) {
                    throw BizException.of400("图片最多支持 1600 万像素");
                }
                BufferedImage decoded = reader.read(0);
                String extension = format.equals("jpeg") ? "jpg" : "png";
                String filename = UUID.randomUUID() + "." + extension;
                Files.createDirectories(directory);
                stored = directory.resolve(filename);
                // 重新编码只保留图像像素，文件名与上传者关联持久化到数据库。
                if (!ImageIO.write(decoded, format, stored.toFile())) {
                    throw new IOException("No image writer available");
                }
                mapper.insert(filename, ownerId);
                return PREFIX + filename;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            removeIncompleteFile(stored);
            throw new BizException(400, "图片读取或保存失败，请重新选择图片后重试");
        } catch (RuntimeException e) {
            removeIncompleteFile(stored);
            throw e;
        }
    }

    public void validateOwnership(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (!url.matches("/uploads/[a-f0-9-]{36}\\.(jpg|png)")) {
            throw BizException.of400("图片地址无效，请通过上传按钮选择图片");
        }
        String filename = url.substring(PREFIX.length());
        Long ownerId = mapper.selectOwner(filename);
        if (!UserContext.require().getId().equals(ownerId) || !Files.isRegularFile(directory.resolve(filename))) {
            throw BizException.of400("图片不存在或不属于当前用户，请重新上传");
        }
    }

    private void removeIncompleteFile(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException cleanupFailure) {
                org.slf4j.LoggerFactory.getLogger(UploadService.class).warn("清理未完成的上传失败", cleanupFailure);
            }
        }
    }
}
