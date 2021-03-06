package ua.p2psafety.listeners;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ua.p2psafety.data.Prefs;
import ua.p2psafety.services.AudioRecordService;
import ua.p2psafety.services.LocationService;
import ua.p2psafety.services.VideoRecordService;
import ua.p2psafety.util.EventManager;
import ua.p2psafety.util.Utils;

public class PowerButtonListener extends BroadcastReceiver{
    final int mPressThreshold = 6; // 6 presses to activate sos
    final int mPressTimeout = 1000; // no more than 1 sec between presses
    final int mVibrationLength = 2000;
    int mPressCount = 0;
    long mLastPressTime = 0;

    @Override
    // user presses button 3 times, waits for vibration
    // and then presses button 3 times more
    public void onReceive(Context context, Intent intent) {
        mainFunctionality(context);
    }

    private void mainFunctionality(Context context) {
        if (EventManager.getInstance(context).isSosStarted())
            return; // Sos is already On, do nothing

        if (Utils.isServiceRunning(context, AudioRecordService.class) ||
            Utils.isServiceRunning(context, VideoRecordService.class))
        {
            return; // media record is going on - do nothing
        }

        long max_timeout;
        if (mPressCount == 3) {
            max_timeout = mVibrationLength + mPressTimeout;
        } else {
            max_timeout = mPressTimeout;
        }

        //if it is first click or time between each click is less than max_timeout, then pressCount++
        if (mLastPressTime == 0 || mPressCount ==0 ||
                ((System.currentTimeMillis() - mLastPressTime) <= max_timeout))
        {
            ++mPressCount;

            //if current pressCount==3 then we start vibration
            if (mPressCount == 3) {
                Utils.startVibration(context);
                context.startService(new Intent(context, LocationService.class));
            }
        }
        else
        {
            //else mPressCount=0 and we start again all functionality with first time clicked button
            mPressCount = 0;
            if (!Prefs.isSupporterMode(context))
            {
                context.stopService(new Intent(context, LocationService.class));
            }
            mainFunctionality(context);
        }
        //save current time
        mLastPressTime = System.currentTimeMillis();

        if (mPressCount == mPressThreshold) {
            // ATTN: vibrate every time when something's activated by hardware buttons
            // (startSos has vibration by itself though)
            if (Prefs.isSupporterMode(context)) {
                // in Supporter mode start audio record (if no other record is on)
                context.startService(new Intent(context, AudioRecordService.class));
                Utils.startVibration(context);
            } else {
                // if not in Supporter mode - start SOS
                EventManager.getInstance(context).startSos();
                mPressCount = 0;
            }
        }
    }
}
