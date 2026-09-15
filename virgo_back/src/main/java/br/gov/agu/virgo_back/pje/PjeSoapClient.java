package br.gov.agu.virgo_back.pje;

import br.gov.agu.virgo_back.processo.domain.OrigemPje;
import br.gov.agu.virgo_back.processo.domain.StatusConsulta;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.xml.transform.StringSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.transform.dom.DOMResult;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import br.gov.agu.virgo_back.processo.domain.RespostaConsultaOrigem;
import br.gov.agu.virgo_back.processo.domain.Movimentacao;


@Component
public class PjeSoapClient {

    private static final String SERVICO_NAMESPACE =
            "http://www.cnj.jus.br/servico-intercomunicacao-2.2.2/";
    private static final String TIPOS_NAMESPACE =
            "http://www.cnj.jus.br/tipos-servico-intercomunicacao-2.2.2";

    private final WebServiceTemplate webServiceTemplate;
    private final XMLOutputFactory xmlOutputFactory;

    public PjeSoapClient(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
        this.xmlOutputFactory = XMLOutputFactory.newFactory();
    }

    public Document consultarProcesso(String uri, CredenciaisPje credenciais, String numProcesso) {

        StringSource request = new StringSource(
                gerarRequest(credenciais, numProcesso)
        );

        DOMResult response = new DOMResult();

        webServiceTemplate.sendSourceAndReceiveToResult(
                uri,
                request,
                response
        );

        return (Document) response.getNode();
    }

    private String gerarRequest(CredenciaisPje user, String numProcesso) {
        try {
            StringWriter payload = new StringWriter();
            XMLStreamWriter xml = xmlOutputFactory.createXMLStreamWriter(payload);

            xml.writeStartElement("ser", "consultarProcesso", SERVICO_NAMESPACE);
            xml.writeNamespace("ser", SERVICO_NAMESPACE);
            xml.writeNamespace("tip", TIPOS_NAMESPACE);

            xml.writeStartElement("tip", "idConsultante", TIPOS_NAMESPACE);
            xml.writeCharacters(user.getLogin());
            xml.writeEndElement();

            xml.writeStartElement("tip", "senhaConsultante", TIPOS_NAMESPACE);
            xml.writeCharacters(user.getSenha());
            xml.writeEndElement();

            xml.writeStartElement("tip", "numeroProcesso", TIPOS_NAMESPACE);
            xml.writeCharacters(numProcesso);
            xml.writeEndElement();

            xml.writeStartElement("tip", "movimentos", TIPOS_NAMESPACE);
            xml.writeCharacters("true");
            xml.writeEndElement();

            xml.writeEndElement();
            xml.close();

            return payload.toString();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Falha ao criar requisição PJe", e);
        }
    }

    private RespostaConsultaOrigem mapearResposta(Document documento, OrigemPje origem) {

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

            movimentacoes.add(new Movimentacao(
                    movimento.getAttribute("dataHora"),
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
