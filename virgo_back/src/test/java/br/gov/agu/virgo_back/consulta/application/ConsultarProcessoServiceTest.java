package br.gov.agu.virgo_back.consulta.application;

import br.gov.agu.virgo_back.consulta.domain.*;
import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.processo.domain.*;
import br.gov.agu.virgo_back.processo.persistence.ProcessoMonitoradoRepository;
import br.gov.agu.virgo_back.support.PostgresTestConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(PostgresTestConfig.class)
class ConsultarProcessoServiceTest {
    private static final IdConsultante DONO = new IdConsultante("44444444444");
    private static final CredenciaisPje CREDENCIAIS = new CredenciaisPje("senha-ficticia", new IdConsultante("55555555555"));
    private static final NumeroProcesso NUMERO = new NumeroProcesso("00000000020264010000");
    @Autowired ConsultarProcessoService service;
    @Autowired ProcessoMonitoradoRepository processos;
    @Autowired JdbcClient jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean ConsultarProcessoGateway gateway;
    private UUID processoId;

    @BeforeEach
    void preparar() {
        jdbc.sql("INSERT INTO usuario (id_consultante) VALUES (?)").param(DONO.valor()).update();
        processoId = UUID.randomUUID();
        processos.salvar(new ProcessoMonitorado(processoId, DONO, NUMERO, null, Set.of(), "", false));
    }

    @AfterEach
    void limpar() {
        jdbc.sql("DELETE FROM movimentacao_detectada WHERE processo_id = ?").param(processoId).update();
        jdbc.sql("DELETE FROM tentativa_consulta_grau WHERE processo_id = ?").param(processoId).update();
        jdbc.sql("DELETE FROM processo_monitorado WHERE id = ?").param(processoId).update();
        jdbc.sql("DELETE FROM usuario WHERE id_consultante = ?").param(DONO.valor()).update();
    }

