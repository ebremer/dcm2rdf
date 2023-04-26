package com.ebremer.dcm2rdf;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashGeneratorUtils {
    private HashGeneratorUtils() {
		
    }
    
    public static String Hex2Dec(String s) {
        return new BigInteger(s,16).toString();
    }
	
    public static String generateMD5(String message) throws HashGenerationException {
	return hashString(message, "MD5");
    }
	
    public static String generateSHA1(String message) throws HashGenerationException {
	return hashString(message, "SHA-1");
    }
	
    public static String generateSHA256(String message) throws HashGenerationException {
    	return hashString(message, "SHA-256");
    }
	
    public static String generateMD5(File file) throws HashGenerationException {
	return hashFile(file, "MD5");
    }
	
    public static String generateSHA1(File file) throws HashGenerationException {
	return hashFile(file, "SHA-1");
    }
	
    public static String generateSHA256(File file) throws HashGenerationException {
	return hashFile(file, "SHA-256");
    }

    public static String generateSHA512(File file) throws HashGenerationException {
	return hashFile(file, "SHA-512");
    }
    
    private static String hashString(String message, String algorithm) throws HashGenerationException {		
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hashedBytes = digest.digest(message.getBytes("UTF-8"));	
            return convertByteArrayToHexString(hashedBytes);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
            throw new HashGenerationException("Could not generate hash from String", ex);
        }
    }
	
    private static String hashFile(File file, String algorithm) throws HashGenerationException {
	try (FileInputStream inputStream = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance(algorithm);		
            byte[] bytesBuffer = new byte[1024];
            int bytesRead = -1;		
            while ((bytesRead = inputStream.read(bytesBuffer)) != -1) {
		digest.update(bytesBuffer, 0, bytesRead);
            }		
            byte[] hashedBytes = digest.digest();		
		return convertByteArrayToHexString(hashedBytes);
            } catch (NoSuchAlgorithmException | IOException ex) {
		throw new HashGenerationException("Could not generate hash from file", ex);
            }
    }
	
    private static String convertByteArrayToHexString(byte[] arrayBytes) {
	StringBuilder stringBuffer = new StringBuilder();
	for (int i = 0; i < arrayBytes.length; i++) {
            stringBuffer.append(Integer.toString((arrayBytes[i] & 0xff) + 0x100, 16).substring(1));
	}		
	return stringBuffer.toString();
    }
}