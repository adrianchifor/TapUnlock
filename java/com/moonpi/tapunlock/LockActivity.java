package com.moonpi.tapunlock;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.os.Build;
import android.provider.MediaStore;
import android.view.GestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;


public class LockActivity extends Activity implements View.OnClickListener, View.OnTouchListener,
        GestureDetector.OnGestureListener {


    // Brightness int flags
    private static final int LOW_BRIGHTNESS = 0;
    private static final int MEDIUM_BRIGHTNESS = 80;
    private static final int HIGH_BRIGHTNESS = 255;

    private static final int SCREEN_TIMEOUT = 15000; // Screen timeout flag
    private static final int TIMEOUT_DELAY = 3000; // For getting system screen timeout

    private static final int PIN_LOCKED_RUNNABLE_DELAY = 30000; // Unlock PIN keypad after x milliseconds

    // Alarm check flags
    private static final String ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT";
    private static final String ALARM_SNOOZE_ACTION = "com.android.deskclock.ALARM_SNOOZE";
    private static final String ALARM_DISMISS_ACTION = "com.android.deskclock.ALARM_DISMISS";
    private static final String ALARM_DONE_ACTION = "com.android.deskclock.ALARM_DONE";

    private PendingIntent pIntent; // Pending intent for NFC Tag discovery

    private int flags; // Window flags

    // SurfaceHolder for flashlight
    private SurfaceHolder surfaceHolder;

    private WindowManager wm;
    private ActivityManager activityManager;
    private PackageManager packageManager;

    // Components for home launcher activity
    private ComponentName cnHome;
    private int componentDisabled, componentEnabled;

    private NfcAdapter nfcAdapter;
    private WifiManager wifiManager;
    private ConnectivityManager connManager;
    private NetworkInfo isMobileDataOn;
    private Vibrator vibrator;
    private AudioManager am;
    private Camera cam;
    private Parameters camParam;

    private Boolean vibratorAvailable = false; // True if device vibrator exists, false otherwise
    private Boolean flashlightAvailable = false; // True if FEATURE_FLASH exists, false otherwise
    private Boolean isFlashOn = false; // True if flash was turned on, false otherwise
    private ContentResolver cResolver; // Content resolver for system settings get and put
    private int brightnessMode = -1; // 0 (LOW), 1 (MEDIUM), 2(HIGH) and 3(AUTO)
    private int systemScreenTimeout = -1; // For getting default system screen timeout
    private int ringerMode; // 1 (NORMAL), 2 (VIBRATE) and 3 (SILENT)

    private int taskId;

    private String pinEntered = ""; // The PIN the user inputs
    private int pinAttempts = 5; // The number of attempts before PIN locked for 30s

    // For updating time and date values
    private Calendar calendar;

    // Layout items
    private View disableStatusBar;
    private TextView time, date, battery, unlockText, pinInput;
    private ImageButton wifi, data, flashlight, brightness, sound, phone, camera, up, down;
    private View toolboxFrame;

    // Phone and Camera open booleans
    private Boolean phoneToOpen = false;
    private Boolean cameraToOpen = false;

    // View animations
    protected Animation slideUp, slideDown, fadeIn, fadeOut;

    // Max number of times the launcher pick dialog toast should show
    private int launcherPickToast = 2;
    private Boolean isPhoneCalling = false; // True if phone state listener is ringing/offhook
    private Boolean isAlarmRinging = false; // True if alarm is ringing

    // For gesture detection
    private static final int SWIPE_MIN_DIST = 180;
    private GestureDetector gestureDetector;
    private Boolean swipedUp = false;
    private Boolean swipedDown = true;

    // Contents of JSON file
    private JSONObject root;
    private JSONObject settings;
    private JSONArray tags;
    private String pin;
    private Boolean pinLocked;
    private int blur;

    private Handler mHandler = new Handler();

    // Vibrate, set pinLocked to false, store and reset pinEntered
    protected Runnable pinLockedRunnable = new Runnable() {
        @Override
        public void run() {
            if (vibratorAvailable)
                vibrator.vibrate(150);

            if (nfcAdapter != null) {
                if (nfcAdapter.isEnabled())
                    unlockText.setText("");

                else
                    unlockText.setText(getResources().getString(R.string.scan_to_unlock_nfc_off));
            }

            else
                unlockText.setText(getResources().getString(R.string.scan_to_unlock_nfc_off));

            pinLocked = false;

            try {
                settings.put("pinLocked", false);

            } catch (JSONException e) {
                e.printStackTrace();
            }

            writeToJSON();

            pinEntered = "";
        }
    };


    // Broadcast receiver for time, date and battery changed
    private BroadcastReceiver mChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(Intent.ACTION_TIME_TICK))
                updateTime();

            else if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                updateBattery(level);
            }

            else if (action.equals(Intent.ACTION_DATE_CHANGED))
                updateDate();
        }
    };


    // Alarm broadcast receiver
    private BroadcastReceiver mAlarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // If alarm is ringing, move task to back
            if (action.equals(ALARM_ALERT_ACTION)) {
                isAlarmRinging = true;
                moveTaskToBack(true);
            }

            // If alarm dismissed or snoozed, move task to front and set 'isAlarmRinging' to false
            else if (action.equals(ALARM_DISMISS_ACTION) || action.equals(ALARM_DONE_ACTION) ||
                     action.equals(ALARM_SNOOZE_ACTION)) {
                if (isAlarmRinging) {
                    activityManager.moveTaskToFront(taskId, 0);
                    isAlarmRinging = false;
                }
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get screen density and calculate disableStatusBar view height
        float screenDensity = getResources().getDisplayMetrics().density;

        float disableStatusBar_height = screenDensity * 70;

        readFromJSON();

        // Activity window flags
        flags = WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

        disableStatusBar = new View(this);

        // DisableStatusBar view parameters
        WindowManager.LayoutParams handleParamsTop = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                (int) disableStatusBar_height,
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSPARENT);

        handleParamsTop.gravity = Gravity.TOP | Gravity.CENTER;

        // Initialize package manager and components
        packageManager = getPackageManager();
        componentEnabled = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        componentDisabled = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

        cnHome = new ComponentName(this, "com.moonpi.tapunlock.LockHome");

        // Enable home launcher activity component
        packageManager.setComponentEnabledSetting(cnHome, componentEnabled, PackageManager.DONT_KILL_APP);

        // Get NFC service and adapter
        NfcManager nfcManager = (NfcManager) this.getSystemService(Context.NFC_SERVICE);
        nfcAdapter = nfcManager.getDefaultAdapter();

        // If Android version less than 4.4, createPendingResult for NFC tag discovery
        if (Build.VERSION.SDK_INT < 19) {
            pIntent = PendingIntent.getActivity(
                    this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        }

        taskId = getTaskId();

        // Get window manager service, add the window flags and the disableStatusBar view
        wm = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
        this.getWindow().addFlags(flags);
        wm.addView(disableStatusBar, handleParamsTop);

        // If Android version 4.4 or bigger, add translucent status bar flag
        if (Build.VERSION.SDK_INT >= 19) {
            this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        // To listen for calls, so user can accept or deny a call
        StateListener phoneStateListener = new StateListener();
        TelephonyManager telephonyManager = (TelephonyManager) this.getSystemService(TELEPHONY_SERVICE);
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        activityManager = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

        // Check if device has vibrator and store true/false in var
        vibratorAvailable = vibrator.hasVibrator();

        setContentView(R.layout.activity_lock);

        // When disableStatusBar view touched, consume touch
        disableStatusBar.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        // If Android 4.1 or lower or blur is 0 or blurred wallpaper doesn't exist
        // Get default wallpaper and set as background drawable
        if (Build.VERSION.SDK_INT < 17 || blur == 0 || !ImageUtils.doesBlurredWallpaperExist()) {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
            Drawable wallpaperDrawable = wallpaperManager.peekFastDrawable();

            if (wallpaperDrawable != null)
                getWindow().setBackgroundDrawable(wallpaperDrawable);
        }

        // Otherwise, retrieve blurred wallpaper and set as background drawable
        else {
            Drawable blurredWallpaper = ImageUtils.retrieveWallpaperDrawable();

            if (blurredWallpaper != null)
                getWindow().setBackgroundDrawable(blurredWallpaper);

            else {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
                Drawable wallpaperDrawable = wallpaperManager.peekFastDrawable();

                if (wallpaperDrawable != null)
                    getWindow().setBackgroundDrawable(wallpaperDrawable);
            }
        }

        am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        connManager = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);

        // Get mobile data connection, to check whether connected or not
        isMobileDataOn = connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        RelativeLayout lockRelativeLayout = (RelativeLayout) findViewById(R.id.lockRelativeLayout);

        // If Android version 4.4 or bigger, set layout fits system windows to true
        if (Build.VERSION.SDK_INT >= 19) {
            lockRelativeLayout.setFitsSystemWindows(true);
        }

        // Check if system has flashlight feature and store true/false in var
        flashlightAvailable = getApplicationContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAMERA_FLASH);

        // Initialize surface view and holder for camera (flashlight) preview
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();

        cResolver = getContentResolver();

        // Get current system screen timeout and store in int
        systemScreenTimeout = Settings.System.getInt(cResolver, Settings.System.SCREEN_OFF_TIMEOUT,
                TIMEOUT_DELAY);

        // Set lockscreen screen timeout to 15s
        Settings.System.putInt(cResolver, Settings.System.SCREEN_OFF_TIMEOUT, SCREEN_TIMEOUT);

        // Initialize layout items
        time = (TextView)findViewById(R.id.time);
        date = (TextView)findViewById(R.id.date);
        battery = (TextView)findViewById(R.id.battery);
        unlockText = (TextView)findViewById(R.id.unlockText);
        pinInput = (TextView)findViewById(R.id.pinInput);
        ImageButton ic_0 = (ImageButton) findViewById(R.id.ic_0);
        ImageButton ic_1 = (ImageButton) findViewById(R.id.ic_1);
        ImageButton ic_2 = (ImageButton) findViewById(R.id.ic_2);
        ImageButton ic_3 = (ImageButton) findViewById(R.id.ic_3);
        ImageButton ic_4 = (ImageButton) findViewById(R.id.ic_4);
        ImageButton ic_5 = (ImageButton) findViewById(R.id.ic_5);
        ImageButton ic_6 = (ImageButton) findViewById(R.id.ic_6);
        ImageButton ic_7 = (ImageButton) findViewById(R.id.ic_7);
        ImageButton ic_8 = (ImageButton) findViewById(R.id.ic_8);
        ImageButton ic_9 = (ImageButton) findViewById(R.id.ic_9);
        toolboxFrame = findViewById(R.id.toolboxFrame);
        wifi = (ImageButton)findViewById(R.id.wifi);
        data = (ImageButton)findViewById(R.id.data);
        flashlight = (ImageButton)findViewById(R.id.flashlight);
        brightness = (ImageButton)findViewById(R.id.brightness);
        sound = (ImageButton)findViewById(R.id.sound);
        Button delete = (Button) findViewById(R.id.delete);
        phone = (ImageButton)findViewById(R.id.phone);
        camera = (ImageButton)findViewById(R.id.camera);
        up = (ImageButton)findViewById(R.id.up);
        down = (ImageButton)findViewById(R.id.down);

        // Set onClick listeners
        ic_0.setOnClickListener(this);
        ic_1.setOnClickListener(this);
        ic_2.setOnClickListener(this);
        ic_3.setOnClickListener(this);
        ic_4.setOnClickListener(this);
        ic_5.setOnClickListener(this);
        ic_6.setOnClickListener(this);
        ic_7.setOnClickListener(this);
        ic_8.setOnClickListener(this);
        ic_9.setOnClickListener(this);
        wifi.setOnClickListener(this);
        data.setOnClickListener(this);
        flashlight.setOnClickListener(this);
        brightness.setOnClickListener(this);
        sound.setOnClickListener(this);
        delete.setOnClickListener(this);
        phone.setOnClickListener(this);
        camera.setOnClickListener(this);
        up.setOnClickListener(this);
        down.setOnClickListener(this);

        gestureDetector = new GestureDetector(this, this);

        up.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (swipedUp)
                    return false;

                swipedUp = true;
                swipedDown = false;
                swipeUp();

                return true;
            }
        });

        down.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (swipedDown)
                    return false;

                swipedDown = true;
                swipedUp = false;
                swipeDown();

                return true;
            }
        });

        // Initialize animations
        slideDown = AnimationUtils.loadAnimation(this, R.anim.slide_down);
        slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);
        fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out);
        fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);

        // Initialize calendar and time/date/battery views
        calendar = Calendar.getInstance();

        updateTime();
        updateDate();
        updateBattery(getBatteryLevel());

        // Check system screen brightness/mode and set brightnessMode accordingly
        try {
            if (Settings.System.getInt(cResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                brightnessMode = 3;
                brightness.setImageDrawable(getResources().getDrawable(R.drawable.ic_bright_auto));
            }

            else {
                if (Settings.System.getInt(cResolver, Settings.System.SCREEN_BRIGHTNESS) >=
                        LOW_BRIGHTNESS && Settings.System.getInt(cResolver,
                        Settings.System.SCREEN_BRIGHTNESS) < MEDIUM_BRIGHTNESS) {

                    brightnessMode = 0;
                    brightness.setImageDrawable(getResources().getDrawable(R.drawable.ic_bright_0));
                }

                else if (Settings.System.getInt(cResolver, Settings.System.SCREEN_BRIGHTNESS) >=
                        MEDIUM_BRIGHTNESS && Settings.System.getInt(cResolver,
                        Settings.System.SCREEN_BRIGHTNESS) < HIGH_BRIGHTNESS) {

                    brightnessMode = 1;
                    brightness.setImageDrawable(getResources().getDrawable(R.drawable.ic_bright_1));
                }

                if (Settings.System.getInt(cResolver, Settings.System.SCREEN_BRIGHTNESS)
                        == HIGH_BRIGHTNESS) {

                    brightnessMode = 2;
                    brightness.setImageDrawable(getResources().getDrawable(R.drawable.ic_bright_2));
                }
            }

        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        // Create intent filter for alarm states and register alarm receiver
        IntentFilter alarmFilter = new IntentFilter(ALARM_ALERT_ACTION);
        alarmFilter.addAction(ALARM_DISMISS_ACTION);
        alarmFilter.addAction(ALARM_SNOOZE_ACTION);
        alarmFilter.addAction(ALARM_DONE_ACTION);
        this.registerReceiver(mAlarmReceiver, alarmFilter);

        // Create an intent filter with time/date/battery changes and register receiver
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_TIME_TICK);
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        intentFilter.addAction(Intent.ACTION_DATE_CHANGED);
        this.registerReceiver(mChangeReceiver, intentFilter);

        // If wifi enabled or not, set drawable accordingly
        if (wifiManager.isWifiEnabled())
            wifi.setImageDrawable(getResources().getDrawable(R.drawable.ic_wifi_on));

        else
            wifi.setImageDrawable(getResources().getDrawable(R.drawable.ic_wifi_off));

        // If mobile data not null and enabled or not, set drawable accordingly
        if (isMobileDataOn != null) {
            if (isMobileDataOn.isConnected())
                data.setImageDrawable(getResources().getDrawable(R.drawable.ic_data_on));

            else
                data.setImageDrawable(getResources().getDrawable(R.drawable.ic_data_off));
        }

        // Check ringer mode and set drawable/ringerMode var accordingly
        switch (am.getRingerMode()) {
            case AudioManager.RINGER_MODE_NORMAL:
                sound.setImageDrawable(getResources().getDrawable(R.drawable.ic_speaker));
                ringerMode = 1;
                break;

            case AudioManager.RINGER_MODE_VIBRATE:
                sound.setImageDrawable(getResources().getDrawable(R.drawable.ic_vibration));
                ringerMode = 2;
                break;

            case AudioManager.RINGER_MODE_SILENT:
                sound.setImageDrawable(getResources().getDrawable(R.drawable.ic_silent));
                ringerMode = 3;
                break;
        }

        // Check default launcher, if current package isn't the default one
        // Open the 'Select home app' dialog for user to pick default home launcher
        if (!isMyLauncherDefault()) {
            packageManager.clearPackagePreferredActivities(getPackageName());

            Intent launcherPicker = new Intent();
            launcherPicker.setAction(Intent.ACTION_MAIN);
            launcherPicker.addCategory(Intent.CATEGORY_HOME);
            startActivity(launcherPicker);

            if (launcherPickToast > 0) {
                launcherPickToast -= 1;

                Toast toast = Toast.makeText(getApplicationContext(),
                        R.string.toast_launcher_dialog,
                        Toast.LENGTH_LONG);
                toast.show();
            }
        }
    }


    // Write content to JSON file
    public void writeToJSON() {
        try {
            BufferedWriter bWrite = new BufferedWriter(new OutputStreamWriter
                    (openFileOutput("settings.json", Context.MODE_PRIVATE)));
            bWrite.write(root.toString());
            bWrite.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void readFromJSON() {
        // Read from JSON file
        try {
            BufferedReader bRead = new BufferedReader(new InputStreamReader
                    (openFileInput("settings.json")));
            root = new JSONObject(bRead.readLine());

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Read settings object from root
        try {
            settings = root.getJSONObject("settings");

        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Read required items from settings object
        try {
            pin = settings.getString("pin");
            pinLocked = settings.getBoolean("pinLocked");
            blur = settings.getInt("blur");
            tags = settings.getJSONArray("tags");

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    // Method to update time view
    public void updateTime() {
        calendar = Calendar.getInstance();
        String hourDay = String.valueOf(calendar.get(Calendar.HOUR_OF_DAY));

        String minuteDay = ":" + String.valueOf(calendar.get(Calendar.MINUTE));

        if (minuteDay.length() < 3)
            minuteDay = ":" + "0" + String.valueOf(calendar.get(Calendar.MINUTE));

        String timeFinal = hourDay + minuteDay;

        time.setText(timeFinal);
    }

    // Method to update date view
    public void updateDate() {
        calendar = Calendar.getInstance();
        String weekDay = String.valueOf(calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT,
                Locale.US));
        String month = " " + String.valueOf(calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT,
                Locale.US));
        String dateDay = " " + String.valueOf(calendar.get(Calendar.DATE));
        String dateFinal = weekDay + dateDay + month;

        date.setText(dateFinal);
    }

    // Method to update battery view
    public void updateBattery(int batteryLevel) {
        battery.setText(String.valueOf(batteryLevel) + "% " +
            getResources().getString(R.string.battery_text));

        // Set text color depending on battery level
        if (batteryLevel > 15)
            battery.setTextColor(getResources().getColor(R.color.light_green));

        else
            battery.setTextColor(getResources().getColor(R.color.light_red));
    }

    // Returns the current battery level
    public int getBatteryLevel() {
        Intent batteryIntent = getApplicationContext().registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        int level = -1;

        try {
            level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);

        } catch (NullPointerException npe) {
            npe.printStackTrace();
        }

        return level;
    }


    // Method to turn wifi on or off
    private void wifiOn(boolean enabled) {
        if (enabled) {
            wifiManager.setWifiEnabled(true);
            wifi.setImageDrawable(getResources().getDrawable(R.drawable.ic_wifi_on));
        }

        else {
            wifiManager.setWifiEnabled(false);
            wifi.setImageDrawable(getResources().getDrawable(R.drawable.ic_wifi_off));
        }
    }


    // Method to turn mobile data on or off
    private void mobileDataOn(boolean enabled) throws NoSuchMethodException,
            InvocationTargetException, IllegalAccessException {

        Method mobileDataMethod = ConnectivityManager.class.getDeclaredMethod(
                "setMobileDataEnabled", boolean.class);

        mobileDataMethod.setAccessible(true);
        mobileDataMethod.invoke(connManager, enabled);

        if (enabled)
            data.setImageDrawable(getResources().getDrawable(R.drawable.ic_data_on));

        else
            data.setImageDrawable(getResources().getDrawable(R.drawable.ic_data_off));
    }


    // Method to get camera and it's parameters
    private void getCamera() {
        if (cam == null) {
            try {
                cam = Camera.open();

                if (cam != null)
                    camParam = cam.getParameters();

            } catch (RuntimeException re) {
                re.printStackTrace();

                Toast toast = Toast.makeText(getApplicationContext(),
                        R.string.toast_camera_failed,
                        Toast.LENGTH_SHORT);
                toast.show();
            }
        }
    }

    // Method to stop camera preview and release
    private void releaseCamera() {
        if (cam != null) {
            try {
                cam.stopPreview();
                cam.release();

            } catch (Exception e) {
                e.printStackTrace();
            }

            cam = null;
        }
    }

    // Method to turn flashlight on or off
    private void flashlightOn(boolean enabled) {
        if (enabled) {
            getCamera();

            if (cam == null || camParam == null) {
                Toast toast = Toast.makeText(getApplicationContext(),
                        R.string.toast_camera_failed,
                        Toast.LENGTH_SHORT);
                toast.show();

                return;
            }

            try {
                if (surfaceHolder != null)
                    cam.setPreviewDisplay(surfaceHolder);

            } catch (IOException e) {
                e.printStackTrace();

                Toast toast = Toast.makeText(getApplicationContext(),
                        R.string.toast_camera_failed,
                        Toast.LENGTH_SHORT);
                toast.show();

                return;
            }

            camParam = cam.getParameters();
            camParam.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);

            try {
                cam.setParameters(camParam);

            } catch (RuntimeException re) {
                re.printStackTrace();

                Toast toast = Toast.makeText(getApplicationContext(),
                        R.string.toast_camera_failed,
                        Toast.LENGTH_SHORT);
                toast.show();

                return;
            }

            try {
                cam.startPreview();

            } catch (Exception e) {
                e.printStackTrace();

                Toast toast = Toast.makeText(getApplicationContext(),
                        R.string.toast_camera_failed,
                        Toast.LENGTH_SHORT);
                toast.show();

                return;
            }

            isFlashOn = true;
            flashlight.setImageDrawable(getResources().getDrawable(R.drawable.ic_flashlight_on));
        }

        // Turn flashlight off
        else {
            if (cam == null || camParam == null) {
                Toast toast = Toast.makeText(getApplicationContext(),
                        R.string.toast_camera_failed,
                        Toast.LENGTH_SHORT);
                toast.show();

                return;
            }

            camParam = cam.getParameters();
            camParam.setFlashMode(Parameters.FLASH_MODE_OFF);

            try {
                cam.setParameters(camParam);

            } catch (RuntimeException re) {
                re.printStackTrace();
            }

            flashlight.setImageDrawable(getResources().getDrawable(R.drawable.ic_flashlight_off));

            releaseCamera();
            isFlashOn = false;
        }
    }


    // 0 for '0', 1 for '80', 2 for '255' and 3 for auto
    // Method to change system brightness
    private void brightnessChange(int mode) {
        if (mode == 0) {
            if (brightness != null)
                brightness.setImageDrawable(getResources().getDrawable(R.drawable.ic_bright_0));

            if (cResolver != null) {
                Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

                Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS,
                        LOW_BRIGHTNESS);
            }
        }

        else if (mode == 1) {
            if (brightness != null)
                brightness.setImageDrawable(getResources().getDrawable(R.drawable.ic_bright_1));

            if (cResolver != null) {
                Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS,
                        MEDIUM_BRIGHTNESS);
            }
        }

        else if (mode == 2) {
            if (brightness != null)
                brightness.setImageDrawable(getResources().getDrawable(R.drawable.ic_bright_2));

            if (cResolver != null) {
                Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS,
                        HIGH_BRIGHTNESS);
            }
        }

        else if (mode == 3) {
            if (brightness != null)
                brightness.setImageDrawable(getResources().getDrawable(R.drawable.ic_bright_auto));

            if (cResolver != null) {
                Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
            }
        }
    }


    // 1 for 'normal', 2 for 'vibrate' and 3 for 'silent'
    // Method to change ringer mode
    private void ringerChange(int mode) {
        if (mode == 1) {
            sound.setImageDrawable(getResources().getDrawable(R.drawable.ic_speaker));
            am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }

        else if (mode == 2) {
            sound.setImageDrawable(getResources().getDrawable(R.drawable.ic_vibration));
            am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        }

        else if (mode == 3) {
            sound.setImageDrawable(getResources().getDrawable(R.drawable.ic_silent));
            am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        }
    }


    // Method to check whether the package is the default home launcher or not
    public boolean isMyLauncherDefault() {
        final IntentFilter launcherFilter = new IntentFilter(Intent.ACTION_MAIN);
        launcherFilter.addCategory(Intent.CATEGORY_HOME);

        List<IntentFilter> filters = new ArrayList<IntentFilter>();
        filters.add(launcherFilter);

        final String myPackageName = getPackageName();
        List<ComponentName> activities = new ArrayList<ComponentName>();

        packageManager.getPreferredActivities(filters, activities, "com.moonpi.tapunlock");

        for (ComponentName activity : activities) {
            if (myPackageName.equals(activity.getPackageName())) {
                return true;
            }
        }

        return false;
    }


    // Method called each time the user presses a keypad button
    public void checkPIN() {
        if (!pinLocked) {
            pinInput.setText(pinEntered);

            if (pinEntered.length() == pin.length()) {
                // If correct PIN entered, reset pinEntered, remove handle callbacks and messages,
                // Set home launcher activity component disabled and finish
                if (pinEntered.equals(pin)) {
                    pinEntered = "";
                    pinInput.setText(pinEntered);

                    mHandler.removeCallbacksAndMessages(null);

                    packageManager.setComponentEnabledSetting(cnHome, componentDisabled,
                            PackageManager.DONT_KILL_APP);

                    finish();
                    overridePendingTransition(0, 0);
                }

                // If incorrect PIN entered, reset drawable and pinEntered, lower pinAttempts
                // Vibrate and display 'Wrong PIN. Try again' for 1s
                else {
                    if (pinAttempts > 0) {
                        pinAttempts -= 1;

                        pinEntered = "";
                        pinInput.setText(pinEntered);

                        unlockText.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (pinAttempts >= 0) {
                                    if (nfcAdapter != null) {
                                        if (nfcAdapter.isEnabled())
                                            unlockText.setText("");

                                        else {
                                            unlockText.setText(getResources().getString(
                                                    R.string.scan_to_unlock_nfc_off));
                                        }
                                    }

                                    else {
                                        unlockText.setText(getResources().getString(
                                                R.string.scan_to_unlock_nfc_off));
                                    }
                                }
                            }
                        }, 1000);

                        if (vibratorAvailable)
                            vibrator.vibrate(250);

                        unlockText.setText(getResources().getString(R.string.wrong_pin));
                    }

                    // 0 attempts left, vibrate, reset pinEntered and pinAttempts,
                    // Set pinLocked to true, store and post reset PIN keypad runnable in 30s
                    else {
                        if (vibratorAvailable)
                            vibrator.vibrate(500);

                        if (nfcAdapter != null) {
                            if (nfcAdapter.isEnabled())
                                unlockText.setText(getResources().getString(R.string.pin_locked));

                            else
                                unlockText.setText(getResources().getString(R.string.pin_locked_nfc_off));
                        }

                        else
                            unlockText.setText(getResources().getString(R.string.pin_locked_nfc_off));


                        pinEntered = "";
                        pinInput.setText(pinEntered);

                        pinLocked = true;

                        pinAttempts = 5;

                        try {
                            settings.put("pinLocked", true);

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        writeToJSON();

                        mHandler.postDelayed(pinLockedRunnable, PIN_LOCKED_RUNNABLE_DELAY);
                    }
                }
            }
        }
    }


    @Override
    public void onClick(View v) {
        // If PIN keypad button pressed, add pressed number to pinEntered and call checkPin method
        if (v.getId() == R.id.ic_0) {
            if (pinEntered.length() < pin.length())
                pinEntered += "0";

            checkPIN();
        }

        else if (v.getId() == R.id.ic_1) {
            if (pinEntered.length() < pin.length())
                pinEntered += "1";

            checkPIN();
        }

        else if (v.getId() == R.id.ic_2) {
            if (pinEntered.length() < pin.length())
                pinEntered += "2";

            checkPIN();
        }

        else if (v.getId() == R.id.ic_3) {
            if (pinEntered.length() < pin.length())
                pinEntered += "3";

            checkPIN();
        }

        else if (v.getId() == R.id.ic_4) {
            if (pinEntered.length() < pin.length())
                pinEntered += "4";

            checkPIN();
        }

        else if (v.getId() == R.id.ic_5) {
            if (pinEntered.length() < pin.length())
                pinEntered += "5";

            checkPIN();
        }

        else if (v.getId() == R.id.ic_6) {
            if (pinEntered.length() < pin.length())
                pinEntered += "6";

            checkPIN();
        }

        else if (v.getId() == R.id.ic_7) {
            if (pinEntered.length() < pin.length())
                pinEntered += "7";

            checkPIN();
        }

        else if (v.getId() == R.id.ic_8) {
            if (pinEntered.length() < pin.length())
                pinEntered += "8";

            checkPIN();
        }

        else if (v.getId() == R.id.ic_9) {
            if (pinEntered.length() < pin.length())
                pinEntered += "9";

            checkPIN();
        }

        // If delete pressed, delete last element of pinEntered; if pinEntered is empty, do nothing
        else if (v.getId() == R.id.delete) {
            if (!pinLocked) {
                if (pinEntered.length() > 0) {
                    if (pinEntered.length() == 1) {
                        pinEntered = "";
                        pinInput.setText(pinEntered);

                        if (vibratorAvailable)
                            vibrator.vibrate(50);

                        return;
                    }

                    else {
                        pinEntered = pinEntered.substring(0, pinEntered.length() - 1);
                        pinInput.setText(pinEntered);
                        return;
                    }
                }

                if (vibratorAvailable)
                    vibrator.vibrate(50);
            }
        }


        // If wifi toolbox button pressed, enable/disable wifi
        else if (v.getId() == R.id.wifi) {
            if (wifiManager.isWifiEnabled())
                wifiOn(false);

            else
                wifiOn(true);
        }


        // If data toolbox button pressed, enable/disable mobile data
        else if (v.getId() == R.id.data) {
            isMobileDataOn = connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

            if (isMobileDataOn != null) {
                if (isMobileDataOn.isConnected()) {
                    try {
                        mobileDataOn(false);

                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }

                else {
                    try {
                        mobileDataOn(true);

                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }

            // No mobile data detected, mostly in the case of SIM-less tablets
            else {
                Toast toast = Toast.makeText(getApplicationContext(),
                        R.string.toast_no_mobile_data,
                        Toast.LENGTH_SHORT);
                toast.show();
            }
        }


        // If flashlight toolbox button pressed, enable/disable flashlight
        else if (v.getId() == R.id.flashlight) {
            if (flashlightAvailable) {
                if (!isFlashOn) {
                    flashlightOn(true);
                    // Add window flag to keep screen on
                    this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }

                else if (isFlashOn) {
                    flashlightOn(false);
                    // Remove window flag that kept the screen on
                    this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }

            // Toast user if flashlight not available
            else {
                Toast toast = Toast.makeText(getApplicationContext(),
                        R.string.toast_no_flashlight,
                        Toast.LENGTH_SHORT);
                toast.show();
            }
        }


        // If brightness toolbox button pressed, set brightness accordingly and change brightnessMode var
        else if (v.getId() == R.id.brightness) {
            // If brightnessMode wasn't initialized, set to AUTO
            if (brightnessMode == -1)
                brightnessMode = 3;

            if (brightnessMode == 3) {
                brightnessChange(0);
                brightnessMode = 0;
            }

            else if (brightnessMode == 0) {
                brightnessChange(1);
                brightnessMode = 1;
            }

            else if (brightnessMode == 1) {
                brightnessChange(2);
                brightnessMode = 2;
            }

            else if (brightnessMode == 2) {
                brightnessChange(3);
                brightnessMode = 3;
            }
        }


        // If sound toolbox button pressed, change ringer mode accordingly and change ringerMode var
        else if (v.getId() == R.id.sound) {
            if (ringerMode == 1) {
                ringerChange(2);
                ringerMode = 2;
            }

            else if (ringerMode == 2) {
                ringerChange(3);
                ringerMode = 3;
            }

            else if (ringerMode == 3) {
                ringerChange(1);
                ringerMode = 1;
            }
        }


        // If Phone button pressed, set boolean to true and toast user to unlock
        else if (v.getId() == R.id.phone) {
            phoneToOpen = true;
            cameraToOpen = false;

            Toast toast = Toast.makeText(getApplicationContext(),
                    R.string.toast_phone_open,
                    Toast.LENGTH_SHORT);
            toast.show();
        }


        // If Camera button pressed, set boolean to true and toast user to unlock
        else if (v.getId() == R.id.camera) {
            cameraToOpen = true;
            phoneToOpen = false;

            Toast toast = Toast.makeText(getApplicationContext(),
                    R.string.toast_camera_open,
                    Toast.LENGTH_SHORT);
            toast.show();
        }


        // If Up button pressed, hide camera, phone and up; animate toolbox up and show down button
        else if (v.getId() == R.id.up) {
            swipeUp();
        }


        // If down button pressed, hide down; animate toolbox down and show camera, phone and up
        else if (v.getId() == R.id.down) {
            swipeDown();
        }
    }


    // Fling listener to detect swipe up/down, to toggle toolbox and phone/camera
    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (e1.getY() < e2.getY() && (e2.getY() - e1.getY()) > SWIPE_MIN_DIST) {
            if (swipedDown)
                return false;

            swipedDown = true;
            swipedUp = false;
            swipeDown();
        }

        else if (e1.getY() > e2.getY() && (e1.getY() - e2.getY()) > SWIPE_MIN_DIST) {
            if (swipedUp)
                return false;

            swipedUp = true;
            swipedDown = false;
            swipeUp();
        }

        return false;
    }

    // Hide up/phone/camera and animate toolbox up
    private void swipeUp() {
        up.startAnimation(fadeOut);
        phone.startAnimation(fadeOut);
        camera.startAnimation(fadeOut);

        up.setVisibility(View.INVISIBLE);
        phone.setVisibility(View.INVISIBLE);
        camera.setVisibility(View.INVISIBLE);

        toolboxFrame.startAnimation(slideUp);
        toolboxFrame.setVisibility(View.VISIBLE);

        wifi.startAnimation(fadeIn);
        data.startAnimation(fadeIn);
        flashlight.startAnimation(fadeIn);
        brightness.startAnimation(fadeIn);
        sound.startAnimation(fadeIn);

        wifi.setVisibility(View.VISIBLE);
        data.setVisibility(View.VISIBLE);
        flashlight.setVisibility(View.VISIBLE);
        brightness.setVisibility(View.VISIBLE);
        sound.setVisibility(View.VISIBLE);

        down.setVisibility(View.VISIBLE);
    }

    // Animate toolbox down and show up/phone/camera
    private void swipeDown() {
        down.setVisibility(View.INVISIBLE);

        wifi.startAnimation(fadeOut);
        data.startAnimation(fadeOut);
        flashlight.startAnimation(fadeOut);
        brightness.startAnimation(fadeOut);
        sound.startAnimation(fadeOut);

        wifi.setVisibility(View.INVISIBLE);
        data.setVisibility(View.INVISIBLE);
        flashlight.setVisibility(View.INVISIBLE);
        brightness.setVisibility(View.INVISIBLE);
        sound.setVisibility(View.INVISIBLE);

        toolboxFrame.startAnimation(slideDown);
        toolboxFrame.setVisibility(View.INVISIBLE);

        up.startAnimation(fadeIn);
        phone.startAnimation(fadeIn);
        camera.startAnimation(fadeIn);

        up.setVisibility(View.VISIBLE);
        phone.setVisibility(View.VISIBLE);
        camera.setVisibility(View.VISIBLE);
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (!hasFocus && !isPhoneCalling) {
            activityManager.moveTaskToFront(taskId, 0);
            Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            sendBroadcast(closeDialog);
        }
    }

    // If the user wants to leave the app like pressing recent apps button, move task to queue front
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();

        activityManager.moveTaskToFront(taskId, 0);
    }

    // Empty methods required for GestureListener
    public boolean onDown(MotionEvent e) {
        return false;
    }

    public void onShowPress(MotionEvent e) {}

    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    public void onLongPress(MotionEvent e) {}


    // If window touched, reset brightness and if touched outside window, move task to queue front
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            activityManager.moveTaskToFront(taskId, 0);

            return true;
        }

        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            activityManager.moveTaskToFront(taskId, 0);

            return gestureDetector.onTouchEvent(event);
        }

        return gestureDetector.onTouchEvent(event);
    }


    @Override
    public void onBackPressed() {
        // If back button pressed, do nothing
    }

    // Added just to be safe; home key event detected - move task to queue front
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            activityManager.moveTaskToFront(taskId, 0);

            return true;
        }

        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            activityManager.moveTaskToFront(taskId, 0);

            return true;
        }

        return false;
    }


    // Call state listener
    class StateListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);

            switch (state) {
                // If state is ringing, set isPhoneCalling var to true and move task to back
                case TelephonyManager.CALL_STATE_RINGING:
                    isPhoneCalling = true;
                    moveTaskToBack(true);

                    break;

                // If state is offhook, set isPhoneCalling var to true and move task to back
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    isPhoneCalling = true;
                    moveTaskToBack(true);

                    break;

                // If call stopped or idle and isPhoneCalling var is true, move task to queue front
                case TelephonyManager.CALL_STATE_IDLE:
                    if (isPhoneCalling) {
                        activityManager.moveTaskToFront(taskId, 0);

                        isPhoneCalling = false;
                    }

                    break;
            }
        }
    }


    // If activity resumed, enable NFC tag discovery
    @Override
    protected void onResume() {
        super.onResume();

        activityManager.moveTaskToFront(taskId, 0);

        // Re-enable home launcher activity component
        if (packageManager != null) {
            packageManager.setComponentEnabledSetting(cnHome, componentEnabled,
                    PackageManager.DONT_KILL_APP);
        }

        if (nfcAdapter != null) {
            if (nfcAdapter.isEnabled()) {
                // If Android version lower than 4.4, use foreground dispatch method with tech filters
                if (Build.VERSION.SDK_INT < 19) {
                    nfcAdapter.enableForegroundDispatch(this, pIntent,
                            new IntentFilter[]{new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)},
                            new String[][]{new String[]{"android.nfc.tech.MifareClassic"},
                                    new String[]{"android.nfc.tech.MifareUltralight"},
                                    new String[]{"android.nfc.tech.NfcA"},
                                    new String[]{"android.nfc.tech.NfcB"},
                                    new String[]{"android.nfc.tech.NfcF"},
                                    new String[]{"android.nfc.tech.NfcV"},
                                    new String[]{"android.nfc.tech.Ndef"},
                                    new String[]{"android.nfc.tech.IsoDep"},
                                    new String[]{"android.nfc.tech.NdefFormatable"}
                            }
                    );
                }

                // If Android version 4.4 or bigger, use enableReaderMode method
                else {
                    nfcAdapter.enableReaderMode(this, new NfcAdapter.ReaderCallback() {

                        @Override
                        public void onTagDiscovered(Tag tag) {
                            // Get tag id and convert to readable string
                            byte[] tagID = tag.getId();
                            String tagDiscovered = bytesToHex(tagID);

                            if (!tagDiscovered.equals("")) {
                                // Loop through added NFC tags
                                for (int i = 0; i < tags.length(); i++) {
                                    try {
                                        // When tag discovered id is equal to one of the stored tags id
                                        if (tagDiscovered.equals(tags.getJSONObject(i).getString("tagID"))) {
                                            // Reset tagDiscovered string, set pinLocked false and store
                                            // Remove handle callback and messages
                                            // Disable home launcher activity component and finish

                                            tagDiscovered = "";

                                            pinLocked = false;
                                            settings.put("pinLocked", false);

                                            writeToJSON();

                                            mHandler.removeCallbacksAndMessages(null);

                                            packageManager.setComponentEnabledSetting(cnHome,
                                                    componentDisabled, PackageManager.DONT_KILL_APP);

                                            finish();
                                            overridePendingTransition(0, 0);
                                            return;
                                        }

                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }

                                // When tag discovered id is not correct
                                // Vibrate, display 'Wrong NFC Tag' and after 1.5s switch back to normal
                                unlockText.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!pinLocked) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    unlockText.setText("");
                                                }
                                            });
                                        }

                                        else {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    unlockText.setText(getResources().getString(
                                                            R.string.pin_locked));
                                                }
                                            });
                                        }
                                    }
                                }, 1500);

                                if (vibratorAvailable)
                                    vibrator.vibrate(250);

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        unlockText.setText(getResources().getString(R.string.wrong_tag));
                                    }
                                });
                            }
                        }
                    }, NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS | NfcAdapter.FLAG_READER_NFC_A |
                            NfcAdapter.FLAG_READER_NFC_B | NfcAdapter.FLAG_READER_NFC_BARCODE |
                            NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V |
                            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
                }
            }

            // If NFC is off
            else {
                if (pinLocked)
                    unlockText.setText(getResources().getString(R.string.pin_locked_nfc_off));

                else
                    unlockText.setText(getResources().getString(R.string.scan_to_unlock_nfc_off));
            }
        }

        // If NFC is off
        else {
            if (pinLocked)
                unlockText.setText(getResources().getString(R.string.pin_locked_nfc_off));

            else
                unlockText.setText(getResources().getString(R.string.scan_to_unlock_nfc_off));
        }

        // Check default launcher, if current package isn't the default one
        // Open the 'Select home app' dialog for user to pick default home launcher
        if (!isMyLauncherDefault()) {
            packageManager.clearPackagePreferredActivities(getPackageName());

            Intent launcherPicker = new Intent();
            launcherPicker.setAction(Intent.ACTION_MAIN);
            launcherPicker.addCategory(Intent.CATEGORY_HOME);
            startActivity(launcherPicker);

            if (launcherPickToast > 0) {
                launcherPickToast -= 1;

                Toast toast = Toast.makeText(getApplicationContext(),
                        R.string.toast_launcher_dialog,
                        Toast.LENGTH_LONG);
                toast.show();
            }
        }
    }


    // If activity offline or paused, disable NFC tag discovery and clear flag that kept screen on
    @Override
    protected void onPause() {
        super.onPause();

        if (flashlightAvailable) {
            if (isFlashOn) {
                flashlightOn(false);
                this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }

        if (Build.VERSION.SDK_INT < 19) {
            if (nfcAdapter != null)
                nfcAdapter.disableForegroundDispatch(this);
        }

        else if (Build.VERSION.SDK_INT >= 19) {
            if (nfcAdapter != null)
                nfcAdapter.disableReaderMode(this);
        }
    }


    // If activity finished and stopped, disable home launcher activity component,
    // Remove disableStatusBar view, clear window flags, unregister receiver, release the camera,
    // Set pinLocked false and store, reset brightness and screen timeout and remove handler callbacks
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (packageManager != null) {
            packageManager.setComponentEnabledSetting(cnHome, componentDisabled,
                    PackageManager.DONT_KILL_APP);
        }

        if (wm != null) {
            try {
                wm.removeView(disableStatusBar);

            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }

        this.getWindow().clearFlags(flags);

        try {
            unregisterReceiver(mChangeReceiver);
            unregisterReceiver(mAlarmReceiver);

        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        if (flashlightAvailable) {
            if (isFlashOn)
                this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        if (Build.VERSION.SDK_INT >= 19) {
            this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        releaseCamera();

        isPhoneCalling = false;

        try {
            settings.put("pinLocked", false);

        } catch (JSONException e) {
            e.printStackTrace();
        }

        writeToJSON();

        // Reset screen timeout to initial
        if (cResolver != null && systemScreenTimeout != -1)
            Settings.System.putInt(cResolver, Settings.System.SCREEN_OFF_TIMEOUT, systemScreenTimeout);

        // Remove all handler callbacks
        mHandler.removeCallbacksAndMessages(null);

        // If Phone button pressed, start default Phone app after 300ms delay
        if (phoneToOpen) {
            phoneToOpen = false;

            final Intent phoneIntent = new Intent(Intent.ACTION_DIAL);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        startActivity(phoneIntent);

                    } catch (ActivityNotFoundException anfe) {
                        anfe.printStackTrace();
                    }
                }
            }, 300);
        }

        // If Camera button pressed, start default Camera app after 300ms delay
        if (cameraToOpen) {
            cameraToOpen = false;

            final Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        startActivity(cameraIntent);

                    } catch (ActivityNotFoundException anfe) {
                        anfe.printStackTrace();
                    }
                }
            }, 300);
        }
    }


    // Tag discovery for Android versions lower than 4.4
    // If correct NFC tag detected, unlock device
    // Else, display wrong tag detected and vibrate
    @Override
    protected void onNewIntent(Intent intent) {
        String action = intent.getAction();

        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

            // Get tag id and convert to readable string
            byte[] tagID = tag.getId();
            String tagDiscovered = bytesToHex(tagID);

            if (!tagDiscovered.equals("")) {
                // Loop through added NFC tags
                for (int i = 0; i < tags.length(); i++) {
                    // When tag discovered id is equal to one of the stored tags id
                    try {
                        if (tagDiscovered.equals(tags.getJSONObject(i).getString("tagID"))) {
                            // Reset tagDiscovered string, set pinLocked false and store
                            // Remove handle callback and messages
                            // Disable home launcher activity component and finish

                            tagDiscovered = "";

                            pinLocked = false;
                            settings.put("pinLocked", false);

                            writeToJSON();

                            mHandler.removeCallbacksAndMessages(null);

                            packageManager.setComponentEnabledSetting(cnHome, componentDisabled,
                                    PackageManager.DONT_KILL_APP);

                            finish();
                            overridePendingTransition(0, 0);
                            return;
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                // If wrong NFC tag id detected
                // Vibrate, display 'Wrong NFC Tag' and after 1.5s switch back to normal
                unlockText.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!pinLocked)
                            unlockText.setText("");

                        else
                            unlockText.setText(getResources().getString(R.string.pin_locked));
                    }
                }, 1500);

                if (vibratorAvailable)
                    vibrator.vibrate(250);

                unlockText.setText(getResources().getString(R.string.wrong_tag));
            }
        }
    }


    // Char array for bytes to hex string method
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    // Bytes to hex string method
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];

        for (int i = 0; i < bytes.length; i++) {
            int x = bytes[i] & 0xFF;
            hexChars[i * 2] = hexArray[x >>> 4];
            hexChars[i * 2+1] = hexArray[x & 0x0F];
        }

        return new String(hexChars);
    }
}