    @ParameterizedTest
    @CsvSource({"TRF1, ENCONTRADO", "TRF1, NAO_ENCONTRADO", "TRF1, INDISPONIVEL",
            "TRF6, ENCONTRADO", "TRF6, NAO_ENCONTRADO", "TRF6, INDISPONIVEL"})
    void confirmaPrimeiroGrauAntesDeEnviarSegundo(Tribunal tribunal, StatusConsulta statusPrimeiro) {
        when(gateway.consultarProcesso(any(), same(CREDENCIAIS), eq(NUMERO))).thenAnswer(invocacao -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            OrigemPje origem = invocacao.getArgument(0);
            if (origem.grau() == GrauJurisdicao.SEGUNDO_GRAU) {
                assertEquals(1, tentativas());
                assertEquals(statusPrimeiro.name(), jdbc.sql("SELECT status FROM tentativa_consulta_grau WHERE processo_id = ?")
                        .param(processoId).query(String.class).single());
                assertEquals(statusPrimeiro == StatusConsulta.ENCONTRADO ? 1 : 0, deteccoes());
            }
            return resposta(origem, origem.grau() == GrauJurisdicao.PRIMEIRO_GRAU ? statusPrimeiro : StatusConsulta.ENCONTRADO, "Movimento");
        });
        var resultado = service.consultarProcesso(CREDENCIAIS, DONO, processoId, tribunal);
        assertEquals(NUMERO, resultado.numeroProcesso());
        assertEquals(List.of(new OrigemPje(tribunal, GrauJurisdicao.PRIMEIRO_GRAU),
                        new OrigemPje(tribunal, GrauJurisdicao.SEGUNDO_GRAU)),
                resultado.respostas().stream().map(RespostaConsultaOrigem::origem).toList());
        assertEquals(2, tentativas());
        assertEquals(statusPrimeiro == StatusConsulta.ENCONTRADO ? 2 : 1, deteccoes());
    }

    @Test
    void cancelamentoDuranteChamadaConfirmaRespostaESuspendeProximoEnvio() {
        var cancelar = new AtomicBoolean();
        when(gateway.consultarProcesso(any(), any(), any())).thenAnswer(i -> {
            cancelar.set(true);
            return resposta(i.getArgument(0), StatusConsulta.ENCONTRADO, "Movimento");
        });
        var resultado = service.consultarProcesso(CREDENCIAIS, DONO, processoId, Tribunal.TRF1, cancelar::get);
        assertEquals(1, resultado.respostas().size());
        assertEquals(1, tentativas());
        assertEquals(1, deteccoes());
        verify(gateway, times(1)).consultarProcesso(any(), any(), any());
    }

    @Test
    void cancelamentoAnteriorNaoCriaTentativas() {
        assertTrue(service.consultarProcesso(CREDENCIAIS, DONO, processoId, Tribunal.TRF1, () -> true).respostas().isEmpty());
        assertEquals(0, tentativas());
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @CsvSource({"PRIMEIRO_GRAU, 0, 1", "SEGUNDO_GRAU, 1, 2"})
    void falhaDePersistenciaPreservaSomenteGrausConfirmados(GrauJurisdicao grauComFalha, int confirmados, int chamadas) {
        when(gateway.consultarProcesso(any(), any(), any())).thenAnswer(i -> {
            OrigemPje origem = i.getArgument(0);
            return resposta(origem, StatusConsulta.ENCONTRADO,
                    origem.grau() == grauComFalha ? "Invalido\u0000" : "Valido");
        });
        assertThrows(DataIntegrityViolationException.class, this::consultar);
        assertEquals(confirmados, tentativas());
        assertEquals(confirmados, deteccoes());
        verify(gateway, times(chamadas)).consultarProcesso(any(), any(), any());
    }

    @Test
    void acessoNegadoEPersistidoEInterrompeNovasChamadas() {
        when(gateway.consultarProcesso(any(), any(), any())).thenAnswer(i ->
                resposta(i.getArgument(0), StatusConsulta.ACESSO_NEGADO, ""));
        assertEquals(StatusConsulta.ACESSO_NEGADO, consultar().respostas().getFirst().status());
        assertEquals(1, tentativas());
        verify(gateway, times(1)).consultarProcesso(any(), any(), any());
    }

    @Test
    void falhaTipadaDoSegundoGrauPermaneceVisivelSemPerderPrimeiro() {
        when(gateway.consultarProcesso(any(), any(), any())).thenAnswer(i -> {
            OrigemPje origem = i.getArgument(0);
            return resposta(origem, origem.grau() == GrauJurisdicao.PRIMEIRO_GRAU
                    ? StatusConsulta.ENCONTRADO : StatusConsulta.INDISPONIVEL, "Movimento");
        });
        var resultado = consultar();
        assertEquals(StatusConsulta.INDISPONIVEL, resultado.respostas().getLast().status());
        assertEquals(2, tentativas());
        assertEquals(1, deteccoes());
    }

    @Test
    void excecaoInesperadaNoSegundoGrauNaoDesfazPrimeiroNemRepeteSoap() {
        when(gateway.consultarProcesso(any(), any(), any())).thenAnswer(i -> {
            OrigemPje origem = i.getArgument(0);
            if (origem.grau() == GrauJurisdicao.SEGUNDO_GRAU) throw new IllegalStateException("Falha inesperada");
            return resposta(origem, StatusConsulta.ENCONTRADO, "Movimento");
        });
        assertThrows(IllegalStateException.class, this::consultar);
        assertEquals(1, tentativas());
        assertEquals(1, deteccoes());
        verify(gateway, times(2)).consultarProcesso(any(), any(), any());
    }

    @Test
    void novaConsultaRepeteGrausMasNaoDuplicaDeteccoes() {
        when(gateway.consultarProcesso(any(), any(), any())).thenAnswer(i ->
                resposta(i.getArgument(0), StatusConsulta.ENCONTRADO, "Movimento"));
        consultar();
        consultar();
        assertEquals(4, tentativas());
        assertEquals(2, deteccoes());
        assertEquals(2, jdbc.sql("SELECT sum(novas_movimentacoes) FROM tentativa_consulta_grau WHERE processo_id = ?")
                .param(processoId).query(Integer.class).single());
    }

    @Test
    void suspendeTransacaoDoChamadorDuranteSoap() {
        when(gateway.consultarProcesso(any(), any(), any())).thenAnswer(i -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return resposta(i.getArgument(0), StatusConsulta.ENCONTRADO, "Movimento");
        });
        new TransactionTemplate(transactionManager).executeWithoutResult(transacao -> {
            consultar();
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            transacao.setRollbackOnly();
        });
        assertEquals(2, tentativas());
        assertEquals(2, deteccoes());
    }

    @Test
    void processoDeOutroProprietarioNaoDisparaSoap() {
        assertThrows(IllegalArgumentException.class,
                () -> service.consultarProcesso(CREDENCIAIS, CREDENCIAIS.login(), processoId, Tribunal.TRF1));
        verifyNoInteractions(gateway);
    }

    @Test
    void origemIncorretaNaoEPersistida() {
        when(gateway.consultarProcesso(any(), any(), any())).thenReturn(resposta(
                new OrigemPje(Tribunal.TRF6, GrauJurisdicao.SEGUNDO_GRAU), StatusConsulta.ENCONTRADO, "Movimento"));
        assertThrows(IllegalStateException.class, this::consultar);
        assertEquals(0, tentativas());
    }

    private ResultadoConsultaProcesso consultar() {
        return service.consultarProcesso(CREDENCIAIS, DONO, processoId, Tribunal.TRF1);
    }

    private int tentativas() {
        return jdbc.sql("SELECT count(*) FROM tentativa_consulta_grau WHERE processo_id = ?").param(processoId).query(Integer.class).single();
    }

    private int deteccoes() {
        return jdbc.sql("SELECT count(*) FROM movimentacao_detectada WHERE processo_id = ?").param(processoId).query(Integer.class).single();
    }

    private static RespostaConsultaOrigem resposta(OrigemPje origem, StatusConsulta status, String descricao) {
        var movimento = new Movimentacao(new IdentificadorMovimento("-8"),
                LocalDateTime.of(2020, 1, 1, 10, 0), TipoMovimentacao.NACIONAL, 85, descricao);
        return new RespostaConsultaOrigem(origem, status,
                status == StatusConsulta.ENCONTRADO ? List.of(movimento) : List.of(),
                status == StatusConsulta.ENCONTRADO ? null : "Falha ficticia");
    }
}
