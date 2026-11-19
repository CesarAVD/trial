package com.simplealarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Rings one alarm of the wake window. Intensity f = index/(count-1) in [0,1]
 * drives the whole profile (see Profile). Final ring forces alarm volume to max.
 */
public class AlarmService extends Service {
    static final String ACTION_RING = "com.simplealarm.RING";
    /** Silence this ring; later rings still come. */
    static final String ACTION_STOP = "com.simplealarm.STOP";
    /** Cancel the rest of this window. */
    static final String ACTION_AWAKE = "com.simplealarm.AWAKE";
    /** Broadcast to RingActivity to close itself. */
    static final String ACTION_STOPPED = "com.simplealarm.STOPPED";

    private static final String CHANNEL_ID = "alarm_ringing";
    private static final int NOTIF_ID = 42;
    private static final long FADE_TICK_MS = 250;

    /** Escalation profile as a pure function of intensity f. */
    static final class Profile {
        final float startVol, targetVol;   // MediaPlayer gain, relative to alarm-stream volume
        final long fadeMs;                 // ramp from startVol to targetVol
        final long ringMs;                 // auto-silence after this
        final int vibAmp;                  // 0 = no vibration, 1..255
        final long[] vibTimings;
        final boolean forceMaxStream;

        Profile(float f, boolean last) {
            targetVol = 0.15f + 0.85f * f;                 // ~-16 dB -> 0 dB
            startVol = targetVol * 0.2f;
            fadeMs = (long) (30_000 * (1 - f)) + 3_000;    // 33 s -> 3 s
            ringMs = last ? 10 * 60_000L                   // final: ring 10 min
                          : 60_000L + (long) (120_000 * f); // 1 -> 3 min (gap is 5 min)
            if (last || f >= 0.9f) {
                vibAmp = 255; vibTimings = new long[]{0, 1000, 300};
            } else if (f >= 0.6f) {
                vibAmp = 160; vibTimings = new long[]{0, 600, 900};
            } else if (f >= 0.25f) {
                vibAmp = 60;  vibTimings = new long[]{0, 300, 1700};
            } else {
                vibAmp = 0;   vibTimings = null;
            }
            forceMaxStream = last;
        }
    }

    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private AudioManager audio;
    private int savedStreamVol = -1;
    private float curVol, volStep, targetVol;
    private long windowEnd;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = this::finish;
    private final Runnable fadeTick = new Runnable() {
        @Override
        public void run() {
            if (player == null) return;
            curVol = Math.min(targetVol, curVol + volStep);
            player.setVolume(curVol, curVol);
            if (curVol < targetVol) handler.postDelayed(this, FADE_TICK_MS);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_RING.equals(action)) {
            start(intent);
        } else if (ACTION_AWAKE.equals(action)) {
            WakeWindow w = WakeWindow.load(this);
            w.dismissedUntil = Math.max(windowEnd, System.currentTimeMillis());
            w.save(this);
            AlarmScheduler.scheduleNext(this);
            finish();
        } else if (ACTION_STOP.equals(action)) {
            finish();
        } else {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void start(Intent intent) {
        stopRinging();                       // a newer ring replaces a still-running one
        int index = intent.getIntExtra(AlarmScheduler.EXTRA_INDEX, 0);
        int count = intent.getIntExtra(AlarmScheduler.EXTRA_COUNT, 1);
        windowEnd = intent.getLongExtra(AlarmScheduler.EXTRA_WINDOW_END, 0);
        boolean last = index >= count - 1;
        Profile p = new Profile(WakeWindow.intensity(index, count), last);

        Notification n = buildNotification(index, count, last);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }

        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimpleAlarm:ring");
        wakeLock.acquire(p.ringMs + 60_000L);

        audio = getSystemService(AudioManager.class);
        if (p.forceMaxStream) {
            try {
                savedStreamVol = audio.getStreamVolume(AudioManager.STREAM_ALARM);
                audio.setStreamVolume(AudioManager.STREAM_ALARM,
                        audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            } catch (SecurityException e) {
                savedStreamVol = -1;
            }
        }

        playSound(p);
        vibrate(p);
        handler.postDelayed(autoStop, p.ringMs);
    }

    private Notification buildNotification(int index, int count, boolean last) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Ringing alarms",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);       // we play sound ourselves on the alarm stream
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setBypassDnd(true);
        nm.createNotificationChannel(ch);

        Intent ring = new Intent(this, RingActivity.class)
                .putExtra(AlarmScheduler.EXTRA_INDEX, index)
                .putExtra(AlarmScheduler.EXTRA_COUNT, count)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent fullScreen = PendingIntent.getActivity(this, 1, ring,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(last ? "Last alarm" : "Wake up")
                .setContentText("Alarm " + (index + 1) + " of " + count)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setFullScreenIntent(fullScreen, true)
                .setContentIntent(fullScreen)
                .addAction(new Notification.Action.Builder(null, "I'm up",
                        servicePi(ACTION_AWAKE, 3)).build());
        if (!last) {
            b.addAction(new Notification.Action.Builder(null, "Stop",
                    servicePi(ACTION_STOP, 2)).build());
        }
        return b.build();
    }

    private PendingIntent servicePi(String action, int req) {
        Intent i = new Intent(this, AlarmService.class).setAction(action);
        return PendingIntent.getService(this, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void playSound(Profile p) {
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            curVol = p.startVol;
            targetVol = p.targetVol;
            volStep = (p.targetVol - p.startVol) / Math.max(1, p.fadeMs / FADE_TICK_MS);
            player.setVolume(curVol, curVol);
            player.start();
            handler.postDelayed(fadeTick, FADE_TICK_MS);
        } catch (Exception e) {
            if (player != null) player.release();
            player = null;
        }
    }

    private void vibrate(Profile p) {
        if (p.vibTimings == null) return;
        vibrator = getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        VibrationEffect effect;
        if (vibrator.hasAmplitudeControl()) {
            int[] amps = new int[p.vibTimings.length];
            for (int i = 1; i < amps.length; i += 2) amps[i] = p.vibAmp;   // odd slots = on
            effect = VibrationEffect.createWaveform(p.vibTimings, amps, 0);
        } else {
            effect = VibrationEffect.createWaveform(p.vibTimings, 0);
        }
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM).build();
        vibrator.vibrate(effect, aa);
    }

    private void stopRinging() {
        handler.removeCallbacks(autoStop);
        handler.removeCallbacks(fadeTick);
        if (player != null) {
            try { player.stop(); } catch (IllegalStateException ignored) { }
            player.release();
            player = null;
        }
        if (vibrator != null) vibrator.cancel();
        vibrator = null;
        if (savedStreamVol >= 0 && audio != null) {
            try {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, savedStreamVol, 0);
            } catch (SecurityException ignored) { }
        }
        savedStreamVol = -1;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    private void finish() {
        stopRinging();
        sendBroadcast(new Intent(ACTION_STOPPED).setPackage(getPackageName()));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopRinging();
        super.onDestroy();
    }
}
