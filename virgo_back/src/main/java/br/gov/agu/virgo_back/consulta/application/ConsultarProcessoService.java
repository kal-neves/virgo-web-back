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
import br.gov.agu.virgo_back.consulta.domain.Rodada;
import br.gov.agu.virgo_back.consulta.domain.ItemRodada;
import br.gov.agu.virgo_back.consulta.domain.ConsultaGrau;
import br.gov.agu.virgo_back.consulta.persistence.ConsultaGrauRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.time.Instant;

@Service
public class ConsultarProcessoService {

    private final ConsultarProcessoGateway gateway;
    private final RegistrarDeteccoesService registro;
    private final ProcessoMonitoradoRepository processos;
    private final ConsultaGrauRepository consultas;

    public ConsultarProcessoService(ConsultarProcessoGateway gateway, RegistrarDeteccoesService registro,
                                   ProcessoMonitoradoRepository processos, ConsultaGrauRepository consultas) {
        this.gateway = gateway;
        this.registro = registro;
        this.processos = processos;
        this.consultas = consultas;
    }

    /**
     * consulta um grau já iniciado usando o endpoint salvo no planejamento
     * mantém a chamada ao PJe fora de uma transação de banco
     * confirma o resultado e as detecções em uma transação própria antes de retornar
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RespostaConsultaOrigem consultarGrauPlanejado(Rodada rodada, ItemRodada item, ConsultaGrau consulta,
                                                        Instant inicio, CredenciaisPje credenciais) {
        Objects.requireNonNull(rodada);
        Objects.requireNonNull(item);
        Objects.requireNonNull(consulta);
        Objects.requireNonNull(inicio);
        Objects.requireNonNull(credenciais);
        if (!rodada.itens().contains(item) || !item.consultas().contains(consulta)) {
            throw new IllegalArgumentException("Consulta ou item nao pertence a rodada");
        }
        var endpoint = consultas.buscarEndpoint(rodada.proprietario(), consulta.id())
                .orElseThrow(() -> new IllegalStateException("Endpoint da consulta nao encontrado"));
        var resposta = Objects.requireNonNull(gateway.consultarProcesso(consulta.origem(), endpoint,
                credenciais, item.numeroProcesso()), "Resposta obrigatoria");
        if (!consulta.origem().equals(resposta.origem())) {
            throw new IllegalStateException("Origem da resposta difere da origem consultada");
        }
        registro.registrarPlanejada(rodada.proprietario(), rodada.id(), consulta.id(), UUID.randomUUID(), inicio, resposta);
        return resposta;
    }

    /**
     * consulta os graus sem receber um sinal de cancelamento
     * suspende a transação do chamador durante as chamadas ao PJe
     * cada resultado é salvo em uma transação própria antes da próxima chamada
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResultadoConsultaProcesso consultarProcesso(
            CredenciaisPje credenciais, IdConsultante proprietario, UUID processoId, Tribunal tribunal) {
        return consultarProcesso(credenciais, proprietario, processoId, tribunal, () -> false);
    }

    /**
     * consulta os graus em sequência sem manter uma transação aberta durante o acesso ao PJe
     * verifica o cancelamento antes de cada envio e salva o resultado de uma chamada já iniciada
     * os resultados já confirmados são preservados se uma chamada posterior falhar
     * o chamador deve autorizar o executor a acessar o processo do proprietário
     */
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
