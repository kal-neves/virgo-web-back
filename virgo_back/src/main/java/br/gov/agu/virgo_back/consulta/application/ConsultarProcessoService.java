package br.gov.agu.virgo_back.consulta.application;

import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.movimentacao.application.RegistrarDeteccoesService;
import br.gov.agu.virgo_back.processo.persistence.ProcessoMonitoradoRepository;
import br.gov.agu.virgo_back.processo.domain.Tribunal;
import br.gov.agu.virgo_back.processo.domain.GrauJurisdicao;
import br.gov.agu.virgo_back.consulta.domain.OrigemPje;
import br.gov.agu.virgo_back.consulta.domain.RespostaConsultaOrigem;
import br.gov.agu.virgo_back.consulta.domain.ResultadoConsultaProcesso;
import br.gov.agu.virgo_back.consulta.domain.StatusConsulta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

@Service
public class ConsultarProcessoService {

    private final ConsultarProcessoGateway gateway;
    private final RegistrarDeteccoesService registro;
    private final ProcessoMonitoradoRepository processos;

    public ConsultarProcessoService(ConsultarProcessoGateway gateway, RegistrarDeteccoesService registro,
                                   ProcessoMonitoradoRepository processos) {
        this.gateway = gateway;
        this.registro = registro;
        this.processos = processos;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResultadoConsultaProcesso consultarProcesso(
            CredenciaisPje credenciais, IdConsultante proprietario, UUID processoId, Tribunal tribunal) {
        return consultarProcesso(credenciais, proprietario, processoId, tribunal, () -> false);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResultadoConsultaProcesso consultarProcesso(
            CredenciaisPje credenciais, IdConsultante proprietario, UUID processoId, Tribunal tribunal,
            BooleanSupplier cancelamentoSolicitado) {
        Objects.requireNonNull(credenciais, "Credenciais sao obrigatorias");
        Objects.requireNonNull(proprietario, "Proprietario e obrigatorio");
        Objects.requireNonNull(processoId, "Processo e obrigatorio");
        Objects.requireNonNull(tribunal, "Tribunal e obrigatorio");
        Objects.requireNonNull(cancelamentoSolicitado, "Sinal de cancelamento e obrigatorio");
        var processo = processos.buscarPorId(proprietario, processoId)
                .orElseThrow(() -> new IllegalArgumentException("Processo nao encontrado para o proprietario"));
        var respostas = new ArrayList<RespostaConsultaOrigem>();
        for (var grau : List.of(GrauJurisdicao.PRIMEIRO_GRAU, GrauJurisdicao.SEGUNDO_GRAU)) {
            if (cancelamentoSolicitado.getAsBoolean()) break;
            var origem = new OrigemPje(tribunal, grau);
            var resposta = Objects.requireNonNull(
                    gateway.consultarProcesso(origem, credenciais, processo.numeroProcesso()), "Resposta obrigatoria");
            if (!origem.equals(resposta.origem())) {
                throw new IllegalStateException("Origem da resposta difere da origem consultada");
            }
            
            registro.registrar(processo.id(), UUID.randomUUID(), resposta);
            respostas.add(resposta);
            if (resposta.status() == StatusConsulta.ACESSO_NEGADO) break;
        }
        return new ResultadoConsultaProcesso(processo.numeroProcesso(), respostas);
    }
}
