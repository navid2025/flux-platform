package io.github.navidzaare.flux.connector.soap;

import org.apache.cxf.wsdl.WSDLManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the SOAP connector type.
 *
 * <p>Adding {@code flux-connector-soap} to the classpath is the entire setup step. The
 * factory is picked up by {@code FluxAutoConfiguration} and becomes available as
 * {@code type: soap}.</p>
 */
@AutoConfiguration(afterName = "io.github.navidzaare.flux.autoconfigure.FluxAutoConfiguration")
@ConditionalOnClass(WSDLManager.class)
public class FluxSoapAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SoapConnectorFactory.class)
    public SoapConnectorFactory soapConnectorFactory() {
        return new SoapConnectorFactory();
    }
}
