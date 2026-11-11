package br.gov.agu.virgo_back.consulta.application;

import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;

public interface ValidarCredenciaisGateway {
    enum Resultado { VALIDAS, INVALIDAS, INDISPONIVEL, RESPOSTA_INVALIDA }

    Resultado validar(CredenciaisPje credenciais);
}
