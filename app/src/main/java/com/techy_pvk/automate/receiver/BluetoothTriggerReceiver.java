package com.techy_pvk.automate.receiver;

import android.Manifest;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.techy_pvk.automate.data.TriggerMatcher;

/**
 * Declared in the manifest (not registered at runtime) so it fires even if the app's process
 * isn't currently running - ACTION_ACL_CONNECTED is one of the broadcasts Android exempts from
 * the background-broadcast limits introduced in Android 8, specifically so receivers like this
 * one keep working.
 *
 * ACL_CONNECTED fires for *any* Bluetooth device completing a low-level connection, not just
 * audio ones (e.g. it also fires for keyboards, smartwatches) - isAudioDevice() filters that
 * down to headsets/earbuds/speakers using the device's reported Bluetooth class.
 */
public class BluetoothTriggerReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) return;

        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (device == null || !isAudioDevice(context, device)) return;

        TriggerMatcher.handleBluetoothConnected(context, safeGetName(context, device));
    }

    private boolean isAudioDevice(Context context, BluetoothDevice device) {
        if (!hasConnectPermission(context)) return false;
        try {
            BluetoothClass bluetoothClass = device.getBluetoothClass();
            return bluetoothClass != null
                    && bluetoothClass.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO;
        } catch (SecurityException e) {
            return false;
        }
    }

    private String safeGetName(Context context, BluetoothDevice device) {
        if (!hasConnectPermission(context)) return null;
        try {
            return device.getName();
        } catch (SecurityException e) {
            return null;
        }
    }

    private boolean hasConnectPermission(Context context) {
        // BLUETOOTH_CONNECT is only a runtime permission from API 31 onward; below that,
        // reading device name/class is covered by the (normal, always-granted) BLUETOOTH permission.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }
}
