package gr.distsystems.fruiting.util;

import java.security.MessageDigest;

public class Hash {

    public static String sha256(String base) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(base.getBytes("UTF-8"));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash)
            hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
