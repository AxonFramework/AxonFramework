/*
 * Copyright to it's respective authors. This class is a dummy-copy of their originals in Spring Framework.
 */

package org.springframework.boot.docker.compose.core;

import java.util.List;

/**
 * Copy of the original Spring Boot interface to allow compilation using JDK 8.
 * <p>
 * Provides access to the ports that can be used to connect to a {@link RunningService}.
 *
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 * @author Phillip Webb
 * @see RunningService
 * @since 3.1.0
 */
public interface ConnectionPorts {

	/**
	 * Return the host port mapped to the given container port.
	 *
	 * @param containerPort the container port. This is usually the standard port for the
	 *                      service (e.g. port 80 for HTTP)
	 *
	 * @return the host port. This can be an ephemeral port that is different from the
	 * container port
	 * @throws IllegalStateException if the container port is not mapped
	 */
	int get(int containerPort);

	/**
	 * Return all host ports in use.
	 *
	 * @return a list of all host ports
	 * @see #getAll(String)
	 */
	List<Integer> getAll();

	/**
	 * Return all host ports in use that match the given protocol.
	 *
	 * @param protocol the protocol in use (for example 'tcp') or {@code null} to return
	 *                 all host ports
	 *
	 * @return a list of all host ports using the given protocol
	 */
	List<Integer> getAll(String protocol);

}
