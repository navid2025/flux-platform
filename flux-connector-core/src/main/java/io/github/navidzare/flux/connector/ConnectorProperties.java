package io.github.navidzare.flux.connector;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for every connector declared under the {@code flux.connectors} prefix.
 *
 * <pre>
 * flux:
 *   connectors:
 *     billing-api:
 *       type: rest
 *       base-url: https://billing.example.com/api
 *       timeout: 5s
 *       auth:
 *         type: basic
 *         settings:
 *           username: ${BILLING_USER}
 *           password: ${BILLING_PASSWORD}
 * </pre>
 */
@ConfigurationProperties(prefix = "flux")
public class ConnectorProperties {

    /** Fail application start-up if a connector cannot be initialised. */
    private boolean failFast = true;

    /** Connectors keyed by their logical name. */
    private Map<String, Definition> connectors = new LinkedHashMap<>();

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public Map<String, Definition> getConnectors() {
        return connectors;
    }

    public void setConnectors(Map<String, Definition> connectors) {
        this.connectors = connectors;
    }

    /** A single connector declaration. */
    public static class Definition {

        /** Transport family: rest, soap, jdbc, kafka, file. */
        private String type;

        /** Root address of the downstream system. */
        private String baseUrl;

        /** Per-call timeout. */
        private Duration timeout = Duration.ofSeconds(10);

        /** Optional authentication block. */
        private Auth auth = new Auth();

        /** Free-form settings handed to the connector implementation. */
        private Map<String, String> settings = new LinkedHashMap<>();

        /** Whether this connector is registered at start-up. */
        private boolean enabled = true;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public Auth getAuth() {
            return auth;
        }

        public void setAuth(Auth auth) {
            this.auth = auth;
        }

        public Map<String, String> getSettings() {
            return settings;
        }

        public void setSettings(Map<String, String> settings) {
            this.settings = settings;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** Credentials block attached to a connector. */
    public static class Auth {

        /** Strategy name: basic, oauth2, none. */
        private String type = "none";

        /** Strategy specific values. */
        private Map<String, String> settings = new LinkedHashMap<>();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Map<String, String> getSettings() {
            return settings;
        }

        public void setSettings(Map<String, String> settings) {
            this.settings = settings;
        }
    }
}
