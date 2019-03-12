package com.google.ar.sceneform.samples.solarsystem;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

public class CameraPermissionHelper {

    public static boolean hasCameraPermission(Activity activity) {
        return ContextCompat.checkSelfPermission(activity, Data.PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestCameraPermission(Activity activity){
        ActivityCompat.requestPermissions(
                activity, new String[] {Data.PERMISSION_CAMERA}, Data.PERMISSION_CAMERA_CODE
        );
    }

    public static boolean shouldShowRequestPermissionRationale(Activity activity){
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, Data.PERMISSION_CAMERA);
    }

    public static void launchPermissionSettings(Activity activity) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }

}
