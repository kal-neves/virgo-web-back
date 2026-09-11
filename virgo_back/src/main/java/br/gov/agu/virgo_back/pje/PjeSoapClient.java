package br.gov.agu.virgo_back.pje;

import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.xml.transform.StringSource;
import org.w3c.dom.Document;

import javax.xml.transform.dom.DOMResult;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;

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

}
