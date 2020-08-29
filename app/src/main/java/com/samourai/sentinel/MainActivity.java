package com.samourai.sentinel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.invertedx.torservice.TorProxyManager;
import com.samourai.sentinel.network.dojo.DojoUtil;
import com.samourai.sentinel.tor.TorManager;
import com.samourai.sentinel.util.AppUtil;
import com.samourai.sentinel.util.ConnectivityStatus;
import com.samourai.sentinel.util.PrefsUtil;

import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.json.JSONException;

import java.io.IOException;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends Activity {

    private CompositeDisposable compositeDisposables = new CompositeDisposable();

    private TextView loaderTxView;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        loaderTxView = findViewById(R.id.loader_text);

        if (PrefsUtil.getInstance(MainActivity.this).getValue(PrefsUtil.TESTNET, false)) {
            SamouraiSentinel.getInstance().setCurrentNetworkParams(TestNet3Params.get());
        }


        if (AppUtil.getInstance(MainActivity.this).isSideLoaded() && !(PrefsUtil.getInstance(MainActivity.this).getValue(PrefsUtil.XPUB, "").length() > 0 || SamouraiSentinel.getInstance(MainActivity.this).payloadExists())) {
            doSelectNet();
        } else {
            doMain();
        }

    }


    private void doMain() {
        Intent intent;
        intent = new Intent(MainActivity.this, MainActivity2.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        this.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        compositeDisposables.dispose();
    }

    private void doSelectNet() {

        AlertDialog.Builder dlg = new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.select_network)
                .setCancelable(false)
                .setPositiveButton(R.string.MainNet, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        dialog.dismiss();
                        PrefsUtil.getInstance(MainActivity.this).removeValue(PrefsUtil.TESTNET);
                        SamouraiSentinel.getInstance().setCurrentNetworkParams(MainNetParams.get());
                        doMain();

                    }
                })
                .setNegativeButton(R.string.TestNet, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        dialog.dismiss();
                        PrefsUtil.getInstance(MainActivity.this).setValue(PrefsUtil.TESTNET, true);
                        SamouraiSentinel.getInstance().setCurrentNetworkParams(TestNet3Params.get());
                        doMain();

                    }
                });
        if (!isFinishing()) {
            dlg.show();
        }

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i("onActivityResult", "DONE");
    }
}
