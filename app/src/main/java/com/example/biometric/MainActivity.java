package com.example.biometric;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;


import android.Manifest;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.security.KeyStore;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class MainActivity extends AppCompatActivity {

    private LinearLayout layout;
    private Button button;
    private TextView title;

    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    private FingerprintManager fingerprintManager;
    private KeyguardManager keyguardManager;

    private static final String KEY_NAME = "BIOAUTH_KEY";
    private KeyStore keyStore;
    private KeyGenerator keyGenerator;
    private Cipher cipher;
    private CancellationSignal signal;
    private FingerprintManager.CryptoObject cryptoObject; // over api 23
    private BiometricPrompt.CryptoObject bioCryptoObject; // over api 28




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // UI 관련
        layout = new LinearLayout(this);
        title = new TextView(this);
        button = new Button(this);


        ViewGroup.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );

        layout.setLayoutParams(layoutParams);
        layout.setOrientation(LinearLayout.VERTICAL);

        title.setText("생체 인증 테스트 앱");

        button.setText("생체 인증");

        layout.addView(title);
        layout.addView(button);

        setContentView(layout);

        button.setOnClickListener(view -> {
            authenticate();
        });



    }

    private void authenticate() {
        // api 28 ( ANDROID 9.0 ) 이상은 biometricPrompt 사용
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Log.d("bioAuth", "start biometricPrompt");

            executor = ContextCompat.getMainExecutor(this);
            biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    Log.d("bioAuth", errString.toString());
                    Toast.makeText(getApplicationContext(),
                            androidx.biometric.R.string.default_error_msg,
                            Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    Log.d("bioAuth", "auth success");
                    Toast.makeText(getApplicationContext(),
                            "인증 성공!"
                            , Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    Log.d("bioAuth", "auth failed");
                    Toast.makeText(getApplicationContext(),
                            "인증 실패!"
                            , Toast.LENGTH_SHORT).show();
                }
            });

            promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("지문 인증")
                    .setSubtitle("기기에 등록된 지문을 이용하여 지문을 인증해주세요.")
                    .setDescription("생체 인증 설명")
                    // BIOMETRIC_STRONG 은 안드로이드 11 에서 정의한 클래스 3 생체 인식을 사용하는 인증
                    // BIOMETRIC_WEAK 은 안드로이드 11 에서 정의한 클래스 2 생체 인식을 사용하는 인증
                    // DEVICE_CREDENTIAL 은 화면 잠금 사용자 ㅇ니증 정보를 사용하는 인증 - 사용자의 PIN, 패턴 또는 비밀번호
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .setConfirmationRequired(false) // 명시적인 사용자 작업 ( 생체 인식 전 한번더 체크 ) 없이 인증할건지 default : true
                    .setNegativeButtonText("취소")
                    .build();

            biometricPrompt.authenticate(promptInfo);

        }
        else // api 23 ( ANDROID 6.0 ) 부터 api 28 ( ANDROID 9.0 ) 까지는 fingerprint 사용
        {
            Log.d("fingerprint", "fingerprint start");


            fingerprintManager = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);
            keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            String comment = "";


            if (!fingerprintManager.isHardwareDetected()) // 지문을 사용할 수 없는 디바이스인 경우
            {
                Log.d("fingerprint", "it is not device that can use fingerprint");
            }
            // 지문 인증 사용을 거부한 경우
            else if (ContextCompat.checkSelfPermission(this, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED)
            {
                Log.d("fingerprint", "permission denied");
            }
            else if (!keyguardManager.isKeyguardSecure()) // 잠금 화면이 설정되지 않은 경우
            {
                Log.d("fingerprint", "please set lock screen");
            }
            else if (!fingerprintManager.hasEnrolledFingerprints()) // 등록된 지문이 없는 경우
            {
                Log.d("fingerprint", "please enroll fingerprint");
            }
            else
            {
                Log.d("fingerprint", "requirement fingerprint needed all pass");
                generateKey();

                if (cipherInit())
                {
                    signal = new CancellationSignal();
                    cryptoObject = new FingerprintManager.CryptoObject(cipher);

                    fingerprintManager.authenticate(cryptoObject, signal, 0, new FingerprintManager.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            Log.d("fingerprint", String.valueOf(errorCode));
                            Toast.makeText(getApplicationContext(),
                                    androidx.biometric.R.string.default_error_msg,
                                    Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            Log.d("fingerprint", "auth success");
                            Toast.makeText(getApplicationContext(),
                                    "인증 성공",
                                    Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                            Log.d("fingerprint", "auth failed");
                            Toast.makeText(getApplicationContext(),
                                    "인증 실패",
                                    Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
                            super.onAuthenticationHelp(helpCode, helpString);
                            Log.d("fingerprint", helpString.toString());
                        }

                        private void stopListeningAuth() {
                            if (signal != null)
                            {
                                signal.cancel();
                                signal = null;
                            }
                        }
                    }, null);
                }

            }
        }
    }

    private void generateKey() {
        try {
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


    private Boolean cipherInit() {
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