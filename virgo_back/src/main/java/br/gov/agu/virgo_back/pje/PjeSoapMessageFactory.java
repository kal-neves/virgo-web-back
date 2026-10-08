package br.gov.agu.virgo_back.pje;

import org.jspecify.annotations.NullMarked;
import org.springframework.ws.soap.SoapMessageCreationException;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import jakarta.xml.soap.SOAPException;
import java.io.IOException;
import java.io.InputStream;

@NullMarked
final class PjeSoapMessageFactory extends SaajSoapMessageFactory {
    @Override
    public SaajSoapMessage createWebServiceMessage(InputStream input) throws IOException {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var parser = factory.newDocumentBuilder();
            // Do not print remote XML or parser diagnostics to stderr.
            parser.setErrorHandler(new DefaultHandler());
            var document = parser.parse(input);
            var root = document.getDocumentElement();
            if (!"Envelope".equals(root.getLocalName())
                    || !"http://schemas.xmlsoap.org/soap/envelope/".equals(root.getNamespaceURI())) {
                throw new SoapMessageCreationException("Envelope SOAP 1.1 esperado");
            }
            var message = createWebServiceMessage();
            message.getSaajMessage().getSOAPPart().setContent(new DOMSource(document));
            message.getSaajMessage().getSOAPPart().getEnvelope();
            return message;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Parser SOAP seguro indisponível", e);
        } catch (SAXException | SOAPException e) {
            throw new SoapMessageCreationException("Resposta SOAP inválida");
        }
    }
}
