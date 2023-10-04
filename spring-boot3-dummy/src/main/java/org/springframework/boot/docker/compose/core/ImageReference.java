/*
 * Copyright to it's respective authors. This class is a dummy-copy of their originals in Spring Framework.
 */

package org.springframework.boot.docker.compose.core;


/**
 * Dummy implementation of the ImageReference interface to allow classes to compile using JDK 8
 * <p>
 * All methods in this class return null or are no-ops. This class is included for compilation reasons and should never
 * make to a runtime environment.
 */
public final class ImageReference {


	private ImageReference() {
	}

	/**
	 * Create a new {@link ImageReference} from the given value. The following value forms
	 * can be used:
	 * <ul>
	 * <li>{@code name} (maps to {@code docker.io/library/name})</li>
	 * <li>{@code domain/name}</li>
	 * <li>{@code domain:port/name}</li>
	 * <li>{@code domain:port/name:tag}</li>
	 * <li>{@code domain:port/name@digest}</li>
	 * </ul>
	 *
	 * @param value the value to parse
	 *
	 * @return an {@link ImageReference} instance
	 */
	public static ImageReference of(String value) {
		return null;
	}

	/**
	 * Return the domain for this image name.
	 *
	 * @return the domain
	 */
	public String getDomain() {
		return null;
	}

	/**
	 * Return the name of this image.
	 *
	 * @return the image name
	 */
	public String getName() {
		return null;
	}

	/**
	 * Return the tag from the reference or {@code null}.
	 *
	 * @return the referenced tag
	 */
	public String getTag() {
		return null;
	}

	/**
	 * Return the digest from the reference or {@code null}.
	 *
	 * @return the referenced digest
	 */
	public String getDigest() {
		return null;
	}

}
