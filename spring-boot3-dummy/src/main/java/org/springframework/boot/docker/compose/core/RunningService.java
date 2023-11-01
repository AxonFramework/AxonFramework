/*
 * Copyright to it's respective authors. This class is a dummy-copy of their originals in Spring Framework.
 */

package org.springframework.boot.docker.compose.core;

import java.util.Map;

 /**
 * Dummy implementation of the RunningService interface to allow classes to compile using JDK 8
 * <p>
 * All methods in this class return null or are no-ops. This class is included for compilation reasons and should never
 * make to a runtime environment.
 */
public interface RunningService {

	/**
	 * Return the name of the service.
	 * @return the service name
	 */
	String name();

	/**
	 * Return the image being used by the service.
	 * @return the service image
	 */
	ImageReference image();

	/**
	 * Return the host that can be used to connect to the service.
	 * @return the service host
	 */
	String host();

	/**
	 * Return the ports that can be used to connect to the service.
	 * @return the service ports
	 */
	ConnectionPorts ports();

	/**
	 * Return the environment defined for the service.
	 * @return the service env
	 */
	Map<String, String> env();

	/**
	 * Return the labels attached to the service.
	 * @return the service labels
	 */
	Map<String, String> labels();

}
