package org.announcementserver.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableEntryException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.BadPaddingException;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;

import javax.crypto.Cipher;

/*
* Cryptographic tools
*/

public class CryptoTools {
	
	private static final String KEYSTORE_FILE_PATH = "src/main/resources/";
	private static final String KEYSTORE_FILENAME = "announcement.jks";
	private static final String PASSWORD_FILENAME = "announcement.properties";
	
	public static KeyStore getKeystore(String password) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		File keystoreResource = new File(KEYSTORE_FILE_PATH + KEYSTORE_FILENAME);
		InputStream keyStoreIS = new FileInputStream(keystoreResource);
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		keyStore.load(keyStoreIS, password.toCharArray());
		return keyStore;
	}

	private static String getPassword() throws IOException {
		Properties passwordProps = new Properties();
		File passwordResource = new File(KEYSTORE_FILE_PATH + PASSWORD_FILENAME);
		InputStream passwordIS = new FileInputStream(passwordResource);
		passwordProps.load(passwordIS);
		String password = passwordProps.getProperty("keystore-password");
		return password;
	}
	
	public static PublicKey getPublicKey(String clientId) throws NoSuchAlgorithmException, UnrecoverableEntryException, KeyStoreException, IOException, CertificateException {
		
		String password = getPassword();
		KeyStore keyStore = getKeystore(getPassword());
		KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(clientId, new KeyStore.PasswordProtection(password.toCharArray()));
		
		RSAPublicKey publicKey = (RSAPublicKey) privateKeyEntry.getCertificate().getPublicKey();
		return publicKey;
	}
	
	public static PrivateKey getPrivateKey(String clientId) throws NoSuchAlgorithmException, UnrecoverableEntryException, KeyStoreException, IOException, CertificateException {
		
		String password = getPassword();
		KeyStore keyStore = getKeystore(getPassword());
		KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(clientId, new KeyStore.PasswordProtection(password.toCharArray()));
		
		RSAPrivateKey privateKey = (RSAPrivateKey) privateKeyEntry.getPrivateKey();
		return privateKey;
	}
	
	public static String publicKeyAsString(PublicKey publicKey) {
		return Base64.getEncoder().encodeToString(publicKey.getEncoded());
	}
	
	public static String privateKeyAsString(PrivateKey privateKey) {
		return Base64.getEncoder().encodeToString(privateKey.getEncoded());
	}
	
	/* Auxiliary functions */
	public static String makeHash(String... args) 
			throws NoSuchAlgorithmException, UnrecoverableEntryException, KeyStoreException, CertificateException, IOException {
		//PrivateKey privKey = CryptoTools.getPrivateKey(username);
		MessageDigest hashFunc = MessageDigest.getInstance("SHA-256");
		
		for (String arg: args) {
			hashFunc.update(arg.getBytes());
		}
		
		byte[] hash = hashFunc.digest();
		
		return byteToString(hash);
	}
	
	public static boolean checkHash(String... ret) 
			throws NoSuchAlgorithmException, UnrecoverableEntryException, KeyStoreException, CertificateException, IOException {
		String[] response = Arrays.copyOfRange(ret, 0, ret.length - 1);
		String signature = ret[ret.length - 1];
		
		String test = makeHash(response);
		
		if (test.equals(signature)) {
			return true;
		} else {
			return false;
		}
	}
	
	public static String makeSignature(String src, String dest, String hash) 
			throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, NoSuchPaddingException, 
				IllegalBlockSizeException, BadPaddingException, InvalidKeyException, UnrecoverableEntryException {
		PrivateKey privKey = getPrivateKey(src);
		
		List<String> toEncrypt = new ArrayList<>();
		toEncrypt.add(src);
		toEncrypt.add(dest);
		toEncrypt.add(hash);
		
//		System.out.println(toEncrypt.toString());
		
		byte[] bytes = null;
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
		    ObjectOutputStream oos = new ObjectOutputStream(bos);
		    oos.writeObject(toEncrypt);
		    bytes = bos.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
		}
			    
	    Cipher cipher = Cipher.getInstance("RSA");
		cipher.init(Cipher.ENCRYPT_MODE, privKey);
		
		cipher.update(bytes);
		
		return byteToString(cipher.doFinal());		
	}
	
	public static List<String> decryptSignature(String src, String signature) 
			throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, NoSuchPaddingException, 
				IllegalBlockSizeException, BadPaddingException, InvalidKeyException, UnrecoverableEntryException {
		PublicKey pubKey = getPublicKey(src);
		
		Cipher cipher = Cipher.getInstance("RSA");
		cipher.init(Cipher.DECRYPT_MODE, pubKey);
		
		cipher.update(stringToByte(signature));
		
		byte[] clearText = cipher.doFinal();
		
		List<String> res = null;
		
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(clearText);
			ObjectInputStream oi = new ObjectInputStream(bis);
			res = (List<String>) oi.readObject();
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Possible tampering of message");
		}
		
//		System.out.println(res.toString());
		
		return res;
	}
	
	private static String byteToString(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}
	
	private static byte[] stringToByte(String str) {
		return Base64.getDecoder().decode(str);
	}
	
	// ---- Old Stuff --------------------------------------------------------------------------------------
	
	//public static String getPublicKeyAsString(String filepath) throws IOException {
    //	byte[] keyBytes = Files.readAllBytes(Paths.get(filepath));
    //	X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
    //  KeyFactory kf = KeyFactory.getInstance("RSA");
    //  PublicKey pk = kf.generatePublic(spec);
		
	//	return Base64.getEncoder().encodeToString(spec.getEncoded());
	//}
	
	public static PrivateKey getPrivKey(String filename) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException  {
	    byte[] keyBytes = Files.readAllBytes(Paths.get(filename));

	    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
	    KeyFactory kf = KeyFactory.getInstance("RSA");
	    return kf.generatePrivate(spec);
	}
	
	public static PublicKey getPubKey(String filename) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		byte[] keyBytes = Files.readAllBytes(Paths.get(filename));
		
		X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
		KeyFactory kf = KeyFactory.getInstance("RSA");
		return kf.generatePublic(spec);
	}
	
	public static String privKeyAsString(String filename) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		return Base64.getEncoder().encodeToString(getPrivKey(filename).getEncoded());
	}
	
	public static String pubKeyAsString(String filename) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		return Base64.getEncoder().encodeToString(getPubKey(filename).getEncoded());
	}
}