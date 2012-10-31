package ru.megaplan.jira.plugins.mail.mpsmailhandler.service.util;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.StringWriter;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 18.07.12
 * Time: 16:00
 * To change this template use File | Settings | File Templates.
 */
public class CypherUtil {

    private final static String CYPHERMETHOD = "HmacSHA1";

    public static String createSignature(String url, String secretKey, String method, String currentDateString) {
        StringBuilder uncoded = new StringBuilder();
        uncoded.append(method).append('\n');
        uncoded.append('\n');
        uncoded.append('\n');
        uncoded.append(currentDateString).append('\n');
        uncoded.append(url);
        String uncodedString = uncoded.toString();
        byte[] digest;
        try {
            Mac mac = Mac.getInstance(CYPHERMETHOD);
            SecretKeySpec secret = new SecretKeySpec(secretKey.getBytes(),CYPHERMETHOD);
            mac.init(secret);
            digest = mac.doFinal(uncodedString.getBytes());
            StringWriter hexDigestWriter = new StringWriter();
            for (byte b : digest) {
                hexDigestWriter.write(String.format("%02x", b));
            }
            String hexDigest = hexDigestWriter.toString();
            digest = hexDigest.getBytes();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("it seems i can't find cypher method " + CYPHERMETHOD);
        } catch (InvalidKeyException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            throw new RuntimeException(e);
        }

        String result = new String(Base64.encodeBase64(digest)).trim();
        return result;
    }
}
