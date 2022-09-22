package com.example.biometric.bioAuthmanager;

import static android.content.Context.FINGERPRINT_SERVICE;
import static android.content.Context.KEYGUARD_SERVICE;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

import javax.crypto.Cipher;

@RequiresApi(api = Build.VERSION_CODES.M)
public class BioAuthManager {

    private static BioAuthManager instance;

    private BioAuthManager() {};

    public static BioAuthManager getInstance() {
        if (instance == null)
        {
            instance = new BioAuthManager();
        }

        return instance;
    }

    private KeyManager keyManager = KeyManager.getInstance();

    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    private FingerprintManager fingerprintManager;
    private KeyguardManager keyguardManager;


    private FingerprintManager.CryptoObject cryptoObject; // over api 23
    private BiometricPrompt.CryptoObject bioCryptoObject; // over api 28

    public void authenticate(FragmentActivity activity, Context context) {
        // api 28 ( ANDROID 9.0 ) 이상은 biometricPrompt 사용
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Log.d("bioAuth", "start biometricPrompt");

            executor = ContextCompat.getMainExecutor(context);
            biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    Log.d("bioAuth", errString.toString());
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    Log.d("bioAuth", "auth success");
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    Log.d("bioAuth", "auth failed");
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


            fingerprintManager = (FingerprintManager) context.getSystemService(FINGERPRINT_SERVICE);
            keyguardManager = (KeyguardManager) context.getSystemService(KEYGUARD_SERVICE);
            String comment = "";


            if (!fingerprintManager.isHardwareDetected()) // 지문을 사용할 수 없는 디바이스인 경우
            {
                Log.d("fingerprint", "it is not device that can use fingerprint");
            }
            // 지문 인증 사용을 거부한 경우
            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED)
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
                keyManager.generateKey();

                if (keyManager.cipherInit())
                {

                    Cipher cipher = keyManager.getCipher();
                    CancellationSignal signal = keyManager.getSignal();

                    cryptoObject = new FingerprintManager.CryptoObject(cipher);

                    fingerprintManager.authenticate(cryptoObject, signal, 0, new FingerprintManager.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            Log.d("fingerprint", String.valueOf(errorCode));
                        }

                        @Override
                        public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            Log.d("fingerprint", "auth success");

                        }

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                            Log.d("fingerprint", "auth failed");

                        }

                        @Override
                        public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
                            super.onAuthenticationHelp(helpCode, helpString);
                            Log.d("fingerprint", helpString.toString());
                        }

                    }, null);
                }

            }
        }
    }



}
