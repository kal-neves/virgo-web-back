package br.gov.agu.virgo_back;

import br.gov.agu.virgo_back.support.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestConfig.class)
class VirgoBackApplicationTests {

	@Test
	void contextLoads() {
	}

}
