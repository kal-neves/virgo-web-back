package br.gov.agu.virgo_back.consulta.persistence;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConsultaGrauMigrationTest {
    private static EmbeddedPostgres postgres;
    private JdbcClient jdbc;
    private UUID processo;

    @BeforeAll
    static void iniciarBancoIsolado() throws IOException {
        postgres = EmbeddedPostgres.builder().setPort(0).start();
    }

    @AfterAll
    static void encerrarBanco() throws IOException {
        if (postgres != null) postgres.close();
    }

    @BeforeEach
    void prepararV2() {
        // Somente o banco temporario exclusivo desta classe e limpo.
        var flyway = Flyway.configure().dataSource(postgres.getPostgresDatabase())
                .target("2").cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
        jdbc = JdbcClient.create(postgres.getPostgresDatabase());
        jdbc.sql("INSERT INTO usuario VALUES ('01234567890'), ('09876543210')").update();
        processo = UUID.randomUUID();
        jdbc.sql("INSERT INTO processo_monitorado (id, proprietario_id, numero_cnj) VALUES (?, '01234567890', '00000010020264010000')")
                .param(processo).update();
    }

    @Test
    void upgradePreservaResultadosDeteccoesEReferenciasSemInventarRodadas() {
        UUID chamada = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO consulta_grau (id, processo_id, tribunal, grau, status, registrada_em, novas_movimentacoes)
                VALUES (?, ?, 'TRF1', 'PRIMEIRO_GRAU', 'ENCONTRADO', '2026-10-09T12:00:00Z', 1)
                """).params(chamada, processo).update();
        jdbc.sql("""
                INSERT INTO movimentacao_detectada (processo_id, grau, id_movimentacao, tribunal, data_hora,
                    tipo, codigo, descricao, primeira_deteccao, consulta_grau_id)
                VALUES (?, 'PRIMEIRO_GRAU', '-8', 'TRF1', '2020-01-02 10:30:00', 'NACIONAL', 85,
                    'Original', '2026-10-09T12:00:00Z', ?)
                """).params(processo, chamada).update();
        var antes = jdbc.sql("SELECT * FROM movimentacao_detectada").query().singleRow();

        migrar();

        assertEquals(antes, jdbc.sql("SELECT * FROM movimentacao_detectada").query().singleRow());
        var tentativa = jdbc.sql("SELECT * FROM tentativa_consulta_grau WHERE id = ?").param(chamada).query().singleRow();
        assertEquals("ENCONTRADO", tentativa.get("status"));
        assertEquals(1, tentativa.get("novas_movimentacoes"));
        assertNull(tentativa.get("consulta_planejada_id"));
        assertNull(tentativa.get("movimentacoes_exibidas"));
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM rodada").query(Long.class).single());
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM consulta_grau").query(Long.class).single());
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.sql("DELETE FROM tentativa_consulta_grau WHERE id = ?").param(chamada).update());
        Flyway.configure().dataSource(postgres.getPostgresDatabase()).load().validate();
    }

    @Test
    void planejamentoExigeVinculosConsistentesEUmGrauPorItem() {
        migrar();
        UUID rodada = criarRodada();
        UUID item = criarItem(rodada);
        UUID consulta = criarConsulta(item);
        assertThrows(DataIntegrityViolationException.class, () -> criarConsulta(item));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("""
                UPDATE consulta_grau SET estado = 'CONCLUIDA', inicio = now(), fim = now() WHERE id = ?
                """).param(consulta).update());
        assertThrows(DataIntegrityViolationException.class, this::criarRodada);

        UUID outroProcesso = UUID.randomUUID();
        jdbc.sql("INSERT INTO processo_monitorado (id, proprietario_id, numero_cnj) VALUES (?, '09876543210', '00000010020264010000')")
                .param(outroProcesso).update();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("""
                INSERT INTO item_rodada (id, rodada_id, proprietario_id, processo_id, numero_cnj, ordem)
                VALUES (?, ?, '01234567890', ?, '00000020020264010000', 1)
                """).params(UUID.randomUUID(), rodada, outroProcesso).update());

        jdbc.sql("UPDATE rodada SET estado = 'CANCELADA', fim = now() WHERE id = ?").param(rodada).update();
        assertDoesNotThrow(this::criarRodada);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.sql("UPDATE rodada SET estado = 'EM_ANDAMENTO' WHERE id = ?").param(rodada).update());
    }

    @Test
    void novaTentativaPreservaFalhaAnteriorEReferenciaAtualPertenceAoPlanejamento() {
        migrar();
        UUID consulta = criarConsulta(criarItem(criarRodada()));
        UUID primeira = inserirTentativa(consulta, 1, "INDISPONIVEL", "Tempo esgotado");
        UUID segunda = inserirTentativa(consulta, 2, "ENCONTRADO", null);
        jdbc.sql("""
                UPDATE consulta_grau SET tentativa_atual_id = ?, estado = 'CONCLUIDA',
                    inicio = '2026-10-15T12:00:00Z', fim = '2026-10-15T12:00:10Z' WHERE id = ?
                """).params(segunda, consulta).update();
        assertEquals("Tempo esgotado", jdbc.sql("SELECT erro FROM tentativa_consulta_grau WHERE id = ?")
                .param(primeira).query(String.class).single());
        assertEquals(2L, jdbc.sql("SELECT count(*) FROM tentativa_consulta_grau WHERE consulta_planejada_id = ?")
                .param(consulta).query(Long.class).single());
        assertThrows(DataIntegrityViolationException.class, () -> inserirTentativa(consulta, 2, "ENCONTRADO", null));

        UUID avulsa = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO tentativa_consulta_grau (id, processo_id, tribunal, grau, status, registrada_em)
                VALUES (?, ?, 'TRF1', 'PRIMEIRO_GRAU', 'ENCONTRADO', now())
                """).params(avulsa, processo).update();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql(
                "UPDATE consulta_grau SET tentativa_atual_id = ? WHERE id = ?").params(avulsa, consulta).update());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql(
                "UPDATE tentativa_consulta_grau SET grau = 'SEGUNDO_GRAU' WHERE id = ?").param(primeira).update());
    }

    private void migrar() {
        Flyway.configure().dataSource(postgres.getPostgresDatabase()).load().migrate();
    }

    private UUID criarRodada() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO rodada (id, proprietario_id, executor_id, escopo, janela_dias, data_referencia, zona_referencia)
                VALUES (?, '01234567890', '09876543210', 'PROCESSO', 0, '2026-10-15', 'America/Sao_Paulo')
                """).param(id).update();
        return id;
    }

    private UUID criarItem(UUID rodada) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO item_rodada (id, rodada_id, proprietario_id, processo_id, numero_cnj, ordem)
                VALUES (?, ?, '01234567890', ?, '00000010020264010000', 0)
                """).params(id, rodada, processo).update();
        return id;
    }

    private UUID criarConsulta(UUID item) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO consulta_grau (id, item_rodada_id, processo_id, tribunal, grau, endpoint)
                VALUES (?, ?, ?, 'TRF1', 'PRIMEIRO_GRAU', 'https://pje.invalid/soap')
                """).params(id, item, processo).update();
        return id;
    }

    private UUID inserirTentativa(UUID consulta, int numero, String status, String erro) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO tentativa_consulta_grau
                    (id, processo_id, tribunal, grau, status, erro, registrada_em, consulta_planejada_id, numero_tentativa)
                VALUES (?, ?, 'TRF1', 'PRIMEIRO_GRAU', ?, ?, now(), ?, ?)
                """).params(id, processo, status, erro, consulta, numero).update();
        return id;
    }
}
