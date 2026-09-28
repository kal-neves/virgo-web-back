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

        boolean sucesso = Boolean.parseBoolean(sucessos.item(0).getTextContent().trim());

        String mensagem = mensagens.getLength() > 0 ? mensagens.item(0).getTextContent().trim() : "";

        if (!sucesso) {
            return mapearFalha(origem, mensagem);
        }

        List<Movimentacao> movimentacoes = new ArrayList<>();

        NodeList elementos = documento.getElementsByTagNameNS("*", "movimento");

        for (int i = 0; i < elementos.getLength(); i++) {
            Element movimento = (Element) elementos.item(i);

            LocalDateTime dataHora = LocalDateTime.parse(
                    movimento.getAttribute("datahora"),
                    DATA_HORA_PJE
            );

            movimentacoes.add(new Movimentacao(
                    dataHora,
                    movimento.getTextContent().trim()
            ));
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

        return new RespostaConsultaOrigem(origem,
                StatusConsulta.RESPOSTA_INVALIDA,
                List.of(),
                mensagem
        );
    }
}
