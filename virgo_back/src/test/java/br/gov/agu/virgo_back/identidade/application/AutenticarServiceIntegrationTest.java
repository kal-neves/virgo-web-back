package br.gov.agu.virgo_back.identidade.application;

import br.gov.agu.virgo_back.identidade.domain.*;
import br.gov.agu.virgo_back.identidade.persistence.UsuarioRepository;
import br.gov.agu.virgo_back.support.PostgresTestConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "pje.tribunal-autenticacao=TRF6", "pje.grau-autenticacao=SEGUNDO_GRAU",
        "pje.endpoints.trf6-segundo-grau=https://login.invalid/soap",
        "pje.numero-prova=00000000120264010000"
})
@Import(PostgresTestConfig.class)
class AutenticarServiceIntegrationTest {
    private static final IdConsultante ID = new IdConsultante("01234567890");
    private static final CredenciaisPje CREDENCIAIS = new CredenciaisPje("senha-ficticia", ID);
    @Autowired AutenticarService service;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcClient jdbc;
    @Autowired PlatformTransactionManager transacoes;
    @MockitoBean WebServiceTemplate transporte;

    @AfterEach
    void limpar() {
        jdbc.sql("DELETE FROM usuario WHERE id_consultante = ?").param(ID.valor()).update();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "true|Processo consultado com sucesso",
            "false|Número do processo inválido: 00000000120264010000",
            "false|Processo de número 00000000120264010000 não encontrado!"
    })
    void respostasReconhecidasCriamSomenteIdentidadeComum(String sucesso, String mensagem) {
        responder(sucesso, mensagem);
        var usuario = service.autenticar(CREDENCIAIS);
        assertEquals(ID, usuario.id());
        assertEquals(Perfil.COMUM, usuario.perfil());
        assertEquals(SituacaoUsuario.ATIVO, usuario.situacao());
        assertFalse(usuario.cadastroCompleto());
        assertNull(usuario.nomeCompleto());
        assertEquals(usuario, usuarios.buscarPorId(ID).orElseThrow());
        assertEquals(0, jdbc.sql("SELECT count(*) FROM rodada WHERE executor_id = ?").param(ID.valor()).query(Integer.class).single());
        assertEquals(0, jdbc.sql("SELECT count(*) FROM processo_monitorado WHERE proprietario_id = ?").param(ID.valor()).query(Integer.class).single());
        verify(transporte).sendSourceAndReceiveToResult(eq("https://login.invalid/soap"), any(Source.class), any(DOMResult.class));
        verifyNoMoreInteractions(transporte);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "false|Erro ao realizar login via MNI. exception invoking: loginFailed|true",
            "false|Erro ao realizar login via MNI. O usuário '01234567890' não está corretamente cadastrado no sistema.|true",
            "false|O usuário '01234567890' não está corretamente cadastrado no sistema.|true",
            "false|Mensagem desconhecida|false",
            "true|Mensagem desconhecida|false",
            "sim|Processo consultado com sucesso|false",
            "true|Número do processo inválido: 00000000120264010000|false"
    })
    void falhasOuMensagensNaoReconhecidasNaoCriamIdentidade(String sucesso, String mensagem, boolean negada) {
        responder(sucesso, mensagem);
        var falha = assertThrows(RuntimeException.class, () -> service.autenticar(CREDENCIAIS));
        if (negada) assertInstanceOf(SecurityException.class, falha);
        else assertInstanceOf(IllegalStateException.class, falha);
        assertTrue(usuarios.buscarPorId(ID).isEmpty());
        assertFalse(falha.getMessage().contains(CREDENCIAIS.senha()));
    }

    @Test
    void timeoutNaoProvisionaNemRepeteAChamada() {
        when(transporte.sendSourceAndReceiveToResult(anyString(), any(Source.class), any(DOMResult.class)))
                .thenThrow(new WebServiceIOException("senha-ficticia"));
        var falha = assertThrows(IllegalStateException.class, () -> service.autenticar(CREDENCIAIS));
        assertFalse(falha.getMessage().contains(CREDENCIAIS.senha()));
        assertTrue(usuarios.buscarPorId(ID).isEmpty());
        verify(transporte).sendSourceAndReceiveToResult(eq("https://login.invalid/soap"), any(Source.class), any(DOMResult.class));
        verifyNoMoreInteractions(transporte);
    }

    @Test
    void novoLoginPreservaPerfilEInativacaoImpedeEntrada() {
        usuarios.criarSeAusente(ID, Instant.parse("2026-11-01T12:00:00Z"));
        usuarios.completarCadastro(ID, "Maria Silva", "maria@example.test", "123");
        usuarios.alterarPerfil(ID, Perfil.ADMIN);
        var antes = usuarios.buscarPorId(ID).orElseThrow();
        responder("false", "Número do processo inválido: 00000000120264010000");
        assertEquals(antes, service.autenticar(CREDENCIAIS));
        usuarios.alterarSituacao(ID, SituacaoUsuario.INATIVO);
        var inativo = usuarios.buscarPorId(ID).orElseThrow();
        assertThrows(SecurityException.class, () -> service.autenticar(CREDENCIAIS));
        assertEquals(inativo, usuarios.buscarPorId(ID).orElseThrow());
    }

    @Test
    void primeirosLoginsConcorrentesRetornamAMesmaIdentidade() throws Exception {
        responder("false", "Processo de número 00000000120264010000 não encontrado!");
        var inicio = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = threads.submit(() -> { assertTrue(inicio.await(5, TimeUnit.SECONDS)); return service.autenticar(CREDENCIAIS); });
            var b = threads.submit(() -> { assertTrue(inicio.await(5, TimeUnit.SECONDS)); return service.autenticar(CREDENCIAIS); });
            inicio.countDown();
            assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.sql("SELECT count(*) FROM usuario WHERE id_consultante = ?").param(ID.valor()).query(Integer.class).single());
    }

    @Test
    void soapFicaForaDaTransacaoEProvisionamentoEConfirmadoAntesDoRetorno() {
        responder("true", "Processo consultado com sucesso");
        new TransactionTemplate(transacoes).executeWithoutResult(tx -> {
            service.autenticar(CREDENCIAIS);
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            tx.setRollbackOnly();
        });
        assertTrue(usuarios.buscarPorId(ID).isPresent());
    }

    private void responder(String sucesso, String mensagem) {
        when(transporte.sendSourceAndReceiveToResult(anyString(), any(Source.class), any(DOMResult.class)))
                .thenAnswer(i -> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    var request = new StringWriter();
                    TransformerFactory.newInstance().newTransformer().transform(i.getArgument(1), new StreamResult(request));
                    assertTrue(request.toString().contains("00000000120264010000"));
                    assertTrue(request.toString().contains(ID.valor()));
                    var xml = """
                            <s:consultarProcessoResposta xmlns:s="http://www.cnj.jus.br/servico-intercomunicacao-2.2.2/"
                                xmlns:t="http://www.cnj.jus.br/tipos-servico-intercomunicacao-2.2.2">
                                <t:sucesso>%s</t:sucesso><t:mensagem>%s</t:mensagem>
                            </s:consultarProcessoResposta>
                            """.formatted(sucesso, mensagem);
                    var factory = DocumentBuilderFactory.newNSInstance();
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    DOMResult response = i.getArgument(2);
                    response.setNode(factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))));
                    return true;
                });
    }
}
