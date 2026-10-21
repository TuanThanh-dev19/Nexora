package com.nexora.dashboard.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DashboardConfig {
    @Bean
    Clock dashboardClock() { return Clock.systemUTC(); }
}
