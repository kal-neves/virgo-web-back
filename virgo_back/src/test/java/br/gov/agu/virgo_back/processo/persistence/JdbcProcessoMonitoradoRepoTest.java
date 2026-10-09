package br.gov.agu.virgo_back.processo.persistence;

import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.processo.domain.*;
import br.gov.agu.virgo_back.support.PostgresTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(PostgresTestConfig.class)
@Transactional
class JdbcProcessoMonitoradoRepoTest {
    private static final IdConsultante DONO = new IdConsultante("01234567890");
    private static final IdConsultante OUTRO = new IdConsultante("09876543210");
    private static final NumeroProcesso NUMERO = new NumeroProcesso("0000001-00.2026.4.01.0000");

    @Autowired ProcessoMonitoradoRepository repository;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void cadastrarProprietarios() {
        jdbc.sql("INSERT INTO usuario (id_consultante) VALUES (?), (?)")
                .params(DONO.valor(), OUTRO.valor()).update();
    }

    @Test
    void salvaProcessoAindaSemTribunalNemGraus() {
        var processo = processo(UUID.randomUUID(), DONO);
        repository.salvar(processo);

        assertEquals(processo, repository.buscarPorId(DONO, processo.id()).orElseThrow());
        assertEquals(processo, repository.buscarPorNumero(DONO, NUMERO).orElseThrow());
        assertTrue(repository.buscarPorId(OUTRO, processo.id()).isEmpty());
        assertTrue(repository.buscarPorNumero(OUTRO, NUMERO).isEmpty());
        assertTrue(repository.buscarPorId(DONO, UUID.randomUUID()).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"TRF1, PRIMEIRO_GRAU", "TRF6, SEGUNDO_GRAU"})
    void preservaTribunalGrauEMetadados(Tribunal tribunal, GrauJurisdicao grau) {
        var processo = new ProcessoMonitorado(UUID.randomUUID(), DONO, NUMERO,
                tribunal, Set.of(grau), "  Atenção: revisão d'água\nPrazo pendente  ", true);
        repository.salvar(processo);
        assertEquals(processo, repository.buscarPorNumero(DONO, NUMERO).orElseThrow());
    }

    @Test
    void atualizaMetadadosEGrausSemTrocarIdentidade() {
        var original = processo(UUID.randomUUID(), DONO);
        repository.salvar(original);
        var atualizado = new ProcessoMonitorado(original.id(), DONO, NUMERO, Tribunal.TRF6,
                EnumSet.allOf(GrauJurisdicao.class), "Recurso", true);
        repository.salvar(atualizado);
        assertEquals(atualizado, repository.buscarPorId(DONO, original.id()).orElseThrow());

        var apenasSegundo = new ProcessoMonitorado(original.id(), DONO, NUMERO, Tribunal.TRF6,
                Set.of(GrauJurisdicao.SEGUNDO_GRAU), "", false);
        repository.salvar(apenasSegundo);
        repository.salvar(apenasSegundo);
        assertEquals(apenasSegundo, repository.buscarPorNumero(DONO, NUMERO).orElseThrow());
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM processo_monitorado").query(Long.class).single());
    }

    @Test
    void permiteMesmoCnjParaProprietariosDiferentes() {
        var primeiro = processo(UUID.randomUUID(), DONO);
        var segundo = processo(UUID.randomUUID(), OUTRO);
        repository.salvar(primeiro);
        repository.salvar(segundo);
        assertEquals(primeiro, repository.buscarPorNumero(DONO, NUMERO).orElseThrow());
        assertEquals(segundo, repository.buscarPorNumero(OUTRO, NUMERO).orElseThrow());
    }

    @Test
    void bancoRejeitaCnjDuplicadoDoMesmoProprietario() {
        repository.salvar(processo(UUID.randomUUID(), DONO));
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.salvar(processo(UUID.randomUUID(), DONO)));
    }

    @Test
    void exigeProprietarioExistente() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.salvar(processo(UUID.randomUUID(), new IdConsultante("11111111111"))));
    }

    @Test
    void impedeAlterarProprietarioOuCnjDoMesmoId() {
        var original = processo(UUID.randomUUID(), DONO);
        repository.salvar(original);
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.salvar(processo(original.id(), OUTRO)));
        var outroNumero = new ProcessoMonitorado(original.id(), DONO,
                new NumeroProcesso("00000020020264010000"), null, Set.of(), "", false);
        assertThrows(DataIntegrityViolationException.class, () -> repository.salvar(outroNumero));
        assertEquals(original, repository.buscarPorId(DONO, original.id()).orElseThrow());
    }

    @Test
    void bancoRejeitaGrauSemTribunalMesmoForaDoRepository() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("""
                INSERT INTO processo_monitorado (id, proprietario_id, numero_cnj, primeiro_grau_observado)
                VALUES (?, ?, ?, true)
                """).params(UUID.randomUUID(), DONO.valor(), NUMERO.valor()).update());
    }

    @Test
    void bancoPreservaReferenciaAoProprietario() {
        repository.salvar(processo(UUID.randomUUID(), DONO));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.sql("DELETE FROM usuario WHERE id_consultante = ?").param(DONO.valor()).update());
    }

    private static ProcessoMonitorado processo(UUID id, IdConsultante proprietario) {
        return new ProcessoMonitorado(id, proprietario, NUMERO, null, Set.of(), "", false);
    }
}
