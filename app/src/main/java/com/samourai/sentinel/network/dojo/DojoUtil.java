package com.samourai.sentinel.network.dojo;

import android.content.Context;
import android.util.Log;

import com.samourai.sentinel.SamouraiSentinel;
import com.samourai.sentinel.api.APIFactory;
import com.samourai.sentinel.util.LogUtil;
import com.samourai.sentinel.util.WebUtil;

import org.json.JSONException;
import org.json.JSONObject;

import io.reactivex.Observable;

public class DojoUtil {

    private static String dojoParams = null;
    private static DojoUtil instance = null;
    private static final String TAG = "DojoUtil";
    private static Context context = null;

    private DojoUtil() {
        ;
    }

    public static DojoUtil getInstance(Context ctx) {

        context = ctx;

        if (instance == null) {
            instance = new DojoUtil();
        }

        return instance;
    }

    public void clear() {
        dojoParams = null;
    }

    public boolean isValidPairingPayload(String data) {

        try {
            JSONObject obj = new JSONObject(data);

            if (obj.has("pairing")) {

                JSONObject pObj = obj.getJSONObject("pairing");
                if (pObj.has("type") && pObj.has("version") && pObj.has("apikey") && pObj.has("url")) {
                    return true;
                } else {
                    return false;
                }

            } else {
                return false;
            }
        } catch (JSONException je) {
            return false;
        }

    }

    public String getDojoParams() {
        return dojoParams;
    }

    public synchronized Observable<Boolean> setDojoParams(String dojoParams) {
        return Observable.fromCallable(() -> {
            DojoUtil.dojoParams = dojoParams;
            String url = getUrl(dojoParams);
            if (url.charAt(url.length() - 1) != '/') {
                url = url + "/";
                JSONObject obj = new JSONObject(dojoParams);
                if (obj.has("pairing") && obj.getJSONObject("pairing").has("url")) {
                    obj.getJSONObject("pairing").put("url", url);
                    DojoUtil.dojoParams = obj.toString();
                }
            }
            if (SamouraiSentinel.getInstance().isTestNet()) {
                WebUtil.SAMOURAI_API2_TESTNET_TOR = url;
            } else {
                WebUtil.SAMOURAI_API2_TOR = url;
            }

            String apiToken = getApiKey(dojoParams);
            APIFactory.getInstance(context).setAppToken(apiToken);
            try {
                APIFactory.getInstance(context).getToken(true);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return true;
        });
    }

    public synchronized void removeDojoParams() {
        DojoUtil.dojoParams = null;

        if (SamouraiSentinel.getInstance().isTestNet()) {
            WebUtil.SAMOURAI_API2_TESTNET_TOR = WebUtil.SAMOURAI_API2_TESTNET_TOR_DIST;
        } else {
            WebUtil.SAMOURAI_API2_TOR = WebUtil.SAMOURAI_API2_TOR_DIST;
        }

        APIFactory.getInstance(context).setAppToken(null);
        APIFactory.getInstance(context).getToken(false);

    }

    public String getVersion(String data) {

        if (!isValidPairingPayload(data)) {
            return null;
        }

        try {
            JSONObject obj = new JSONObject(data);
            JSONObject pObj = obj.getJSONObject("pairing");
            return pObj.getString("version");
        } catch (JSONException je) {
            return null;
        }

    }

    public String getApiKey(String data) {

        if (!isValidPairingPayload(data)) {
            return null;
        }

        try {
            JSONObject obj = new JSONObject(data);
            JSONObject pObj = obj.getJSONObject("pairing");
            return pObj.getString("apikey");
        } catch (JSONException je) {
            return null;
        }

    }

    public boolean isDojoEnabled() {
        Log.i("CALLED", ":CcccccLL");

        return getDojoParams() != null;
    }

    public String getUrl(String data) {

        if (!isValidPairingPayload(data)) {
            return null;
        }

        try {
            JSONObject obj = new JSONObject(data);
            JSONObject pObj = obj.getJSONObject("pairing");
            return pObj.getString("url");
        } catch (JSONException je) {
            return null;
        }

    }

    public JSONObject toJSON() {

        JSONObject obj = null;

        try {
            if (dojoParams != null) {
                obj = new JSONObject(dojoParams);
            } else {
                obj = new JSONObject();
            }
        } catch (JSONException je) {
            ;
        }

        return obj;
    }

    public void fromJSON(JSONObject obj) {
        Log.i("CALLED", ":CLL");
        if (isValidPairingPayload(obj.toString())) {

            dojoParams = obj.toString();

            if (dojoParams != null) {

                if (SamouraiSentinel.getInstance().isTestNet()) {
                    WebUtil.SAMOURAI_API2_TESTNET_TOR = getUrl(dojoParams);
                } else {
                    WebUtil.SAMOURAI_API2_TOR = getUrl(dojoParams);
                }

                String apiToken = getApiKey(dojoParams);
                APIFactory.getInstance(context).setAppToken(apiToken);

            }

        }

    }

}
