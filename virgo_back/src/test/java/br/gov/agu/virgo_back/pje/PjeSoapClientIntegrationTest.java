package br.gov.agu.virgo_back.pje;

import br.gov.agu.virgo_back.consulta.domain.*;
import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.processo.domain.Tribunal;
import br.gov.agu.virgo_back.processo.domain.GrauJurisdicao;
import br.gov.agu.virgo_back.processo.domain.NumeroProcesso;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

/** Teste real opt-in; consulte src/test/README.md antes de executar. */
@SpringBootTest
@Tag("pje-live")
@EnabledIfEnvironmentVariable(named = "PJE_LIVE_TEST", matches = "true")
@EnabledIfEnvironmentVariable(named = "TESTE_LOGIN", matches = "(?s).*\\S.*")
@EnabledIfEnvironmentVariable(named = "TESTE_SENHA", matches = "(?s).*\\S.*")
@EnabledIfEnvironmentVariable(named = "TESTE_PROCESSO", matches = "(?s).*\\S.*")
class PjeSoapClientIntegrationTest {
    @Autowired
    private PjeSoapClient cliente;

    @Test
    void consultaOrigemRealEMapeiaMovimentos() {
        String configurada = System.getenv("TESTE_ORIGEM");
        OrigemPje origem = configurada == null ? new OrigemPje(Tribunal.TRF1, GrauJurisdicao.PRIMEIRO_GRAU) : switch (configurada.trim()) {
            case "PJE1", "TRF1PJE1" -> new OrigemPje(Tribunal.TRF1, GrauJurisdicao.PRIMEIRO_GRAU);
            case "PJE2", "TRF1PJE2" -> new OrigemPje(Tribunal.TRF1, GrauJurisdicao.SEGUNDO_GRAU);
            case "TRF6PJE1" -> new OrigemPje(Tribunal.TRF6, GrauJurisdicao.PRIMEIRO_GRAU);
            case "TRF6PJE2" -> new OrigemPje(Tribunal.TRF6, GrauJurisdicao.SEGUNDO_GRAU);
            default -> throw new IllegalArgumentException("TESTE_ORIGEM inválida: " + configurada);
        };
        var credenciais = new CredenciaisPje(System.getenv("TESTE_SENHA"),
                new IdConsultante(System.getenv("TESTE_LOGIN")));
        var resposta = cliente.consultarProcesso(origem, credenciais,
                new NumeroProcesso(System.getenv("TESTE_PROCESSO")));
        assertEquals(StatusConsulta.ENCONTRADO, resposta.status());
        assertEquals(origem, resposta.origem());
        assertNull(resposta.erro());
        assertFalse(resposta.movimentacoes().isEmpty(), "Use um processo acessível com movimentos na origem selecionada");
        resposta.movimentacoes().forEach(movimento -> {
            assertFalse(movimento.id().valor().isBlank());
            assertNotNull(movimento.dataHora());
            assertNotNull(movimento.tipo());
            assertNotNull(movimento.descricao()); // Complementos nacionais são opcionais.
        });
    }
}
