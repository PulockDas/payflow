package com.pulock.payflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Starts the whole app (all beans, config, DB connection). If anything fails to start,
// this test fails. "test" profile = fake secrets from application-test.yml.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PayflowApplicationTests {

	@Test
	void contextLoads() {
	}

}
