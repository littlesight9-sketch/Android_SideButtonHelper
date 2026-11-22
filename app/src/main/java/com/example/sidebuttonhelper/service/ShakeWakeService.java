package com.example.sidebuttonhelper.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.sidebuttonhelper.MainActivity;
import com.example.sidebuttonhelper.settings.SettingsPrefs;

public class ShakeWakeService extends Service implements SensorEventListener {

    private static final String CHANNEL_ID = "shake_wake_channel";
    private static final int NOTIFICATION_ID = 1001;

    // Default sensitivity. The Settings slider (1.5 - 4.0) overrides this and drives
    // both gestures, so users on phones with a noisy accelerometer (realme C21Y and
    // other entry-level Go-edition devices) can tune it on the actual hardware.
    private static final float DEFAULT_SHAKE_THRESHOLD = 2.5f;
    private static final float MIN_SHAKE_THRESHOLD = 1.5f;
    private static final float MAX_SHAKE_THRESHOLD = 4.0f;

    private static final int SHAKE_WINDOW_MS = 1000;
    private static final int REQUIRED_SHAKES = 2;
    private static final int WAKE_DEBOUNCE_MS = 1500;

    // Double-tap-to-wake. While the screen is off, taps on the glass are never
    // delivered to apps, but a tap on the phone body still moves the accelerometer.
    // Two sharp spikes close together are treated as a double tap.
    private static final int REQUIRED_TAPS = 2;
    private static final float TAP_GRAVITY_RATIO = 0.52f;  // 2.5 -> 1.3 (resting = 1.0)
    private static final float TAP_RISE_RATIO = 0.12f;     // 2.5 -> 0.3 jump in one sample
    private static final float TAP_MIN_GRAVITY_FLOOR = 1.1f;
    private static final float TAP_MIN_RISE_FLOOR = 0.12f;
    private static final long TAP_MIN_GAP_MS = 80;   // ignore the same spike sampled twice
    private static final long TAP_MAX_GAP_MS = 700;  // two taps must land within this gap

    private static final long SENSITIVITY_REFRESH_MS = 2000;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private PowerManager.WakeLock cpuWakeLock;

    private int shakeCount = 0;
    private long lastShakeTime = 0;
    private long lastWakeTriggerTime = 0;

    private float previousGForce = SensorManager.GRAVITY_EARTH;
    private int tapCount = 0;
    private long lastTapTime = 0;

