package com.applozic.audiovideo.authentication;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.applozic.mobicomkit.api.HttpRequestUtils;
import com.applozic.mobicomkit.api.MobiComKitClientService;
import com.applozic.mobicomkit.api.account.user.MobiComUserPreference;
import com.applozic.mobicomkit.api.account.user.User;
import com.applozic.mobicommons.commons.core.utils.Utils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by devashish on 30/07/16.
 */
public class TokenGenClientService extends MobiComKitClientService {

    private static final String TAG = "TokenGenClientService";

    public TokenGenClientService(Context context) {
        super.context = context;
    }

    //Todo: Take url based on env.
    public String getUrl() {
        return getBaseUrl() + "/twilio/token";
    }

    public String getGeneratedToken() {

        try {

            MobiComUserPreference pref = MobiComUserPreference.getInstance(context);
            String identity = pref.getUserId();
            String device = pref.getDeviceKeyString();
            String data = "identity=" + identity + "&device=" + device;
            Log.i(TAG, getUrl());

            String response = new HttpRequestUtils(context).postData(getUrl(), "application/x-www-form-urlencoded", null, data);
            Log.i(TAG, response);
            return response;

        } catch (Exception e) {
            Log.i(TAG, " Exception ::", e);
            return null;
        }
    }
}
