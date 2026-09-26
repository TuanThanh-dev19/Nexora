package com.nexora;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import com.nexora.testsupport.NexoraIntegrationTest;

@NexoraIntegrationTest
class NexoraBackendApplicationTests {

	@Autowired
	private Environment environment;

	@Test
	void contextLoads() {
		org.assertj.core.api.Assertions.assertThat(environment.acceptsProfiles(Profiles.of("test"))).isTrue();
	}

}
