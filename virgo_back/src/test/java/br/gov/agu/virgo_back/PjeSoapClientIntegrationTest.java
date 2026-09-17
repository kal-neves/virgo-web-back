package br.gov.agu.virgo_back;

import br.gov.agu.virgo_back.pje.CredenciaisPje;
import br.gov.agu.virgo_back.pje.PjeSoapClient;
import br.gov.agu.virgo_back.processo.domain.Movimentacao;
import br.gov.agu.virgo_back.processo.domain.OrigemPje;
import br.gov.agu.virgo_back.processo.domain.RespostaConsultaOrigem;
import br.gov.agu.virgo_back.processo.domain.StatusConsulta;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Consulta o PJe usando TESTE_LOGIN, TESTE_SENHA e TESTE_PROCESSO
 * Processo deve ser acessível com as credenciais e ter movimentações
 * TESTE_ORIGEM pode ser PJE1 (padrão) ou PJE2
 * Endereços estão em application.properties
 * Sem as três variáveis, o teste é desabilitado
 * Falhas de comunicação, autenticação ou mapeamento falham o teste
 */

@SpringBootTest
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "TESTE_LOGIN", matches = "(?s).*\\S.*")
@EnabledIfEnvironmentVariable(named = "TESTE_SENHA", matches = "(?s).*\\S.*")
@EnabledIfEnvironmentVariable(named = "TESTE_PROCESSO", matches = "(?s).*\\S.*")
class PjeSoapClientIntegrationTest {

    @Autowired
    private PjeSoapClient client;

    @Test
    void deveConsultarPjeRealEMapearMovimentacoesDoProcesso() {
        
        String origemConfigurada = System.getenv("TESTE_ORIGEM");
        OrigemPje origem = origemConfigurada == null
                ? OrigemPje.PJE1
                : OrigemPje.valueOf(origemConfigurada.trim());

        CredenciaisPje credenciais = new CredenciaisPje();
        credenciais.setLogin(System.getenv("TESTE_LOGIN"));
        credenciais.setSenha(System.getenv("TESTE_SENHA"));

        RespostaConsultaOrigem resposta = client.consultarProcesso(
                origem, credenciais, System.getenv("TESTE_PROCESSO").trim()
        );

        assertNotNull(resposta, "O cliente deve retornar resposta tipada");
        assertEquals(origem, resposta.origem());
        assertEquals(StatusConsulta.ENCONTRADO, resposta.status(),
                "A consulta deve encontrar o processo na origem selecionada");
        assertNull(resposta.erro(), "Uma consulta bem-sucedida não deve conter erro");
        assertNotNull(resposta.movimentacoes());
        assertFalse(resposta.movimentacoes().isEmpty(),
                "TESTE_PROCESSO precisa ter movimentações");

        for (Movimentacao movimentacao : resposta.movimentacoes()) {
            assertNotNull(movimentacao);
            assertNotNull(movimentacao.dataHora());
            assertFalse(movimentacao.dataHora().isBlank(),
                    "A data/hora da movimentação deve ser extraída do XML");
            assertNotNull(movimentacao.descricao());
            assertFalse(movimentacao.descricao().isBlank(),
                    "A descrição da movimentação deve ser extraída do XML");
        }
    }
}
