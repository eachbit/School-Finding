package com.project.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class UploadConfig implements WebMvcConfigurer {
    private final String location;

    public UploadConfig(@Value("${app.upload.directory:.local/uploads}") String directory) {
        String uri = Path.of(directory).toAbsolutePath().normalize().toUri().toString();
        location = uri.endsWith("/") ? uri : uri + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
