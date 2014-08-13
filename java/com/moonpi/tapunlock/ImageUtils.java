package com.moonpi.tapunlock;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;


public class ImageUtils {

    //Bitmap (blurring) function using ScriptIntrinsicBlur (API 17+), returns blurred Bitmap
    //takes radius 1-25
    public static Bitmap fastBlur(Context context, Bitmap sentBitmap, int radius) {
        float BITMAP_SCALE = 0.1f;
        float BLUR_RADIUS = (float)radius;

        if(Build.VERSION.SDK_INT > 16) {
            int width = Math.round(sentBitmap.getWidth() * BITMAP_SCALE);
            int height = Math.round(sentBitmap.getHeight() * BITMAP_SCALE);

            Bitmap inputBitmap = Bitmap.createScaledBitmap(sentBitmap, width, height, false);
            Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);

            RenderScript rs = RenderScript.create(context);
            ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
            Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);

            theIntrinsic.setRadius(BLUR_RADIUS);
            theIntrinsic.setInput(tmpIn);
            theIntrinsic.forEach(tmpOut);
            tmpOut.copyTo(outputBitmap);

            return outputBitmap;
        }

        return null;
    }


    //Bitmap function that turns the passed 'drawable' into a Bitmap
    public static Bitmap drawableToBitmap(Drawable drawable) {
        if(drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable)drawable).getBitmap();
        }

        Bitmap bitmap = null;
        if(!(drawable instanceof ColorDrawable)) {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }

        return bitmap;
    }


    //function to store passed Bitmap as .png on external storage
    public static void storeImage(Bitmap image) {
        File pictureFile = new File(Environment.getExternalStorageDirectory() + "/TapUnlock/blurredWallpaper.png");

        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            image.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    //boolean function to check whether a blurred wallpaper png exists or not
    public static Boolean doesBlurredWallpaperExist() {
        File blurredWallpaper = new File(Environment.getExternalStorageDirectory() + "/TapUnlock/blurredWallpaper.png");

        if(blurredWallpaper.exists())
            return true;

        else
            return false;
    }


    //Drawable function that returns the blurred wallpaper png as Drawable
    public static Drawable retrieveWallpaperDrawable() {
        Drawable wallpaper = Drawable.createFromPath(Environment.getExternalStorageDirectory() + "/TapUnlock/blurredWallpaper.png");

        return wallpaper;
    }
}
