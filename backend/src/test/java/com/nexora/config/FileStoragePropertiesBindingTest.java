package com.nexora.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

class FileStoragePropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner(
            ServletWebServerApplicationContext::new)
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    ServletWebServerFactoryAutoConfiguration.class))
            .withUserConfiguration(FileStorageConfig.class)
            .withPropertyValues("server.port=0");

    @Test
    void bindsRelativePathOutsideServletRoot() {
        contextRunner.withPropertyValues("app.file-storage.root=../.data/uploads")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(FileStorageProperties.class).getRoot())
                            .isEqualTo(Path.of("..", ".data", "uploads"));
                });
    }

    @Test
    void bindsAbsoluteFilesystemPathWithSpaces(@TempDir Path temporaryDirectory) {
        Path storageRoot = temporaryDirectory.resolve("file uploads");
        contextRunner.withPropertyValues("app.file-storage.root=" + storageRoot)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(FileStorageProperties.class).getRoot()).isEqualTo(storageRoot);
                });
    }
}
