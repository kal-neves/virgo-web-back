package br.gov.agu.virgo_back.pje;

import br.gov.agu.virgo_back.processo.domain.NumeroProcesso;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;

@Component
public class PjeRequestWriter {

    private final XMLOutputFactory xmlOutputFactory;

    private static final String SERVICO_NAMESPACE =
            "http://www.cnj.jus.br/servico-intercomunicacao-2.2.2/";
    private static final String TIPOS_NAMESPACE =
            "http://www.cnj.jus.br/tipos-servico-intercomunicacao-2.2.2";

    public PjeRequestWriter() {
        this.xmlOutputFactory = XMLOutputFactory.newFactory();
    }

    public String gerarRequest(CredenciaisPje user, NumeroProcesso numProcesso) {
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
            xml.writeCharacters(numProcesso.valor());
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
