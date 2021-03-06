package ua.p2psafety;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ua.p2psafety.adapters.StableArrayAdapter;
import ua.p2psafety.data.Prefs;
import ua.p2psafety.fragments.SendMessageFragment;
import ua.p2psafety.json.Event;
import ua.p2psafety.services.LocationService;
import ua.p2psafety.services.PowerButtonService;
import ua.p2psafety.services.XmppService;
import ua.p2psafety.util.EventManager;
import ua.p2psafety.util.GmailOAuth2Sender;
import ua.p2psafety.util.Logs;
import ua.p2psafety.util.NetworkManager;
import ua.p2psafety.util.Utils;

/**
 * Created by ihorpysmennyi on 12/14/13.
 */
public class SosActivity extends ActionBarActivity {
    public static final String FRAGMENT_KEY = "fragmentKey";
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private static final String ACTION_SYNCHRONIZE = "ua.p2psafety.action.SYNCHRONIZE";
    public static final String ACTION_SET_LOADING = "ua.p2psafety.action.SET_LOADING";
    public static final String ACTION_UNSET_LOADING = "ua.p2psafety.action.UNSET_LOADING";

    private UiLifecycleHelper mUiHelper;
    public static Logs mLogs;
    private EventManager mEventManager;
    private boolean mStartedFromHistory = false;

