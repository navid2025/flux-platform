package io.github.navidzaare.flux.connector.soap;

import org.apache.cxf.wsdl.WSDLManager;
import org.apache.cxf.wsdl11.WSDLManagerImpl;

import javax.wsdl.Binding;
import javax.wsdl.BindingOperation;
import javax.wsdl.Definition;
import javax.wsdl.Message;
import javax.wsdl.Operation;
import javax.wsdl.Part;
import javax.wsdl.Port;
import javax.wsdl.Service;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.extensions.soap.SOAPOperation;
import javax.wsdl.extensions.soap12.SOAP12Address;
import javax.wsdl.extensions.soap12.SOAP12Operation;
import javax.xml.namespace.QName;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the connector needs to know about a WSDL: where to send requests, which SOAP
 * version to speak, and for each operation the element that wraps the request body.
 *
 * <p>Parsed once when the connector initialises. A WSDL that cannot be read, or that
 * declares no reachable SOAP port, fails at start-up rather than on the first call.</p>
 */
final class SoapContract {

    enum Version {
        SOAP_11("http://schemas.xmlsoap.org/soap/envelope/", "text/xml"),
        SOAP_12("http://www.w3.org/2003/05/soap-envelope", "application/soap+xml");

        final String namespace;
        final String contentType;

        Version(String namespace, String contentType) {
            this.namespace = namespace;
            this.contentType = contentType;
        }
    }

    /**
     * @param name           the WSDL operation name
     * @param requestElement the element wrapping the request body, or null when the
     *                       operation declares no input element
     * @param soapAction     the SOAPAction header value, null when the binding sets none
     */
    record Operation(String name, QName requestElement, String soapAction) {
    }

    private record Address(Version version, String location) {
    }

    private final Version version;
    private final String endpointAddress;
    private final Map<String, Operation> operations;

    private SoapContract(Version version, String endpointAddress, Map<String, Operation> operations) {
        this.version = version;
        this.endpointAddress = endpointAddress;
        this.operations = operations;
    }

    Version version() {
        return version;
    }

    String endpointAddress() {
        return endpointAddress;
    }

    /** Case-insensitive lookup, so a flow need not match the WSDL's casing exactly. */
    Operation operation(String name) {
        return operations.get(name.toLowerCase());
    }

    static SoapContract parse(String wsdlLocation, String serviceName, String portName) {
        Definition definition = read(wsdlLocation);

        Port port = selectPort(definition, serviceName, portName);
        Address address = readAddress(port);
        Binding binding = port.getBinding();
        if (binding == null || binding.getPortType() == null) {
            throw new IllegalArgumentException(
                    "Port '%s' declares no portType, so its operations cannot be read"
                            .formatted(port.getName()));
        }

        Map<String, String> actions = readActions(binding);
        Map<String, Operation> operations = new LinkedHashMap<>();
        List<?> declared = binding.getPortType().getOperations();
        for (Object candidate : declared) {
            javax.wsdl.Operation operation = (javax.wsdl.Operation) candidate;
            operations.put(operation.getName().toLowerCase(),
                    new Operation(operation.getName(),
                            readRequestElement(operation),
                            actions.get(operation.getName().toLowerCase())));
        }

        if (operations.isEmpty()) {
            throw new IllegalArgumentException(
                    "The WSDL declares no operations on port '%s'".formatted(port.getName()));
        }
        return new SoapContract(address.version(), address.location(), operations);
    }

    private static Definition read(String wsdlLocation) {
        String resolved = resolve(wsdlLocation);
        try {
            WSDLManager manager = new WSDLManagerImpl();
            return manager.getDefinition(resolved);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Could not read the WSDL at '%s'".formatted(wsdlLocation), ex);
        }
    }

    /** Accepts an absolute URL or a classpath resource name. */
    private static String resolve(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("No WSDL location configured");
        }
        if (location.contains("://") || location.startsWith("file:")) {
            return location;
        }
        URL resource = SoapContract.class.getClassLoader().getResource(location);
        if (resource == null) {
            throw new IllegalArgumentException(
                    "WSDL '%s' is neither an absolute URL nor a classpath resource".formatted(location));
        }
        return resource.toExternalForm();
    }

    private static Port selectPort(Definition definition, String serviceName, String portName) {
        Map<?, ?> services = definition.getServices();
        if (services.isEmpty()) {
            throw new IllegalArgumentException("The WSDL declares no service");
        }

        Service service = serviceName == null || serviceName.isBlank()
                ? onlyService(services)
                : (Service) services.get(new QName(definition.getTargetNamespace(), serviceName));
        if (service == null) {
            throw new IllegalArgumentException(
                    "The WSDL declares no service named '%s'".formatted(serviceName));
        }

        Map<?, ?> ports = service.getPorts();
        if (portName != null && !portName.isBlank()) {
            Port port = (Port) ports.get(portName);
            if (port == null) {
                throw new IllegalArgumentException(
                        "Service '%s' declares no port named '%s'".formatted(service.getQName(), portName));
            }
            return port;
        }

        for (Object candidate : ports.values()) {
            Port port = (Port) candidate;
            if (hasSoapAddress(port)) {
                return port;
            }
        }
        throw new IllegalArgumentException(
                "No port of service '%s' declares a SOAP address".formatted(service.getQName()));
    }

    private static Service onlyService(Map<?, ?> services) {
        if (services.size() > 1) {
            throw new IllegalArgumentException(
                    "The WSDL declares %d services; set 'service' to choose one".formatted(services.size()));
        }
        return (Service) services.values().iterator().next();
    }

    private static boolean hasSoapAddress(Port port) {
        for (Object element : port.getExtensibilityElements()) {
            if (element instanceof SOAPAddress || element instanceof SOAP12Address) {
                return true;
            }
        }
        return false;
    }

    private static Address readAddress(Port port) {
        for (Object element : port.getExtensibilityElements()) {
            if (element instanceof SOAPAddress address) {
                return new Address(Version.SOAP_11, address.getLocationURI());
            }
            if (element instanceof SOAP12Address address) {
                return new Address(Version.SOAP_12, address.getLocationURI());
            }
        }
        throw new IllegalArgumentException(
                "Port '%s' declares no soap:address".formatted(port.getName()));
    }

    /** SOAPAction per operation, keyed lower-case. */
    private static Map<String, String> readActions(Binding binding) {
        Map<String, String> actions = new LinkedHashMap<>();
        List<?> bindingOperations = binding.getBindingOperations();
        for (Object candidate : bindingOperations) {
            BindingOperation operation = (BindingOperation) candidate;
            for (Object element : operation.getExtensibilityElements()) {
                if (element instanceof SOAPOperation soap) {
                    actions.put(operation.getName().toLowerCase(), soap.getSoapActionURI());
                } else if (element instanceof SOAP12Operation soap12) {
                    actions.put(operation.getName().toLowerCase(), soap12.getSoapActionURI());
                }
            }
        }
        return actions;
    }

    private static QName readRequestElement(javax.wsdl.Operation operation) {
        if (operation.getInput() == null || operation.getInput().getMessage() == null) {
            return null;
        }
        for (Object candidate : operation.getInput().getMessage().getParts().values()) {
            Part part = (Part) candidate;
            if (part.getElementName() != null) {
                return part.getElementName();
            }
        }
        return null;
    }
}
