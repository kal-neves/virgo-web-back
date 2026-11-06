package br.gov.agu.virgo_back.consulta.application;

import br.gov.agu.virgo_back.consulta.domain.OrigemPje;
import br.gov.agu.virgo_back.consulta.domain.StatusConsulta;
import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;

public interface VerificarConectividadeGateway {
    StatusConsulta verificar(OrigemPje origem, CredenciaisPje credenciais);
}
