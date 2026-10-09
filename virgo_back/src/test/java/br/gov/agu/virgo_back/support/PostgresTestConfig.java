package br.gov.agu.virgo_back.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.io.IOException;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {
    @Bean(destroyMethod = "close")
    EmbeddedPostgres postgres() throws IOException {
        return EmbeddedPostgres.builder().setPort(0).start();
    }

    @Bean
    DataSource dataSource(EmbeddedPostgres postgres) {
        return postgres.getPostgresDatabase();
    }
}
