/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.digest;

import org.axonframework.common.AxonConfigurationException;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for generating hashes for values using several algorithms. It uses the {@link MessageDigest} as
 * underlying mechanism.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public final class Digester {

    private final MessageDigest messageDigest;

    /**
     * Creates a new Digester instance for the given {@code algorithm}.
     *
     * @param algorithm The algorithm to use, e.g. "MD5"
     * @return a fully initialized Digester instance
     */
    public static Digester newInstance(String algorithm) {
        try {
            return new Digester(MessageDigest.getInstance(algorithm));
        } catch (NoSuchAlgorithmException e) {
            throw new AxonConfigurationException("This environment doesn't support the MD5 hashing algorithm", e);
        }
    }

    /**
     * Creates a new Digester instance for the MD5 Algorithm
     *
     * @return a Digester instance to create MD5 hashes
     */
    public static Digester newMD5Instance() {
        return newInstance("MD5");
    }

    /**
     * Utility method that creates a hex string of the MD5 hash of the given {@code input}
     *
     * @param input The value to create a MD5 hash for
     * @return The hex representation of the MD5 hash of given {@code input}
     */
    public static String md5Hex(String input) {
        try {
            return newMD5Instance().update(input.getBytes("UTF-8")).digestHex();
        } catch (UnsupportedEncodingException e) {
            throw new AxonConfigurationException("The UTF-8 encoding is not available on this environment", e);
        }
    }

    private Digester(MessageDigest messageDigest) {
        this.messageDigest = messageDigest;
    }

    /**
     * Update the Digester with given {@code additionalData}.
     *
     * @param additionalData The data to add to the digest source
     * @return {@code this} for method chaining
     */
    public Digester update(byte[] additionalData) {
        messageDigest.update(additionalData);
        return this;
    }

    /**
     * Returns the hex representation of the digest of all data that has been provided so far.
     *
     * @return the hex representation of the digest of all data that has been provided so far
     *
     * @see #update(byte[])
     */
    public String digestHex() {
        return hex(messageDigest.digest());
    }

    private static String hex(byte[] hash) {
        return pad(new BigInteger(1, hash).toString(16));
    }

    private static String pad(String md5) {
        if (md5.length() == 32) {
            return md5;
        }
        StringBuilder sb = new StringBuilder(32);
        for (int t = 0; t < 32 - md5.length(); t++) {
            sb.append("0");
        }
        sb.append(md5);
        return sb.toString();
    }
}
