package com.moonpi.tapunlock;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;


public class MainActivity extends Activity implements View.OnClickListener,
        CompoundButton.OnCheckedChangeListener, SeekBar.OnSeekBarChangeListener {

    /*
    Copyright © 2014 MoonPi

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
    */

    /*
    JSON file structure:
    root_OBJ:{
        settings_OBJ:{
            "lockscreen":true/false,
            "pin":"4-6 digits",
            "pinLocked":true/false,
            "blur":0-25,
            tags_ARR:[
                {"tagName":"Bracelet", "tagID":"86ja8asdbb2385"},
                {"tagName":"Ring", "tagID":"r2365sd98123sj"} etc.
            ]
        }
    }
    */

    private static final int DIALOG_READ = 1; // int for 'Scan NFC Tag' dialog
    private static final int DIALOG_SET_TAGNAME = 2; // int for 'Set Tag name' dialog
    private static final int SEEK_BAR_INTERVAL = 5; // Interval skip for seek bar

    private NfcAdapter nfcAdapter;
    private PendingIntent pIntent; // PendingIntent for NFC tag discovery

    // Layout items
    private ScrollView scrollView;
    private EditText pinEdit;
    private SeekBar seekBar;
    private ProgressBar progressBar;
    private TextView enabled_disabled, backgroundBlurValue, noTags;
    private ListView listView;

    private InputMethodManager imm;

    // Content of JSON file
    private JSONObject root;
    private JSONObject settings;
    private JSONArray tags;
    private int blur;

    private String tagID = ""; // ID of tag discovered
    private Boolean dialogCancelled = false; // var for when 'Scan NFC Tag' dialog is called
    private Boolean onStart = false; // var for when lockscreen is enabled when app started

    // Used for action bar title design
    private Typeface lobsterTwo;
    private TextView actionBarTitleView;



    // Custom tag adapter class (to populate NFC tags listView)
    private class TagAdapter extends BaseAdapter implements ListAdapter {

        Activity parentActivity;
        JSONArray adapterData;
        LayoutInflater inflater;

        public TagAdapter(Activity activity, JSONArray adapterData) {
            this.parentActivity = activity;
            this.adapterData = adapterData;
            this.inflater = (LayoutInflater)getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getCount() {
            if (this.adapterData != null)
                return this.adapterData.length();

            else
                return 0;
        }

        @Override
        public JSONObject getItem(int position) {
            if (this.adapterData != null)
                return this.adapterData.optJSONObject(position);

            else
                return null;
        }

        @Override
        public long getItemId(int position) {
            JSONObject jsonObject = getItem(position);

            return jsonObject.optLong("id");
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            if (convertView == null)
                convertView = this.parentActivity.getLayoutInflater().inflate(R.layout.list_view_tags, null);

            TextView text = (TextView) convertView.findViewById(R.id.backgroundBlurValue);

            JSONObject jsonData = getItem(position);

            // Set listView item text to tagName
            if (jsonData != null) {
                try {
                    String data = jsonData.getString("tagName");
                    text.setText(data);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            return convertView;
        }
    }


    private TagAdapter adapter; // Instance of TagAdapter

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get lobster_two asset and create typeface
        // Set action bar title to lobster_two typeface
        lobsterTwo = Typeface.createFromAsset(getAssets(), "lobster_two.otf");

        int actionBarTitle = Resources.getSystem().getIdentifier("action_bar_title", "id", "android");
        actionBarTitleView = (TextView) getWindow().findViewById(actionBarTitle);

        if (actionBarTitleView != null) {
            actionBarTitleView.setTypeface(lobsterTwo);
            actionBarTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f);
            actionBarTitleView.setTextColor(getResources().getColor(R.color.blue));
        }

        setContentView(R.layout.activity_main);

        // Hide keyboard on app launch
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        // Get NFC service and adapter
        NfcManager nfcManager = (NfcManager) this.getSystemService(Context.NFC_SERVICE);
        nfcAdapter = nfcManager.getDefaultAdapter();

        // Create PendingIntent for enableForegroundDispatch for NFC tag discovery
        pIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        readFromJSON();
        writeToJSON();
        readFromJSON();


        // If Android 4.2 or bigger
        if (Build.VERSION.SDK_INT > 16) {
            // Check if TapUnlock folder exists, if not, create directory
            File folder = new File(Environment.getExternalStorageDirectory() + "/TapUnlock");
            boolean folderSuccess = true;

            if (!folder.exists()) {
                folderSuccess = folder.mkdir();
            }

            try {
                // If blur var bigger than 0
                if (settings.getInt("blur") > 0) {
                    // If folder exists or successfully created
                    if (folderSuccess) {
                        // If blurred wallpaper file doesn't exist
                        if (!ImageUtils.doesBlurredWallpaperExist()) {
                            // Get default wallpaper
                            WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
                            final Drawable wallpaperDrawable = wallpaperManager.peekFastDrawable();

                            if (wallpaperDrawable != null) {
                                // Default wallpaper to bitmap - fastBlur the bitmap - store bitmap
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Bitmap bitmapToBlur = ImageUtils.drawableToBitmap(wallpaperDrawable);

                                        Bitmap blurredWallpaper = null;
                                        if (bitmapToBlur != null)
                                            blurredWallpaper = ImageUtils.fastBlur(MainActivity.this, bitmapToBlur, blur);

                                        if (blurredWallpaper != null) {
                                            ImageUtils.storeImage(blurredWallpaper);
                                        }
                                    }
                                }).start();
                            }
                        }
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // Initialize layout items
        pinEdit = (EditText)findViewById(R.id.pinEdit);
        pinEdit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        Button setPin = (Button) findViewById(R.id.setPin);
        ImageButton newTag = (ImageButton) findViewById(R.id.newTag);
        enabled_disabled = (TextView)findViewById(R.id.enabled_disabled);
        Switch toggle = (Switch) findViewById(R.id.toggle);
        seekBar = (SeekBar)findViewById(R.id.seekBar);
        progressBar = (ProgressBar)findViewById(R.id.progressBar);
        Button refreshWallpaper = (Button) findViewById(R.id.refreshWallpaper);
        listView = (ListView)findViewById(R.id.listView);
        backgroundBlurValue = (TextView)findViewById(R.id.backgroundBlurValue);
        noTags = (TextView)findViewById(R.id.noTags);

        // Initialize TagAdapter
        adapter = new TagAdapter(this, tags);

        registerForContextMenu(listView);

        // Set listView adapter to TapAdapter object
        listView.setAdapter(adapter);

        // Set click, check and seekBar listeners
        setPin.setOnClickListener(this);
        newTag.setOnClickListener(this);
        refreshWallpaper.setOnClickListener(this);
        toggle.setOnCheckedChangeListener(this);
        seekBar.setOnSeekBarChangeListener(this);

        // Set seekBar progress to blur var
        try {
            seekBar.setProgress(settings.getInt("blur"));

        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Refresh the listView height
        updateListViewHeight(listView);

        // If no tags, show 'Press + to add Tags' textView
        if (tags.length() == 0)
            noTags.setVisibility(View.VISIBLE);

        else
            noTags.setVisibility(View.INVISIBLE);

        // Scroll up
        scrollView = (ScrollView)findViewById(R.id.scrollView);

        scrollView.post(new Runnable() {
            public void run() {
                scrollView.scrollTo(0, scrollView.getTop());
                scrollView.fullScroll(View.FOCUS_UP);
            }
        });

        // If lockscreen enabled, initialize switch, text and start service
        try {
            if (settings.getBoolean("lockscreen")) {
                onStart = true;
                enabled_disabled.setText(R.string.lockscreen_enabled);
                enabled_disabled.setTextColor(getResources().getColor(R.color.green));

                toggle.setChecked(true);
            }

        } catch (JSONException e1) {
            e1.printStackTrace();
        }
    }


    // Function to update listView height
    // Because of it being inside a scrollView
    public static void updateListViewHeight(ListView myListView) {
        ListAdapter myListAdapter = myListView.getAdapter();

        if (myListAdapter == null)
            return;

        // Get listView height
        int totalHeight = myListView.getPaddingTop() + myListView.getPaddingBottom();
        int adapterCount = myListAdapter.getCount();

        for (int i = 0; i < adapterCount; i++) {
            View listItem = myListAdapter.getView(i, null, myListView);

            if (listItem instanceof ViewGroup) {
                listItem.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                    ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            listItem.measure(0, 0);
            totalHeight += listItem.getMeasuredHeight();
        }

        // Change height of listView
        ViewGroup.LayoutParams paramsList = myListView.getLayoutParams();
        paramsList.height = totalHeight + (myListView.getDividerHeight() * (adapterCount - 1));
        myListView.setLayoutParams(paramsList);
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
        // Read root from JSON file
        try {
            BufferedReader bRead = new BufferedReader(new InputStreamReader
                    (openFileInput("settings.json")));
            root = new JSONObject(bRead.readLine());

        } catch (FileNotFoundException e) {
            e.printStackTrace();

            root = new JSONObject();

        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Read settings, or put JSON object in root if it doesn't exist
        try {
            settings = root.getJSONObject("settings");

        } catch (JSONException e) {
            try {
                settings = new JSONObject();
                root.put("settings", settings);

            } catch (JSONException e1) {
                e1.printStackTrace();
            }
        }

        // Read settings, or put if they don't exist
        try {
            Boolean lockscreen = settings.getBoolean("lockscreen");
            String pin = settings.getString("pin");
            Boolean pinLocked = settings.getBoolean("pinLocked");
            blur = settings.getInt("blur");
            tags = settings.getJSONArray("tags");

        } catch (JSONException e) {
            try {
                tags = new JSONArray();
                settings.put("lockscreen", false);
                settings.put("pin", "");
                settings.put("pinLocked", false);

                if (Build.VERSION.SDK_INT > 16)
                    settings.put("blur", 15);

                else
                    settings.put("blur", 0);

                settings.put("tags", tags);

            } catch (JSONException e1) {
                e1.printStackTrace();
            }
        }
    }


    // Stop NFC tag discovery when 'Cancel' pressed
    public void killForegroundDispatch() {
        if (dialogCancelled) {
            try {
                if (nfcAdapter != null)
                    nfcAdapter.disableForegroundDispatch(this);

            } catch (IllegalStateException e) {
                e.printStackTrace();
            }

            dialogCancelled = false;
        }
    }


    // Stop service
    public void killService(Context context) {
        stopService(new Intent(context, ScreenLockService.class));
    }


    // Dialog for NFC tag adding/changing
    @Override
    protected Dialog onCreateDialog(int id) {
        // Scan NFC Tag dialog, inflate image
        LayoutInflater factory = LayoutInflater.from(MainActivity.this);
        final View view = factory.inflate(R.layout.scan_tag_image, null);

        // Ask for tagTitle (tagName) in dialog
        final EditText tagTitle = new EditText(this);
        tagTitle.setHint(getResources().getString(R.string.set_tag_name_dialog_hint));
        tagTitle.setSingleLine(true);
        tagTitle.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        // Set tagTitle maxLength
        int maxLength = 50;
        InputFilter[] array = new InputFilter[1];
        array[0] = new InputFilter.LengthFilter(maxLength);
        tagTitle.setFilters(array);

        final LinearLayout l = new LinearLayout(this);

        l.setOrientation(LinearLayout.VERTICAL);
        l.addView(tagTitle);

        // Dialog that shows scan tag image
        if (id == DIALOG_READ) {
            return new AlertDialog.Builder(this)
                    .setTitle(R.string.scan_tag_dialog_title)
                    .setView(view)
                    .setCancelable(false)
                    .setNeutralButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialogCancelled = true;
                            killForegroundDispatch();
                            dialog.cancel();
                        }
                    }).create();
        }

        // Dialog that asks for tagName and stores it after 'Ok' pressed
        else if (id == DIALOG_SET_TAGNAME) {
            tagTitle.requestFocus();
            return new AlertDialog.Builder(this)
                    .setTitle(R.string.set_tag_name_dialog_title)
                    .setView(l)
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            JSONObject newTag = new JSONObject();

                            try {
                                newTag.put("tagName", tagTitle.getText());
                                newTag.put("tagID", tagID);

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            tags.put(newTag);

                            writeToJSON();

                            adapter.notifyDataSetChanged();

                            updateListViewHeight(listView);

                            if (tags.length() == 0)
                                noTags.setVisibility(View.VISIBLE);

                            else
                                noTags.setVisibility(View.INVISIBLE);

                            Toast toast = Toast.makeText(getApplicationContext(),
                                    R.string.toast_tag_added,
                                    Toast.LENGTH_SHORT);
                            toast.show();

                            imm.hideSoftInputFromWindow(tagTitle.getWindowToken(), 0);

                            tagID = "";
                            tagTitle.setText("");
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            tagID = "";
                            tagTitle.setText("");
                            imm.hideSoftInputFromWindow(tagTitle.getWindowToken(), 0);
                            dialog.cancel();
                        }
                    })
                    .create();
        }

        return null;
    }


    // Create context menu for tags listView
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        menu.add(getResources().getString(R.string.rename_context_menu));
        menu.add(getResources().getString(R.string.delete_context_menu));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // Get pressed item information
        final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        // If Rename Tag pressed
        if (item.getTitle().equals(getResources().getString(R.string.rename_context_menu))) {
            // Create new EdiText and configure
            final EditText tagTitle = new EditText(this);
            tagTitle.setSingleLine(true);
            tagTitle.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

            // Set tagTitle maxLength
            int maxLength = 50;
            InputFilter[] array = new InputFilter[1];
            array[0] = new InputFilter.LengthFilter(maxLength);
            tagTitle.setFilters(array);

            // Get tagName text into EditText
            try {
                assert info != null;
                tagTitle.setText(tags.getJSONObject(info.position).getString("tagName"));

            } catch (JSONException e) {
                e.printStackTrace();
            }

            final LinearLayout l = new LinearLayout(this);

            l.setOrientation(LinearLayout.VERTICAL);
            l.addView(tagTitle);

            // Show rename dialog
            new AlertDialog.Builder(this)
                    .setTitle(R.string.rename_tag_dialog_title)
                    .setView(l)
                    .setPositiveButton(R.string.rename_tag_dialog_button, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // 'Rename' pressed, change tagName and store
                            try {
                                JSONObject newTagName = tags.getJSONObject(info.position);
                                newTagName.put("tagName", tagTitle.getText());

                                tags.put(info.position, newTagName);
                                adapter.notifyDataSetChanged();

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            writeToJSON();

                            Toast toast = Toast.makeText(getApplicationContext(),
                                    R.string.toast_tag_renamed,
                                    Toast.LENGTH_SHORT);
                            toast.show();

                            imm.hideSoftInputFromWindow(tagTitle.getWindowToken(), 0);

                        }
                    }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    imm.hideSoftInputFromWindow(tagTitle.getWindowToken(), 0);
                }
            }).show();
            tagTitle.requestFocus();
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);

            return true;
        }


        // If Delete Tag pressed
        else if (item.getTitle().equals(getResources().getString(R.string.delete_context_menu))) {
            // Construct dialog message
            String dialogMessage = "";

            assert info != null;
            try {
                dialogMessage = getResources().getString(R.string.delete_context_menu_dialog1) + " '"
                        + tags.getJSONObject(info.position).getString("tagName") + "'?";

            } catch (JSONException e) {
                e.printStackTrace();
                dialogMessage = getResources().getString(R.string.delete_context_menu_dialog2);
            }

            // Show delete dialog
            new AlertDialog.Builder(this)
                    .setMessage(dialogMessage)
                    .setPositiveButton(R.string.yes_button, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            JSONArray newArray = new JSONArray();

                            // Copy contents to new array, without the deleted item
                            for (int i = 0; i < tags.length(); i++) {
                                if (i != info.position) {
                                    try {
                                        newArray.put(tags.get(i));

                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }

                            // Equal original array to new array
                            tags = newArray;

                            // Write to file
                            try {
                                settings.put("tags", tags);
                                root.put("settings", settings);

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            writeToJSON();

                            adapter.adapterData = tags;
                            adapter.notifyDataSetChanged();

                            updateListViewHeight(listView);

                            // If no tags, show 'Press + to add Tags' textView
                            if (tags.length() == 0)
                                noTags.setVisibility(View.VISIBLE);

                            else
                                noTags.setVisibility(View.INVISIBLE);

                            Toast toast = Toast.makeText(getApplicationContext(),
                                    R.string.toast_tag_deleted,
                                    Toast.LENGTH_SHORT);
                            toast.show();

                        }
                    }).setNegativeButton(R.string.no_button, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Do nothing, close dialog
                }
            }).show();

            return true;
        }

        return super.onContextItemSelected(item);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // If Rate app pressed, ask user if it's ok to leave app and go to Play Store
        // If yes, open app in Play Store; if no, close dialog
        if (item.getItemId() == R.id.rate_app) {
            final String appPackageName = getPackageName(); // getPackageName() from Context or Activity object

            new AlertDialog.Builder(this)
                    .setTitle(R.string.rate_app_dialog_title)
                    .setMessage(R.string.rate_app_dialog_message)
                    .setPositiveButton(R.string.yes_button, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("market://details?id=" + appPackageName)));

                            } catch (android.content.ActivityNotFoundException anfe) {
                                startActivity(new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("http://play.google.com/store/apps/details?id=" + appPackageName)));
                            }
                        }
                    }).setNegativeButton(R.string.no_button, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Do nothing, close dialog
                }
            }).show();

            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onClick(View v) {
        // If OK(setPin) clicked, ask user if sure; if yes, store PIN; else, go back
        if (v.getId() == R.id.setPin) {
            // If PIN length between 4 and 6, store PIN and toast successful
            if (pinEdit.length() >= 4 && pinEdit.length() <= 6) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.set_pin_confirmation)
                        .setPositiveButton(R.string.yes_button, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    settings.put("pin", String.valueOf(pinEdit.getText()));

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                                writeToJSON();

                                Toast toast = Toast.makeText(getApplicationContext(),
                                        R.string.toast_pin_set,
                                        Toast.LENGTH_SHORT);
                                toast.show();

                                imm.hideSoftInputFromWindow(pinEdit.getWindowToken(), 0);

                            }
                        }).setNegativeButton(R.string.no_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        imm.hideSoftInputFromWindow(pinEdit.getWindowToken(), 0);
                        // Do nothing, close dialog
                    }
                }).show();
            }

            // Toast user that PIN needs to be at least 4 digits long
            else {
                Toast toast = Toast.makeText(getApplicationContext(),
                        R.string.toast_pin_needs4digits,
                        Toast.LENGTH_LONG);
                toast.show();
            }
        }

        // If 'Refresh wallpaper' pressed, check if Android 4.2 or above, if yes
        // Store new blur var, if blur bigger than 0 re-blur wallpaper
        else if (v.getId() == R.id.refreshWallpaper) {
            if (Build.VERSION.SDK_INT > 16) {
                try {
                    settings.put("blur", blur);

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                writeToJSON();

                // If blur is 0, don't change anything, just toast
                if (blur == 0) {
                    Toast toast = Toast.makeText(getApplicationContext(),
                            R.string.toast_wallpaper_refreshed,
                            Toast.LENGTH_SHORT);
                    toast.show();
                }

                // If blur is bigger than 0, get default wallpaper - to bitmap - fastblur bitmap - store
                else {
                    // Check if TapUnlock folder exists, if not, create directory
                    File folder = new File(Environment.getExternalStorageDirectory() + "/TapUnlock");
                    boolean folderSuccess = true;

                    if (!folder.exists()) {
                        folderSuccess = folder.mkdir();
                    }

                    if (folderSuccess) {
                        WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
                        final Drawable wallpaperDrawable = wallpaperManager.peekFastDrawable();

                        if (wallpaperDrawable != null) {
                            // Display indeterminate progress bar while blurring
                            progressBar.setVisibility(View.VISIBLE);

                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    Bitmap bitmapToBlur = ImageUtils.drawableToBitmap(wallpaperDrawable);

                                    Bitmap blurredWallpaper = null;
                                    if (bitmapToBlur != null)
                                        blurredWallpaper = ImageUtils.fastBlur(MainActivity.this, bitmapToBlur, blur);

                                    boolean stored = false;
                                    if (blurredWallpaper != null) {
                                        stored = ImageUtils.storeImage(blurredWallpaper);

                                        final boolean finalStored = stored;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                progressBar.setVisibility(View.INVISIBLE);

                                                if (finalStored) {
                                                    Toast toast = Toast.makeText(getApplicationContext(),
                                                            R.string.toast_wallpaper_refreshed,
                                                            Toast.LENGTH_SHORT);
                                                    toast.show();
                                                }
                                            }
                                        });
                                    }

                                    if (bitmapToBlur == null || blurredWallpaper == null || !stored) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                progressBar.setVisibility(View.INVISIBLE);

                                                Toast toast = Toast.makeText(getApplicationContext(),
                                                        R.string.toast_wallpaper_not_refreshed,
                                                        Toast.LENGTH_SHORT);
                                                toast.show();
                                            }
                                        });
                                    }
                                }
                            }).start();
                        }

                        else {
                            Toast toast = Toast.makeText(getApplicationContext(),
                                    R.string.toast_wallpaper_not_refreshed,
                                    Toast.LENGTH_SHORT);
                            toast.show();
                        }
                    }
                }
            }

            // If Android version less than 4.2, display toast cannot blur
            else {
                Toast toast = Toast.makeText(getApplicationContext(),
                        R.string.toast_cannot_blur,
                        Toast.LENGTH_SHORT);
                toast.show();
            }
        }

        // If '+' pressed
        else if (v.getId() == R.id.newTag) {
            if (nfcAdapter != null) {
                // If NFC is on, show scan dialog and enableForegroundDispatch
                if (nfcAdapter.isEnabled()) {
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


                    MainActivity.this.showDialog(DIALOG_READ);
                }

                // NFC is off, prompt user to enable it and send him to NFC settings
                else {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.nfc_off_dialog_title)
                            .setMessage(R.string.nfc_off_dialog_message)
                            .setPositiveButton(R.string.yes_button, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
                                    startActivity(intent);
                                }
                            }).setNegativeButton(R.string.no_button, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Do nothing, close dialog
                        }
                    }).show();
                }
            }

            // NFC adapter is null
            else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.nfc_off_dialog_title)
                        .setMessage(R.string.nfc_off_dialog_message)
                        .setPositiveButton(R.string.yes_button, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
                                startActivity(intent);
                            }
                        }).setNegativeButton(R.string.no_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing, close dialog
                    }
                }).show();
            }
        }
    }


    // If enable/disable screen lock switch changed
    public void onCheckedChanged(final CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            // If NFC is on
            if (nfcAdapter.isEnabled()) {
                try {
                    // If no PIN remembered, toast user to enter a PIN
                    if (settings.getString("pin").equals("")) {
                        Toast toast = Toast.makeText(getApplicationContext(),
                                R.string.toast_lock_set_pin,
                                Toast.LENGTH_LONG);
                        toast.show();

                        buttonView.setChecked(false);
                    }

                    // If no NFC Tag remembered, toast user to scan an NFC Tag
                    else if (tags.length() == 0) {
                        Toast toast = Toast.makeText(getApplicationContext(),
                                R.string.toast_lock_add_tag,
                                Toast.LENGTH_LONG);
                        toast.show();

                        // Set lockscreen false, stop service and store
                        try {
                            if (settings.getBoolean("lockscreen")) {
                                settings.put("lockscreen", false);

                                enabled_disabled.setText(R.string.lockscreen_disabled);
                                enabled_disabled.setTextColor(getResources().getColor(R.color.red));
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        writeToJSON();

                        killService(this);

                        buttonView.setChecked(false);
                    }

                    // If everything ok, set lockscreen true, start service and store
                    else {
                        if (!onStart) {
                            try {
                                settings.put("lockscreen", true);

                                enabled_disabled.setText(R.string.lockscreen_enabled);
                                enabled_disabled.setTextColor(getResources().getColor(R.color.green));

                                startService(new Intent(this, ScreenLockService.class));

                                Toast toast = Toast.makeText(getApplicationContext(),
                                        R.string.toast_lock_enabled,
                                        Toast.LENGTH_SHORT);
                                toast.show();

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            writeToJSON();
                            return;
                        }

                        onStart = false;
                        startService(new Intent(this, ScreenLockService.class));
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            // NFC is off, prompt user to enable it and send him to NFC settings
            else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.nfc_off_dialog_title)
                        .setMessage(R.string.nfc_off_dialog_message)
                        .setPositiveButton(R.string.yes_button, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Settings.ACTION_NFC_SETTINGS);
                                startActivity(intent);
                            }
                        }).setNegativeButton(R.string.no_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //Do nothing, close dialog
                    }
                }).show();

                // Set lockscreen false, stop service and store
                try {
                    if (settings.getBoolean("lockscreen"))
                        settings.put("lockscreen", false);

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                writeToJSON();

                enabled_disabled.setText(R.string.lockscreen_disabled);
                enabled_disabled.setTextColor(getResources().getColor(R.color.red));

                killService(this);

                buttonView.setChecked(false);
            }
        }


        // If unchecked, set lockscreen false, stop service and store
        else {
            try {
                settings.put("lockscreen", false);

            } catch (JSONException e) {
                e.printStackTrace();
            }

            writeToJSON();

            enabled_disabled.setText(R.string.lockscreen_disabled);
            enabled_disabled.setTextColor(getResources().getColor(R.color.red));

            killService(this);

            Toast toast = Toast.makeText(getApplicationContext(),
                    R.string.toast_lock_disabled,
                    Toast.LENGTH_SHORT);
            toast.show();
        }
    }


    // If seekBar progress changed
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        // Calculate progress based on change; for seekBar progress interval
        // (5 instead of 1 in this case)
        progress = ((int)Math.round(progress / SEEK_BAR_INTERVAL)) * SEEK_BAR_INTERVAL;

        // Set progress
        this.seekBar.setProgress(progress);

        // Equal blur var to calculated progress
        blur = progress;

        // Change blur level text to progress
        backgroundBlurValue.setText(String.valueOf(progress));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {}


    // If NFC Tag discovered, read ID, ask for tagName and store into file
    @Override
    protected void onNewIntent(Intent intent) {
        String action = intent.getAction();

        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

            // Get tag ID and turn into String
            byte[] tagIDbytes = tag.getId();
            tagID = bytesToHex(tagIDbytes);

            if (!tagID.equals("")) {
                // Dismiss the 'Scan NFC Tag' dialog and show the 'Set tag name' dialog
                dismissDialog(DIALOG_READ);

                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);

                MainActivity.this.showDialog(DIALOG_SET_TAGNAME);
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


    // Disable NFC foreground dispatch when activity paused
    @Override
    protected void onPause() {
        super.onPause();

        if (nfcAdapter != null)
            nfcAdapter.disableForegroundDispatch(this);
    }

    // If activity resumed, refresh action bar title design
    @Override
    protected void onResume() {
        super.onResume();

        if (actionBarTitleView != null) {
            actionBarTitleView.setTypeface(lobsterTwo);
            actionBarTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f);
            actionBarTitleView.setTextColor(getResources().getColor(R.color.blue));
        }
    }
}
