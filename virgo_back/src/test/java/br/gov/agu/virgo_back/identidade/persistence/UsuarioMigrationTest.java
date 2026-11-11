package br.gov.agu.virgo_back.identidade.persistence;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UsuarioMigrationTest {
    @Test
    void evoluiIdentidadesExistentesPreservandoReferenciasEImpondoInvariantes() throws Exception {
        try (var postgres = EmbeddedPostgres.builder().setPort(0).start()) {
            var fonte = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(fonte).target("3").load().migrate();
            var jdbc = JdbcClient.create(fonte);
            jdbc.sql("INSERT INTO usuario (id_consultante) VALUES ('01234567890')").update();
            var processo = UUID.randomUUID();
            jdbc.sql("INSERT INTO processo_monitorado (id, proprietario_id, numero_cnj) VALUES (?, '01234567890', '00000000020264010000')")
                    .param(processo).update();
            var antes = Instant.now().minusSeconds(1);
            Flyway.configure().dataSource(fonte).load().migrate();
            var linha = jdbc.sql("SELECT * FROM usuario").query().singleRow();
            assertEquals("COMUM", linha.get("perfil"));
            assertEquals("ATIVO", linha.get("situacao"));
            assertEquals(false, linha.get("cadastro_completo"));
            assertNull(linha.get("nome_completo"));
            assertNull(linha.get("email"));
            assertNull(linha.get("telefone"));
            var criado = jdbc.sql("SELECT criado_em FROM usuario").query(OffsetDateTime.class).single().toInstant();
            assertFalse(criado.isBefore(antes));
            assertFalse(criado.isAfter(Instant.now()));
            assertEquals("01234567890", jdbc.sql("SELECT proprietario_id FROM processo_monitorado WHERE id = ?")
                    .param(processo).query(String.class).single());
            for (var atribuicao : new String[]{"perfil = 'ROOT'", "situacao = 'APAGADO'", "cadastro_completo = true",
                    "nome_completo = ' '", "email = E'\\t'", "telefone = E'\\n'", "criado_em = NULL"}) {
                assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("UPDATE usuario SET " + atribuicao).update());
            }
            Flyway.configure().dataSource(fonte).load().validate();
        }
    }
}
