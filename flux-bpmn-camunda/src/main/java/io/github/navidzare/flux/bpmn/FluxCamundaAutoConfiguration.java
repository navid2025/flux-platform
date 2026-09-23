package io.github.navidzare.flux.bpmn;

import io.github.navidzare.flux.connector.ConnectorRegistry;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Exposes the connector delegate as a bean that BPMN models can reference.
 *
 * <p>Once this module is on the classpath, any service task can use:</p>
 *
 * <pre>camunda:delegateExpression="${connectorDelegate}"</pre>
 *
 * <p>The bean name matters — it is what the model references — so it is named explicitly
 * rather than derived from the class.</p>
 */
@AutoConfiguration
@ConditionalOnClass(JavaDelegate.class)
public class FluxCamundaAutoConfiguration {

    public static final String DELEGATE_BEAN_NAME = "connectorDelegate";

    @Bean(DELEGATE_BEAN_NAME)
    @ConditionalOnMissingBean(name = DELEGATE_BEAN_NAME)
    public ConnectorDelegate connectorDelegate(ConnectorRegistry registry) {
        return new ConnectorDelegate(registry);
    }
}