    private float shakeThreshold = DEFAULT_SHAKE_THRESHOLD;
    private float tapMinGravity = DEFAULT_SHAKE_THRESHOLD * TAP_GRAVITY_RATIO;
    private float tapMinRiseGravity = DEFAULT_SHAKE_THRESHOLD * TAP_RISE_RATIO;
    private long lastSensitivityRefresh = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // Without this, the CPU sleeps when the screen is off and accelerometer events
        // stop arriving entirely - shake detection would silently never fire.
        // This is the real battery-cost tradeoff of screen-off shake detection.
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            cpuWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "SideButtonHelper:ShakeDetectionCpuLock");
            cpuWakeLock.setReferenceCounted(false);
            cpuWakeLock.acquire();
        }

        startForeground(NOTIFICATION_ID, buildNotification());

        applySensitivity();
        startSensing();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // The system can restart a sticky service without calling onCreate's sensor
        // setup again in edge cases, so make sure listening is always live.
        applySensitivity();
        startSensing();
        return START_STICKY; // ask the OS to restart this service if it gets killed
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Registers the accelerometer without batching and asks for the wake-up variant
     * first. On low-end phones (realme C21Y, Go edition) the sensor hub otherwise
     * batches samples while the screen is off and the event that should wake the
     * phone arrives too late, or not at all during Doze.
     */
    private void startSensing() {
        if (sensorManager == null) return;

        if (accelerometer == null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true);
            if (accelerometer == null) {
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
        }
        if (accelerometer == null) {
            // No accelerometer at all - run as a normal foreground service and let
            // the user wake the phone some other way, instead of crashing.
            return;
        }

        sensorManager.unregisterListener(this);
        // maxReportLatencyUs = 0 -> deliver every sample immediately, no batching.
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME, 0);
    }

    /** Reads the sensitivity slider and derives the shake and double-tap thresholds. */
    private void applySensitivity() {
        float threshold = SettingsPrefs.getShakeThreshold(this);
        if (threshold < MIN_SHAKE_THRESHOLD) threshold = MIN_SHAKE_THRESHOLD;
        if (threshold > MAX_SHAKE_THRESHOLD) threshold = MAX_SHAKE_THRESHOLD;

        shakeThreshold = threshold;
        tapMinGravity = Math.max(TAP_MIN_GRAVITY_FLOOR, threshold * TAP_GRAVITY_RATIO);
        tapMinRiseGravity = Math.max(TAP_MIN_RISE_FLOOR, threshold * TAP_RISE_RATIO);
        lastSensitivityRefresh = SystemClock.elapsedRealtime();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float gX = event.values[0] / SensorManager.GRAVITY_EARTH;
        float gY = event.values[1] / SensorManager.GRAVITY_EARTH;
        float gZ = event.values[2] / SensorManager.GRAVITY_EARTH;
        float gForce = (float) Math.sqrt(gX * gX + gY * gY + gZ * gZ);
        long now = SystemClock.elapsedRealtime();

        // Pick up Settings changes without a listener/lifecycle to manage.
        if (now - lastSensitivityRefresh > SENSITIVITY_REFRESH_MS) {
            applySensitivity();
        }

        detectDoubleTap(gForce, now);
        previousGForce = gForce;

        if (gForce > shakeThreshold) {
            shakeCount = (now - lastShakeTime > SHAKE_WINDOW_MS) ? 1 : shakeCount + 1;
            lastShakeTime = now;

            if (shakeCount >= REQUIRED_SHAKES) {
                shakeCount = 0;
                wakeIfAsleep(now);
            }
        }
    }

    /**
     * Counts sharp accelerometer spikes as taps and wakes the screen on a double tap.
     * A firm tap on the phone body shows up as one quick spike; smooth movement such
     * as walking or shaking does not rise far enough in a single sample.
     */
    private void detectDoubleTap(float gForce, long now) {
        boolean sharpSpike = gForce >= tapMinGravity
                && (gForce - previousGForce) >= tapMinRiseGravity;
        if (!sharpSpike) return;

        // The same physical tap can span two sensor samples - don't count it twice.
        if (tapCount > 0 && now - lastTapTime < TAP_MIN_GAP_MS) return;

        tapCount = (tapCount > 0 && now - lastTapTime <= TAP_MAX_GAP_MS) ? tapCount + 1 : 1;
        lastTapTime = now;

        if (tapCount >= REQUIRED_TAPS) {
            tapCount = 0;
            wakeIfAsleep(now);
        }
    }

    private void wakeIfAsleep(long now) {
        if (!SettingsPrefs.isTapWakeEnabled(this)) return;

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null || powerManager.isInteractive()) return;
        if (now - lastWakeTriggerTime <= WAKE_DEBOUNCE_MS) return;

        lastWakeTriggerTime = now;
        wakeScreen();
    }

    @SuppressWarnings("deprecation")
    private void wakeScreen() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) return;

        // SCREEN_BRIGHT_WAKE_LOCK is deprecated but remains the only reliable way
        // to turn the screen on from a background service with no visible Activity.
        // It is honoured on realme UI / ColorOS as long as the app is exempt from
        // battery optimization and allowed to auto-start.
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | PowerManager.ON_AFTER_RELEASE,
                "SideButtonHelper:ShakeWakeLock");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(3000);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // not used
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Shake to Wake", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Side Button Helper active")
                .setContentText("Shake or double-tap your phone to wake the screen")
                .setSmallIcon(android.R.drawable.ic_lock_power_off)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (cpuWakeLock != null && cpuWakeLock.isHeld()) {
            cpuWakeLock.release();
        }
    }
}
