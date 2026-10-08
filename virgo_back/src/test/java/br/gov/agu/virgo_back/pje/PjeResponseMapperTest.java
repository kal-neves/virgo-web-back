package br.gov.agu.virgo_back.pje;

import br.gov.agu.virgo_back.consulta.domain.*;
import br.gov.agu.virgo_back.processo.domain.Tribunal;
import br.gov.agu.virgo_back.processo.domain.GrauJurisdicao;
import br.gov.agu.virgo_back.processo.domain.TipoMovimentacao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class PjeResponseMapperTest {
    private final PjeResponseMapper mapper = new PjeResponseMapper();

    @Test
    void mapeiaCamposSemMisturarDocumentosOuMovimentosAninhados() throws Exception {
        var resposta = mapear(XmlFixtures.movimentos());
        assertEquals(StatusConsulta.ENCONTRADO, resposta.status());
        assertEquals(new OrigemPje(Tribunal.TRF1, GrauJurisdicao.SEGUNDO_GRAU), resposta.origem());
        assertNull(resposta.erro());
        assertEquals(2, resposta.movimentacoes().size());
        var nacional = resposta.movimentacoes().getFirst();
        assertEquals("-0008", nacional.id().valor());
        assertEquals(TipoMovimentacao.NACIONAL, nacional.tipo());
        assertEquals(85, nacional.codigo());
        assertEquals(LocalDateTime.of(2026, 9, 29, 12, 30, 45), nacional.dataHora());
        assertEquals("Juntada & conferência\nPetição <recebida>", nacional.descricao());
        var local = resposta.movimentacoes().get(1);
        assertEquals("local-92233720368547758080", local.id().valor());
        assertEquals(TipoMovimentacao.LOCAL, local.tipo());
        assertEquals(123, local.codigo());
        assertEquals("Conclusão para decisão", local.descricao());
    }

    @Test
    void prefixosNaoAlteramMapeamento() throws Exception {
        String xml = XmlFixtures.movimentos();
        assertEquals(mapear(xml), mapear(xml.replace("m:", "outro:").replace("xmlns:m=", "xmlns:outro=")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "20260230120000", "20260929250000", "invalida"})
    void dataInvalidaNoSegundoMovimentoNaoProduzSucessoParcial(String data) throws Exception {
        invalida(XmlFixtures.movimentos().replace("20260928100000", data));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dataHora=\"20260928100000\"", "identificadorMovimento=\"-0008\"",
            "codigoNacional=\"85\"", "descricao=\"Conclusão para decisão\""})
    void rejeitaAtributosObrigatoriosAusentes(String atributo) throws Exception {
        invalida(XmlFixtures.movimentos().replace(atributo, ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "talvez", "TRUE"})
    void rejeitaSucessoInvalido(String valor) throws Exception {
        invalida(XmlFixtures.movimentos().replace(">true</t:sucesso>", ">" + valor + "</t:sucesso>"));
    }

    @Test
    void exigeSucessoDiretoUnicoEProcesso() throws Exception {
        String xml = XmlFixtures.movimentos();
        invalida(xml.replace("<t:sucesso>true</t:sucesso>", ""));
        invalida(xml.replace("<t:sucesso>true</t:sucesso>",
                "<t:sucesso>true</t:sucesso><t:sucesso>false</t:sucesso>"));
        invalida(xml.replace("t:processo", "t:outro"));
        invalida(xml.replace("<t:sucesso>true</t:sucesso>", "<t:outro><t:sucesso>true</t:sucesso></t:outro>"));
    }

    @Test
    void rejeitaNamespaceOuRaizInesperados() throws Exception {
        String xml = XmlFixtures.movimentos();
        invalida(xml.replace("http://www.cnj.jus.br/intercomunicacao-2.2.2", "urn:errado"));
        invalida(xml.replace("http://www.cnj.jus.br/tipos-servico-intercomunicacao-2.2.2", "urn:errado"));
        invalida(xml.replace("s:consultarProcessoResposta", "s:outraResposta"));
    }

    @Test
    void rejeitaTipoAmbiguoAusenteECodigoInvalido() throws Exception {
        String xml = XmlFixtures.movimentos();
        invalida(xml.replace("</m:movimentoNacional>",
                "</m:movimentoNacional><m:movimentoLocal codigoMovimento=\"1\" descricao=\"outro\"/>"));
        invalida(xml.replace("m:movimentoNacional", "m:outro"));
        invalida(xml.replace("codigoNacional=\"85\"", "codigoNacional=\"abc\""));
        invalida(xml.replace("codigoNacional=\"85\"", "codigoNacional=\"999999999999999\""));
    }

    @Test
    void aceitaProcessoSemMovimentosEComplementoOpcional() throws Exception {
        String xml = XmlFixtures.movimentos();
        var vazio = mapear(xml.replaceAll("(?s)<m:movimento .*?</m:movimento>", ""));
        assertEquals(StatusConsulta.ENCONTRADO, vazio.status());
        assertTrue(vazio.movimentacoes().isEmpty());
        assertEquals("", mapear(xml.replaceAll("(?s)<m:complemento>.*?</m:complemento>", ""))
                .movimentacoes().getFirst().descricao());
    }

    @ParameterizedTest
    @CsvSource({"Número do processo inválido, PROCESSO_INVALIDO",
            "Erro ao realizar login via MNI. O usuário '01234567890' não está corretamente cadastrado no sistema., ACESSO_NEGADO",
            "O usuário '01234567890' não está corretamente cadastrado no sistema., ACESSO_NEGADO",
            "Erro ao realizar login via MNI. exception invoking: loginFailed, ACESSO_NEGADO",
            "Processo de número 00000000020264010000 não encontrado!, NAO_ENCONTRADO",
            "Erro ao realizar login via MNI. Falha desconhecida no serviço., RESPOSTA_INVALIDA",
            "Mensagem desconhecida, RESPOSTA_INVALIDA"})
    void classificaFalhasSemConfundirAusenciaComAutenticacao(String mensagem, StatusConsulta status) throws Exception {
        var resposta = mapear(XmlFixtures.movimentos().replace(">true</t:sucesso>", ">false</t:sucesso>")
                .replace("Processo consultado com sucesso", mensagem));
        assertEquals(status, resposta.status());
        assertEquals(new OrigemPje(Tribunal.TRF1, GrauJurisdicao.SEGUNDO_GRAU), resposta.origem());
        assertTrue(resposta.movimentacoes().isEmpty());
        assertNotNull(resposta.erro());
        assertFalse(resposta.erro().isBlank());
        if (status == StatusConsulta.ACESSO_NEGADO) {
            assertFalse(resposta.erro().contains("01234567890"));
            assertFalse(resposta.erro().contains("exception invoking"));
            assertFalse(resposta.erro().contains("loginFailed"));
        }
    }

    private void invalida(String xml) throws Exception {
        var resposta = mapear(xml);
        assertEquals(StatusConsulta.RESPOSTA_INVALIDA, resposta.status());
        assertTrue(resposta.movimentacoes().isEmpty());
        assertNotNull(resposta.erro());
    }

    private RespostaConsultaOrigem mapear(String xml) throws Exception {
        return mapper.mapearResposta(XmlFixtures.parse(xml), new OrigemPje(Tribunal.TRF1, GrauJurisdicao.SEGUNDO_GRAU));
    }
}
