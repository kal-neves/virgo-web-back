package br.gov.agu.virgo_back.consulta.application;

import br.gov.agu.virgo_back.pje.CredenciaisPje;
import br.gov.agu.virgo_back.processo.domain.NumeroProcesso;
import br.gov.agu.virgo_back.processo.domain.Tribunal;
import br.gov.agu.virgo_back.processo.domain.GrauJurisdicao;
import br.gov.agu.virgo_back.consulta.domain.OrigemPje;
import br.gov.agu.virgo_back.consulta.domain.RespostaConsultaOrigem;
import br.gov.agu.virgo_back.consulta.domain.ResultadoConsultaProcesso;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConsultarProcessoService {

    private final ConsultarProcessoGateway gateway;

    public ConsultarProcessoService(ConsultarProcessoGateway gateway) {
        this.gateway = gateway;
    }

    public ResultadoConsultaProcesso consultarProcesso(
            CredenciaisPje credenciais, NumeroProcesso numProcesso, Tribunal tribunal) {

        var primeiroGrau = new OrigemPje(tribunal, GrauJurisdicao.PRIMEIRO_GRAU);
        var segundoGrau = new OrigemPje(tribunal, GrauJurisdicao.SEGUNDO_GRAU);
        RespostaConsultaOrigem respostaPje1 = gateway.consultarProcesso(primeiroGrau, credenciais, numProcesso);
        RespostaConsultaOrigem respostaPje2 = gateway.consultarProcesso(segundoGrau, credenciais, numProcesso);

        return new ResultadoConsultaProcesso(numProcesso, List.of(respostaPje1,respostaPje2)
        );
    }
}
