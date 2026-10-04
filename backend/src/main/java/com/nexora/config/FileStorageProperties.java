package com.nexora.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.file-storage")
public class FileStorageProperties {

    private Path root = Path.of("..", ".data", "uploads");

    public Path getRoot() {
        return root;
    }

    public void setRoot(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("File storage root is required.");
        }
        this.root = root;
    }
}
