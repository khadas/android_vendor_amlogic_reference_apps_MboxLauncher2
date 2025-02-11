/*
* Copyright (c) 2014 Amlogic, Inc. All rights reserved.
* *
This source code is subject to the terms and conditions defined in the
* file 'LICENSE' which is part of this source code package.
* *
Description: java file
*/

package com.droidlogic.mboxlauncher;

import android.app.SearchManager;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.pm.ActivityInfo;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.ContentUris;
import android.database.ContentObserver;
import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.media.tv.TvView;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.View.OnTouchListener;
import android.media.tv.TvContentRating;
import android.media.tv.TvTrackInfo;
import android.content.ContentValues;
import android.database.Cursor;

import android.widget.TextView;
import android.widget.ImageView;
import android.widget.GridView;
import android.widget.BaseAdapter;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.widget.FrameLayout;

import com.droidlogic.app.SystemControlManager;
import com.droidlogic.app.DataProviderManager;
import com.droidlogic.app.DroidLogicUtils;
import com.droidlogic.app.tv.ChannelInfo;
import com.droidlogic.app.tv.DroidLogicTvUtils;
import com.droidlogic.app.tv.TvControlManager;
import com.droidlogic.app.tv.TvDataBaseManager;
import com.droidlogic.app.DataProviderManager;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

public class Launcher extends Activity{

    private final static String TAG="MediaBoxLauncher";

    private final String net_change_action = "android.net.conn.CONNECTIVITY_CHANGE";
    private final String wifi_signal_action = "android.net.wifi.RSSI_CHANGED";
    private final String outputmode_change_action = "android.amlogic.settings.CHANGE_OUTPUT_MODE";
    private final String DROIDVOLD_MEDIA_UNMOUNTED_ACTION = "com.droidvold.action.MEDIA_UNMOUNTED";
    private final String DROIDVOLD_MEDIA_EJECT_ACTION = "com.droidvold.action.MEDIA_EJECT";
    private final String DROIDVOLD_MEDIA_MOUNTED_ACTION = "com.droidvold.action.MEDIA_MOUNTED";
    private static final String ACTION_OTP_INPUT_SOURCE_CHANGE = "droidlogic.tv.action.OTP_INPUT_SOURCE_CHANGED";

    public static String COMPONENT_TV_SOURCE = "com.droidlogic.tv.settings/com.droidlogic.tv.settings.TvSourceActivity";
    public static String COMPONENT_TV_APP = "com.droidlogic.tvsource/com.droidlogic.tvsource.DroidLogicTv";
    public static String COMPONENT_LIVE_TV = "com.droidlogic.android.tv/com.android.tv.TvActivity";
    public static String COMPONENT_TV_SETTINGS = "com.android.tv.settings/com.android.tv.settings.more.MorePrefFragmentActivity";
    public static String DEFAULT_INPUT_ID = "com.droidlogic.tvinput/.services.ATVInputService/HW0";

    public static final String PROP_TV_PREVIEW = "vendor.tv.is.preview.window";
    public static final String COMPONENT_THOMASROOM = "com.android.gl2jni";
    public static final String COMPONENT_TVSETTINGS = "com.android.tv.settings/com.android.tv.settings.MainSettings";
    public static final String DTVKIT_PACKAGE = "com.droidlogic.dtvkit.inputsource";
    public static boolean isLaunchingTvSettings = false;
    public static boolean isLaunchingThomasroom = false;

    public static final String EVENT_SIGNAL_SCRAMBLED_SERVICE = "signal_scrambled_service";
    public static final String EVENT_SIGNAL_INVALID_SERVICE = "signal_invalid_service";
    public static final String EVENT_SIGNAL_DATA_SERVICE = "signal_data_service";

    public static final int TYPE_VIDEO                           = 0;
    public static final int TYPE_RECOMMEND                       = 1;
    public static final int TYPE_MUSIC                           = 2;
    public static final int TYPE_APP                             = 3;
    public static final int TYPE_LOCAL                           = 4;
    public static final int TYPE_SETTINGS                        = 5;
    public static final int TYPE_HOME_SHORTCUT                   = 6;
    public static final int TYPE_APP_SHORTCUT                    = 7;

    public static final int MODE_HOME                            = 0;
    public static final int MODE_VIDEO                           = 1;
    public static final int MODE_RECOMMEND                       = 2;
    public static final int MODE_MUSIC                           = 3;
    public static final int MODE_APP                             = 4;
    public static final int MODE_LOCAL                           = 5;
    public static final int MODE_CUSTOM                          = 6;
    private int current_screen_mode = 0;
    private int saveModeBeforeCustom = 0;

    private static final int MSG_REFRESH_SHORTCUT                = 0;
    private static final int MSG_RECOVER_HOME                    = 1;
    private static final int MSG_START_CUSTOM_SCREEN             = 2;
    private static final int MSG_SET_HOVER_VIEW                  = 3;
    private static final int animDuration                        = 70;
    private static final int animDelay                           = 0;

    private static final int[] childScreens = {
        MODE_VIDEO,
        MODE_RECOMMEND,
        MODE_APP,
        MODE_MUSIC,
        MODE_LOCAL
    };
    private static final int[] childScreensTv = {
        MODE_RECOMMEND,
        MODE_APP,
        MODE_MUSIC,
        MODE_LOCAL
    };
    private int[] mChildScreens = childScreens;

    private GridView lv_status;
    private HoverView mHoverView;
    private ViewGroup mHomeView = null;
    private AppLayout mSecondScreen = null;
    private View saveHomeFocusView = null;
    private MyGridLayout mHomeShortcutView = null;
    private MyRelativeLayout mVideoView;
    private MyRelativeLayout mRecommendView;
    private MyRelativeLayout mMusicView;
    private MyRelativeLayout mAppView;
    private MyRelativeLayout mLocalView;
    private MyRelativeLayout mSettingsView;
    private CustomView mCustomView = null;

    public static int HOME_SHORTCUT_COUNT                      = 10;

    private TvView tvView = null;
    private TextView tvPrompt = null;
    public static final int TV_MODE_NORMAL                     = 0;
    public static final int TV_MODE_TOP                        = 1;
    public static final int TV_MODE_BOTTOM                     = 2;
    private static final int TV_PROMPT_GOT_SIGNAL              = 0;
    private static final int TV_PROMPT_NO_SIGNAL               = 1;
    private static final int TV_PROMPT_IS_SCRAMBLED            = 2;
    private static final int TV_PROMPT_NO_DEVICE               = 3;
    private static final int TV_PROMPT_SPDIF                   = 4;
    private static final int TV_PROMPT_BLOCKED                 = 5;
    private static final int TV_PROMPT_NO_CHANNEL              = 6;
    private static final int TV_PROMPT_RADIO                   = 7;
    private static final int TV_PROMPT_TUNING                  = 8;
    private static final int TV_PROMPT_IS_DATA_SERVICE         = 9;
    private static final int TV_PROMPT_IS_SKIP                 = 10;
    private static final int TV_PROMPT_IS_DELETE               = 11;
    private static final int TV_PROMPT_IS_INVALID_SERVICE      = 12;
    private static final int TV_WINDOW_WIDTH                   = 296;
    private static final int TV_WINDOW_HEIGHT                  = 176;
    private static final int TV_WINDOW_NORMAL_LEFT             = 130;
    private static final int TV_WINDOW_NORMAL_TOP              = 196;
    private static final int TV_WINDOW_RIGHT_LEFT              = 1230 - TV_WINDOW_WIDTH;
    private static final int TV_WINDOW_TOP_TOP = 0;
    private static final int TV_WINDOW_BOTTOM_TOP              = 690 - TV_WINDOW_HEIGHT;
    private static final int TV_MSG_PLAY_TV                    = 0;
    private static final int TV_MSG_BOOTUP_TO_TVAPP                = 1;

    private static final int INPUT_ID_LENGTH = 3;

    public int tvViewMode = -1;
    private int mTvTop = -1;
    private boolean isRadioChannel = false;
    private boolean isChannelBlocked = false;
    private boolean isAvNoSignal = false;
    private ChannelObserver mChannelObserver;
    private TvInputManager mTvInputManager;
    private TvInputChangeCallback mTvInputChangeCallback;
    private TvDataBaseManager mTvDataBaseManager;
    private String mTvInputId;
    private Uri mChannelUri;

    public static float startX;
    public static float endX;
    private SystemControlManager mSystemControlManager;
    private AppDataLoader mAppDataLoader;
    private StatusLoader mStatusLoader;
    private Object mlock = new Object();

    private FrameLayout mMainFrameLayout;
    private FrameLayout mBlackFrameLayout;

