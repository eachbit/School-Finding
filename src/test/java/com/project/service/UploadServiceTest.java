package com.project.service;

import com.project.auth.LoginUser;
import com.project.auth.UserContext;
import com.project.common.BizException;
import com.project.mapper.UploadMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UploadServiceTest {
    @TempDir
    Path directory;

    private UploadMapper mapper;
    private UploadService service;

    @BeforeEach
    void setUp() {
        mapper = mock(UploadMapper.class);
        service = new UploadService(mapper, directory.toString());
        UserContext.set(new LoginUser(10L, "uploader", "STUDENT"));
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void savesRealImageAndRecordsItsOwner() throws Exception {
        String url = service.upload(image());
        assertTrue(url.matches("/uploads/[a-f0-9-]{36}\\.png"));
        Path stored = directory.resolve(url.substring("/uploads/".length()));
        assertNotNull(ImageIO.read(stored.toFile()));
        verify(mapper).insert(stored.getFileName().toString(), 10L);
    }

    @Test
    void rejectsTextDisguisedAsImage() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "hello".getBytes());
        assertThrows(BizException.class, () -> service.upload(file));
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        assertThrows(BizException.class, () -> service.upload(new MockMultipartFile("file", new byte[0])));
        assertThrows(BizException.class, () -> service.upload(new MockMultipartFile("file", new byte[5 * 1024 * 1024 + 1])));
    }

    @Test
    void validatesOwnershipAndExistingFile() throws Exception {
        String url = service.upload(image());
        String filename = url.substring("/uploads/".length());
        when(mapper.selectOwner(filename)).thenReturn(10L);
        assertDoesNotThrow(() -> service.validateOwnership(url));
        UserContext.set(new LoginUser(11L, "another", "STUDENT"));
        assertThrows(BizException.class, () -> service.validateOwnership(url));
        UserContext.set(new LoginUser(10L, "uploader", "STUDENT"));
        Files.delete(directory.resolve(filename));
        assertThrows(BizException.class, () -> service.validateOwnership(url));
    }

    @Test
    void rejectsExternalAndTraversalPaths() {
        assertThrows(BizException.class, () -> service.validateOwnership("https://example.com/photo.png"));
        assertThrows(BizException.class, () -> service.validateOwnership("/uploads/../photo.png"));
        assertDoesNotThrow(() -> service.validateOwnership(null));
    }

    @Test
    void removesFileWhenDatabaseWriteFails() throws Exception {
        doThrow(new IllegalStateException("database unavailable")).when(mapper).insert(anyString(), anyLong());
        assertThrows(IllegalStateException.class, () -> service.upload(image()));
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    private MockMultipartFile image() throws Exception {
        BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("file", "photo.png", "image/png", bytes.toByteArray());
    }
}
