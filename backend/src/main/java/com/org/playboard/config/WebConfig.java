package com.org.playboard.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves uploaded avatar photos back out as plain static files under {@code /avatars/**}. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Path avatarDir;

    public WebConfig(@Value("${playboard.storage.avatar-dir}") String avatarDir) {
        this.avatarDir = Path.of(avatarDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/avatars/**").addResourceLocations("file:" + avatarDir + "/");
    }
}
