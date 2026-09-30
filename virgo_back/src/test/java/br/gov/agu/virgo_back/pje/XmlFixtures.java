package br.gov.agu.virgo_back.pje;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class XmlFixtures {
    static String movimentos() throws Exception {
        try (var input = Objects.requireNonNull(XmlFixtures.class.getResourceAsStream("/pje/consulta-movimentos.xml"))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newNSInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }
}
