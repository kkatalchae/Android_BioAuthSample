package com.example.biometric.bioAuthmanager;

import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import lombok.Getter;
import lombok.Setter;

public class KeyManager {

    private static KeyManager instance;

    private KeyManager() {
        signal = new CancellationSignal();
    };

    public static KeyManager getInstance() {
        if (instance == null)
        {
            instance = new KeyManager();
        }

        return instance;
    }

    private static final String KEY_NAME = "BIOAUTH_KEY";
    private KeyStore keyStore;
    private KeyGenerator keyGenerator;

    @Getter @Setter
    private Cipher cipher;

    @Getter @Setter
    private CancellationSignal signal;

    /**
     * 지문 인증을 사용하기 위한 키를 생성하는 함수
     */
    public void generateKey() {
        try {

            // 안드로이드에서 기본적으로 제공하는 KeyStore 인듯하다 ( AndroidKeyStore )
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            keyStore.load(null);
            keyGenerator.init(new KeyGenParameterSpec.Builder(
                    KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());
            keyGenerator.generateKey();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 키스토어 저장된 키를 암호화하는 함수
     * @return Boolean
     */
    public Boolean cipherInit() {
        try {
            cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                    + KeyProperties.BLOCK_MODE_CBC + "/"
                    + KeyProperties.ENCRYPTION_PADDING_PKCS7);

            SecretKey key = (SecretKey) keyStore.getKey(KEY_NAME, null);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }
}
