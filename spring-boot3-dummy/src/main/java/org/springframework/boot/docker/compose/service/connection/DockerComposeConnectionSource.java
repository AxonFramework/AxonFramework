/*
 * Copyright to it's respective authors. This class is a dummy-copy of their originals in Spring Framework.
 */

package org.springframework.boot.docker.compose.service.connection;

import org.springframework.boot.docker.compose.core.RunningService;

/**
 * Dummy implementation of the real DockerComposeConnectionSource to allow Axon classes to compile using JDK8
 * <p>
 * All methods in this class return null or are no-ops. This class is included for compilation reasons and should never
 * make to a runtime environment.
 */
public final class DockerComposeConnectionSource {

	public RunningService getRunningService() {
		return null;
	}

}
