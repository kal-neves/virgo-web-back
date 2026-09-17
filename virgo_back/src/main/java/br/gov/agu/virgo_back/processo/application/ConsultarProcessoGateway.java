package br.gov.agu.virgo_back.processo.application;

import br.gov.agu.virgo_back.pje.CredenciaisPje;
import br.gov.agu.virgo_back.processo.domain.OrigemPje;
import br.gov.agu.virgo_back.processo.domain.RespostaConsultaOrigem;

public interface ConsultarProcessoGateway {

    RespostaConsultaOrigem consultarProcesso(
            OrigemPje origem,
            CredenciaisPje Credenciais,
            String numProcesso
    );
}
