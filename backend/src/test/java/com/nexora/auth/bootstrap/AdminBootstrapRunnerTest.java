package com.nexora.auth.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class AdminBootstrapRunnerTest {

    private static final String EMAIL = "admin@example.test";
    private static final String PASSWORD = "test-only-password";

    private final AdminBootstrapService service = mock(AdminBootstrapService.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AdminBootstrapService.class, () -> service)
            .withUserConfiguration(AdminBootstrapRunner.class);

    @ParameterizedTest
    @ValueSource(strings = {"local", "demo"})
    void runsOnlyWhenExplicitlyEnabledInAllowedProfile(String profile, CapturedOutput output) {
        when(service.createInitialAdmin(EMAIL, PASSWORD)).thenReturn(true);

        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withPropertyValues("app.bootstrap.admin.enabled=true",
                        "app.bootstrap.admin.email=" + EMAIL,
                        "app.bootstrap.admin.password=" + PASSWORD)
                .run(context -> {
                    assertThat(context).hasSingleBean(AdminBootstrapRunner.class);
                    context.getBean(AdminBootstrapRunner.class).run(new DefaultApplicationArguments());
                    verify(service).createInitialAdmin(EMAIL, PASSWORD);
                });

        assertThat(output).contains("Local/demo Admin bootstrap created an account.")
                .doesNotContain(EMAIL, PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "test"})
    void ignoresEnableFlagOutsideLocalAndDemo(String profile) {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withPropertyValues("app.bootstrap.admin.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(AdminBootstrapRunner.class));

        verifyNoInteractions(service);
    }

    @Test
    void doesNotRunWithoutAnExplicitProfile() {
        contextRunner.withPropertyValues("app.bootstrap.admin.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(AdminBootstrapRunner.class));
        verifyNoInteractions(service);
    }

    @Test
    void remainsDisabledByDefaultEvenInLocalProfile() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .run(context -> assertThat(context).doesNotHaveBean(AdminBootstrapRunner.class));
        verifyNoInteractions(service);
    }

    @Test
    void passesMissingCredentialsToValidationWhenExplicitlyEnabled() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .withPropertyValues("app.bootstrap.admin.enabled=true")
                .run(context -> {
                    context.getBean(AdminBootstrapRunner.class).run(new DefaultApplicationArguments());
                    verify(service).createInitialAdmin("", "");
                });
    }
}
