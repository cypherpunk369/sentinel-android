package com.samourai.sentinel.network.dojo;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import androidx.constraintlayout.widget.Group;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


import com.invertedx.torservice.TorProxyManager;
import com.samourai.sentinel.R;
import com.samourai.sentinel.SamouraiSentinel;
import com.samourai.sentinel.codescanner.CameraFragmentBottomSheet;
import com.samourai.sentinel.tor.TorManager;
import com.samourai.sentinel.tor.TorService;
import com.samourai.sentinel.util.PrefsUtil;

import org.json.JSONException;

import java.io.IOException;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class DojoConfigureBottomSheet extends BottomSheetDialogFragment {

    Button preOrder, connect;
    int SCAN_QR = 1200;
    private CompositeDisposable compositeDisposables = new CompositeDisposable();
    private ProgressBar dojoConnectProgress;
    private TextView progressStates;
    private Group btnGroup, progressGroup;
    private DojoConfigurationListener dojoConfigurationListener;
    private CameraFragmentBottomSheet cameraFragmentBottomSheet;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottomsheet_dojo_configure, null);
        connect = view.findViewById(R.id.connect_dojo);
        preOrder = view.findViewById(R.id.preorder_dojo);
        view.findViewById(R.id.close_dojo).setOnClickListener(view1 -> {
            this.dismiss();
        });
        progressGroup = view.findViewById(R.id.dojo_progress_group);
        btnGroup = view.findViewById(R.id.dojo_btn_group);
        progressStates = view.findViewById(R.id.dojo_progress_status_text);
        dojoConnectProgress = view.findViewById(R.id.dojo_connect_progress);
        dojoConnectProgress.setIndeterminate(false);
        dojoConnectProgress.setMax(100);
        connect.setOnClickListener(view1 -> {
            showConnectionAlert();
        });


        return view;
    }

    public void setDojoConfigurationListener(DojoConfigurationListener dojoConfigurationListener) {
        this.dojoConfigurationListener = dojoConfigurationListener;
    }

    @Override
    public void onStart() {
        super.onStart();

        View view = getView();
        if (view != null) {
            view.post(() -> {
                View parent = (View) view.getParent();
                Log.i("parent", String.valueOf(parent instanceof CoordinatorLayout));
                CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) (parent).getLayoutParams();
                CoordinatorLayout.Behavior behavior = params.getBehavior();
                BottomSheetBehavior bottomSheetBehavior = (BottomSheetBehavior) behavior;
                if (bottomSheetBehavior != null) {
                    bottomSheetBehavior.setPeekHeight(view.getMeasuredHeight());
                }

            });
        }
    }

    private void showConnectionAlert() {

        Dialog dialog = new Dialog(getActivity(), android.R.style.Theme_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dojo_connect_dialog);
        dialog.setCanceledOnTouchOutside(true);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        dialog.setCanceledOnTouchOutside(false);

        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        dialog.show();

        dialog.findViewById(R.id.dojo_scan_qr).setOnClickListener(view -> {
            dialog.dismiss();
            cameraFragmentBottomSheet = new CameraFragmentBottomSheet();
            cameraFragmentBottomSheet.show(getActivity().getSupportFragmentManager(), cameraFragmentBottomSheet.getTag());
            cameraFragmentBottomSheet.setQrCodeScanLisenter(code -> {
                cameraFragmentBottomSheet.dismiss();
                this.connectToDojo(code);
            });

        });

//        dialog.findViewById(R.id.dojo_paste_config).setVisibility(View.GONE);

        dialog.findViewById(R.id.dojo_paste_config).setOnClickListener(view -> {

            try {
                ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                connectToDojo(item.getText().toString());
            } catch (Exception e) {
                e.printStackTrace();
            }

            dialog.dismiss();

        });


    }

    private void connectToDojo(String dojoParams) {
        btnGroup.setVisibility(View.INVISIBLE);
        progressGroup.setVisibility(View.VISIBLE);
        dojoConnectProgress.setProgress(30);
        if (TorManager.getInstance(getActivity().getApplicationContext()).isConnected()) {
            dojoConnectProgress.setProgress(60);
            progressStates.setText("Tor Connected, Connecting to Dojo Node...");
            DojoUtil.getInstance(getActivity().getApplicationContext()).clear();
            doPairing(dojoParams);
        } else {
            progressStates.setText("Waiting for Tor...");
            Intent startIntent = new Intent(getActivity().getApplicationContext(), TorService.class);
            startIntent.setAction(TorService.START_SERVICE);
            getActivity().startService(startIntent);
            Disposable disposable = TorManager.getInstance(getActivity().getApplicationContext())
                    .getTorStatus()
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(state -> {
                        if (state == TorProxyManager.ConnectionStatus.CONNECTING) {
                            progressStates.setText("Waiting for Tor...");
                        } else if (state == TorProxyManager.ConnectionStatus.CONNECTED) {
                            PrefsUtil.getInstance(getActivity()).setValue(PrefsUtil.ENABLE_TOR, true);
                            dojoConnectProgress.setProgress(60);
                            progressStates.setText("Tor Connected, Connecting to Dojo Node...");
                            DojoUtil.getInstance(getActivity().getApplicationContext()).clear();
                            doPairing(dojoParams);
                        }
                    });
            compositeDisposables.add(disposable);
        }
    }

    private void doPairing(String params) {

        Disposable disposable = DojoUtil.getInstance(getContext()).setDojoParams(params)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(aBoolean -> {
                    progressStates.setText("Successfully connected to Dojo Node");
                    if (this.dojoConfigurationListener != null) {
                        this.dojoConfigurationListener.onConnect();
                    }
                    dojoConnectProgress.setProgress(100);
                    new Handler().postDelayed(() -> {
                        Toast.makeText(getActivity(), "Successfully connected to Dojo", Toast.LENGTH_SHORT).show();
                        try {
                            SamouraiSentinel.getInstance(getContext()).serialize(SamouraiSentinel.getInstance(getContext()).toJSON(), null);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        dismissAllowingStateLoss();
                    }, 800);
                }, error -> {
                    error.printStackTrace();
                    if (this.dojoConfigurationListener != null) {
                        this.dojoConfigurationListener.onError();
                    }
                    progressStates.setText("Error Connecting node : ".concat(error.getMessage()));
                });
        compositeDisposables.add(disposable);

    }

    @Override
    public void onDestroy() {
        if (!compositeDisposables.isDisposed())
            compositeDisposables.dispose();
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

    }

    public interface DojoConfigurationListener {
        void onConnect();

        void onError();
    }
}