    private IntentFilter mIntentFilter;
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            mLogs.info("SosActivity. Got action from NetworkStateChangedReceiver: " + action);
            if (action.equals(ACTION_SET_LOADING))
            {
                Utils.setLoading(SosActivity.this, true);
            }
            else if (action.equals(ACTION_UNSET_LOADING))
            {
                Utils.setLoading(SosActivity.this, false);
            }
        }
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_sosmain);

        mLogs = new Logs(this);
        mLogs.info("\n\n\n==========================\n==============================");
        mLogs.info("SosActivity. onCreate()");
        mUiHelper = new UiLifecycleHelper(this, null);
        mUiHelper.onCreate(savedInstanceState);

        mEventManager = EventManager.getInstance(this);

        mLogs.info("SosActivity. onCreate. Initiating NetworkManager");
        NetworkManager.init(this);
        mLogs.info("SosActivity. onCreate. Starting PowerButtonService");
        startService(new Intent(this, PowerButtonService.class));
        if (!Utils.isServiceRunning(this, XmppService.class) &&
            Utils.isServerAuthenticated(this) &&
            !mEventManager.isEventActive())
        {
            startService(new Intent(this, XmppService.class));
        }
        Prefs.setProgramRunning(true, this);

        mIntentFilter = new IntentFilter(ACTION_SET_LOADING);
        mIntentFilter.addAction(ACTION_UNSET_LOADING);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        Intent intent = new Intent();
        intent.setAction(ACTION_SYNCHRONIZE);
        sendBroadcast(intent);
        mLogs.info("SosActivity. onPostCreate(). Action SYNCHRONIZE was sent");
    }

    @Override
    public void onResume() {
        super.onResume();
        mUiHelper.onResume();
        registerReceiver(mBroadcastReceiver, mIntentFilter);

        mStartedFromHistory = (getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
                == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY;

        int result = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (result != ConnectionResult.SUCCESS) {
            showErrorDialog(result);
        }

        if (!(mEventManager.isEventActive() || mEventManager.isSosStarted()) &&
            !Utils.isServiceRunning(this, LocationService.class))
        {
            startService(new Intent(this, LocationService.class));
        }

        Fragment fragment;

        // normal start
        mLogs.info("SosActiviy. onCreate. Normal start. Opening SendMessageFragment");
        fragment = new SendMessageFragment();

        FragmentManager fragmentManager = getSupportFragmentManager();
        if (!Utils.isFragmentAdded(fragment, fragmentManager))
        {
            fragmentManager.beginTransaction().addToBackStack(fragment.getClass().getName())
                    .replace(R.id.content_frame, fragment).commit();
        }

        String fragmentClass = getIntent().getStringExtra(FRAGMENT_KEY);
        if (fragmentClass != null && !mStartedFromHistory) {
            // activity started from outside
            // and requested to show specific fragment
            mLogs.info("SosActiviy. onCreate. Activity requested to open " + fragmentClass);
            fragment = Fragment.instantiate(this, fragmentClass);
            fragment.setArguments(getIntent().getExtras());

            if (!Utils.isFragmentAdded(fragment, fragmentManager))
            {
                fragmentManager.beginTransaction().addToBackStack(fragment.getClass().getName())
                        .replace(R.id.content_frame, fragment).commit();
            }

            setIntent(new Intent(this, SosActivity.class));
        }

        if (Utils.getEmail(this) != null && Utils.isNetworkConnected(this, mLogs) && Prefs.getGmailToken(this) == null)
        {
            mLogs.info("SosActiviy. onCreate. Getting new GmailOAuth token");
            GmailOAuth2Sender sender = new GmailOAuth2Sender(this);
            sender.initToken();
        }
        mLogs.info("SosActiviy. onCreate. Checking for location services");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i("onNewIntent", "NEW INTENT!");
        setIntent(intent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mLogs.info("SosActiviy.onActivityResult()");
        mUiHelper.onActivityResult(requestCode, resultCode, data);
        Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
    }

    @Override
    public void onPause() {
        super.onPause();
        mLogs.info("SosActiviy.onPause");
        mUiHelper.onPause();
        unregisterReceiver(mBroadcastReceiver);

        if (!(mEventManager.isEventActive() || mEventManager.isSosStarted())
            && Utils.isServiceRunning(this, LocationService.class))
        {
            stopService(new Intent(this, LocationService.class));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mLogs.info("SosActiviy.onDestroy()");
        mLogs.info("\n\n\n==========================\n==============================");
        mUiHelper.onDestroy();
        mLogs.close();
        Prefs.setProgramRunning(false, this);
    }

    @Override
    public void onBackPressed() {
        mLogs.info("SosActivity.onBackPressed()");
        Session currentSession = Session.getActiveSession();
        if (currentSession == null || currentSession.getState() != SessionState.OPENING) {
            super.onBackPressed();

            FragmentManager fm = getSupportFragmentManager();
            if (fm.getBackStackEntryCount() == 0) {
                finish();
            }
        } else {
            mLogs.info("SosActivity. onBackPressed. Ignoring");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (!(mEventManager.isEventActive() || mEventManager.isSosStarted())
            && Utils.isServiceRunning(this, LocationService.class))
        {
            stopService(new Intent(this, LocationService.class));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!((mEventManager.isEventActive() || mEventManager.isSosStarted())) &&
            !Utils.isServiceRunning(this, LocationService.class))
        {
            startService(new Intent(this, LocationService.class));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mLogs.info("SosActivity.onSaveInstanceState()");
        mUiHelper.onSaveInstanceState(outState);
        mLogs.info("SosActivity. onSaveInstanceState. Saving session");
        Session session = Session.getActiveSession();
        Session.saveSession(session, outState);
    }

    private void showErrorDialog(int result) {
        Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(result,
                this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
        if (errorDialog != null)
            errorDialog.show();
    }

    public void loginToFacebook(Activity activity, Session.StatusCallback callback) {
        mLogs.info("SosActivity. loginToFacebook()");
        if (!Utils.isNetworkConnected(activity, mLogs)) {
            mLogs.info("SosActivity. loginToFacebook. No network");
            Utils.errorDialog(activity, Utils.DIALOG_NO_CONNECTION);
            return;
        }
        Session session = Session.getActiveSession();
        if (session == null) {
            mLogs.info("SosActivity. No FB session. Opening a new one");
            Session.openActiveSession(activity, true, callback);
        }
        else if (!session.getState().isOpened() && !session.getState().isClosed()) {
            mLogs.info("SosActivity. loginToFacebook. FB session not opened AND not closed. Opening for read");
            session.openForRead(new Session.OpenRequest(activity)
                    //.setPermissions(Const.FB_PERMISSIONS_READ)
                    .setCallback(callback));
        } else {
            mLogs.info("SosActivity. loginToFacebook. FB session opened or closed. Opening a new one");
            Session.openActiveSession(activity, true, callback);
        }
    }
}