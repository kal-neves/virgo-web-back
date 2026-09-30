package br.gov.agu.virgo_back.pje;

import br.gov.agu.virgo_back.processo.domain.NumeroProcesso;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PjeRequestWriterTest {
    @Test
    void escreveCamposENamespacesPreservandoCaracteresEspeciais() throws Exception {
        var credenciais = new CredenciaisPje();
        credenciais.setLogin("usuario<&>");
        credenciais.setSenha("senha<&>\"'á</senhaConsultante><intruso/>");
        var documento = XmlFixtures.parse(new PjeRequestWriter().gerarRequest(
                credenciais, new NumeroProcesso("0000000-00.2026.4.01.0000")));
        var raiz = documento.getDocumentElement();
        assertEquals("consultarProcesso", raiz.getLocalName());
        assertEquals("http://www.cnj.jus.br/servico-intercomunicacao-2.2.2/", raiz.getNamespaceURI());
        String ns = "http://www.cnj.jus.br/tipos-servico-intercomunicacao-2.2.2";
        var esperados = java.util.Map.of("idConsultante", credenciais.getLogin(),
                "senhaConsultante", credenciais.getSenha(), "numeroProcesso", "00000000020264010000", "movimentos", "true");
        esperados.forEach((nome, valor) -> {
            var campos = raiz.getElementsByTagNameNS(ns, nome);
            assertEquals(1, campos.getLength());
            assertEquals(raiz, campos.item(0).getParentNode());
            assertEquals(valor, campos.item(0).getTextContent());
        });
        assertEquals(0, documento.getElementsByTagName("intruso").getLength());
    }
}
