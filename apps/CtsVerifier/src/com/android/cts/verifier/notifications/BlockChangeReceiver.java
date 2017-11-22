package com.android.cts.verifier.notifications;

import static android.app.NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED;
import static android.app.NotificationManager.ACTION_NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BlockChangeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(
                NotificationListenerVerifierActivity.PREFS, Context.MODE_PRIVATE);
        if (ACTION_NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED.equals(intent.getAction())
                || ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED.equals(intent.getAction())) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(
                    intent.getStringExtra(NotificationManager.EXTRA_BLOCK_STATE_CHANGED_ID),
                    intent.getBooleanExtra(NotificationManager.EXTRA_BLOCKED_STATE, false));
            editor.commit();
        }
    }
}
