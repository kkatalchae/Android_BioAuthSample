package com.example.biometric.bioAuthmanager;

import static android.content.Context.FINGERPRINT_SERVICE;
import static android.content.Context.KEYGUARD_SERVICE;
import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.provider.Settings;
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

    private BioAuthManager() { };

    public static BioAuthManager getInstance() {
        if (instance == null)
        {
            instance = new BioAuthManager();
        }

        return instance;
    }

    private static final int REQUEST_FINGERPRINT_ENROLLMENT_AUTH = 10;

    private KeyManager keyManager;

    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    private FingerprintManager fingerprintManager;
    private KeyguardManager keyguardManager;

    private FingerprintManager.CryptoObject cryptoObject; // over api 23
    private BiometricPrompt.CryptoObject bioCryptoObject; // over api 28

    private boolean canAuthenticate(Activity activity, Context context){

        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            BiometricManager biometricManager = BiometricManager.from(context);
            switch (biometricManager.canAuthenticate(BIOMETRIC_STRONG))
            {
                case BiometricManager.BIOMETRIC_SUCCESS:
                    Log.d("MY_APP_TAG", "App can authenticate using biometrics.");
                    return true;
                case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                    Log.e("MY_APP_TAG", "No biometric features available on this device.");
                    return false;
                case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                    Log.e("MY_APP_TAG", "Biometric features are currently unavailable.");
                    return false;
                case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                    // Prompts the user to create credentials that your app accepts.
                    Log.d("MY_APP_TAG", "enrolled biometric doesn't existed. please enroll");

                    intent = new Intent(Settings.ACTION_FINGERPRINT_ENROLL);
                    intent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                            BIOMETRIC_STRONG | DEVICE_CREDENTIAL);
                    activity.startActivityForResult(intent, REQUEST_FINGERPRINT_ENROLLMENT_AUTH);
                    return false;
            }
        }
        else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (!fingerprintManager.isHardwareDetected()) // ????????? ????????? ??? ?????? ??????????????? ??????
            {
                Log.d("fingerprint", "it is not device that can use fingerprint");
                return false;
            }
            // ?????? ?????? ????????? ????????? ??????
            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED)
            {
                Log.d("fingerprint", "permission denied");
                return false;
            }
            else if (!keyguardManager.isKeyguardSecure()) // ?????? ????????? ???????????? ?????? ??????
            {
                Log.d("fingerprint", "please set lock screen");
                return false;
            }
            else if (!fingerprintManager.hasEnrolledFingerprints()) // ????????? ????????? ?????? ??????
            {
                Log.d("fingerprint", "please enroll fingerprint");
                intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                activity.startActivityForResult(intent, REQUEST_FINGERPRINT_ENROLLMENT_AUTH);
                return false;
            }

            return true;
        }

        return false;
    }


    public void authenticate(FragmentActivity activity, Context context) {

        keyManager = KeyManager.getInstance();
        // api 28 ( ANDROID 9.0 ) ????????? biometricPrompt ??????
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Log.d("bioAuth", "start biometricPrompt");

            if (canAuthenticate(activity, context))
            {
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

                // DEVICE_CREDENTIAL ??? BIOMETRIC_STRING | DEVICE_CREDENTIAL ??? ??????????????? 10 ???????????? ???????????? ?????????.
                // ??????????????? 10 ???????????? PIN ?????? ??????, ??????????????? ????????? ??????????????? KeyguardManager.isDeviceSecure() ????????? ????????? ???
                promptInfo = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("?????? ??????")
                        .setSubtitle("????????? ????????? ????????? ???????????? ????????? ??????????????????.")
                        .setDescription("?????? ?????? ??????")
                        // BIOMETRIC_STRONG ??? ??????????????? 11 ?????? ????????? ????????? 3 ?????? ????????? ???????????? ?????? - ???????????? ??? ??????
                        // BIOMETRIC_WEAK ??? ??????????????? 11 ?????? ????????? ????????? 2 ?????? ????????? ???????????? ?????? - ???????????? ????????? ??????????????? ??????
                        // DEVICE_CREDENTIAL ??? ?????? ?????? ????????? ?????? ????????? ???????????? ?????? - ???????????? PIN, ?????? ?????? ????????????
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .setConfirmationRequired(false) // ???????????? ????????? ?????? ( ?????? ?????? ??? ????????? ?????? ) ?????? ??????????????? default : true
                        .setNegativeButtonText("??????")
                        .build();

                keyManager.generateKey();

                if (keyManager.cipherInit())
                {
                    bioCryptoObject = new BiometricPrompt.CryptoObject(keyManager.getCipher());
                    biometricPrompt.authenticate(promptInfo, bioCryptoObject);
                }

                biometricPrompt.authenticate(promptInfo);
            }

        }
        else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)// api 23 ( ANDROID 6.0 ) ?????? api 28 ( ANDROID 9.0 ) ????????? fingerprint ??????
        {
            Log.d("fingerprint", "fingerprint start");

            fingerprintManager = (FingerprintManager) context.getSystemService(FINGERPRINT_SERVICE);
            keyguardManager = (KeyguardManager) context.getSystemService(KEYGUARD_SERVICE);
//            if (!fingerprintManager.isHardwareDetected()) // ????????? ????????? ??? ?????? ??????????????? ??????
//            {
//                Log.d("fingerprint", "it is not device that can use fingerprint");
//            }
//            // ?????? ?????? ????????? ????????? ??????
//            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED)
//            {
//                Log.d("fingerprint", "permission denied");
//            }
//            else if (!keyguardManager.isKeyguardSecure()) // ?????? ????????? ???????????? ?????? ??????
//            {
//                Log.d("fingerprint", "please set lock screen");
//            }
//            else if (!fingerprintManager.hasEnrolledFingerprints()) // ????????? ????????? ?????? ??????
//            {
//                Log.d("fingerprint", "please enroll fingerprint");
//            }
//            else
            if (canAuthenticate(activity, context))
            {
                Log.d("fingerprint", "requirement fingerprint needed all pass");

                keyManager.generateKey();

                if (keyManager.cipherInit())
                {

                    Cipher cipher = keyManager.getCipher();

                    cryptoObject = new FingerprintManager.CryptoObject(cipher);

                    fingerprintManager.authenticate(cryptoObject, new CancellationSignal(), 0, new FingerprintManager.AuthenticationCallback() {
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