    private static final int REQUEST_CODE_START_TV_SOURCE = 3;
    private boolean mTvStartPlaying = false;
    private boolean resumeFromTVSource = false;
    private boolean mActivityResumed;
    private boolean mSyncChannelUri = false;
    private boolean mIsPortraitMode = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        skipUserSetup();
        mTvInputManager = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);

        if (mTvInputManager == null) {
            Log.d(TAG, "------onCreate mbox");
            setContentView(R.layout.main_box);
        } else {
            Log.d(TAG, "------onCreate tv");
            setContentView(R.layout.main);
        }

        mIsPortraitMode = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        mSystemControlManager = SystemControlManager.getInstance();
        mMainFrameLayout = (FrameLayout) findViewById(R.id.layout_main);
        mBlackFrameLayout = (FrameLayout) findViewById(R.id.layout_black);
        if (!checkNeedStartTvApp(false)) {
            mBlackFrameLayout.setVisibility(View.GONE);
            mMainFrameLayout.setVisibility(View.VISIBLE);
        }
        if (needPreviewFeture()) {
            mTvDataBaseManager = new TvDataBaseManager(this);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            COMPONENT_TV_APP = COMPONENT_LIVE_TV;
        }

        if (DesUtils.isAmlogicChip() == false) {
            //finish();
        }

        mAppDataLoader = new AppDataLoader(this);
        mStatusLoader = new StatusLoader(this);

        mAppDataLoader.update();
        initChildViews();
    }

    public boolean isMboxFeture () {
        return mSystemControlManager.getPropertyBoolean("ro.vendor.platform.has.mbxuimode", false);
    }

    public boolean isTvFeture () {
        return TextUtils.equals(mSystemControlManager.getPropertyString("ro.vendor.platform.is.tv", ""), "1");
    }

    public boolean needPreviewFeture () {
        return isTvFeture() && mSystemControlManager.getPropertyBoolean("vendor.tv.need.droidlogic.preview_window", false);
    }

    private void releasePlayingTv() {
        Log.d(TAG, "------releasePlayingTv");
        isChannelBlocked = false;
        recycleBigBackgroundDrawable();
        mTvHandler.removeMessages(TV_MSG_PLAY_TV);
        releaseTvView();
        mTvStartPlaying = false;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "------onResume");

        if (resumeFromTVSource) {
            //launcher will start tvapp, don't do anything
            resumeFromTVSource = false;
            return;
        }

        if (checkNeedStartTvApp(true)) {
            mTvHandler.sendEmptyMessage(TV_MSG_BOOTUP_TO_TVAPP);
            return;
        } else if (mMainFrameLayout.getVisibility() != View.VISIBLE) {
            mBlackFrameLayout.setVisibility(View.GONE);
            mMainFrameLayout.setVisibility(View.VISIBLE);
        }

        if (isTvFeture()) {
            stopMusicPlayer();
        }
        //getMainView().animate().translationY(0).start();
        setBigBackgroundDrawable();
        displayShortcuts();
        displayStatus();
        displayDate();
        registerBroadcasts();

        if (needPreviewFeture()) {
            //need to init channel when tv provider is ready
            tvView.setVisibility(View.VISIBLE);
            // if pressing Home key in Launcher screen, don't re-tune TV source.
            if (mTvStartPlaying) {
                return;
            }
            mTvHandler.sendEmptyMessage(TV_MSG_PLAY_TV);
        } else if (tvView != null) {
            tvView.setVisibility(View.INVISIBLE);
        }
        mActivityResumed = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mActivityResumed = false;
        mHandler.removeMessages(MSG_START_CUSTOM_SCREEN);
        Log.d(TAG, "------onPause");

        if (needPreviewFeture()) {
            //if launch Thomas' Room, we should call onStop() to release TvView.
            if (isLaunchingThomasroom || isLaunchingTvSettings
                    || mSecondScreen.getVisibility() == View.VISIBLE) {
                releasePlayingTv();
                isLaunchingThomasroom = false;
                isLaunchingTvSettings = false;
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "------onStop");

        if (needPreviewFeture() && mTvStartPlaying) {
            releasePlayingTv();
        }
        unregisterBroadcasts();
    }

    @Override
    protected void onDestroy(){
        Log.d(TAG, "------onDestroy");
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            setHomeViewVisible(true);
            current_screen_mode = MODE_HOME;
            MyRelativeLayout videoView = (MyRelativeLayout)findViewById(R.id.layout_video);
            videoView.requestFocus();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            startX = ev.getX();
        } else if (ev.getAction() == MotionEvent.ACTION_UP) {
            endX = ev.getX();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent (MotionEvent event){
        if (event.getAction() == MotionEvent.ACTION_UP) {
        }
        return true;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
                switch (current_screen_mode) {
                    case MODE_APP:
                        mSecondScreen.clearAnimation();
                    case MODE_VIDEO:
                    case MODE_RECOMMEND:
                    case MODE_MUSIC:
                    case MODE_LOCAL:
                        setHomeViewVisible(true);
                        break;
                    case MODE_CUSTOM:
                        current_screen_mode = saveModeBeforeCustom;
                        mAppDataLoader.update();
                        break;
                }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
        } else if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            ComponentName globalSearchActivity = searchManager.getGlobalSearchActivity();
            if (globalSearchActivity == null) {
                return false;
            }
            Intent intent = new Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setComponent(globalSearchActivity);
            Bundle appSearchData = new Bundle();
            appSearchData.putString("source", "launcher-search");
            intent.putExtra(SearchManager.APP_DATA, appSearchData);
            startActivity(intent);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_TV_INPUT) {
            startTvSource();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void displayStatus() {
        LocalAdapter ad = new LocalAdapter(this,
                mStatusLoader.getStatusData(),
                R.layout.homelist_item,
                new String[] {StatusLoader.ICON},
                new int[] {R.id.item_type});
        lv_status.setAdapter(ad);
    }

    private void displayDate() {
        TextView  time = (TextView)findViewById(R.id.tx_time);
        TextView  date = (TextView)findViewById(R.id.tx_date);
        time.setText(mStatusLoader.getTime());
        time.setTypeface(Typeface.DEFAULT_BOLD);
        date.setText(mStatusLoader.getDate());
    }

    private void initChildViews(){
        lv_status = (GridView)findViewById(R.id.list_status);
        lv_status.setOnTouchListener(new OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    return true;
                }
                return false;
            }
        });
        mHoverView = (HoverView)findViewById(R.id.hover_view);
        mHomeView = (ViewGroup)findViewById(R.id.layout_homepage);
        mSecondScreen  = (AppLayout)findViewById(R.id.second_screen);
        mHomeShortcutView = (MyGridLayout)findViewById(R.id.gv_shortcut);
        mVideoView = (MyRelativeLayout)findViewById(R.id.layout_video);
        mRecommendView = (MyRelativeLayout)findViewById(R.id.layout_recommend);
        mMusicView = (MyRelativeLayout)findViewById(R.id.layout_music);
        mAppView = (MyRelativeLayout)findViewById(R.id.layout_app);
        mLocalView = (MyRelativeLayout)findViewById(R.id.layout_local);
        mSettingsView = (MyRelativeLayout)findViewById(R.id.layout_setting);
        setHomeRectType();

        tvView = (TvView)findViewById(R.id.tv_view);
        tvPrompt = (TextView)findViewById(R.id.tx_tv_prompt);
        if (needPreviewFeture()) {
            mChildScreens = childScreensTv;
            setTvView();
        } else {
            mChildScreens = childScreens;
            if (tvView != null) {
                tvView.setVisibility(View.GONE);
            }
            tvPrompt.setVisibility(View.GONE);
        }
    }

    private void setBigBackgroundDrawable() {
        getMainView().setBackgroundDrawable(getResources().getDrawable(R.drawable.bg));
        ((ImageView)findViewById(R.id.img_video)).setImageDrawable(getResources().getDrawable(R.drawable.img_video));
        ((ImageView)findViewById(R.id.img_recommend)).setImageDrawable(getResources().getDrawable(R.drawable.img_recommend));
        ((ImageView)findViewById(R.id.img_music)).setImageDrawable(getResources().getDrawable(R.drawable.img_music));
        ((ImageView)findViewById(R.id.img_app)).setImageDrawable(getResources().getDrawable(R.drawable.img_app));
        ((ImageView)findViewById(R.id.img_local)).setImageDrawable(getResources().getDrawable(R.drawable.img_local));
        ((ImageView)findViewById(R.id.img_setting)).setImageDrawable(getResources().getDrawable(R.drawable.img_setting));
    }

    private void recycleBigBackgroundDrawable() {
        Drawable drawable = getMainView().getBackground();
        getMainView().setBackgroundResource(0);
        if (drawable != null)
            drawable.setCallback(null);

        drawable = ((ImageView)findViewById(R.id.img_video)).getDrawable();
        if (drawable != null)
            drawable.setCallback(null);
    }

    private void setHomeRectType(){
        mVideoView.setType(TYPE_VIDEO);
        mMusicView.setType(TYPE_MUSIC);
        mRecommendView.setType(TYPE_RECOMMEND);
        mAppView.setType(TYPE_APP);
        mLocalView.setType(TYPE_LOCAL);

        Intent intent = new Intent();
        //intent.setComponent(ComponentName.unflattenFromString(COMPONENT_TV_SETTINGS));
        intent.setComponent(ComponentName.unflattenFromString(COMPONENT_TVSETTINGS));
        mSettingsView.setType(TYPE_SETTINGS);
        mSettingsView.setIntent(intent);
    }

    public void displayShortcuts() {
        mAppDataLoader.update();
        switch (current_screen_mode) {
            case MODE_HOME:
            case MODE_VIDEO:
            case MODE_RECOMMEND:
            case MODE_MUSIC:
            case MODE_APP:
            case MODE_LOCAL:
                setShortcutScreen(current_screen_mode);
                break;
            default:
                setShortcutScreen(saveModeBeforeCustom);
                break;
        }
    }

    private void updateStatus() {
        ((BaseAdapter) lv_status.getAdapter()).notifyDataSetChanged();
    }

    public int getCurrentScreenMode() {
        return current_screen_mode;
    }
    public void setShortcutScreen(int mode) {
        resetShortcutScreen(mode);
        current_screen_mode = mode;
    }

    public void resetShortcutScreen(int mode) {
        mHandler.removeMessages(MSG_REFRESH_SHORTCUT);
        Log.d(TAG, "resetShortcutScreen mode is " + mode);
        if (mAppDataLoader.isDataLoaded()) {
            if (mode == MODE_HOME) {
                mHomeShortcutView.setLayoutView(mode, mAppDataLoader.getShortcutList(mode));
            } else {
                mSecondScreen.setLayout(mode, mAppDataLoader.getShortcutList(mode));
            }
        } else {
            Message msg = new Message();
            msg.what = MSG_REFRESH_SHORTCUT;
            msg.arg1 = mode;
            mHandler.sendMessageDelayed(msg, 100);
        }
    }

    private int getChildModeIndex() {
        for (int i = 0; i < mChildScreens.length; i++) {
            if (current_screen_mode == mChildScreens[i]) {
                return i;
            }
        }
        return -1;
    }

    public AppDataLoader getAppDataLoader() {
        return mAppDataLoader;
    }

    public void switchSecondScren(int animType){
        int mode = -1;
        if (animType == AppLayout.ANIM_LEFT) {
            mode = mChildScreens[(getChildModeIndex() + mChildScreens.length - 1) % mChildScreens.length];
        } else {
            mode = mChildScreens[(getChildModeIndex() + 1) % mChildScreens.length];
        }
        mSecondScreen.setLayoutWithAnim(animType, mode, mAppDataLoader.getShortcutList(mode));
        current_screen_mode = mode;
    }

    public void setHomeViewVisible (boolean isShowHome) {
        if (isShowHome) {
            if (mCustomView != null && current_screen_mode == MODE_CUSTOM) {
                mCustomView.recoverMainView();
            }
            current_screen_mode = MODE_HOME;
            mSecondScreen.setVisibility(View.GONE);
            mHomeView.setVisibility(View.VISIBLE);
            if (!mHomeView.hasFocus()) {
                MyRelativeLayout videoView = (MyRelativeLayout)findViewById(R.id.layout_video);
                videoView.requestFocus();
            }
            if (needPreviewFeture())
                setTvViewPosition(TV_MODE_NORMAL);
        } else {
            mHomeView.setVisibility(View.GONE);
            mSecondScreen.setVisibility(View.VISIBLE);
            if (needPreviewFeture()) {
                setTvViewPosition(TV_MODE_BOTTOM);
            }
        }
    }

    public HoverView getHoverView(){
        return mHoverView;
    }

    public ViewGroup getHomeView(){
        return mHomeView;
    }

    public ViewGroup getMainView(){
        return (ViewGroup)findViewById(R.id.layout_main);
    }

    public ViewGroup getRootView(){
        return (ViewGroup)findViewById(R.id.layout_root);
    }

    public Object getLock() {
        return mlock;
    }

    public void saveHomeFocus(View view) {
        saveHomeFocusView = view;
    }

    private void sendKeyCode(final int keyCode){
        new Thread () {
            public void run() {
                try {
                    Instrumentation inst = new Instrumentation();
                    inst.sendKeyDownUpSync(keyCode);
                } catch (Exception e) {
                    Log.e("Exception when sendPointerSync", e.toString());
                }
            }
        }.start();
    }

    private void updateAppList(Intent intent){
        boolean isShortcutIndex = false;
        String packageName = null;

        if (intent.getData() != null) {
            packageName = intent.getData().getSchemeSpecificPart();
            if (packageName == null || packageName.length() == 0) {
                // they sent us a bad intent
                return;
            }
            if (packageName.equals("com.android.provision"))
                return;
        }
        displayShortcuts();
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REFRESH_SHORTCUT:
                    resetShortcutScreen(msg.arg1);
                    break;
                case MSG_RECOVER_HOME:
                    resetShortcutScreen(current_screen_mode);
                    break;
                case MSG_START_CUSTOM_SCREEN:
                    View CustomView=(View)msg.obj;
                    CustomScreen(CustomView);
                    break;
                case MSG_SET_HOVER_VIEW:
                    getHoverView().setHover((MyRelativeLayout)(msg.obj));
                    break;
                default:
                    break;
            }
        }
    };

    private BroadcastReceiver mediaReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            //Log.d(TAG, " mediaReceiver          action = " + action);
            if (action == null)
                return;

            if (Intent.ACTION_MEDIA_EJECT.equals(action)
                    || Intent.ACTION_MEDIA_UNMOUNTED.equals(action)
                    || Intent.ACTION_MEDIA_MOUNTED.equals(action)
                    || action.equals ("com.droidvold.action.MEDIA_UNMOUNTED")
                    || action.equals ("com.droidvold.action.MEDIA_EJECT")
                    || action.equals ("com.droidvold.action.MEDIA_MOUNTED")
                    ) {
                displayStatus();
                updateStatus();
            }
        }
    };

    private BroadcastReceiver netReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action == null)
                return;

             //Log.d(TAG, "netReceiver         action = " + action);
            if (action.equals(Intent.ACTION_TIME_CHANGED)) {
                displayDate();
            }
            if (action.equals(Intent.ACTION_TIME_TICK)) {
                displayDate();
            } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE.equals(action)
                    || Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE.equals(action)) {
                updateAppList(intent);
            }else {
                displayStatus();
                updateStatus();
            }
        }
    };

    private BroadcastReceiver appReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub

            final String action = intent.getAction();
            Log.d(TAG,"appReceiver receive " + action);
            if (Intent.ACTION_PACKAGE_CHANGED.equals(action)
                    || Intent.ACTION_PACKAGE_REMOVED.equals(action)
                    || Intent.ACTION_PACKAGE_ADDED.equals(action)) {

                updateAppList(intent);
            }
        }
    };

    private Intent mDelayedSourceChange;
    private BroadcastReceiver otherReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {

            final String action = intent.getAction();
            Log.d(TAG," receive " + action);
            if (ACTION_OTP_INPUT_SOURCE_CHANGE.equals(action)) {
                Intent i = new Intent(TvInputManager.ACTION_SETUP_INPUTS);
                i.putExtra("from_cec_otp", true);
                i.putExtra(TvInputInfo.EXTRA_INPUT_ID, intent.getStringExtra(TvInputInfo.EXTRA_INPUT_ID));
                if (!isBootvideoStopped()) {
                    mDelayedSourceChange = i;
                    if (mActivityResumed) {
                        mTvHandler.sendEmptyMessage(TV_MSG_BOOTUP_TO_TVAPP);
                    }
                } else if (mActivityResumed) {
                    Toast.makeText(Launcher.this, R.string.toast_otp_input_change, Toast.LENGTH_LONG).show();
                    startOtpSource(i);
                } else {
                    Log.d(TAG," acitivity not resumed or bootvideo not finished, drop " + ACTION_OTP_INPUT_SOURCE_CHANGE);
                }
            } else if(action.equals("android.intent.action.BOOT_COMPLETED")) {
                if (needPreviewFeture() && mTvDataBaseManager != null) {
                    ChannelInfo currentChannel = mTvDataBaseManager.getChannelInfo(mChannelUri);
                    if (mChannelUri == null || (DroidLogicTvUtils.matchsWhich(mChannelUri) == 2 && currentChannel == null)) {
                        Log.d(TAG,"Switch channels if you haven't switched channels before");
                        mTvHandler.sendEmptyMessage(TV_MSG_PLAY_TV);
                    }
                }
            }
        }
    };

    private boolean mBroadcastsRegistered = false;
    private void registerBroadcasts() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction (DROIDVOLD_MEDIA_UNMOUNTED_ACTION);
        filter.addAction (DROIDVOLD_MEDIA_MOUNTED_ACTION);
        filter.addAction (DROIDVOLD_MEDIA_EJECT_ACTION);
        filter.addDataScheme("file");
        registerReceiver(mediaReceiver, filter, 2/*RECEIVER_EXPORTED*/);

        filter = new IntentFilter();
        filter.addAction(net_change_action);
        filter.addAction(wifi_signal_action);
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(outputmode_change_action);
        filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        registerReceiver(netReceiver, filter, 2/*RECEIVER_EXPORTED*/);

        filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(appReceiver, filter, 2/*RECEIVER_EXPORTED*/);

        filter = new IntentFilter();
        filter.addAction(ACTION_OTP_INPUT_SOURCE_CHANGE);
        filter.addAction("android.intent.action.BOOT_COMPLETED");
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        registerReceiver(otherReceiver, filter, 2/*RECEIVER_EXPORTED*/);

        mBroadcastsRegistered = true;
    }

    private void unregisterBroadcasts() {
        if (!mBroadcastsRegistered) {
            return;
        }
        unregisterReceiver(mediaReceiver);
        unregisterReceiver(netReceiver);
        unregisterReceiver(appReceiver);
        unregisterReceiver(otherReceiver);
        mBroadcastsRegistered = false;
    }

    public void startTvSettings() {
        try {
            Intent intent = new Intent();
            intent.setComponent(ComponentName.unflattenFromString(COMPONENT_TV_SETTINGS));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, " can't start TvSettings:" + e);
        }
    }

    public void startTvSource() {
        try {
            Intent intent = new Intent();
            intent.setComponent(ComponentName.unflattenFromString(COMPONENT_TV_SOURCE));
            intent.putExtra("requestpackage", "com.droidlogic.mboxlauncher");
            startActivityForResult(intent, REQUEST_CODE_START_TV_SOURCE);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, " can't start TvSources:" + e);
        }
    }

    private void startOtpSource(Intent intent) {
        Log.d(TAG, "startOtpSource");
        if (mTvStartPlaying) {
            releasePlayingTv();
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, " can't start LiveTv:" + e);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult requestCode = " + requestCode + ", resultCode = " + resultCode);
        switch (requestCode) {
            case REQUEST_CODE_START_TV_SOURCE:
                if (resultCode == RESULT_OK && data != null) {
                    resumeFromTVSource = true;
                    if (mTvStartPlaying) {
                        releasePlayingTv();
                    }
                    try {
                        startActivity(data);
                    } catch (ActivityNotFoundException e) {
                        Log.e(TAG, " can't start LiveTv:" + e);
                    }
                }
                break;
            default:
                break;
        }

    }

    public void startTvApp() {
        try {
            Intent intent = new Intent();
            intent.setComponent(ComponentName.unflattenFromString(COMPONENT_TV_APP));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, " can't start TvSettings:" + e);
        }
    }

    private boolean checkNeedStartTvApp(boolean close) {
        boolean ret = false;
        if ((DroidLogicUtils.isTv() &&
            !TextUtils.equals(mSystemControlManager.getProperty("vendor.tv.launcher.firsttime.launch"), "false") &&
            DataProviderManager.getIntValue(Launcher.this, DroidLogicTvUtils.TV_START_UP_ENTER_APP, 0) > 0)
            || mDelayedSourceChange != null) {
            Log.d(TAG, "starting tvapp...");

            ret = true;
        }
        if (close) {
            mSystemControlManager.setProperty("vendor.tv.launcher.firsttime.launch", "false");
        }

        return ret;
    }

    public void startCustomScreen(View view) {
        Message msg = new Message();
        msg.what = MSG_START_CUSTOM_SCREEN;
        msg.obj = view;
        mHandler.sendMessageDelayed(msg, 500);
    }

    public void CustomScreen(View view) {
        if (current_screen_mode == MODE_CUSTOM) return;
        mHoverView.clear();
        if (needPreviewFeture()) {
            hideTvViewForCustom();
        }
        saveModeBeforeCustom = current_screen_mode;
        mCustomView = new CustomView(this, view, current_screen_mode);
        current_screen_mode = MODE_CUSTOM;

        Rect rect = new Rect();
        view.getGlobalVisibleRect(rect);
        if (rect.top > getResources().getDisplayMetrics().heightPixels / 2) {
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            getRootView().addView(mCustomView, lp);
        } else {
            getRootView().addView(mCustomView);
        }

        getMainView().bringToFront();
    }

    public void recoverFromCustom() {
        mHandler.sendEmptyMessage(MSG_RECOVER_HOME);
        if (needPreviewFeture()) {
            recoverTvViewForCustom();
        }
        getMainView().setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        getMainView().requestFocus();
    }

    public void setHoverView(MyRelativeLayout view) {
        mHandler.removeMessages(MSG_SET_HOVER_VIEW);
        getHoverView().setHover(view);
    }
    public void setHoverViewDelayed(MyRelativeLayout view, long time) {
        mHandler.removeMessages(MSG_SET_HOVER_VIEW);
        Message msg = new Message();
        msg.what = MSG_SET_HOVER_VIEW;
        msg.obj = view;
        mHandler.sendMessageDelayed(msg, time);
    }

    public static int pxToDip(Context context, float pxValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }

    public static int dipToPx(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    private void setTvView() {
        TextView title_video = (TextView)findViewById(R.id.tx_video);
        title_video.setText(R.string.str_tvapp);
        tvView.setVisibility(View.VISIBLE);
        tvView.setCallback(new TvViewInputCallback());
        tvView.setZOrderMediaOverlay(false);

        setTvViewPosition(TV_MODE_NORMAL);
    }

    private void hideTvViewForCustom () {
        tvPrompt.setBackgroundDrawable(getResources().getDrawable(R.drawable.black));
        tvView.setVisibility(View.INVISIBLE);
    }

    private void recoverTvViewForCustom () {
        tvView.setVisibility(View.VISIBLE);
        if (isRadioChannel) {
            tvPrompt.setBackgroundDrawable(getResources().getDrawable(R.drawable.bg_radio));
        } else {
            tvPrompt.setBackgroundDrawable(null);
        }
        mTvHandler.removeMessages(TV_MSG_PLAY_TV);
        mTvHandler.sendEmptyMessage(TV_MSG_PLAY_TV);
    }

    public void setTvViewElevation(float elevation) {
        tvView.setElevation(elevation);
        tvPrompt.setElevation(elevation);
        tvPrompt.bringToFront();
    }

    public void setTvViewPosition(int mode) {
        int left = -1;
        int top = -1;
        int right = -1;
        int bottom = -1;
        int transY = 0;
        int duration = 0;

        tvViewMode = mode;
        switch (mode) {
            case TV_MODE_TOP:
                transY = -dipToPx(this, TV_WINDOW_BOTTOM_TOP);
            case TV_MODE_BOTTOM:
                left = dipToPx(this, TV_WINDOW_RIGHT_LEFT);
                top = dipToPx(this, TV_WINDOW_BOTTOM_TOP);
                right = left + dipToPx(this, TV_WINDOW_WIDTH);
                bottom = top + dipToPx(this, TV_WINDOW_HEIGHT);
                duration = 500;
                break;
            case TV_MODE_NORMAL:
            default:
                left = dipToPx(this, TV_WINDOW_NORMAL_LEFT);
                top = dipToPx(this, TV_WINDOW_NORMAL_TOP);
                right = left + dipToPx(this, TV_WINDOW_WIDTH);
                bottom = top + dipToPx(this, TV_WINDOW_HEIGHT);
                duration = 0;
                break;
        }
        if (mIsPortraitMode) {
            left = left / 2;
            top = top / 2;
            right = right / 2;
            bottom = bottom / 2;
            tvPrompt.setTextSize(15);
        }
        HoverView.setViewPosition(tvView, new Rect(left, top, right, bottom));
        HoverView.setViewPosition(tvPrompt, new Rect(left, top, right, bottom));

        tvView.animate()
            .translationY(transY)
            .setDuration(duration)
            .start();
        tvPrompt.animate()
            .translationY(transY)
            .setDuration(duration)
            .start();
    }

    private boolean isBootvideoStopped() {
        ContentProviderClient tvProvider = getContentResolver().acquireContentProviderClient(TvContract.AUTHORITY);

        return (tvProvider != null) &&
                (((SystemProperties.getInt("persist.vendor.media.bootvideo", 50)  > 100)
                        && TextUtils.equals(SystemProperties.get("service.bootvideo.exit", "1"), "0"))
                || ((SystemProperties.getInt("persist.vendor.media.bootvideo", 50)  <= 100)));
    }

    private boolean isCurrentChannelBlocked() {
        return DataProviderManager.getBooleanValue(this, DroidLogicTvUtils.TV_CURRENT_BLOCK_STATUS, false);
    }

    private boolean isCurrentChannelBlockBlocked() {
        return DataProviderManager.getBooleanValue(this, DroidLogicTvUtils.TV_CURRENT_CHANNELBLOCK_STATUS, false);
    }

    private String getDtvKitSystem() {
        return DataProviderManager.getStringValue(this, "tv_dtvkit_system", "");
    }

    private boolean isChannelSearch() {
        return DataProviderManager.getBooleanValue(Launcher.this, "is_channel_searching", false);
    }

    public void setCurrentChannelBlocked(boolean blocked){
        DataProviderManager.putBooleanValue(this, DroidLogicTvUtils.TV_CURRENT_BLOCK_STATUS, blocked);
    }

    private boolean isTunerSource (int deviceId) {
        return deviceId == DroidLogicTvUtils.DEVICE_ID_ADTV;
    }

    private boolean isTunerSource (String inputId) {
        return !mTvInputManager.getTvInputInfo(inputId).isPassthroughInput();
    }

    private void tuneTvView() {
        stopMusicPlayer();

        //float window don't need load PQ
        mSystemControlManager.setProperty(PROP_TV_PREVIEW, "true");

        mTvInputId = null;
        mChannelUri = null;
        /* Launcher should not update current input without user's select or notification from cec.
        if (mTvInputChangeCallback == null) {
            mTvInputChangeCallback = new TvInputChangeCallback();
            Log.d(TAG, "registerCallback:" + mTvInputChangeCallback);
            mTvInputManager.registerCallback(mTvInputChangeCallback, new Handler());
        }
        */
        setTvPrompt(TV_PROMPT_TUNING/*TV_PROMPT_GOT_SIGNAL*/);

        int device_id;
        long channel_id;
        device_id = DataProviderManager.getIntValue(Launcher.this, DroidLogicTvUtils.TV_CURRENT_DEVICE_ID, 0);
        channel_id = DataProviderManager.getLongValue(Launcher.this, DroidLogicTvUtils.TV_DTV_CHANNEL_INDEX, -1);
        isRadioChannel = DataProviderManager.getIntValue(Launcher.this, DroidLogicTvUtils.TV_CURRENT_CHANNEL_IS_RADIO, 0) == 1 ? true : false;
        Log.d(TAG, "TV get device_id=" + device_id + " dtv=" + channel_id );

        List<TvInputInfo> input_list = mTvInputManager.getTvInputList();
        String inputid = DroidLogicTvUtils.getCurrentInputId(this);
        TvInputInfo currentInfo = null;
        for (TvInputInfo info : input_list) {
            /*if (parseDeviceId(info.getId()) == device_id) {
                mTvInputId = info.getId();
            }*/
            if (compareInputId(inputid, info)) {
                mTvInputId = info.getId();
                currentInfo = info;
                break;
            }
        }

        if (TextUtils.isEmpty(mTvInputId)) {
            Log.d(TAG, "device" + device_id + " is not exist");
            setTvPrompt(TV_PROMPT_NO_CHANNEL);
            return;
            //mTvInputId = DEFAULT_INPUT_ID;
            //mChannelUri = TvContract.buildChannelUri(-1);
        } else {
            if (isTunerSource(inputid)) {
                setChannelUri(channel_id);
            } else {
                mChannelUri = TvContract.buildChannelUriForPassthroughInput(mTvInputId);
            }
        }

        mChannelUri = mIsPortraitMode ? null : mChannelUri;
        Log.d(TAG, "TV play tune inputId=" + mTvInputId + " uri=" + mChannelUri);
        ChannelInfo currentChannel = mTvDataBaseManager.getChannelInfo(mChannelUri);
        if (mChannelUri != null
                && ((DroidLogicTvUtils.getChannelId(mChannelUri) > 0
                    && currentChannel != null
                    && !currentChannel.isLocked()
                    && currentChannel.getSetHidden() == 0
                    && currentChannel.getSetDelete() == 0)
                    || (currentInfo != null && currentInfo.isPassthroughInput()))) {
            tvView.tune(mTvInputId, mChannelUri);
        }

        if (mChannelUri != null && !TvContract.isChannelUriForPassthroughInput(mChannelUri)) {
            ChannelInfo current = mTvDataBaseManager.getChannelInfo(mChannelUri);
            if (current != null/* && (!mTvInputManager.isParentalControlsEnabled() ||
                        (mTvInputManager.isParentalControlsEnabled() && !current.isLocked()))*/) {
                if (isCurrentChannelBlocked() && !current.getInputId().startsWith(DTVKIT_PACKAGE)) {
                    Log.d(TAG, "current channel is blocked");
                    setTvPrompt(TV_PROMPT_BLOCKED);
                } else if (current.isLocked()) {
                    Log.d(TAG, "current channel is locked");
                    setTvPrompt(TV_PROMPT_BLOCKED);
                } else if (current.getSetDelete() == 1) {
                    Log.d(TAG, "current channel is delete");
                    setTvPrompt(TV_PROMPT_NO_CHANNEL);
                } else if (current.getSetHidden() == 1) {
                    Log.d(TAG, "current channel is hide");
                    setTvPrompt(TV_PROMPT_IS_SKIP);
                }  else {
                    setTvPrompt(TV_PROMPT_TUNING);
                    Log.d(TAG, "TV play tune continue as no channel blocks");
                }
            } else {
                setTvPrompt(TV_PROMPT_NO_CHANNEL);
                tvView.setStreamVolume(0);
                Log.d(TAG, "TV play not tune as channel blocked");
            }
        } else if (mChannelUri == null) {
            Log.d(TAG, "TV play not tune as mChannelUri null");
            setTvPrompt(TV_PROMPT_NO_CHANNEL);
            tvView.setStreamVolume(0);
        }

        if (device_id == DroidLogicTvUtils.DEVICE_ID_SPDIF) {
            setTvPrompt(TV_PROMPT_SPDIF);
        }
        if (mChannelObserver == null)
            mChannelObserver = new ChannelObserver();
        getContentResolver().registerContentObserver(Channels.CONTENT_URI, true, mChannelObserver);
        mTvStartPlaying = true;
    }

    private boolean compareInputId(String inputId, TvInputInfo info) {
        Log.d(TAG, "compareInputId currentInputId " + inputId + " info " + info);
        if (null == info) {
            Log.d(TAG, "compareInputId info null");
            return false;
        }
        String infoInputId = info.getId();
        if (TextUtils.isEmpty(inputId) || TextUtils.isEmpty(infoInputId)) {
            Log.d(TAG, "inputId empty");
            return false;
        }
        if (TextUtils.equals(inputId, infoInputId)) {
            return true;
        }

        String[] inputIdArr = inputId.split("/");
        String[] infoInputIdArr = infoInputId.split("/");
        // InputId is like com.droidlogic.tvinput/.services.Hdmi1InputService/HW5
        if (inputIdArr.length == INPUT_ID_LENGTH && infoInputIdArr.length == INPUT_ID_LENGTH) {
            // For hdmi device inputId could change to com.droidlogic.tvinput/.services.Hdmi2InputService/HDMI200008
            if (inputIdArr[0].equals(infoInputIdArr[0]) && inputIdArr[1].equals(infoInputIdArr[1])) {
                return true;
            }
        }
        return false;
    }

    private void releaseTvView() {
        Log.d(TAG, "releaseTvView:" + mChannelUri);
        tvView.setVisibility(View.GONE);
        tvView.reset();

        if (mTvHandler.hasMessages(TV_MSG_PLAY_TV)) {
            mTvHandler.removeMessages(TV_MSG_PLAY_TV);
        }
        /*
        if (mTvInputChangeCallback != null) {
            Log.d(TAG, "unregisterCallback:" + mTvInputChangeCallback);
            mTvInputManager.unregisterCallback(mTvInputChangeCallback);
            mTvInputChangeCallback = null;
        }
        */
        if (mChannelObserver != null) {
            getContentResolver().unregisterContentObserver(mChannelObserver);
            mChannelObserver = null;
        }
    }

    private void setChannelUri (long     channelId) {
        Uri channelUri = TvContract.buildChannelUri(channelId);
        ChannelInfo currentChannel = mTvDataBaseManager.getChannelInfo(channelUri);
        String currentSignalType = DroidLogicTvUtils.getCurrentSignalType(this) == DroidLogicTvUtils.SIGNAL_TYPE_ERROR
            ? TvContract.Channels.TYPE_ATSC_T : DroidLogicTvUtils.getCurrentSignalType(this);
        Log.d(TAG, "channelid = " + channelId + "   [currentChannel] =" + currentChannel);
        if (currentChannel != null) {
            if (!TvContract.Channels.TYPE_OTHER.equals(currentChannel.getType())) {
                if (mTvInputId.startsWith(DTVKIT_PACKAGE) && currentChannel.isDigitalChannel()) {
                    isRadioChannel = ChannelInfo.isRadioChannel(currentChannel);
                    mChannelUri = channelUri;
                    setTvPrompt(TV_PROMPT_GOT_SIGNAL);
                } else if (DroidLogicTvUtils.isAtscCountry(this)) {
                    if (currentChannel.getSignalType().equals(currentSignalType)) {
                        isRadioChannel = ChannelInfo.isRadioChannel(currentChannel);
                        mChannelUri = channelUri;
                        setTvPrompt(TV_PROMPT_GOT_SIGNAL);
                    }
                } else if (DroidLogicTvUtils.isATV(this) && currentChannel.isAnalogChannel()) {
                        isRadioChannel = ChannelInfo.isRadioChannel(currentChannel);
                        mChannelUri = channelUri;
                        setTvPrompt(TV_PROMPT_GOT_SIGNAL);
                } else if (DroidLogicTvUtils.isDTV(this) && currentChannel.isDigitalChannel()) {
                    isRadioChannel = ChannelInfo.isRadioChannel(currentChannel);
                    mChannelUri = channelUri;
                    setTvPrompt(TV_PROMPT_GOT_SIGNAL);
                } else {
                    if (TextUtils.equals(DroidLogicTvUtils.getSearchInputId(this), currentChannel.getInputId())) {
                        isRadioChannel = ChannelInfo.isRadioChannel(currentChannel);
                        mChannelUri = channelUri;
                        setTvPrompt(TV_PROMPT_GOT_SIGNAL);
                    }
                }
            }
        } else {
            ArrayList<ChannelInfo> channelList =  mTvDataBaseManager.getChannelList(mTvInputId, ChannelInfo.COMMON_PROJECTION, null, null);
            if (channelList != null && channelList.size() > 0) {
                String currentDtvKitSystem = "";
                if (mTvInputId.startsWith(DTVKIT_PACKAGE)) {
                    currentDtvKitSystem = getDtvKitSystem();
                }
                for (int i = 0; i < channelList.size(); i++) {
                    ChannelInfo channel = channelList.get(i);
                    if (TvContract.Channels.TYPE_OTHER.equals(channel.getType())) {
                        if (TextUtils.equals(DroidLogicTvUtils.getSearchInputId(this), channel.getInputId())) {
                            mChannelUri = channel.getUri();
                            Log.d(TAG, "current other type channel not exisit, find a new channel instead: " + mChannelUri);
                            return;
                        }
                    } else if (mTvInputId.startsWith(DTVKIT_PACKAGE) && channel.isDigitalChannel() && dtvkitChannelsMatchSystem(channel.getType(),  currentDtvKitSystem)) {
                            mChannelUri = channel.getUri();
                            Log.d(TAG, "current channel not exisit, find a new dtvKit channel instead: " + mChannelUri);
                            return;
                    } else if (DroidLogicTvUtils.isAtscCountry(this)) {
                        if (channel.getSignalType().equals(currentSignalType)) {
                            mChannelUri = channel.getUri();
                            Log.d(TAG, "current channel not exisit, find a new channel instead: " + mChannelUri);
                            return;
                        }
                    } else {
                        if (DroidLogicTvUtils.isATV(this) && channel.isAnalogChannel()) {
                            mChannelUri = channel.getUri();
                            Log.d(TAG, "current channel not exisit, find a new channel instead: " + mChannelUri);
                            return;
                        } else if (DroidLogicTvUtils.isDTV(this) && channel.isDigitalChannel()) {
                            mChannelUri = channel.getUri();
                            Log.d(TAG, "current channel not exisit, find a new channel instead: " + mChannelUri);
                            return;
                        }
                    }
                }
            } else {
                mChannelUri = TvContract.buildChannelUri(-1);
            }
        }
    }

    public static boolean dtvkitChannelsMatchSystem(String channelType, String dvbSystem) {
        boolean ret = false;
        if (TextUtils.isEmpty(dvbSystem)) {
            ret = true;
        } else {
            switch (dvbSystem) {
                case "DVB-T": {
                    if (TvContract.Channels.TYPE_DVB_T.equals(channelType)
                            || TvContract.Channels.TYPE_DVB_T2.equals(channelType)) {
                        ret = true;
                    }
                    break;
                }
                case "DVB-C": {
                    if (TvContract.Channels.TYPE_DVB_C.equals(channelType)
                            || TvContract.Channels.TYPE_DVB_C2.equals(channelType)) {
                        ret = true;
                    }
                    break;
                }
                case "DVB-S": {
                    if (TvContract.Channels.TYPE_DVB_S.equals(channelType)
                            || TvContract.Channels.TYPE_DVB_S2.equals(channelType)
                            || TvContract.Channels.TYPE_DVB_SH.equals(channelType)) {
                        ret = true;
                    }
                    break;
                }
                case "ISDB-T": {
                    if (TvContract.Channels.TYPE_ISDB_T.equals(channelType)
                            || TvContract.Channels.TYPE_ISDB_TB.equals(channelType)) {
                        ret = true;
                    }
                    break;
                }
                case "ANALOG": {
                    if (TvContract.Channels.TYPE_PAL.equals(channelType)
                            || TvContract.Channels.TYPE_SECAM.equals(channelType)
                            || TvContract.Channels.TYPE_NTSC.equals(channelType)) {
                        ret = true;
                    }
                    break;
                }
            }
        }
        return ret;
    }

    //private int currentTvPromptMode = TV_PROMPT_GOT_SIGNAL;
    private void setTvPrompt(int mode) {
        /*if (currentTvPromptMode == TV_PROMPT_BLOCKED && mode != TV_PROMPT_BLOCKED) {
            Log.d(TAG, "setTvPrompt: TV_PROMPT_BLOCKED");
            return;
        }*/

        //currentTvPromptMode = mode;
        switch (mode) {
            case TV_PROMPT_GOT_SIGNAL:
                tvPrompt.setText(null);
                tvPrompt.setBackground(null);
                break;
            case TV_PROMPT_NO_SIGNAL:
                if (mTvInputId != null && mTvInputId.startsWith(DTVKIT_PACKAGE)) {
                    tvPrompt.setText(getResources().getString(R.string.str_no_signal));
                    tvPrompt.setBackground(getResources().getDrawable(R.drawable.black));
                } else {
                    tvPrompt.setText(null);
                    tvPrompt.setBackground(null);
                }
                break;
            case TV_PROMPT_IS_SCRAMBLED:
                tvPrompt.setText(getResources().getString(R.string.str_scrambeled));
                if (isRadioChannel) {
                    tvPrompt.setBackgroundDrawable(getResources().getDrawable(R.drawable.black));
                }  else {
                    tvPrompt.setBackground(null);
                }
                break;
            case TV_PROMPT_NO_DEVICE:
                tvPrompt.setText(null);
                tvPrompt.setBackgroundDrawable(getResources().getDrawable(R.drawable.hotplug_out));
                break;
            case TV_PROMPT_SPDIF:
                tvPrompt.setText(null);
                tvPrompt.setBackgroundDrawable(getResources().getDrawable(R.drawable.spdifin));
                break;
            case TV_PROMPT_BLOCKED:
                tvPrompt.setText(getResources().getString(R.string.str_blocked));
                tvPrompt.setBackground(getResources().getDrawable(R.drawable.black));
                break;
            case TV_PROMPT_NO_CHANNEL:
                tvPrompt.setText(getResources().getString(R.string.str_no_channel));
                tvPrompt.setBackground(getResources().getDrawable(R.drawable.black));
                break;
            case TV_PROMPT_RADIO:
                tvPrompt.setText(getResources().getString(R.string.str_audio_only));
                tvPrompt.setBackgroundDrawable(getResources().getDrawable(R.drawable.black));
                break;
            case TV_PROMPT_TUNING:
                tvPrompt.setText(null);
                tvPrompt.setBackground(getResources().getDrawable(R.drawable.black));
                break;
            case TV_PROMPT_IS_DATA_SERVICE:
                tvPrompt.setText(getResources().getString(R.string.str_data_service));
                tvPrompt.setBackgroundDrawable(getResources().getDrawable(R.drawable.black));
                break;
            case TV_PROMPT_IS_INVALID_SERVICE:
                tvPrompt.setText(getResources().getString(R.string.str_invalid_service));
                tvPrompt.setBackgroundDrawable(getResources().getDrawable(R.drawable.black));
                break;
            case TV_PROMPT_IS_SKIP:
                tvPrompt.setText(getResources().getString(R.string.str_channel_skip));
                tvPrompt.setBackgroundDrawable(getResources().getDrawable(R.drawable.black));
                break;
            case TV_PROMPT_IS_DELETE:
                tvPrompt.setText(getResources().getString(R.string.str_channel_delete));
                tvPrompt.setBackgroundDrawable(getResources().getDrawable(R.drawable.black));
                break;
        }
    }

    //stop the background music player
    public void stopMusicPlayer() {
        Intent intent = new Intent();
        intent.setAction ("com.android.music.pause");
        intent.putExtra ("command", "stop");
        sendBroadcast (intent);
    }

    private Handler mTvHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TV_MSG_PLAY_TV:
                    if (isBootvideoStopped()) {
                        Log.d(TAG, "======== bootvideo is stopped, and tvapp released, start tv play");
                        if (initChannelWhenChannelReady() && !isChannelSearch()) {
                            tuneTvView();
                        } else if (isChannelSearch()){
                            Log.d(TAG, "======== Channel search has not been completed, waiting");
                            mTvHandler.sendEmptyMessageDelayed(TV_MSG_PLAY_TV, 1000);
                        } else {
                            Log.d(TAG, "======== screen blocked and no need start tv play");
                        }
                    } else {
                        //Log.d(TAG, "======== bootvideo is not stopped, or tvapp not released, wait it");
                        mTvHandler.sendEmptyMessageDelayed(TV_MSG_PLAY_TV, 10);
                    }
                    break;
                case TV_MSG_BOOTUP_TO_TVAPP:
                    if (isBootvideoStopped()) {
                        Log.d(TAG, "======== bootvideo is stopped, start tv app");
                        if (mDelayedSourceChange != null) {
                            startOtpSource(mDelayedSourceChange);
                            mDelayedSourceChange = null;
                            Toast.makeText(Launcher.this, R.string.toast_otp_input_change, Toast.LENGTH_LONG).show();
                            return;
                        } else {
                            startTvApp();
                            finish();
                        }
                    } else {
                        Log.d(TAG, "======== bootvideo is not stopped, wait it");
                        mTvHandler.sendEmptyMessageDelayed(TV_MSG_BOOTUP_TO_TVAPP, 50);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    public class TvViewInputCallback extends TvView.TvInputCallback {
        @Override
        public void onEvent(String inputId, String eventType, Bundle eventArgs) {
            Log.d(TAG, "====onEvent==inputId =" + inputId +", ===eventType ="+ eventType);
            if (eventType.equals(DroidLogicTvUtils.AV_SIG_SCRAMBLED)) {
                setTvPrompt(TV_PROMPT_IS_SCRAMBLED);
            } else if (eventType.equals(DroidLogicTvUtils.EVENT_CHANNEL_LIST_UPDATED)) {
                if (eventArgs == null) {
                    return;
                }
                Uri channelUri = eventArgs.getParcelable("channelUri");
                if (channelUri != null) {
                    mChannelUri = channelUri;
                    tvView.tune(mTvInputId, mChannelUri);
                    Log.i(TAG, "onEvent: tune to new " + channelUri);
                }
            } else if (eventType.equals(EVENT_SIGNAL_SCRAMBLED_SERVICE)) {
                setTvPrompt(TV_PROMPT_IS_SCRAMBLED);
            } else if (eventType.equals(EVENT_SIGNAL_INVALID_SERVICE)) {
                setTvPrompt(TV_PROMPT_IS_INVALID_SERVICE);
            } else if (eventType.equals(EVENT_SIGNAL_DATA_SERVICE)) {
                setTvPrompt(TV_PROMPT_IS_DATA_SERVICE);
            }
        }

        @Override
        public void onVideoAvailable(String inputId) {
            //tvView.invalidate();
            int device_id = DataProviderManager.getIntValue(Launcher.this, DroidLogicTvUtils.TV_CURRENT_DEVICE_ID, 0);
            if (device_id == DroidLogicTvUtils.DEVICE_ID_AV1 || device_id == DroidLogicTvUtils.DEVICE_ID_AV2) {
                isAvNoSignal = false;
            }
            if (!isChannelBlocked || !isCurrentChannelBlockBlocked()) {
                setTvPrompt(TV_PROMPT_GOT_SIGNAL);
                if (inputId != null && inputId.startsWith(DTVKIT_PACKAGE)) {
                    tvView.setStreamVolume(1);
                }
            } else {
                setTvPrompt(TV_PROMPT_BLOCKED);
                tvView.setStreamVolume(0);
            }

            Log.d(TAG, "====onVideoAvailable==inputId =" + inputId);
        }

        @Override
        public void onConnectionFailed(String inputId) {
            Log.d(TAG, "====onConnectionFailed==inputId =" + inputId);
            new Thread( new Runnable() {
                public void run() {
                    try{
                        Thread.sleep(200);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (TextUtils.isEmpty(mTvInputId))
                        return;
                    tvView.tune(mTvInputId, mChannelUri);
                }
            }).start();
        }

        @Override
        public void onVideoUnavailable(String inputId, int reason) {
            Log.d(TAG, "====onVideoUnavailable==inputId =" + inputId +", ===reason ="+ reason);
            switch (reason) {
                case TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN:
                case TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING:
                case TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING:
                    break;
                default:
                    break;
            }
            int device_id = DataProviderManager.getIntValue(Launcher.this, DroidLogicTvUtils.TV_CURRENT_DEVICE_ID, 0);
            if (device_id == DroidLogicTvUtils.DEVICE_ID_SPDIF) {
                setTvPrompt(TV_PROMPT_SPDIF);
            } else if (device_id == DroidLogicTvUtils.DEVICE_ID_AV1 || device_id == DroidLogicTvUtils.DEVICE_ID_AV2) {
                isAvNoSignal = true;
                setTvPrompt(TV_PROMPT_NO_SIGNAL);
            } else if (reason != TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY &&
                    reason != TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING) {
                if (!TextUtils.equals(mChannelUri.toString(), TvContract.buildChannelUri(-1).toString())) {
                    ChannelInfo currentChannel = mTvDataBaseManager.getChannelInfo(mChannelUri);
                    Log.i(TAG,"====onVideoUnavailable===" + currentChannel);
                    if (currentChannel != null && currentChannel.isData()) {
                        setTvPrompt(TV_PROMPT_IS_DATA_SERVICE);
                    } else {
                        setTvPrompt(TV_PROMPT_NO_SIGNAL);
                    }
                    if (inputId != null && inputId.startsWith(DTVKIT_PACKAGE)) {
                        tvView.setStreamVolume(0);
                    }
                }
            } else if (reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY) {
                if (inputId != null && inputId.startsWith(DTVKIT_PACKAGE)) {
                    setTvPrompt(TV_PROMPT_RADIO);
                    if (inputId != null && inputId.startsWith(DTVKIT_PACKAGE)) {
                        tvView.setStreamVolume(1);
                    }
                }
            }
        }

        @Override
        public void onContentBlocked(String inputId, TvContentRating rating) {
            Log.d(TAG, "====onContentBlocked");
            setCurrentChannelBlocked(true);
            int device_id = DataProviderManager.getIntValue(Launcher.this, DroidLogicTvUtils.TV_CURRENT_DEVICE_ID, 0);
            isChannelBlocked = true;
            if (isAvNoSignal && (device_id == DroidLogicTvUtils.DEVICE_ID_AV1 || device_id == DroidLogicTvUtils.DEVICE_ID_AV2)) {
                setTvPrompt(TV_PROMPT_NO_SIGNAL);
            } else {
                setTvPrompt(TV_PROMPT_BLOCKED);
            }
            tvView.setStreamVolume(0);
        }

        @Override
        public void onContentAllowed(String inputId) {
            Log.d(TAG, "====onContentAllowed ");
            setCurrentChannelBlocked(false);
            int device_id = DataProviderManager.getIntValue(Launcher.this, DroidLogicTvUtils.TV_CURRENT_DEVICE_ID, 0);
            if (device_id == DroidLogicTvUtils.DEVICE_ID_AV1 || device_id == DroidLogicTvUtils.DEVICE_ID_AV2) {
                isAvNoSignal = false;
            }
            isChannelBlocked = false;
            setTvPrompt(TV_PROMPT_GOT_SIGNAL);
            tvView.setStreamVolume(1);
        }

        @Override
        public void onTracksChanged(String inputId, List<TvTrackInfo> tracks) {
            Log.d(TAG, "onTracksChanged inputId = " + inputId);
            //appyPrimaryAudioLanguage(tracks);
        }

        @Override
        public void onTrackSelected(String inputId, int type, String trackId) {
            Log.d(TAG, "onTrackSelected inputId = " + inputId + ", type = " + type + ", trackId = " + trackId);
        }
    }

    private final class TvInputChangeCallback extends TvInputManager.TvInputCallback {
        @Override
        public void onInputAdded(String inputId) {
            String current_inputid = DroidLogicTvUtils.getCurrentInputId(getApplicationContext());
            Log.d(TAG, "==== onInputAdded, inputId=" + inputId + " curent inputid=" + current_inputid + "curent mChannelUri" + mChannelUri);
            if (inputId.equals(current_inputid) || mTvInputId == null) {
                mTvInputId = inputId;
                if (!mTvInputManager.getTvInputInfo(inputId).isPassthroughInput()) {
                    if (!mTvStartPlaying) {
                        tuneTvView();
                    }
                    /*setChannelUri(Settings.System.getLong(getContentResolver(), DroidLogicTvUtils.TV_DTV_CHANNEL_INDEX, -1));
                    tvView.tune(mTvInputId, mChannelUri);*/
                } else {
                    setTvPrompt(TV_PROMPT_GOT_SIGNAL);
                    mChannelUri = TvContract.buildChannelUriForPassthroughInput(mTvInputId);
                    tvView.tune(mTvInputId, mChannelUri);
                }
            }
        }

        @Override
        public void onInputRemoved(String inputId) {
            Log.d(TAG, "==== onInputRemoved, inputId=" + inputId + " curent inputid=" + mTvInputId+",this:"+this);
            if (TextUtils.equals(inputId, mTvInputId)) {
                Log.d(TAG, "==== current input device removed");
                mTvInputId = null;
                setTvPrompt(TV_PROMPT_NO_DEVICE);
                /*mTvInputId = DEFAULT_INPUT_ID;
                Settings.System.putInt(getContentResolver(), DroidLogicTvUtils.TV_CURRENT_DEVICE_ID, DroidLogicTvUtils.DEVICE_ID_ATV);

                ArrayList<ChannelInfo> channelList = mTvDataBaseManager.getChannelList(mTvInputId, Channels.SERVICE_TYPE_AUDIO_VIDEO, true);
                int index_atv = Settings.System.getInt(getContentResolver(), DroidLogicTvUtils.TV_ATV_CHANNEL_INDEX, -1);
                setChannelUri(channelList, index_atv);
                tvView.tune(mTvInputId, mChannelUri);*/
            }
        }
    }

    private final class ChannelObserver extends ContentObserver {
        public ChannelObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            Log.d(TAG, "detect channel changed =" + uri);
            if (!mSyncChannelUri && (mChannelUri == null || DroidLogicTvUtils.matchsWhich(mChannelUri)
                    == DroidLogicTvUtils.NO_MATCH)) {
                ChannelInfo changedChannel = mTvDataBaseManager.getChannelInfo(uri);
                Log.d(TAG, "changedChannel :" + changedChannel);
                if (changedChannel != null) {
                    if (TextUtils.equals(changedChannel.getInputId(), mTvInputId)) {
                        Log.d(TAG, "current channel is null, so tune to a new channel");
                        //mChannelUri = ContentUris.withAppendedId(uri, 1);
                        tvView.tune(mTvInputId, mChannelUri);
                        mSyncChannelUri = true;
                    }
                }
            }
        }
    }

    private int parseDeviceId(String inputId) {
        String[] temp = inputId.split("/");
        if (temp.length == 3) {
            /*  ignore for HDMI CEC device */
            if (temp[2].contains("HDMI"))
                return -1;
            return Integer.parseInt(temp[2].substring(2));
        } else {
            return -1;
        }
    }

    private void appyPrimaryAudioLanguage(List<TvTrackInfo> tracks) {
        List<TvTrackInfo> audiotracks = new ArrayList<TvTrackInfo>();
        if (tracks != null) {
            for (TvTrackInfo track : tracks) {
                if (track.getType() == TvTrackInfo.TYPE_AUDIO) {
                    audiotracks.add(track);
                }
            }
        }
        if (tvView != null && audiotracks.size() > 0) {
            List<TvTrackInfo> list = audiotracks;
            String selecttrack = tvView.getSelectedTrack(TvTrackInfo.TYPE_AUDIO);
            String primarytrack = null;
            int primary = getPrimaryLanguage(Launcher.this);
            int select = -1;
            if (list != null) {
                for (TvTrackInfo track : list) {
                    select = getDisplayLanguageIndex(track.getLanguage());
                    if (select >= ENGLISH_INDEX && select == primary) {
                        primarytrack = track.getId();
                        if (!TextUtils.equals(primarytrack, selecttrack)) {
                            tvView.selectTrack(TvTrackInfo.TYPE_AUDIO, primarytrack);
                            Log.d(TAG, "primarytrack = " + primarytrack);
                        }
                        break;
                    }
                }
            }
        }
    }

    //getPrimaryLanguage
    private int getPrimaryLanguage(Context context) {
        return DataProviderManager.getIntValue(context, "primary_audio_lang", getSystemLang());
    }

    //getSystemLang
    private int getSystemLang() {
        return getDisplayLanguageIndex(Locale.getDefault().getLanguage());
    }

    //is0639 language code
    private final String ENGLISH = "en";
    private final String FRENCH = "fr";
    private final String ESPANOL = "es";
    private final String SPANISH = "sp";
    public final int ENGLISH_INDEX = 0;
    private final int FRENCH_INDEX = 1;
    private final int ESPANOL_INDEX = 2;

    //return fixed index for eng\fr\spa\esl
    private int getDisplayLanguageIndex(String value) {
        int diaplaylang = -1;
        if (value == null) {
            return diaplaylang;
        }
        if (value.contains(ENGLISH)) {
            diaplaylang = ENGLISH_INDEX;
        } else if (value.contains(FRENCH)) {
            diaplaylang = FRENCH_INDEX;
        } else if (value.contains(ESPANOL) || value.contains(SPANISH)) {
            diaplaylang = ESPANOL_INDEX;
        }
        return diaplaylang;
    }

    /*in AOSP version, we have no user setup APK, so force skip it
    or we can't use home key */
    private void skipUserSetup() {
        if (Settings.Secure.getInt(getContentResolver(), Settings.Secure.TV_USER_SETUP_COMPLETE, 0) == 0) {
            Log.d(TAG, "force skip user setup, or we can't use home key");
            Settings.Global.putInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1);
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 1);
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.TV_USER_SETUP_COMPLETE, 1);
        }
    }

    private boolean initChannelWhenChannelReady() {
        boolean result = false;
        long channelId = DataProviderManager.getLongValue(Launcher.this, DroidLogicTvUtils.TV_DTV_CHANNEL_INDEX, -1);
        //int deviceId = DataProviderManager.getIntValue(getContentResolver(), DroidLogicTvUtils.TV_CURRENT_DEVICE_ID, 0);
        if (channelId != -1) {
            Uri channelUri = TvContract.buildChannelUri(channelId);
            ChannelInfo currentChannel = mTvDataBaseManager.getChannelInfo(channelUri);
            String inputid = DroidLogicTvUtils.getCurrentInputId(this);
            if (isTunerSource(inputid) && currentChannel != null
                    && currentChannel.isLocked() && mTvInputManager.isParentalControlsEnabled()) {
                isChannelBlocked = true;
            } else {
                isChannelBlocked = false;
            }
        } else {
            isChannelBlocked = false;
        }
        Log.d(TAG, "initChannelWhenChannelReady isChannelBlocked = " + isChannelBlocked + ", isCurrentChannelBlockBlocked = " + isCurrentChannelBlockBlocked());
        if (!isChannelBlocked || !isCurrentChannelBlockBlocked()) {
            result = true;
        } else {
            setTvPrompt(TV_PROMPT_BLOCKED);
            tvView.setStreamVolume(0);
            result = false;
        }
        return result;
    }
}
