/**
 * The single dependency an application adds to use Flux.
 *
 * <p>Depending on this starter pulls in
 * {@code io.github.navidzaare:flux-connector-core} and
 * {@code io.github.navidzaare:flux-starter-autoconfigure}, together with the Spring
 * Boot starter they build on. There is no code in this module — it exists so an
 * application can depend on one coordinate instead of three.</p>
 *
 * <p>Connectors are declared under {@code flux.connectors} in {@code application.yml}
 * and resolved at runtime through the {@code ConnectorRegistry} bean the
 * auto-configuration provides. Nothing needs to be annotated or registered by hand.</p>
 *
 * <pre>
 * flux:
 *   connectors:
 *     customer-api:
 *       type: rest
 *       base-url: https://api.example.com/v1
 *       auth:
 *         type: basic
 *         settings:
 *           username: ${CUSTOMER_API_USER}
 *           password: ${CUSTOMER_API_PASSWORD}
 * </pre>
 *
 * <p>Optional connector types such as Kafka live in their own modules and are added
 * separately, so an application only carries the transports it actually uses.</p>
 */
package io.github.navidzaare.flux.starter;
