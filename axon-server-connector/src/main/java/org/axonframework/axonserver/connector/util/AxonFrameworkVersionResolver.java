package org.axonframework.axonserver.connector.util;


import org.axonframework.util.MavenArtifactVersionResolver;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Provides Axon Framework current version.
 * <p>
 * At first it looks for the version in the Meta Data of the maven artifact.
 * As a fallback it looks for the version in the environment variables.
 *
 * @author Sara Pellegrini
 * @since 4.2.1
 */
public class AxonFrameworkVersionResolver implements Supplier<String> {

    private static final String ORG_AXONFRAMEWORK_GROUP_ID = "org.axonframework";
    private static final String AXON_SERVER_CONNECTOR_ARTIFACT_ID = "axon-server-connector";
    private static final String AXON_FRAMEWORK_VERSION_ENV_PROPERTY = "AXON_FRAMEWORK_VERSION";
    private static final String UNKNOWN_VERSION = "";

    private final Supplier<String> mavenAxonVersionSupplier;
    private final Function<String, String> envPropertySupplier;

    private final AtomicReference<String> version = new AtomicReference<>();

    /**
     * Creates an instance that uses a {@link MavenArtifactVersionResolver} to resolve the Axon Server Connector jar
     * version, and {@link System#getenv(String)} to resolve the AXON_FRAMEWORK_VERSION environment property as a
     * fallback.
     */
    public AxonFrameworkVersionResolver() {
        this(() -> {
            try {
                return new MavenArtifactVersionResolver(ORG_AXONFRAMEWORK_GROUP_ID,
                                                        AXON_SERVER_CONNECTOR_ARTIFACT_ID,
                                                        AxonFrameworkVersionResolver.class.getClassLoader()).get();
            } catch (IOException e) {
                throw new RuntimeException(
                        "Impossible to read maven artifact version for " + AXON_SERVER_CONNECTOR_ARTIFACT_ID);
            }
        }, System::getenv);
    }

    /**
     * Creates an instance that tries to obtain the axon version from the specified axonArtifactVersionSupplier, or as
     * a fallback in case of null/empty string, tries to obtain the axon version from the environment properties through
     * the envPropertySupplier.
     *
     * @param axonArtifactVersionSupplier used to retrieve the axon version from artifact metadata
     * @param envPropertySupplier         used to retrieve environment properties
     */
    AxonFrameworkVersionResolver(Supplier<String> axonArtifactVersionSupplier,
                                 Function<String, String> envPropertySupplier) {
        this.mavenAxonVersionSupplier = axonArtifactVersionSupplier;
        this.envPropertySupplier = envPropertySupplier;
    }

    /**
     * Provides the current Axon Framework version, and cache it in memory during the first call.
     *
     * @return the Axon Framework version
     */
    @Override
    public String get() {
        if (version.get() == null) {
            version.set(resolveVersion());
        }
        return version.get();
    }

    private String resolveVersion() {
        String version = mavenAxonVersionSupplier.get();

        if (version == null) {
            version = envPropertySupplier.apply(AXON_FRAMEWORK_VERSION_ENV_PROPERTY);
        }

        return (version != null) ? version : UNKNOWN_VERSION;
    }
}
