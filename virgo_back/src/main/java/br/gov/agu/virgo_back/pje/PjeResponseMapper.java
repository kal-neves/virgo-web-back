package br.gov.agu.virgo_back.pje;

import br.gov.agu.virgo_back.processo.domain.Movimentacao;
import br.gov.agu.virgo_back.processo.domain.IdentificadorMovimento;
import br.gov.agu.virgo_back.processo.domain.TipoMovimentacao;
import br.gov.agu.virgo_back.consulta.domain.OrigemPje;
import br.gov.agu.virgo_back.consulta.domain.RespostaConsultaOrigem;
import br.gov.agu.virgo_back.consulta.domain.StatusConsulta;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PjeResponseMapper {

    private static final String SERVICO = "http://www.cnj.jus.br/servico-intercomunicacao-2.2.2/";
    private static final String TIPOS = "http://www.cnj.jus.br/tipos-servico-intercomunicacao-2.2.2";
    private static final String INTERCOMUNICACAO = "http://www.cnj.jus.br/intercomunicacao-2.2.2";

    private static final DateTimeFormatter DATA_HORA_PJE = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);

    public RespostaConsultaOrigem mapearResposta(Document documento, OrigemPje origem) {
        try {
            Element resposta = documento == null ? null : documento.getDocumentElement();
            if (resposta == null || !SERVICO.equals(resposta.getNamespaceURI())
                    || !"consultarProcessoResposta".equals(resposta.getLocalName())) {
                throw new IllegalArgumentException("Elemento de resposta inesperado");
            }
            List<Element> sucessos = filhos(resposta, TIPOS, "sucesso");
            if (sucessos.size() != 1) {
                throw new IllegalArgumentException("Resposta deve conter um campo sucesso");
            }
            String valorSucesso = sucessos.getFirst().getTextContent().trim();
            if (!valorSucesso.equals("true") && !valorSucesso.equals("false")) {
                throw new IllegalArgumentException("Campo 'sucesso' inválido");
            }
            if (valorSucesso.equals("false")) {
                List<Element> mensagens = filhos(resposta, TIPOS, "mensagem");
                return mapearFalha(origem, mensagens.isEmpty() ? "" : mensagens.getFirst().getTextContent().trim());
            }
            List<Element> processos = filhos(resposta, TIPOS, "processo");
            if (processos.size() != 1) {
                throw new IllegalArgumentException("Resposta deve conter um processo");
            }
            List<Movimentacao> movimentacoes = new ArrayList<>();
            for (Element movimento : filhos(processos.getFirst(), INTERCOMUNICACAO, "movimento")) {
                movimentacoes.add(mapearMovimento(movimento));
            }
            return new RespostaConsultaOrigem(origem, StatusConsulta.ENCONTRADO, movimentacoes, null);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return new RespostaConsultaOrigem(origem, StatusConsulta.RESPOSTA_INVALIDA, List.of(),
                    "Resposta do PJe contém estrutura ou campos obrigatórios inválidos");
        }
    }

    private Movimentacao mapearMovimento(Element movimento) {
        IdentificadorMovimento identificador = new IdentificadorMovimento(
                movimento.getAttribute("identificadorMovimento"));
        LocalDateTime dataHora = LocalDateTime.parse(movimento.getAttribute("dataHora"), DATA_HORA_PJE);
        List<Element> nacionais = filhos(movimento, INTERCOMUNICACAO, "movimentoNacional");
        List<Element> locais = filhos(movimento, INTERCOMUNICACAO, "movimentoLocal");
        if (nacionais.size() + locais.size() != 1) {
            throw new IllegalArgumentException("Movimentação deve conter um tipo nacional ou local");
        }
        if (!nacionais.isEmpty()) {
            Element nacional = nacionais.getFirst();
            List<Element> complementos = filhos(movimento, INTERCOMUNICACAO, "complemento");
            complementos.addAll(filhos(nacional, INTERCOMUNICACAO, "complemento"));
            String descricao = complementos.stream()
                    .map(complemento -> complemento.getTextContent().trim())
                    .collect(Collectors.joining("\n"));
            return new Movimentacao(identificador, dataHora, TipoMovimentacao.NACIONAL,
                    Integer.parseInt(nacional.getAttribute("codigoNacional").trim()), descricao);
        }
        Element local = locais.getFirst();
        if (!local.hasAttribute("descricao")) {
            throw new IllegalArgumentException("Movimento local sem descrição");
        }
        return new Movimentacao(identificador, dataHora, TipoMovimentacao.LOCAL,
                Integer.parseInt(local.getAttribute("codigoMovimento").trim()), local.getAttribute("descricao").trim());
    }

    private List<Element> filhos(Element pai, String namespace, String nome) {
        List<Element> encontrados = new ArrayList<>();
        for (Node no = pai.getFirstChild(); no != null; no = no.getNextSibling()) {
            if (no instanceof Element elemento && nome.equals(elemento.getLocalName())) {
                if (!namespace.equals(elemento.getNamespaceURI())) {
                    throw new IllegalArgumentException("Namespace inesperado para " + nome);
                }
                encontrados.add(elemento);
            }
        }
        return encontrados;
    }

    private RespostaConsultaOrigem mapearFalha(OrigemPje origem, String mensagem) {
        if (mensagem.contains("Número do processo inválido")) {
            return new RespostaConsultaOrigem(origem,
                    StatusConsulta.PROCESSO_INVALIDO,
                    List.of(),
                    mensagem);
        }

        if (mensagem.contains("Erro ao realizar login ")) {
            return new RespostaConsultaOrigem(origem,
                    StatusConsulta.ACESSO_NEGADO,
                    List.of(),
                    "Falha de autenticação no PJE");
        }

        if (mensagem.matches(".*Processo de número .+ não encontrado!.*")) {
            return new RespostaConsultaOrigem(origem,
                    StatusConsulta.NAO_ENCONTRADO,
                    List.of(),
                    mensagem);
        }

        return new RespostaConsultaOrigem(origem,
                StatusConsulta.RESPOSTA_INVALIDA,
                List.of(),
                mensagem
        );
    }
}
