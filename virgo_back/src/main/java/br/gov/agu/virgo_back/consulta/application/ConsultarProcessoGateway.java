package br.gov.agu.virgo_back.consulta.application;

import br.gov.agu.virgo_back.pje.CredenciaisPje;
import br.gov.agu.virgo_back.processo.domain.NumeroProcesso;
import br.gov.agu.virgo_back.consulta.domain.OrigemPje;
import br.gov.agu.virgo_back.consulta.domain.RespostaConsultaOrigem;

public interface ConsultarProcessoGateway {

    RespostaConsultaOrigem consultarProcesso(
            OrigemPje origem,
            CredenciaisPje Credenciais,
            NumeroProcesso numProcesso
    );
}
