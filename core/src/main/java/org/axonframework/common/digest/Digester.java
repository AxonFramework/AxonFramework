package org.axonframework.common.digest;

import org.axonframework.common.AxonConfigurationException;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author Allard Buijze
 */
public class Digester {

    private MessageDigest messageDigest;

    public static Digester newInstance(String algorithm) {
        try {
            return new Digester(MessageDigest.getInstance(algorithm));
        } catch (NoSuchAlgorithmException e) {
            throw new AxonConfigurationException("This environment doesn't support the MD5 hashing algorithm", e);
        }
    }

    public static Digester newMD5Instance() {
        return newInstance("MD5");
    }

    public static String md5Hex(String item) {
        try {
            return newMD5Instance().update(item.getBytes("UTF-8")).digestHex();
        } catch (UnsupportedEncodingException e) {
            throw new AxonConfigurationException("The UTF-8 encoding is not available on this environment", e);
        }
    }

    private Digester(MessageDigest messageDigest) {
        this.messageDigest = messageDigest;
    }

    public Digester update(byte[] additionalData) {
        messageDigest.update(additionalData);
        return this;
    }

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
