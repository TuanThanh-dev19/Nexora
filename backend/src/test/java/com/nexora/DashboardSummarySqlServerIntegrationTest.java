package com.nexora;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import com.nexora.dashboard.DashboardQueryContract;

@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
@Import(TestcontainersConfiguration.class)
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_SQLSERVER_IT", matches = "(?i)true")
class DashboardSummarySqlServerIntegrationTest extends DashboardQueryContract {}
