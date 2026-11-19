package com.simplealarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
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

/** Foreground service that rings + vibrates until dismissed, snoozed, or timed out. */
public class AlarmService extends Service {
    static final String ACTION_START = "com.simplealarm.START";
    static final String ACTION_DISMISS = "com.simplealarm.DISMISS";
    static final String ACTION_SNOOZE = "com.simplealarm.SNOOZE";
    /** Broadcast to RingActivity to close itself. */
    static final String ACTION_STOPPED = "com.simplealarm.STOPPED";

    static final int SNOOZE_MINUTES = 10;
    private static final long AUTO_SILENCE_MS = 10 * 60_000L;
    private static final String CHANNEL_ID = "alarm_ringing";
    private static final int NOTIF_ID = 42;

    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private int currentId;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoSilence = () -> finish(true);

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_DISMISS.equals(action)) {
            finish(false);
        } else if (ACTION_SNOOZE.equals(action)) {
            finish(true);
        } else if (ACTION_START.equals(action)) {
            start(intent.getIntExtra(AlarmScheduler.EXTRA_ID, 0));
        } else {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void start(int id) {
        stopRinging();                 // a second alarm replaces the first
        currentId = id;
        Alarm a = AlarmStore.get(this, id);
        String label = a != null && !a.label.isEmpty() ? a.label : "Alarm";
        String time = a != null ? a.timeText() : "";

        Notification n = buildNotification(label, time);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }

        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimpleAlarm:ring");
        wakeLock.acquire(AUTO_SILENCE_MS + 60_000L);

        playSound();
        vibrate();
        handler.postDelayed(autoSilence, AUTO_SILENCE_MS);
    }

    private Notification buildNotification(String label, String time) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Ringing alarms",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);       // we play sound ourselves on the alarm stream
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setBypassDnd(true);
        nm.createNotificationChannel(ch);

        Intent ring = new Intent(this, RingActivity.class)
                .putExtra(AlarmScheduler.EXTRA_ID, currentId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        PendingIntent fullScreen = PendingIntent.getActivity(this, 1, ring,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(label)
                .setContentText(time)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setFullScreenIntent(fullScreen, true)
                .setContentIntent(fullScreen)
                .addAction(new Notification.Action.Builder(null, "Snooze",
                        servicePi(ACTION_SNOOZE, 2)).build())
                .addAction(new Notification.Action.Builder(null, "Dismiss",
                        servicePi(ACTION_DISMISS, 3)).build())
                .build();
    }

    private PendingIntent servicePi(String action, int req) {
        Intent i = new Intent(this, AlarmService.class).setAction(action);
        return PendingIntent.getService(this, req, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void playSound() {
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
            player.start();
        } catch (Exception e) {
            if (player != null) player.release();
            player = null;
        }
    }

    private void vibrate() {
        vibrator = getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        long[] pattern = {0, 800, 600};
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM).build();
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0), aa);
    }

    private void stopRinging() {
        handler.removeCallbacks(autoSilence);
        if (player != null) {
            try { player.stop(); } catch (IllegalStateException ignored) { }
            player.release();
            player = null;
        }
        if (vibrator != null) vibrator.cancel();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    private void finish(boolean snooze) {
        if (snooze && currentId != 0) AlarmScheduler.snooze(this, currentId, SNOOZE_MINUTES);
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
