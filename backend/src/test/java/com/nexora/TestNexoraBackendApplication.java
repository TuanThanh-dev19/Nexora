package com.nexora;

import org.springframework.boot.SpringApplication;

public class TestNexoraBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(NexoraBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
