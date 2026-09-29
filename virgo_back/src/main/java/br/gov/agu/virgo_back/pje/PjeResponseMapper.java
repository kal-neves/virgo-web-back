package br.gov.agu.virgo_back.pje;

import br.gov.agu.virgo_back.processo.domain.Movimentacao;
import br.gov.agu.virgo_back.processo.domain.OrigemPje;
import br.gov.agu.virgo_back.processo.domain.RespostaConsultaOrigem;
import br.gov.agu.virgo_back.processo.domain.StatusConsulta;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;

@Component
public class PjeResponseMapper {

    private static final DateTimeFormatter DATA_HORA_PJE = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);

    public RespostaConsultaOrigem mapearResposta(Document documento, OrigemPje origem) {

        NodeList sucessos = documento.getElementsByTagNameNS("*", "sucesso");

        NodeList mensagens = documento.getElementsByTagNameNS("*", "mensagem");

        if (sucessos.getLength() == 0) {
            return new RespostaConsultaOrigem(
                    origem,
                    StatusConsulta.RESPOSTA_INVALIDA,
                    List.of(),
                    "Resposta não tem campo sucesso");
        }

        String valorSucesso = sucessos.item(0).getTextContent().trim();

        if (!valorSucesso.equals("true") && !valorSucesso.equals("false")) {
            return new RespostaConsultaOrigem(
                    origem,
                    StatusConsulta.RESPOSTA_INVALIDA,
                    List.of(),
                    "Campo 'sucesso' inválido"
            );
        }

        boolean sucesso = valorSucesso.equals("true");

        String mensagem = mensagens.getLength() > 0 ? mensagens.item(0).getTextContent().trim() : "";

        if (!sucesso) {
            return mapearFalha(origem, mensagem);
        }

        List<Movimentacao> movimentacoes = new ArrayList<>();

        NodeList elementos = documento.getElementsByTagNameNS("*", "movimento");

        for (int i = 0; i < elementos.getLength(); i++) {
            Element movimento = (Element) elementos.item(i);

            try {
                LocalDateTime dataHora = LocalDateTime.parse(
                        movimento.getAttribute("dataHora"),
                        DATA_HORA_PJE
                );

                movimentacoes.add(new Movimentacao(
                        dataHora,
                        movimento.getTextContent().trim()
                ));
            } catch (DateTimeParseException e) {
                return new RespostaConsultaOrigem(
                        origem,
                        StatusConsulta.RESPOSTA_INVALIDA,
                        List.of(),
                        "Movimentação contém data/hora ausente ou inválida"
                );
            }
        }

        return new RespostaConsultaOrigem(origem,
                StatusConsulta.ENCONTRADO,
                movimentacoes,
                null
        );
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
