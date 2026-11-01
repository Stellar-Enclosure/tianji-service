package top.egon.cola.platform.tianji.process.server.release;

import org.w3c.dom.Document;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Supplier;

/** Parses bounded UTF-8 without entity resolution and validates against packaged Flowable schemas. */
final class SafeModelXml {
    static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_ELEMENTS = 2_000;
    private static final int MAX_DEPTH = 24;
    private static final int MAX_ATTRIBUTES = 32;
    private static final ErrorHandler ERRORS = new ErrorHandler() {
        @Override public void warning(SAXParseException error) throws SAXException { throw error; }
        @Override public void error(SAXParseException error) throws SAXException { throw error; }
        @Override public void fatalError(SAXParseException error) throws SAXException { throw error; }
    };

    private SafeModelXml() { }

    static Document parseCmmn(byte[] bytes) {
        return parse(bytes, () -> CmmnSchema.CMMN);
    }

    static Document parse(byte[] bytes, boolean bpmn) {
        return parse(bytes, () -> bpmn ? Schemas.BPMN : Schemas.DMN);
    }

    private static Document parse(byte[] bytes, Supplier<Schema> schemaSupplier) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw invalid();
        try {
            String xml = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            scan(xml);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(ERRORS);
            builder.setEntityResolver((publicId, systemId) -> { throw new SAXException("external entities are disabled"); });
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            String declaredEncoding = document.getXmlEncoding();
            if (declaredEncoding != null && !declaredEncoding.equalsIgnoreCase("UTF-8")) throw invalid();
            var validator = schemaSupplier.get().newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.setErrorHandler(ERRORS);
            validator.validate(new DOMSource(document));
            return document;
        } catch (Exception failure) {
            throw invalid();
        }
    }

    private static void scan(String xml) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        factory.setXMLResolver((publicId, systemId, base, namespace) -> { throw new javax.xml.stream.XMLStreamException("external entities are disabled"); });
        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
        try {
            if (reader.getVersion() != null && !"1.0".equals(reader.getVersion())) throw invalid();
            int depth = 0;
            int elements = 0;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE
                        || event == XMLStreamConstants.PROCESSING_INSTRUCTION) throw invalid();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if (++depth > MAX_DEPTH || ++elements > MAX_ELEMENTS || reader.getAttributeCount() > MAX_ATTRIBUTES) throw invalid();
                } else if (event == XMLStreamConstants.END_ELEMENT) depth--;
            }
        } finally {
            reader.close();
        }
    }

    private static Schema schema(String root, String directory, Set<String> allowed) {
        try (InputStream input = resource(directory + root)) {
            SchemaFactory factory = SchemaFactory.newDefaultInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DOMImplementationLS dom = (DOMImplementationLS) DOMImplementationRegistry.newInstance().getDOMImplementation("LS");
            factory.setResourceResolver((type, namespace, publicId, systemId, baseUri) -> {
                if (!allowed.contains(systemId)) throw new IllegalStateException("unexpected schema dependency");
                var resolved = dom.createLSInput();
                resolved.setPublicId(publicId);
                resolved.setSystemId("urn:tianji:schema:" + systemId);
                resolved.setByteStream(resource(directory + systemId));
                return resolved;
            });
            StreamSource source = new StreamSource(input);
            source.setSystemId("urn:tianji:schema:" + root);
            return factory.newSchema(source);
        } catch (Exception failure) {
            throw new IllegalStateException("packaged process model schema is unavailable", failure);
        }
    }

    private static InputStream resource(String path) {
        InputStream input = SafeModelXml.class.getClassLoader().getResourceAsStream(path);
        if (input == null) throw new IllegalStateException("packaged process model schema is unavailable");
        return input;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Process model is invalid or not supported");
    }

    private static final class Schemas {
        private static final Schema BPMN = schema("BPMN20.xsd", "org/flowable/impl/bpmn/parser/",
                Set.of("BPMN20.xsd", "Semantic.xsd", "BPMNDI.xsd", "DC.xsd", "DI.xsd"));
        private static final Schema DMN = schema("dmn.xsd", "org/flowable/impl/dmn/parser/", Set.of("dmn.xsd"));
    }

    private static final class CmmnSchema {
        private static final Schema CMMN = schema("CMMN11.xsd", "org/flowable/impl/cmmn/parser/",
                Set.of("CMMN11.xsd", "CMMN11CaseModel.xsd", "CMMNDI11.xsd", "DC.xsd", "DI.xsd"));
    }
}
