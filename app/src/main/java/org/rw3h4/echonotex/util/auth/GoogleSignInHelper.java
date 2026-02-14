package org.rw3h4.echonotex.util.auth;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.PublicKeyCredential;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GoogleSignInHelper {
    private static final String TAG = "GoogleSignInHelper";
    private final Context context;
    private final CredentialManager credentialManager;
    private final FirebaseAuth mAuth;
    private final ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface SignInCallback {
        void onSignInSuccess();
        void onSignInFailure(String errorMessage);
    }

    private final Activity activity;

    public GoogleSignInHelper(Activity activity) {
        this.activity = activity;
        this.context = activity;
        this.credentialManager = CredentialManager.create(context);
        this.mAuth = FirebaseAuth.getInstance();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void startGoogleSignIn(SignInCallback callback, String webClientId) {
        if (isWebClientIdMissing(webClientId, callback)) return;
        getCredential(webClientId, new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
            @Override
            public void onResult(GetCredentialResponse result) {
                handleGoogleCredential(result.getCredential(), callback, null);
            }

            @Override
            public void onError(@NonNull GetCredentialException e) {
                handleGetCredentialError(e, callback);
            }
        });
    }

    public void linkAnonymousAccountToGoogle(String webClientId, Runnable onSuccess) {
        if (isWebClientIdMissing(webClientId, null)) return;
        getCredential(webClientId, new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
            @Override
            public void onResult(GetCredentialResponse result) {
                handleGoogleCredential(result.getCredential(), null, onSuccess);
            }

            @Override
            public void onError(@NonNull GetCredentialException e) {
                AuthUtils.showToast(activity, "Google link failed: " + e.getMessage());
            }
        });
    }

    private void getCredential(String webClientId, CredentialManagerCallback<GetCredentialResponse, GetCredentialException> callback) {
        GetGoogleIdOption option = new GetGoogleIdOption
                .Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setNonce(createNonce())
                .build();

        GetCredentialRequest request = new GetCredentialRequest
                .Builder()
                .addCredentialOption(option)
                .build();

        credentialManager.getCredentialAsync(
                context,
                request,
                new CancellationSignal(),
                executor,
                callback
        );
    }

    private void handleGoogleCredential(Credential credential, SignInCallback signInCallback, Runnable linkSuccessCallback) {
        if (credential instanceof GoogleIdTokenCredential) {
            handleIdTokenCredential((GoogleIdTokenCredential) credential, signInCallback, linkSuccessCallback);
        } else if (credential instanceof CustomCredential) {
            handleCustomCredential((CustomCredential) credential, signInCallback, linkSuccessCallback);
        } else if (credential instanceof PublicKeyCredential) {
            handlePublicKeyCredential((PublicKeyCredential) credential, signInCallback);
        } else {
            handleUnknownCredential(credential, signInCallback);
        }
    }

    private void handleIdTokenCredential(GoogleIdTokenCredential idTokenCredential, SignInCallback signInCallback, Runnable linkSuccessCallback) {
        String idToken = idTokenCredential.getIdToken();
        AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(idToken, null);

        if (signInCallback != null) {
            signInWithFirebase(firebaseCredential, signInCallback);
        } else if (linkSuccessCallback != null) {
            linkWithFirebase(firebaseCredential, linkSuccessCallback);
        }
    }

    private void handleCustomCredential(CustomCredential customCredential, SignInCallback signInCallback, Runnable linkSuccessCallback) {
        if (customCredential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
            try {
                GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(customCredential.getData());
                handleGoogleCredential(googleIdTokenCredential, signInCallback, linkSuccessCallback);
            } catch (Exception e) {
                if (signInCallback != null) {
                    signInCallback.onSignInFailure("Failed to process Google credential.");
                } else {
                    AuthUtils.showToast(activity, "Failed to process Google credential.");
                }
            }
        } else {
            if (signInCallback != null) {
                signInCallback.onSignInFailure("Unexpected custom credential type.");
            } else {
                AuthUtils.showToast(activity, "Unexpected custom credential type.");
            }
        }
    }

    private void handlePublicKeyCredential(PublicKeyCredential publicKeyCredential, SignInCallback signInCallback) {
        String actualType = publicKeyCredential.getClass().getName();
        Log.e(TAG, "Unexpected credential type: " + actualType);
        if (signInCallback != null) {
            signInCallback.onSignInFailure("Sign-in with Passkey is not yet supported.");
        } else {
            AuthUtils.showToast(activity, "Sign-in with Passkey is not yet supported.");
        }
    }

    private void handleUnknownCredential(Credential credential, SignInCallback signInCallback) {
        String actualType = credential.getClass().getName();
        Log.e(TAG, "Unexpected credential type: " + actualType);
        if (signInCallback != null) {
            signInCallback.onSignInFailure("Unexpected credential type: " + actualType);
        } else {
            AuthUtils.showToast(activity, "Unexpected credential type: " + actualType);
        }
    }

    private void signInWithFirebase(AuthCredential credential, SignInCallback callback) {
        mAuth.signInWithCredential(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                mainHandler.post(callback::onSignInSuccess);
            } else {
                Exception e = task.getException();
                if (e instanceof FirebaseAuthUserCollisionException) {
                    mainHandler.post(() -> callback.onSignInFailure("Account exists with different sign-in method."));
                } else {
                    mainHandler.post(() -> callback.onSignInFailure("Google sign-in failed: " + e.getMessage()));
                }
            }
        });
    }

    private void linkWithFirebase(AuthCredential credential, Runnable onSuccess) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && user.isAnonymous()) {
            user.linkWithCredential(credential).addOnCompleteListener(
                    task -> {
                        if (task.isSuccessful()) {
                            mainHandler.post(onSuccess);
                        } else {
                            AuthUtils.showToast(activity, "Linking failed: " + task.getException().getMessage());
                        }
                    });
        }
    }

    private void handleGetCredentialError(GetCredentialException e, SignInCallback callback) {
        String message = e.getLocalizedMessage();
        Log.e(TAG, "GetCredential error: " + message);

        if (message != null && message.toLowerCase().contains("cancel")) {
            mainHandler.post(() -> callback.onSignInFailure("Sign-in cancelled by user"));
        } else {
            mainHandler.post(() -> callback.onSignInFailure("Google sign-in failed " + message));
        }
    }

    private boolean isWebClientIdMissing(String webClientId, SignInCallback callback) {
        if (TextUtils.isEmpty(webClientId)) {
            Log.e(TAG, "WEB_CLIENT_ID is not configured. Please check your local.properties file.");
            if (callback != null) {
                callback.onSignInFailure("Google Sign-In is not configured.");
            } else {
                AuthUtils.showToast(activity, "Google Sign-In is not configured.");
            }
            return true;
        }
        return false;
    }

    private String createNonce() {
        try {
            String rawNonce = UUID.randomUUID().toString();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawNonce.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-256 not supported, fallback to raw nonce");
            return UUID.randomUUID().toString();
        }
    }
}