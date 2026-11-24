package com.project.service;

import com.project.common.GlobalExceptionHandler;
import com.project.common.PageUtil;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestBoundaryTest {
    @Test
    void missingUploadedFileReturnsClientError() {
        var handler = new GlobalExceptionHandler();
        assertEquals(400, handler.handleMissingUpload(new MissingServletRequestPartException("file")).getCode());
    }

    @Test
    void veryLargePageCannotOverflowIntoNegativeSqlOffset() {
        assertTrue(PageUtil.offset(Integer.MAX_VALUE, 50) >= 0);
    }

    @Test
    void uploadSizeLimitReturnsClientError() {
        var handler = new GlobalExceptionHandler();
        assertEquals(400, handler.handleUploadSize(new MaxUploadSizeExceededException(5 * 1024 * 1024)).getCode());
    }
}
