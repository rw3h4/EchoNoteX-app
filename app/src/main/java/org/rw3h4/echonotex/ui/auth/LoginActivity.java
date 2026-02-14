package org.rw3h4.echonotex.ui.auth;

import android.os.Bundle;
import android.view.View;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.rw3h4.echonotex.R;
import org.rw3h4.echonotex.util.auth.AuthUtils;
import org.rw3h4.echonotex.util.auth.GoogleSignInHelper;
import org.rw3h4.echonotex.ui.auth.base.BaseActivity;

import java.util.Objects;

public class LoginActivity extends BaseActivity {

    private FirebaseAuth mAuth;
    private GoogleSignInHelper googleSignInHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth =  FirebaseAuth.getInstance();
        googleSignInHelper = new GoogleSignInHelper(this);

        View mainContent = findViewById(R.id.main);
        setupSplashScreen(mainContent);
        setupWindowInsets(mainContent);
        setupSharedElementTransition(R.id.ic_logo_image);

        setNavigationWithSharedElement(R.id.forgot_password_textView, PasswordResetActivity.class, R.id.ic_logo_image);
        setNavigationWithSharedElement(R.id.account_sign_text, RegisterActivity.class, R.id.ic_logo_image);

        findViewById(R.id.alt_login_cardView).setOnClickListener(v -> startGoogleSignIn());

        findViewById(R.id.login_button).setOnClickListener(v -> startEmailPasswordLogin());

        findViewById(R.id.skip_textView).setOnClickListener(v -> {
            mAuth.signInAnonymously().addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    goToNotes();
                } else {
                    AuthUtils.showToast(this, "Guest sign-in failed. Please try again");
                }
            });
        });

        isContentReady = true;
    }

    private void startGoogleSignIn() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.isAnonymous()) {
            // Handle upgrading guest user to Google
            googleSignInHelper.linkAnonymousAccountToGoogle(
                    getString(R.string.web_client_id), () ->
                            AuthUtils.showToast(this, "Account linked successfully"));
        } else {
            googleSignInHelper.startGoogleSignIn(
                    new GoogleSignInHelper.SignInCallback() {
                        @Override
                        public void onSignInSuccess() {
                            goToNotes();
                        }

                        @Override
                        public void onSignInFailure(String errorMessage) {
                            AuthUtils.showToast(LoginActivity.this, errorMessage);
                        }
                    }, getString(R.string.web_client_id)
            );
        }
    }

    private void startEmailPasswordLogin() {
        TextInputEditText emailInput = findViewById(R.id.email_textInput);
        TextInputEditText passwordInput = findViewById(R.id.password_textInput);
        if (emailInput != null &&
                passwordInput != null &&
                !AuthUtils.isEmpty(Objects.requireNonNull(emailInput.getText()).toString())
                && !AuthUtils.isEmpty(Objects.requireNonNull(passwordInput.getText()).toString())) {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null && currentUser.isAnonymous()) {
                // Handle guest user upgrade to email/password authentication
                AuthUtils.linkAnonymousAccountToEmailPassword(this, email, password,
                        () -> {
                    AuthUtils.showToast(this, "Account linked successfully");
                    goToNotes();
                        });
            } else {
                mAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                goToNotes();
                            } else {
                                AuthUtils.showToast(this, "Login Failed: "
                                        + Objects.requireNonNull(task.getException()).getMessage());
                            }
                        });
            }
        } else {
            AuthUtils.showToast(this, "Please enter Email and Password.");
        }
    }
}