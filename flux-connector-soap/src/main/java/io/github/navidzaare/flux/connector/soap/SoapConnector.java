package io.github.navidzaare.flux.connector.soap;

import io.github.navidzaare.flux.connector.Connector;
import io.github.navidzaare.flux.connector.ConnectorProperties;
import io.github.navidzaare.flux.connector.ConnectorRequest;
import io.github.navidzaare.flux.connector.ConnectorResponse;
import io.github.navidzaare.flux.connector.exception.ConnectorException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Calls a SOAP service described by a WSDL.
 *
 * <p>The WSDL is read once at start-up, so a bad location or an unreachable port fails
 * the application rather than the first call. Each {@code execute} builds an envelope
 * from the request payload and posts it.</p>
 *
 * <pre>
 * flux:
 *   connectors:
 *     billing:
 *       type: soap
 *       timeout: 5s
 *       settings:
 *         wsdl: classpath:wsdl/billing.wsdl
 *         service: BillingService     # optional when the WSDL declares one
 *         port: BillingPort           # optional when the port declares a soap:address
 * </pre>
 *
 * <p>The payload supplies the children of the request element: keys become element
 * names, values become their text. For an operation whose request element is
 * {@code GetSubscriber}, a payload of {@code {"msisdn": "0912..."}} produces
 * {@code <GetSubscriber><msisdn>0912...</msisdn></GetSubscriber>}.</p>
 *
 * <p>A SOAP fault is returned as a failed {@link ConnectorResponse}, not thrown: a
 * mediation flow should be able to branch on a fault the same way it branches on an
 * HTTP error.</p>
 */
public class SoapConnector implements Connector {

    public static final String TYPE = "soap";

    private static final String WSDL_SETTING = "wsdl";
    private static final String SERVICE_SETTING = "service";
    private static final String PORT_SETTING = "port";
    private static final String ENDPOINT_SETTING = "endpoint";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final String name;
    private final String wsdl;
    private final String service;
    private final String port;
    private final String endpointOverride;
    private final Duration timeout;
    private final HttpClient http;

    private volatile SoapContract contract;

    SoapConnector(String name, ConnectorProperties.Definition definition) {
        Map<String, String> settings = definition.getSettings();
        this.name = name;
        this.wsdl = settings.get(WSDL_SETTING);
        this.service = settings.get(SERVICE_SETTING);
        this.port = settings.get(PORT_SETTING);
        this.endpointOverride = settings.get(ENDPOINT_SETTING);
        this.timeout = definition.getTimeout() == null ? DEFAULT_TIMEOUT : definition.getTimeout();
        this.http = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        if (this.wsdl == null || this.wsdl.isBlank()) {
            throw new IllegalArgumentException(
                    "Connector '%s' declares no '%s' setting".formatted(name, WSDL_SETTING));
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return TYPE;
    }

    /** Reads the WSDL. Called before the connector becomes visible in the registry. */
    @Override
    public void initialise() {
        this.contract = SoapContract.parse(wsdl, service, port);
    }

    @Override
    public ConnectorResponse execute(ConnectorRequest request) {
        SoapContract local = contract;
        if (local == null) {
            throw new ConnectorException(name, request.operation(), "Connector was not initialised");
        }

        SoapContract.Operation operation = local.operation(request.operation());
        if (operation == null) {
            return ConnectorResponse.failure(0,
                    "The WSDL for connector '%s' declares no operation '%s'"
                            .formatted(name, request.operation()),
                    Duration.ZERO);
        }

        String envelope = buildEnvelope(local.version(), operation, request.payload());
        HttpRequest httpRequest = buildRequest(local, operation, envelope);

        long started = System.nanoTime();
        try {
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            return interpret(response, elapsed);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ConnectorException(name, request.operation(), "Interrupted while calling the service", ex);
        } catch (Exception ex) {
            throw new ConnectorException(name, request.operation(),
                    "Failed to call '%s'".formatted(local.endpointAddress()), ex);
        }
    }

    @Override
    public void close() {
        // HttpClient owns no resource this code can release on Java 17. Connections are
        // pooled per client and reclaimed by the collector when the connector is dropped.
    }

    private HttpRequest buildRequest(SoapContract contract,
                                    SoapContract.Operation operation,
                                    String envelope) {

        // The WSDL's soap:address is often a leftover from another environment, so a
        // connector is allowed to point somewhere else without editing the WSDL.
        String address = endpointOverride == null || endpointOverride.isBlank()
                ? contract.endpointAddress()
                : endpointOverride;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(address))
                .timeout(timeout)
                .header("Content-Type", contract.version().contentType + "; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8));

        if (contract.version() == SoapContract.Version.SOAP_11 && operation.soapAction() != null) {
            builder.header("SOAPAction", '"' + operation.soapAction() + '"');
        }
        return builder.build();
    }

    private String buildEnvelope(SoapContract.Version version,
                                 SoapContract.Operation operation,
                                 Map<String, Object> payload) {

        var factory = XMLOutputFactory.newFactory();
        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartDocument("UTF-8", "1.0");

            writer.writeStartElement("soapenv", "Envelope", version.namespace);
            writer.writeNamespace("soapenv", version.namespace);
            writer.writeStartElement("soapenv", "Body", version.namespace);

            String elementNamespace = operation.requestElement() == null
                    ? version.namespace
                    : operation.requestElement().getNamespaceURI();
            String elementName = operation.requestElement() == null
                    ? operation.name()
                    : operation.requestElement().getLocalPart();

            writer.writeStartElement("req", elementName, elementNamespace);
            writer.writeNamespace("req", elementNamespace);
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                writer.writeStartElement("req", entry.getKey(), elementNamespace);
                writer.writeCharacters(String.valueOf(entry.getValue()));
                writer.writeEndElement();
            }
            writer.writeEndElement();

            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.close();
        } catch (Exception ex) {
            throw new ConnectorException(name, operation.name(), "Could not build the request envelope", ex);
        }
        return out.toString();
    }

    private ConnectorResponse interpret(HttpResponse<String> response, Duration elapsed) {
        String body = response.body();
        Element fault = findFault(body);

        if (fault != null) {
            return ConnectorResponse.failure(response.statusCode(), faultText(fault), elapsed);
        }
        if (response.statusCode() >= 400) {
            return ConnectorResponse.failure(response.statusCode(),
                    body == null ? "" : body, elapsed);
        }
        return ConnectorResponse.ok(body, elapsed);
    }

    /** The Fault element, or null when the body is not a fault or cannot be parsed. */
    private Element findFault(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        Document document = parse(body);
        if (document == null) {
            return null;
        }
        Element soapBody = firstChild(document.getDocumentElement(), "Body");
        if (soapBody == null) {
            return null;
        }
        return firstChild(soapBody, "Fault");
    }

    /** faultstring on SOAP 1.1, Reason/Text on SOAP 1.2; falls back to the whole fault. */
    private String faultText(Element fault) {
        Element faultString = firstChild(fault, "faultstring");
        if (faultString != null) {
            return faultString.getTextContent().trim();
        }
        Element reason = firstChild(fault, "Reason");
        if (reason != null) {
            Element text = firstChild(reason, "Text");
            if (text != null) {
                return text.getTextContent().trim();
            }
        }
        return fault.getTextContent().trim();
    }

    private Element firstChild(Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && localName.equals(node.getLocalName())) {
                return (Element) node;
            }
        }
        return null;
    }

    /** Parses with DTDs and external entities disabled; the body comes from a peer. */
    private Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return null;
        }
    }
}
