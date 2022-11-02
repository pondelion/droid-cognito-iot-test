package com.example.cognito_test;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cognitoidentity.AmazonCognitoIdentityClient;
import com.amazonaws.services.cognitoidentity.model.GetIdRequest;
import com.amazonaws.services.cognitoidentity.model.GetIdResult;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.model.AttachPolicyRequest;
import com.amazonaws.services.iot.model.AttachThingPrincipalRequest;
import com.amazonaws.services.iot.model.CreateThingRequest;
import com.amazonaws.services.iot.model.ListPrincipalThingsRequest;
import com.amazonaws.services.iot.model.ListPrincipalThingsResult;
import com.amazonaws.services.iot.model.ListThingsRequest;
import com.amazonaws.services.iot.model.ListThingsResult;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private Handler mHandler = new Handler();
    private AWSIotMqttManager mMQTTManager;
    private static final String TAG = "cognito-test";
    private Button mButton1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mButton1 = (Button)findViewById(R.id.button);

        mButton1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                (new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mMQTTManager.publishString("test message from android", "test_topic", AWSIotMqttQos.QOS0);
                    }
                })).start();
            }
        });

        (new Thread(new Runnable() {
            @Override
            public void run() {
                CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                        getApplicationContext(),
                        "ap-northeast-1:*******", // ID プールの ID
                        Regions.AP_NORTHEAST_1 // リージョン
                );

                String USER_POOL_ID = "cognito-idp.ap-northeast-1.amazonaws.com/ap-northeast-1_******";
                String ID_TOKEN = "eyJ*******";  // IDトークン(Cognitoのユーザーで認証してIDトークン取得済みとする)
                Map<String, String> logins = new HashMap<>();
                logins.put(USER_POOL_ID, ID_TOKEN);
                credentialsProvider.setLogins(logins);

                AWSIotClient client = new AWSIotClient(credentialsProvider);
                client.setRegion(Region.getRegion(Regions.AP_NORTHEAST_1));

                String thingName = "test_organization1_test_device2";  // 任意モノの名前。(cognitoのユーザー名と同じでOK)

                // モノがすでに作成済みか確認
                ListThingsRequest listThingsReq = new ListThingsRequest();
                ListThingsResult listThings = client.listThings(listThingsReq);
                AtomicBoolean thingExists = new AtomicBoolean(false);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    listThings.getThings().forEach(i -> {
                                Log.d("DeviceList", i.getThingName());
                                if (i.getThingName() == thingName) {
                                    thingExists.set(true);
                                }
                            }
                    );
                }
                // モノが作成されていない場合は作成
                if (!thingExists.get()) {
                    CreateThingRequest thingReq = new CreateThingRequest();
                    thingReq.setThingName(thingName);
                    client.createThing(thingReq);
                    Log.d(TAG, "created things : " + thingName);
                }

                // Identity IDの取得
                String IDENTITY_POOL_ID = "ap-northeast-1:***********";
                GetIdRequest getIdReq = new GetIdRequest();
                getIdReq.setLogins(credentialsProvider.getLogins());
                getIdReq.setIdentityPoolId(IDENTITY_POOL_ID);
                AmazonCognitoIdentityClient cognitoIdentity = new AmazonCognitoIdentityClient(credentialsProvider);
                cognitoIdentity.setRegion(Region.getRegion(Regions.AP_NORTHEAST_1));
                GetIdResult getIdRes = cognitoIdentity.getId(getIdReq);
                Log.d(TAG, getIdRes.getIdentityId());

                // プリンシパル(Identity ID)がモノに割り当てられているか確認
                ListPrincipalThingsRequest listPrincipalThingsReq = new ListPrincipalThingsRequest();
                listPrincipalThingsReq.setPrincipal(getIdRes.getIdentityId());
                ListPrincipalThingsResult listPrincipalThings = client.listPrincipalThings(listPrincipalThingsReq);
                AtomicBoolean principalAttached = new AtomicBoolean(false);
                listPrincipalThings.getThings().forEach((v) -> {
                    if (v == thingName) {
                        principalAttached.set(true);
                    }
                });

                Log.d(TAG, principalAttached.toString());

                // プリンシパル(Identity ID)が割り当てられていない場合はモノにプリンシパルを割り当てる
                String POLICY_NAME = "test_policy_name";  // すでにポリシーは作成済みとする
                if (!principalAttached.get()) {
                    AttachPolicyRequest policyReq = new AttachPolicyRequest();
                    policyReq.setPolicyName(POLICY_NAME);
                    policyReq.setTarget(getIdRes.getIdentityId());
                    client.attachPolicy(policyReq); // ポリシーをIdentity IDに割り当てる

                    AttachThingPrincipalRequest principalReq = new AttachThingPrincipalRequest();
                    principalReq.setPrincipal(getIdRes.getIdentityId()); // プリンシパル（Cognito ID）
                    principalReq.setThingName(thingName); // モノの名前
                    client.attachThingPrincipal(principalReq); // プリンシパルをモノに割り当てる
                    Log.d(TAG, "Attached princepal to thing");
                }

                String IOT_ENDPOINT = "********-ats.iot.ap-northeast-1.amazonaws.com";
                mMQTTManager = new AWSIotMqttManager(thingName, IOT_ENDPOINT);
                mMQTTManager.setKeepAlive(10);

                mMQTTManager.connect(
                    (AWSCredentialsProvider)credentialsProvider, new AWSIotMqttClientStatusCallback() {
                        @Override
                        public void onStatusChanged(AWSIotMqttClientStatus status, Throwable throwable) {
                            Log.d(TAG, "AWSIotMqttClientStatus changed." + status);
                        }
                    });
            }
        })).start();
    }
}