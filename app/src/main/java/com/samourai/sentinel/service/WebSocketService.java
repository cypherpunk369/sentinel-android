package com.samourai.sentinel.service;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import com.invertedx.torservice.TorProxyManager;
import com.samourai.sentinel.SamouraiSentinel;
import com.samourai.sentinel.network.dojo.DojoUtil;
import com.samourai.sentinel.tor.TorManager;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class WebSocketService extends JobService {

    private Context context = null;

    private Timer timer = new Timer();
    private static final long checkIfNotConnectedDelay = 15000L;
    private WebSocketHandler webSocketHandler = null;
    private final Handler handler = new Handler();
    private String[] addrs = null;

    public static List<String> addrSubs = null;

    @Override
    public void onCreate() {

        super.onCreate();


    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }


    @Override
    public boolean onStartJob(JobParameters params) {
        //
        context = this.getApplicationContext();

        if(TorManager.getInstance(context).isConnected()){
            return false;
        }

        List<String> addrSubs = SamouraiSentinel.getInstance(WebSocketService.this).getAllAddrsSorted();
        addrs = addrSubs.toArray(new String[addrSubs.size()]);

        if (addrs.length == 0) {
        } else {
            webSocketHandler = new WebSocketHandler(WebSocketService.this, addrs);
            connectToWebsocketIfNotConnected();

            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            connectToWebsocketIfNotConnected();
                        }
                    });
                }
            }, 5000, checkIfNotConnectedDelay);
        }

        //Job is not completed so e
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        stop();
        return true;
    }

    public void connectToWebsocketIfNotConnected() {
        try {
            if (!webSocketHandler.isConnected()) {
                webSocketHandler.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        try {
            if (webSocketHandler != null) {
                webSocketHandler.stop();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }


    public static void startJob(Context mContext) {
        JobScheduler mJobScheduler = (JobScheduler) mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo jobInfo = new JobInfo.Builder(12, new ComponentName(mContext, WebSocketService.class))
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        if (mJobScheduler != null) {
            mJobScheduler.schedule(jobInfo);
        }
    } public static void stopJobs(Context mContext) {
        JobScheduler obScheduler = (JobScheduler) mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE );
        if (obScheduler != null) {
            obScheduler.cancelAll();
        }
    }
}