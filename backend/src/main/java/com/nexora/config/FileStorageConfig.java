package com.nexora.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfig {

    @Bean
    @ConfigurationPropertiesBinding
    static Converter<String, Path> fileStoragePathConverter() {
        return new FileStoragePathConverter();
    }

    /** Resolve configuration as filesystem paths, independent of Tomcat's web root. */
    private static final class FileStoragePathConverter implements Converter<String, Path> {

        @Override
        public Path convert(String source) {
            return Path.of(source);
        }
    }
}
