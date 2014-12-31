package com.bsb.hike.voip;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import android.annotation.SuppressLint;
import android.util.Log;


public class VoIPEncryptor {
	private static final int RSA_KEY_SIZE = 2048;
	private static final int AES_KEY_SIZE = 256;		// This depends on the RSA_KEY_SIZE 
	
	private KeyPairGenerator kpg = null;
	private Key publicKey = null;
	private Key privateKey = null;
	
	private SecretKeySpec aesKeySpec = null;
	private Cipher aesEncryptCipher = null;
	private Cipher aesDecryptCipher = null;
	
	private byte[] sessionKey = null;
	
	enum EncryptionStage {
		STAGE_INITIAL,
		STAGE_GOT_PUBLIC_KEY,
		STAGE_GOT_SESSION_KEY,
		STAGE_READY
	}
	
	public VoIPEncryptor() {
		kpg = null;
		publicKey = null;
		privateKey = null;
		aesKeySpec = null;
		aesEncryptCipher = null;
		aesDecryptCipher = null;
	}

	@SuppressLint("TrulyRandom") public void initKeys() {
		if (kpg != null)
			return;		
		
		try {
			// Get RSA public / private keys
			kpg = KeyPairGenerator.getInstance("RSA");
			kpg.initialize(RSA_KEY_SIZE);
			KeyPair kp = kpg.genKeyPair();
			publicKey = kp.getPublic();
			privateKey = kp.getPrivate();
			
		} catch (NoSuchAlgorithmException e) {
			Log.d(VoIPConstants.TAG, "NoSuchAlgorithmException: " + e.toString());
		}
	}
	
	public void initSessionKey() {

		if (sessionKey != null)
			return;
		
		// Generate session key
		sessionKey = new byte[AES_KEY_SIZE / 8];
		PRNGFixes.apply();
		SecureRandom sr = new SecureRandom();
		Log.d(VoIPConstants.TAG, "New AES key generated.");
		sr.nextBytes(sessionKey);
		aesKeySpec = null;
		aesDecryptCipher = null;
		aesEncryptCipher = null;
	}
	
	public byte[] getSessionKey() {
		return sessionKey;
	}
	
	public void setSessionKey(byte[] sessionKey) {
		this.sessionKey = sessionKey;
	}
	
	public byte[] getPublicKey() {
		if (publicKey != null)
			return publicKey.getEncoded();
		else
			return null;
	}
	
	public void setPublicKey(byte[] pubKey) {
		
		if (publicKey != null)
			return;
		
		try {
			publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubKey));
		} catch (InvalidKeySpecException e) {
			Log.d(VoIPConstants.TAG, "InvalidKeySpecException: " + e.toString());
		} catch (NoSuchAlgorithmException e) {
			Log.d(VoIPConstants.TAG, "NoSuchAlgorithmException: " + e.toString());
		}
	}

	public byte[] rsaEncrypt(byte[] data, byte[] pubKey) {
		
		byte[] encryptedData = null;
		
		try {
			PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubKey));
			Cipher cipher = Cipher.getInstance("RSA");
			cipher.init(Cipher.ENCRYPT_MODE, key);
			encryptedData = cipher.doFinal(data);
			
		} catch (NoSuchAlgorithmException e) {
			Log.d(VoIPConstants.TAG, "NoSuchAlgorithmException: " + e.toString());
		} catch (NoSuchPaddingException e) {
			Log.d(VoIPConstants.TAG, "NoSuchPaddingException: " + e.toString());
		} catch (InvalidKeyException e) {
			Log.d(VoIPConstants.TAG, "InvalidKeyException: " + e.toString());
		} catch (IllegalBlockSizeException e) {
			Log.d(VoIPConstants.TAG, "IllegalBlockSizeException: " + e.toString());
		} catch (BadPaddingException e) {
			Log.d(VoIPConstants.TAG, "BadPaddingException: " + e.toString());
		} catch (InvalidKeySpecException e) {
			Log.d(VoIPConstants.TAG, "InvalidKeySpecException: " + e.toString());
		}

		return encryptedData;
	}
	
	public byte[] rsaDecrypt(byte[] data) {
		byte[] decryptedData = null;
		
		try {
			Cipher cipher = Cipher.getInstance("RSA");
			cipher.init(Cipher.DECRYPT_MODE, privateKey);
			decryptedData = cipher.doFinal(data);
		} catch (NoSuchAlgorithmException e) {
			Log.d(VoIPConstants.TAG, "NoSuchAlgorithmException: " + e.toString());
		} catch (NoSuchPaddingException e) {
			Log.d(VoIPConstants.TAG, "NoSuchPaddingException: " + e.toString());
		} catch (InvalidKeyException e) {
			Log.d(VoIPConstants.TAG, "InvalidKeyException: " + e.toString());
		} catch (IllegalBlockSizeException e) {
			Log.d(VoIPConstants.TAG, "IllegalBlockSizeException: " + e.toString());
		} catch (BadPaddingException e) {
			Log.d(VoIPConstants.TAG, "BadPaddingException: " + e.toString());
		}
		
		return decryptedData;
	}
	
	public byte[] aesEncrypt(byte[] data) {
		byte[] encryptedData = null;
		
		try {
			if (aesKeySpec == null)
				aesKeySpec = new SecretKeySpec(sessionKey, "AES");
			if (aesEncryptCipher == null) {
				aesEncryptCipher = Cipher.getInstance("AES");
				aesEncryptCipher.init(Cipher.ENCRYPT_MODE, aesKeySpec);
			}
			encryptedData = aesEncryptCipher.doFinal(data);
			
		} catch (NoSuchAlgorithmException e) {
			Log.d(VoIPConstants.TAG, "NoSuchAlgorithmException: " + e.toString());
		} catch (NoSuchPaddingException e) {
			Log.d(VoIPConstants.TAG, "NoSuchPaddingException: " + e.toString());
		} catch (InvalidKeyException e) {
			Log.d(VoIPConstants.TAG, "InvalidKeyException: " + e.toString());
		} catch (IllegalBlockSizeException e) {
			Log.d(VoIPConstants.TAG, "IllegalBlockSizeException: " + e.toString());
		} catch (BadPaddingException e) {
			Log.d(VoIPConstants.TAG, "BadPaddingException: " + e.toString());
		}
		
		return encryptedData;
	}

	public byte[] aesDecrypt(byte[] data) {
		byte[] decryptedData = null;
		
		try {
			if (aesKeySpec == null)
				aesKeySpec = new SecretKeySpec(sessionKey, "AES");
			if (aesDecryptCipher == null) {
				aesDecryptCipher = Cipher.getInstance("AES");
				aesDecryptCipher.init(Cipher.DECRYPT_MODE, aesKeySpec);
			}
			decryptedData = aesDecryptCipher.doFinal(data);
			
		} catch (NoSuchAlgorithmException e) {
			Log.d(VoIPConstants.TAG, "NoSuchAlgorithmException: " + e.toString());
		} catch (NoSuchPaddingException e) {
			Log.d(VoIPConstants.TAG, "NoSuchPaddingException: " + e.toString());
		} catch (InvalidKeyException e) {
			Log.d(VoIPConstants.TAG, "InvalidKeyException: " + e.toString());
		} catch (IllegalBlockSizeException e) {
			Log.d(VoIPConstants.TAG, "IllegalBlockSizeException: " + e.toString());
		} catch (BadPaddingException e) {
			Log.d(VoIPConstants.TAG, "BadPaddingException: " + e.toString());
		}
		
		return decryptedData;
	}

}