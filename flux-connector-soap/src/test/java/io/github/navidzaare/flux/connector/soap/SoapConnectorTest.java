package io.github.navidzaare.flux.connector.soap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.navidzaare.flux.connector.ConnectorProperties;
import io.github.navidzaare.flux.connector.ConnectorRequest;
import io.github.navidzaare.flux.connector.ConnectorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SoapConnectorTest {

    private static final String ACTIVE_SUBSCRIBER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <ns:GetSubscriberResponse xmlns:ns="http://example.com/subscriber">
                  <ns:status>ACTIVE</ns:status>
                </ns:GetSubscriberResponse>
              </soap:Body>
            </soap:Envelope>
            """;

    private static final String FAULT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <soap:Fault>
                  <faultcode>soap:Server</faultcode>
                  <faultstring>Subscriber not found</faultstring>
                </soap:Fault>
              </soap:Body>
            </soap:Envelope>
            """;

    private HttpServer server;
    private int port;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();
    private final AtomicReference<String> lastSoapAction = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = ACTIVE_SUBSCRIBER;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/subscriber", this::handle);
        server.start();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        lastSoapAction.set(exchange.getRequestHeaders().getFirst("SOAPAction"));

        byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/xml");
        exchange.sendResponseHeaders(responseStatus, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private SoapConnector connector(String... extraSettings) {
        Map<String, String> settings = new HashMap<>();
        settings.put("wsdl", "wsdl/subscriber.wsdl");
        settings.put("endpoint", "http://127.0.0.1:" + port + "/subscriber");
        for (int i = 0; i + 1 < extraSettings.length; i += 2) {
            settings.put(extraSettings[i], extraSettings[i + 1]);
        }

        ConnectorProperties.Definition definition = new ConnectorProperties.Definition();
        definition.setType(SoapConnector.TYPE);
        definition.setTimeout(Duration.ofSeconds(5));
        definition.setSettings(settings);

        SoapConnector connector = new SoapConnector("subscriber", definition);
        connector.initialise();
        return connector;
    }

    private ConnectorResponse callGetSubscriber(SoapConnector connector) {
        return connector.execute(
                ConnectorRequest.of("GetSubscriber", Map.of("msisdn", "09120000000")));
    }

    @Test
    void returnsTheResponseBodyWhenTheServiceAnswers() {
        ConnectorResponse response = callGetSubscriber(connector());

        assertThat(response.success()).isTrue();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.bodyAs(String.class)).contains("<ns:status>ACTIVE</ns:status>");
        assertThat(response.duration()).isPositive();
    }

    @Test
    void buildsTheRequestElementFromThePayload() {
        callGetSubscriber(connector());

        assertThat(lastBody.get())
                .contains("<req:GetSubscriber")
                .contains("http://example.com/subscriber")
                .contains("<req:msisdn>09120000000</req:msisdn>");
    }

    @Test
    void sendsTheSoapActionAndContentTypeFromTheBinding() {
        callGetSubscriber(connector());

        assertThat(lastSoapAction.get()).isEqualTo("\"http://example.com/subscriber/GetSubscriber\"");
        assertThat(lastContentType.get()).startsWith("text/xml");
    }

    @Test
    void turnsAFaultIntoAFailedResponseRatherThanAnException() {
        responseStatus = 500;
        responseBody = FAULT;

        ConnectorResponse response = callGetSubscriber(connector());

        assertThat(response.success()).isFalse();
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.bodyAs(String.class)).isEqualTo("Subscriber not found");
    }

    @Test
    void failsTheCallWhenTheOperationIsNotInTheWsdl() {
        ConnectorResponse response = connector()
                .execute(ConnectorRequest.of("NoSuchOperation", Map.of()));

        assertThat(response.success()).isFalse();
        assertThat(response.bodyAs(String.class)).contains("NoSuchOperation");
    }

    @Test
    void matchesOperationNamesRegardlessOfCase() {
        ConnectorResponse response = connector()
                .execute(ConnectorRequest.of("getsubscriber", Map.of("msisdn", "09120000000")));

        assertThat(response.success()).isTrue();
    }

    @Test
    void prefersTheConfiguredEndpointOverTheOneInTheWsdl() {
        // the WSDL points at localhost:18080, where nothing is listening
        ConnectorResponse response = callGetSubscriber(connector("endpoint",
                "http://127.0.0.1:" + port + "/subscriber"));

        assertThat(response.success()).isTrue();
    }

    @Test
    void refusesToStartWhenTheWsdlCannotBeRead() {
        ConnectorProperties.Definition definition = new ConnectorProperties.Definition();
        definition.setType(SoapConnector.TYPE);
        definition.setSettings(Map.of("wsdl", "wsdl/does-not-exist.wsdl"));

        SoapConnector connector = new SoapConnector("broken", definition);

        assertThatThrownBy(connector::initialise)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does-not-exist.wsdl");
    }

    @Test
    void refusesToBuildWithoutAWsdlSetting() {
        ConnectorProperties.Definition definition = new ConnectorProperties.Definition();
        definition.setType(SoapConnector.TYPE);
        definition.setSettings(Map.of());

        assertThatThrownBy(() -> new SoapConnector("broken", definition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wsdl");
    }

    @Test
    void declaresTheSoapTypeAndItsName() {
        SoapConnector connector = connector();

        assertThat(connector.type()).isEqualTo("soap");
        assertThat(connector.name()).isEqualTo("subscriber");
        assertThat(connector.supports("GetSubscriber")).isTrue();
    }
}
