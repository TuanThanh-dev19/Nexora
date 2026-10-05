package com.nexora.auth.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Explicitly enabled local/demo startup provisioning for the first Admin. */
@Component
@Profile({"local", "demo"})
@ConditionalOnProperty(prefix = "app.bootstrap.admin", name = "enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapService bootstrapService;
    private final String email;
    private final String password;

    public AdminBootstrapRunner(AdminBootstrapService bootstrapService,
                                @Value("${app.bootstrap.admin.email:}") String email,
                                @Value("${app.bootstrap.admin.password:}") String password) {
        this.bootstrapService = bootstrapService;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapService.createInitialAdmin(email, password)) {
            log.info("Local/demo Admin bootstrap created an account.");
        } else {
            log.info("Local/demo Admin bootstrap skipped: an Admin already exists.");
        }
    }
}
