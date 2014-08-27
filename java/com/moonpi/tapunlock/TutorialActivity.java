package com.moonpi.tapunlock;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;


public class TutorialActivity extends Activity implements View.OnClickListener {

    private TextView welcomeTo;
    private TextView tapunlock;
    private TextView appDescription;
    private TextView navigation;
    private TextView screenshotDescription;
    private ImageButton navLeft;
    private ImageView progressImage, screenshotView;

    private int page = 1; //to keep track of the page we're on


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //if Android version 4.4 and above, add translucent status bar flag
        if (Build.VERSION.SDK_INT >= 19) {
            this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        setContentView(R.layout.activity_tutorial);

        RelativeLayout tutorialRelativeLayout = (RelativeLayout) findViewById(R.id.tutorialRelativeLayout);

        //if Android version 4.4 or bigger, set layout fits system windows to true
        if (Build.VERSION.SDK_INT >= 19) {
            tutorialRelativeLayout.setFitsSystemWindows(true);
        }

        //initialize layout items
        TextView moonPiBrand = (TextView) findViewById(R.id.moonPiBrand);
        welcomeTo = (TextView)findViewById(R.id.welcomeTo);
        tapunlock = (TextView)findViewById(R.id.tapunlock);
        appDescription = (TextView)findViewById(R.id.appDescription);
        navigation  = (TextView)findViewById(R.id.navigation);
        screenshotDescription = (TextView)findViewById(R.id.screenshotDescription);
        screenshotView = (ImageView)findViewById(R.id.screenshotView);
        ImageButton navRight = (ImageButton) findViewById(R.id.navRight);
        navLeft = (ImageButton)findViewById(R.id.navLeft);
        Button skip = (Button) findViewById(R.id.skip);
        progressImage = (ImageView)findViewById(R.id.progressImage);

        //create typefaces from lobster two and ubuntu font asset
        Typeface lobsterTwo = Typeface.createFromAsset(getAssets(), "lobster_two.otf");
        Typeface ubuntu = Typeface.createFromAsset(getAssets(), "ubuntu.ttf");

        //set typefaces
        moonPiBrand.setTypeface(lobsterTwo);
        welcomeTo.setTypeface(lobsterTwo);
        tapunlock.setTypeface(lobsterTwo);
        screenshotDescription.setTypeface(ubuntu);

        //set onClickListeners
        navRight.setOnClickListener(this);
        navLeft.setOnClickListener(this);
        skip.setOnClickListener(this);
    }


    @Override
    public void onClick(View v) {
        //if right navigation button pressed, change page content accordingly based on page number
        if (v.getId() == R.id.navRight) {
            //if last page, launch MainActivity and finish
            if (page == 6) {
                onBackPressed();
                return;
            }

            if (page < 6)
                page++;

            if (page == 2) {
                progressImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_6_2));
                navLeft.setVisibility(View.VISIBLE);

                welcomeTo.setVisibility(View.INVISIBLE);
                tapunlock.setVisibility(View.INVISIBLE);
                appDescription.setVisibility(View.INVISIBLE);
                navigation.setVisibility(View.INVISIBLE);

                screenshotView.setVisibility(View.VISIBLE);
                screenshotView.setImageDrawable(getResources().getDrawable(R.drawable.ic_tutorial2));
                screenshotDescription.setVisibility(View.VISIBLE);
                screenshotDescription.setText(getResources().getString(R.string.tutorial_screenshot2));
            }

            else if (page == 3) {
                progressImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_6_3));
                screenshotView.setImageDrawable(getResources().getDrawable(R.drawable.ic_tutorial3));
                screenshotDescription.setText(getResources().getString(R.string.tutorial_screenshot3));
            }

            else if (page == 4) {
                progressImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_6_4));
                screenshotView.setImageDrawable(getResources().getDrawable(R.drawable.ic_tutorial4));
                screenshotDescription.setText(getResources().getString(R.string.tutorial_screenshot4));
            }

            else if (page == 5) {
                progressImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_6_5));
                screenshotView.setImageDrawable(getResources().getDrawable(R.drawable.ic_tutorial5));
                screenshotDescription.setText(getResources().getString(R.string.tutorial_screenshot5));
            }

            else if (page == 6) {
                progressImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_6_6));
                screenshotView.setImageDrawable(getResources().getDrawable(R.drawable.ic_tutorial6));
                screenshotDescription.setText(getResources().getString(R.string.tutorial_screenshot6));
            }
        }


        //if left navigation button pressed, change page content accordingly based on page number
        else if (v.getId() == R.id.navLeft) {
            if (page > 1)
                page--;

            if (page == 1) {
                progressImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_6_1));
                navLeft.setVisibility(View.INVISIBLE);
                screenshotView.setVisibility(View.INVISIBLE);
                screenshotDescription.setVisibility(View.INVISIBLE);

                welcomeTo.setVisibility(View.VISIBLE);
                tapunlock.setVisibility(View.VISIBLE);
                appDescription.setVisibility(View.VISIBLE);
                navigation.setVisibility(View.VISIBLE);
                return;
            }

            if (page == 2) {
                progressImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_6_2));
                screenshotView.setImageDrawable(getResources().getDrawable(R.drawable.ic_tutorial2));
                screenshotDescription.setText(getResources().getString(R.string.tutorial_screenshot2));
            }

            else if (page == 3) {
                progressImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_6_3));
                screenshotView.setImageDrawable(getResources().getDrawable(R.drawable.ic_tutorial3));
                screenshotDescription.setText(getResources().getString(R.string.tutorial_screenshot3));
            }

            else if (page == 4) {
                progressImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_6_4));
                screenshotView.setImageDrawable(getResources().getDrawable(R.drawable.ic_tutorial4));
                screenshotDescription.setText(getResources().getString(R.string.tutorial_screenshot4));
            }

            else if (page == 5) {
                progressImage.setImageDrawable(getResources().getDrawable(R.drawable.ic_6_5));
                screenshotView.setImageDrawable(getResources().getDrawable(R.drawable.ic_tutorial5));
                screenshotDescription.setText(getResources().getString(R.string.tutorial_screenshot5));
            }
        }

        //if 'Skip' button pressed, launch MainActivity and finish
        else if (v.getId() == R.id.skip) {
            onBackPressed();
        }
    }


    //if back button pressed, launch MainActivity and finish
    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);

        startActivity(intent);

        finish();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        //if Android version 4.4 and bigger, clear translucent status bar flag
        if (Build.VERSION.SDK_INT >= 19) {
            this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }
    }
}
