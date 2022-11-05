package com.example.cognito_login_test;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoDevice;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserAttributes;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserPool;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.CognitoUserSession;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.AuthenticationDetails;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.ChallengeContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.continuations.MultiFactorAuthenticationContinuation;
import com.amazonaws.mobileconnectors.cognitoidentityprovider.handlers.AuthenticationHandler;
import com.amazonaws.regions.Regions;

public class MainActivity extends AppCompatActivity {

    private CognitoUserPool mUserPool;
    private CognitoUserSession mUserSession;
    private CognitoDevice mDevice;
    private static String TAG = "cognito_login_test";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String userPoolId = "ap-northeast-1_**********";
        String clientId = "**************";
        String clientSecret = null;
        mUserPool = new CognitoUserPool(MainActivity.this, userPoolId, clientId, clientSecret, Regions.AP_NORTHEAST_1);
        signIn("username", "password");
    }

    private void signIn(String username, String password) {
        mUserPool.getUser(username).getSessionInBackground(
                new AuthenticationHandler() {
                    @Override
                    public void onSuccess(CognitoUserSession userSession, CognitoDevice newDevice) {
                        Log.d(TAG, "onSuccess");
                        mUserSession = userSession;
                        mDevice = newDevice;
                        Log.d(TAG, "ID token : " + userSession.getIdToken().getJWTToken());
                        Log.d(TAG, "Access token : " + userSession.getAccessToken().getJWTToken());
                    }

                    @Override
                    public void getAuthenticationDetails(AuthenticationContinuation authenticationContinuation, String userId) {
                        Log.d(TAG, "user id : " + userId);
                        AuthenticationDetails authenticationDetails = new AuthenticationDetails(userId, password, null);
                        authenticationContinuation.setAuthenticationDetails(authenticationDetails);
                        authenticationContinuation.continueTask();
                    }

                    @Override
                    public void getMFACode(MultiFactorAuthenticationContinuation continuation) {

                    }

                    @Override
                    public void authenticationChallenge(ChallengeContinuation continuation) {

                    }

                    @Override
                    public void onFailure(Exception exception) {
                        Log.d(TAG, "onFailure");
                        exception.printStackTrace();
                    }
                }
        );
    }
}