package br.gov.agu.virgo_back.identidade.persistence;

import br.gov.agu.virgo_back.identidade.domain.*;
import br.gov.agu.virgo_back.support.PostgresTestConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(PostgresTestConfig.class)
class JdbcUsuarioRepositoryTest {
    private static final IdConsultante ID = new IdConsultante("01234567890");
    private static final Instant CRIADO = Instant.parse("2026-11-11T12:00:00.123456Z");
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcClient jdbc;
    @Autowired PlatformTransactionManager transacoes;

    @AfterEach
    void limpar() {
        jdbc.sql("DELETE FROM usuario WHERE id_consultante = ?").param(ID.valor()).update();
    }

    @Test
    void criaComumAtivoSemContatosNemCadastroCompleto() {
        assertTrue(usuarios.buscarPorId(ID).isEmpty());
        var usuario = usuarios.criarSeAusente(ID, CRIADO);
        assertEquals(new Usuario(ID, null, null, null, Perfil.COMUM, SituacaoUsuario.ATIVO, false, CRIADO), usuario);
        assertEquals(usuario, usuarios.buscarPorId(ID).orElseThrow());
    }

    @Test
    void novaCriacaoNaoSobrescreveAdministradorInativoNemContatos() {
        usuarios.criarSeAusente(ID, CRIADO);
        assertTrue(usuarios.alterarPerfil(ID, Perfil.ADMIN));
        assertTrue(usuarios.alterarSituacao(ID, SituacaoUsuario.INATIVO));
        assertTrue(usuarios.completarCadastro(ID, " Maria Silva ", "maria@example.test", "+55 31 99999-0000"));
        var antes = usuarios.buscarPorId(ID).orElseThrow();
        assertEquals(antes, usuarios.criarSeAusente(ID, CRIADO.plusSeconds(100)));
        assertEquals(Perfil.ADMIN, antes.perfil());
        assertEquals(SituacaoUsuario.INATIVO, antes.situacao());
        assertTrue(antes.cadastroCompleto());
        assertEquals(CRIADO, antes.criadoEm());
        assertEquals("Maria Silva", antes.nomeCompleto());
    }

    @Test
    void mudancasAdministrativasPreservamContatosEPermitemReativarEDespromover() {
        usuarios.criarSeAusente(ID, CRIADO);
        usuarios.completarCadastro(ID, "Maria Silva", "maria@example.test", "123");
        var antes = usuarios.buscarPorId(ID).orElseThrow();
        usuarios.alterarPerfil(ID, Perfil.ADMIN);
        usuarios.alterarSituacao(ID, SituacaoUsuario.INATIVO);
        usuarios.alterarPerfil(ID, Perfil.COMUM);
        usuarios.alterarSituacao(ID, SituacaoUsuario.ATIVO);
        assertEquals(antes, usuarios.buscarPorId(ID).orElseThrow());
    }

    @Test
    void atualizacaoNaoCriaUsuarioAusente() {
        assertFalse(usuarios.completarCadastro(ID, "Maria Silva", "maria@example.test", "123"));
        assertFalse(usuarios.alterarPerfil(ID, Perfil.ADMIN));
        assertFalse(usuarios.alterarSituacao(ID, SituacaoUsuario.INATIVO));
        assertTrue(usuarios.buscarPorId(ID).isEmpty());
    }

    @Test
    void contatoInvalidoNaoAlteraRegistro() {
        var antes = usuarios.criarSeAusente(ID, CRIADO);
        assertThrows(IllegalArgumentException.class, () -> usuarios.completarCadastro(ID, "Maria Silva", " ", "123"));
        assertEquals(antes, usuarios.buscarPorId(ID).orElseThrow());
    }

    @Test
    void participaDaTransacaoDoChamador() {
        new TransactionTemplate(transacoes).executeWithoutResult(tx -> {
            usuarios.criarSeAusente(ID, CRIADO);
            usuarios.completarCadastro(ID, "Maria Silva", "maria@example.test", "123");
            tx.setRollbackOnly();
        });
        assertTrue(usuarios.buscarPorId(ID).isEmpty());
    }

    @Test
    void criacoesConcorrentesRetornamOMesmoRegistroSemSobrescreverData() throws Exception {
        var inicio = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var primeira = threads.submit(() -> { assertTrue(inicio.await(5, TimeUnit.SECONDS)); return usuarios.criarSeAusente(ID, CRIADO); });
            var segunda = threads.submit(() -> { assertTrue(inicio.await(5, TimeUnit.SECONDS)); return usuarios.criarSeAusente(ID, CRIADO.plusSeconds(1)); });
            inicio.countDown();
            assertEquals(primeira.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.sql("SELECT count(*) FROM usuario WHERE id_consultante = ?").param(ID.valor()).query(Integer.class).single());
    }
}
