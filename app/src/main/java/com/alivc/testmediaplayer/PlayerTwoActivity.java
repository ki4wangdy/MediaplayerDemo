package com.alivc.testmediaplayer;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.*;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.widget.*;
import com.alivc.player.AliVcMediaPlayer;
import com.alivc.player.AliVcMediaPlayerFactory;
import com.alivc.player.MediaPlayer;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerTwoActivity extends Activity {

    public static final String TAG = "PlayerTwoActivity";

    public static final int STATUS_START = 1;
    public static final int STATUS_STOP = 2;
    public static final int STATUS_PAUSE = 3;
    public static final int STATUS_RESUME = 4;

    public static final int CMD_START = 1;
    public static final int CMD_STOP = 2;
    public static final int CMD_PAUSE = 3;
    public static final int CMD_RESUME = 4;
    public static final int CMD_VOLUME = 5;
    public static final int CMD_SEEK = 6;

    public static final int TEST = 0;

    private ListView mListView;
    private VideoListAdapter mVideoListAdapter;
    private Context mContext = PlayerTwoActivity.this;
    private AtomicBoolean isFirst = new AtomicBoolean(false);
    private ArrayList<Video> videoList = new ArrayList<Video>();

    private final String mRootDir = "/mnt/sdcard/aliyun";
    private String mPrefixTitle = "";

    private static final int REQUEST_CODE = 1;   //request code
    public static final String EXTRA_FILE_CHOOSER = "file_chooser";
    private int mSelectedPosition = -1;

    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            if(msg.what == 0) {
                mVideoListAdapter.notifyDataSetChanged();
            }
            if(msg.what == 1){
                mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                if(mSelectedPosition < 0) mSelectedPosition = 0;
                mListView.setSelection(mSelectedPosition);
                mListView.setItemChecked(mSelectedPosition, true);
            }
            super.handleMessage(msg);
        }

    };



    private AliVcMediaPlayer mPlayer = null;
    private SurfaceHolder mSurfaceHolder = null;
    private SurfaceView mSurfaceView = null;

    private SeekBar mSeekBar = null;
    private TextView mTipView = null;
    private TextView mCurDurationView = null;
    private TextView mErrInfoView = null;
    private TextView mDecoderTypeView = null;
    private LinearLayout mTipLayout = null;

    private boolean mEnableUpdateProgress = true;
    private int mLastPercent = -1;
    private int mPlayingIndex = -1;
    private StringBuilder msURI = new StringBuilder("");
    private StringBuilder msTitle = new StringBuilder("");
    private boolean mUseHardDecoder = true;
    private GestureDetector mGestureDetector;
    private int mPosition = 0;
    private int mVolumn = 50;

    private PlayerControl mPlayerControl = null;

    private PowerManager.WakeLock mWakeLock = null;

    private StatusListener mStatusListener = null;

    private boolean isLastWifiConnected = false;

    // 标记播放器是否已经停止
    private boolean isStopPlayer = false;
    // 标记播放器是否已经暂停
    private boolean isPausePlayer = false;
    //用来控制应用前后台切换的逻辑
    private boolean isCurrentRunningForeground = true;

    // 重点:发生从wifi切换到4g时,提示用户是否需要继续播放,此处有两种做法:
    // 1.从历史位置从新播放
    // 2.暂停播放,因为存在网络切换,续播有时会不成功
    private BroadcastReceiver connectionReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectMgr = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo mobNetInfo = connectMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            NetworkInfo wifiNetInfo = connectMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

            Log.d(TAG, "mobile " + mobNetInfo.isConnected() + " wifi " + wifiNetInfo.isConnected());

            if (!isLastWifiConnected && wifiNetInfo.isConnected()) {
                isLastWifiConnected = true;
            }
            if (isLastWifiConnected && mobNetInfo.isConnected() && !wifiNetInfo.isConnected()) {
                isLastWifiConnected = false;
                if (mPlayer != null) {
                    // TODO by xinye : 开始播放seek有问题
//                    mPosition = mPlayer.getCurrentPosition();
                    // 重点:新增接口,此处必须要将之前的surface释放掉
                    mPlayer.releaseVideoSurface();
                    mPlayer.stop();
                    mPlayer.destroy();
                    mPlayer = null;
                }
                dialog();

//                createVideoSurface();
            }
        }
    };

    protected void dialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(PlayerTwoActivity.this);
        builder.setMessage("确认继续播放吗？");

        builder.setTitle("提示");

        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                createVideoSurface();

            }
        });

        builder.setNegativeButton("退出", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                PlayerTwoActivity.this.finish();
            }
        });

        builder.create().show();
    }

    void setStatusListener(StatusListener listener) {
        mStatusListener = listener;
    }

    private PlayerControl.ControllerListener mController = new PlayerControl.ControllerListener() {

        @Override
        public void notifyController(int cmd, int extra) {
            Message msg = Message.obtain();
            switch (cmd) {
                case PlayerControl.CMD_PAUSE:
                    msg.what = CMD_PAUSE;
                    break;
                case PlayerControl.CMD_RESUME:
                    msg.what = CMD_RESUME;
                    break;
                case PlayerControl.CMD_SEEK:
                    msg.what = CMD_SEEK;
                    msg.arg1 = extra;
                    break;
                case PlayerControl.CMD_START:
                    msg.what = CMD_START;
                    break;
                case PlayerControl.CMD_STOP:
                    msg.what = CMD_STOP;
                    break;
                case PlayerControl.CMD_VOLUME:
                    msg.what = CMD_VOLUME;
                    msg.arg1 = extra;

                    break;

                default:
                    break;

            }

            if (TEST != 0) {
                mTimerHandler.sendMessage(msg);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e(TAG, "onCreate.");
        super.onCreate(savedInstanceState);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectionReceiver, intentFilter);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_player_two);
        mPlayingIndex = -1;

        if (TEST == 1) {
            mPlayerControl = new PlayerControl(this);
            mPlayerControl.setControllerListener(mController);
        }
        acquireWakeLock();

        init();

        // video list
        acquireVersion();

        findViews();
        new FilelistRefreshThread().start();

        fileChooserIntent =  new Intent(this ,
                FileChooserActivity.class);
    }


    private class MyGestureListener extends SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                float distanceX, float distanceY) {

            final double FLING_MIN_DISTANCE = 0.5;
            final double FLING_MIN_VELOCITY = 0.5;

            if (e1.getY() - e2.getY() > FLING_MIN_DISTANCE
                    && Math.abs(distanceY) > FLING_MIN_VELOCITY) {
                onVolumeSlide(1);
            }
            if (e1.getY() - e2.getY() < FLING_MIN_DISTANCE
                    && Math.abs(distanceY) > FLING_MIN_VELOCITY) {
                onVolumeSlide(-1);
            }

            return super.onScroll(e1, e2, distanceX, distanceY);
        }
    }

    private void onVolumeSlide(int vol) {
        if (mPlayer != null) {
            mVolumn += vol;
            if (mVolumn > 100)
                mVolumn = 100;
            if (mVolumn < 0)
                mVolumn = 0;
            mPlayer.setVolume(mVolumn);
        }
    }

    private void acquireWakeLock() {
        if (mWakeLock == null) {
            PowerManager pMgr = (PowerManager) this.getSystemService(this.POWER_SERVICE);
            mWakeLock = pMgr.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                    "SmsSyncService.sync() wakelock.");

        }
        mWakeLock.acquire();
    }

    private void releaseWakeLock() {
        mWakeLock.release();
        mWakeLock = null;
    }

    private void update_progress(int ms) {
        if (mEnableUpdateProgress) {
            mSeekBar.setProgress(ms);
        }
        return;
    }

    private void update_second_progress(int ms) {
        if (mEnableUpdateProgress) {
            mSeekBar.setSecondaryProgress(ms);
        }
        return;
    }

    private void show_progress_ui(boolean bShowPause) {
        LinearLayout progress_layout = (LinearLayout) findViewById(R.id.progress_layout);
        TextView video_title = (TextView) findViewById(R.id.video_title);

        if (bShowPause) {
            progress_layout.setVisibility(View.VISIBLE);
            video_title.setVisibility(View.VISIBLE);

        } else {
//            progress_layout.setVisibility(View.GONE);
//            video_title.setVisibility(View.GONE);
        }
    }

    private void show_pause_ui(boolean bShowPauseBtn, boolean bShowReplayBtn) {
        LinearLayout layout = (LinearLayout) findViewById(R.id.buttonLayout);
        if (!bShowPauseBtn && !bShowReplayBtn) {
            layout.setVisibility(View.GONE);
        } else {
            layout.setVisibility(View.VISIBLE);
        }
        ImageView pause_view = (ImageView) findViewById(R.id.pause_button);
        pause_view.setVisibility(bShowPauseBtn ? View.VISIBLE : View.GONE);

        Button replay_btn = (Button) findViewById(R.id.replay_button);
        replay_btn.setVisibility(bShowReplayBtn ? View.VISIBLE : View.GONE);

        return;
    }

    private int show_tip_ui(boolean bShowTip, float percent) {

        int vnum = (int) (percent);
        vnum = vnum > 100 ? 100 : vnum;

        mTipLayout.setVisibility(bShowTip ? View.VISIBLE : View.GONE);
        mTipView.setVisibility(bShowTip ? View.VISIBLE : View.GONE);

        if (mLastPercent < 0) {
            mLastPercent = vnum;
        } else if (vnum < mLastPercent) {
            vnum = mLastPercent;
        } else {
            mLastPercent = vnum;
        }

        String strValue = String.format("Buffering(%1$d%%)...", vnum);
        mTipView.setText(strValue);

        if (!bShowTip) { //hide it, then we need reset the percent value here.
            mLastPercent = -1;
        }

        return vnum;
    }

    private void show_buffering_ui(boolean bShowTip) {

        mTipLayout.setVisibility(bShowTip ? View.VISIBLE : View.GONE);
        mTipView.setVisibility(bShowTip ? View.VISIBLE : View.GONE);

        String strValue = "Buffering...";
        mTipView.setText(strValue);
    }

    private void update_total_duration(int ms) {
        int var = (int) (ms / 1000.0f + 0.5f);
        int min = var / 60;
        int sec = var % 60;
        TextView total = (TextView) findViewById(R.id.total_duration);
        total.setText("" + min + ":" + sec);


        SeekBar sb = (SeekBar) findViewById(R.id.progress);
        sb.setMax(ms);
        sb.setKeyProgressIncrement(10000); //5000ms = 5sec.
        sb.setProgress(0);
        sb.setSecondaryProgress(0); //reset progress now.

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            public void onProgressChanged(SeekBar seekBar, int i, boolean fromuser) {
                int var = (int) (i / 1000.0f + 0.5f);
                int min = var / 60;
                int sec = var % 60;
                String strCur = String.format("%1$d:%2$d", min, sec);
                mCurDurationView.setText(strCur);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                mEnableUpdateProgress = false;
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                int ms = seekBar.getProgress();
                mPlayer.seekTo(ms);
            }
        });

        return;
    }

    private void report_error(String err, boolean bshow) {
        if (mErrInfoView.getVisibility() == View.GONE && !bshow) {
            return;
        }
        mErrInfoView.setVisibility(bshow ? View.VISIBLE : View.GONE);
        mErrInfoView.setText(err);
        mErrInfoView.setTextColor(Color.RED);
        return;
    }


    private SurfaceHolder.Callback mSurfaceHolderCB = new SurfaceHolder.Callback() {
        @SuppressWarnings("deprecation")
        public void surfaceCreated(SurfaceHolder holder) {
            holder.setType(SurfaceHolder.SURFACE_TYPE_GPU);
            holder.setKeepScreenOn(true);
            Log.d(TAG, "AlivcPlayer onSurfaceCreated.");

            // 重点:
            if (mPlayer != null) {
                // 对于从后台切换到前台,需要重设surface;部分手机锁屏也会做前后台切换的处理
                mPlayer.setVideoSurface(mSurfaceView.getHolder().getSurface());
            } else {
                // 创建并启动播放器
                startToPlay();
            }

            if (mPlayerControl != null)
                mPlayerControl.start();
            Log.d(TAG, "AlivcPlayeron SurfaceCreated over.");
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            Log.d(TAG, "onSurfaceChanged is valid ? " + holder.getSurface().isValid());
            if (mPlayer != null)
                mPlayer.setSurfaceChanged();
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "onSurfaceDestroy.");

            if (mPlayer != null) {
                mPlayer.releaseVideoSurface();
            }
        }
    };

    public void switchSurface(View view) {
        if (mPlayer != null) {
            // release old surface;
            mPlayer.releaseVideoSurface();
            mSurfaceHolder.removeCallback(mSurfaceHolderCB);
            FrameLayout frameContainer = (FrameLayout) findViewById(R.id.GLViewContainer);
            frameContainer.removeAllViews();

            // init surface
            LinearLayout linearLayout = (LinearLayout) findViewById(R.id.surface_view_container);
            mSurfaceView = new SurfaceView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            params.gravity = Gravity.CENTER;
            mSurfaceView.setLayoutParams(params);
            linearLayout.addView(mSurfaceView);

            mSurfaceHolder = mSurfaceView.getHolder();
            mSurfaceHolder.addCallback(mSurfaceHolderCB);
        }

    }

    /**
     * 重点:初始化播放器使用的SurfaceView,此处的SurfaceView采用动态添加
     *
     * @return 是否成功
     */
    private boolean createVideoSurface() {
        show_buffering_ui(true);
        FrameLayout frameContainer = (FrameLayout) findViewById(R.id.GLViewContainer);
        frameContainer.setBackgroundColor(Color.rgb(0, 0, 0));
        mSurfaceView = new SurfaceView(this);
        mGestureDetector = new GestureDetector(this, new MyGestureListener());

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.CENTER;
        mSurfaceView.setLayoutParams(params);
        // 为避免重复添加,事先remove子view
        frameContainer.removeAllViews();
        frameContainer.addView(mSurfaceView);

        mSurfaceView.setZOrderOnTop(false);

        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            private long mLastDownTimestamp = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (mGestureDetector.onTouchEvent(event))
                    return true;

                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    mLastDownTimestamp = System.currentTimeMillis();
                    return true;
                }

                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (mPlayer != null && !mPlayer.isPlaying() && mPlayer.getDuration() > 0) {
                        start();

                        return false;
                    }

                    //just show the progress bar
                    if ((System.currentTimeMillis() - mLastDownTimestamp) > 200) {
                        show_progress_ui(true);
                        mTimerHandler.postDelayed(mUIRunnable, 3000);
                        return true;
                    } else {
                        if (mPlayer!= null && mPlayer.getDuration() > 0)
                            pause();

                    }
                    return false;
                }
                return false;
            }
        });

        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(mSurfaceHolderCB);

        return true;
    }

    private boolean init() {
        mTipLayout = (LinearLayout) findViewById(R.id.LayoutTip);
        mSeekBar = (SeekBar) findViewById(R.id.progress);
        mTipView = (TextView) findViewById(R.id.text_tip);
        mCurDurationView = (TextView) findViewById(R.id.current_duration);
        mErrInfoView = (TextView) findViewById(R.id.error_info);
        mDecoderTypeView = (TextView) findViewById(R.id.decoder_type);

        return true;
    }

    private boolean startToPlay() {
        Log.d(TAG, "start play.");
        resetUI();

        if (mPlayer == null) {
            // 初始化播放器
            mPlayer = new AliVcMediaPlayer(this, mSurfaceView);
            mPlayer.setPreparedListener(new VideoPreparedListener());
            mPlayer.setErrorListener(new VideoErrorListener());
            mPlayer.setInfoListener(new VideoInfolistener());
            mPlayer.setSeekCompleteListener(new VideoSeekCompletelistener());
            mPlayer.setCompletedListener(new VideoCompletelistener());
            mPlayer.setVideoSizeChangeListener(new VideoSizeChangelistener());
            mPlayer.setBufferingUpdateListener(new VideoBufferUpdatelistener());
            mPlayer.setStopedListener(new VideoStoppedListener());
            // 如果同时支持软解和硬解是有用
            Bundle bundle = (Bundle) getIntent().getExtras();
            mPlayer.setDefaultDecoder(mUseHardDecoder?0:1);
            // 重点: 在调试阶段可以使用以下方法打开native log
            mPlayer.enableNativeLog();

            if (mPosition != 0) {
                mPlayer.seekTo(mPosition);
            }
        }

        TextView vt = (TextView) findViewById(R.id.video_title);
        vt.setText(msTitle.toString());
        vt.setVisibility(View.VISIBLE);

        Log.d(TAG,"xiongbo url = "+msURI.toString());
        mPlayer.prepareAndPlay("/sdcard/test.mp4");
        if (mStatusListener != null)
            mStatusListener.notifyStatus(STATUS_START);

        new Handler().postDelayed(new Runnable() {
            public void run() {
//                mDecoderTypeView.setText(NDKCallback.getDecoderType() == 0 ? "HardDeCoder" : "SoftDecoder");
            }
        }, 5000);
        return true;

    }

    private void resetUI() {
        mSeekBar.setProgress(0);
        show_pause_ui(false, false);
        show_progress_ui(false);
        mErrInfoView.setText("");
    }

    //pause the video
    private void pause() {
        if (mPlayer != null) {
            mPlayer.pause();
            isPausePlayer = true;
            if (mStatusListener != null)
                mStatusListener.notifyStatus(STATUS_PAUSE);
            show_pause_ui(true, false);
            show_progress_ui(true);
        }
    }

    //start the video
    private void start() {

        if (mPlayer != null) {
            mPlayer.play();
            if (mStatusListener != null)
                mStatusListener.notifyStatus(STATUS_RESUME);
            show_pause_ui(false, false);
            show_progress_ui(false);
        }
    }

    //stop the video
    private void stop() {
        Log.d(TAG, "AudioRender: stop play");
        if (mPlayer != null) {
            mPlayer.stop();
            if (mStatusListener != null)
                mStatusListener.notifyStatus(STATUS_STOP);
            mPlayer.destroy();
            mPlayer = null;
        }
    }

//    private void getPlayUrl() {
//        mPlayingIndex = getVideoSourcePath(mPlayingIndex, msURI, msTitle);
//    }

    /**
     * 准备完成监听器:调度更新进度
     */
    private class VideoPreparedListener implements MediaPlayer.MediaPlayerPreparedListener {

        @Override
        public void onPrepared() {
            show_buffering_ui(false);
            Log.d(TAG, "onPrepared");
            if (mPlayer != null) {
                update_total_duration(mPlayer.getDuration());
                mTimerHandler.postDelayed(mRunnable, 1000);
                show_progress_ui(true);
                mTimerHandler.postDelayed(mUIRunnable, 3000);
            }
        }
    }


    /**
     * 错误处理监听器
     */
    private class VideoErrorListener implements MediaPlayer.MediaPlayerErrorListener {

        public void onError(int what, int extra) {
            show_buffering_ui(false);
            int errCode = 0;

            if (mPlayer == null) {
                return;
            }

            errCode = mPlayer.getErrorCode();
            switch (errCode) {
                case MediaPlayer.ALIVC_ERR_LOADING_TIMEOUT:
                    report_error("缓冲超时,请确认网络连接正常后重试", true);
//                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_INPUTFILE:
                    report_error("no input file", true);
//                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_VIEW:
                    report_error("no surface", true);
//                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_INVALID_INPUTFILE:
                    report_error("视频资源或者网络不可用", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_SUPPORT_CODEC:
                    report_error("no codec", true);
//                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_FUNCTION_DENIED:
                    report_error("no priority", true);
//                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_UNKNOWN:
                    report_error("unknown error", true);
//                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_NETWORK:
                    report_error("视频资源或者网络不可用", true);
//                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_ILLEGALSTATUS:
                    report_error("illegal call", true);
                    break;
                case MediaPlayer.ALIVC_ERR_NOTAUTH:
                    report_error("auth failed", true);
                    break;
                case MediaPlayer.ALIVC_ERR_READD:
                    report_error("资源访问失败,请重试", true);
//                    mPlayer.reset();
                    break;
                default:
                    break;

            }
        }
    }

    /**
     * 信息通知监听器:重点是缓存开始/结束
     */
    private class VideoInfolistener implements MediaPlayer.MediaPlayerInfoListener {

        public void onInfo(int what, int extra) {
            Log.d(TAG, "onInfo what = " + what + " extra = " + extra);
            switch (what) {
                case MediaPlayer.MEDIA_INFO_UNKNOW:
                    break;
                case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                    //pause();
                    show_buffering_ui(true);
                    break;
                case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                    //start();
                    show_buffering_ui(false);
                    break;
                case MediaPlayer.MEDIA_INFO_TRACKING_LAGGING:
                    break;
                case MediaPlayer.MEDIA_INFO_NETWORK_ERROR:
                    report_error("�������!", true);
                    break;
                case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                    if (mPlayer != null)
                        Log.d(TAG, "on Info first render start : " + ((long) mPlayer.getPropertyDouble(AliVcMediaPlayer.FFP_PROP_DOUBLE_1st_VFRAME_SHOW_TIME, -1) - (long) mPlayer.getPropertyDouble(AliVcMediaPlayer.FFP_PROP_DOUBLE_OPEN_STREAM_TIME, -1)));

                    break;
            }
        }
    }

    /**
     * 快进完成监听器
     */
    private class VideoSeekCompletelistener implements MediaPlayer.MediaPlayerSeekCompleteListener {

        public void onSeekCompleted() {
            mEnableUpdateProgress = true;
        }
    }

    /**
     * 视频播完监听器
     */
    private class VideoCompletelistener implements MediaPlayer.MediaPlayerCompletedListener {

        public void onCompleted() {
            Log.d(TAG, "onCompleted.");

            AlertDialog.Builder builder = new AlertDialog.Builder(PlayerTwoActivity.this);
            builder.setMessage("播放结束");

            builder.setTitle("提示");


            builder.setNegativeButton("退出", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    PlayerTwoActivity.this.finish();
                }
            });

            builder.create().show();
        }
    }

    /**
     * 视频大小变化监听器
     */
    private class VideoSizeChangelistener implements MediaPlayer.MediaPlayerVideoSizeChangeListener {

        public void onVideoSizeChange(int width, int height) {
            Log.d(TAG, "onVideoSizeChange width = " + width + " height = " + height);
        }
    }

    /**
     * 视频缓存变化监听器: percent 为 0~100之间的数字】
     */
    private class VideoBufferUpdatelistener implements MediaPlayer.MediaPlayerBufferingUpdateListener {

        public void onBufferingUpdateListener(int percent) {

        }
    }

    private class VideoStoppedListener implements  MediaPlayer.MediaPlayerStopedListener {
        @Override
        public void onStopped() {
            Log.d(TAG,"onVideoStopped.");
        }
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "AudioRender: onDestroy.");
        if (mPlayer != null) {
//            stop();
            mTimerHandler.removeCallbacks(mRunnable);
        }

        releaseWakeLock();

        if (connectionReceiver != null) {
            unregisterReceiver(connectionReceiver);
        }
        // 重点:在 activity destroy的时候,要停止播放器并释放播放器
        if (mPlayer != null) {
            mPosition = mPlayer.getCurrentPosition();
            stop();
            if (mPlayerControl != null)
                mPlayerControl.stop();
        }

        super.onDestroy();
        return;
    }

    @Override
    protected void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();

        // 重点:如果播放器是从锁屏/后台切换到前台,那么调用player.stat
        if (mPlayer != null && !isStopPlayer && isPausePlayer) {
            isPausePlayer = false;
            mPlayer.play();
            // 更新ui
            show_pause_ui(false, false);
            show_progress_ui(false);
        }

        mListView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onStart() {
        Log.e(TAG, "onStart.");
        super.onStart();
        if (!isCurrentRunningForeground) {
            Log.d(TAG, ">>>>>>>>>>>>>>>>>>>切到前台 activity process");
        }
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause.");
        super.onPause();
        // 重点:播放器没有停止,也没有暂停的时候,在activity的pause的时候也需要pause
        if (!isStopPlayer && !isPausePlayer && mPlayer != null) {
            mPlayer.pause();
            isPausePlayer = true;
        }
    }

    @Override
    protected void onStop() {
        Log.e(TAG, "onStop.");
        super.onStop();

        isCurrentRunningForeground = isRunningForeground();
        if (!isCurrentRunningForeground) {
            if(mPlayer != null){
                mPlayer.pause();
            }
            Log.d(TAG, ">>>>>>>>>>>>>>>>>>>切到后台 activity process");
        }
    }

    private Handler mTimerHandler = new Handler() {
        public void handleMessage(Message msg) {

            switch (msg.what) {

                case CMD_PAUSE:
                    pause();
                    break;
                case CMD_RESUME:
                    start();
                    break;
                case CMD_SEEK:
                    mPlayer.seekTo(msg.arg1);
                    break;
                case CMD_START:
                    startToPlay();
                    break;
                case CMD_STOP:
                    stop();
                    break;
                case CMD_VOLUME:
                    mPlayer.setVolume(msg.arg1);
                    break;
                default:
                    break;
            }
        }
    };
    Runnable mRunnable = new Runnable() {
        @Override
        public void run() {

            if (mPlayer != null && mPlayer.isPlaying())
                update_progress(mPlayer.getCurrentPosition());

            mTimerHandler.postDelayed(this, 1000);
        }
    };

    Runnable mUIRunnable = new Runnable() {
        @Override
        public void run() {
            show_progress_ui(false);
        }
    };


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            isStopPlayer = true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // 重点:判定是否在前台工作
    public boolean isRunningForeground() {
        ActivityManager activityManager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcessInfos = activityManager.getRunningAppProcesses();
        // 枚举进程
        for (ActivityManager.RunningAppProcessInfo appProcessInfo : appProcessInfos) {
            if (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                if (appProcessInfo.processName.equals(this.getApplicationInfo().processName)) {
                    Log.d(TAG, "EntryActivity isRunningForeGround");
                    return true;
                }
            }
        }
        Log.d(TAG, "EntryActivity isRunningBackGround");
        return false;
    }

    public void acquireVersion()
    {
        try {
            PackageManager pm = getPackageManager();
            //       String pn = getPackageName();
            PackageInfo pinfo = pm.getPackageInfo(getPackageName(), 0);
            String versionName = pinfo.versionName;
            mPrefixTitle = versionName;

        }catch(PackageManager.NameNotFoundException e){

        }

        return;
    }

    private Intent fileChooserIntent ;
    private void startPlayer(Video video,boolean hardwareCodec) {

        msURI.delete(0, msURI.length());
        msTitle.delete(0, msTitle.length());
        msTitle.append(video.getName());
        msURI.append(video.getUri());

        mUseHardDecoder = video.useHwDecoder;
        resetUI();

        createVideoSurface();

////        if(video.getName() == "aquire path")
////        {
////            startActivityForResult(fileChooserIntent , REQUEST_CODE);
////            return;
////        }
//
//        Intent intent = new Intent();
//        Bundle bundle = new Bundle();
//
//        bundle.putString("TITLE", video.getName());
//        bundle.putString("URI", video.getUri());
//        bundle.putInt("decode_type", video.useHwDecoder?0:1);
//
//        if(video.inLoopPlay()) {
//            int selectedIndex = 0;
//            Bundle loopBundle = new Bundle();
//            bundle.putBundle("loopList", loopBundle);
//            int k = 0;
//
//            for (Video v : videoList) {
//                if (v.inLoopPlay()) {
//                    loopBundle.putString("TITLE" + k, v.getName());
//                    loopBundle.putString("URI" + k, v.getUri());
//
//                    if(v.getUri() == video.getUri()){
//                        selectedIndex = k;
//                    }
//
//                    k++;
//                }
//            }
//            loopBundle.putInt("ItemCount", k);
//
//            loopBundle.putInt("SelectedIndex", selectedIndex);
//        }
//
//        intent.putExtras(bundle);
//        intent.setClass(mContext, PlayerActivity.class);
//        startActivity(intent);
//        mListView.setVisibility(View.INVISIBLE);
    }

    private View mLastSeletedItemView = null;
    private void decorateItem(View view, int position){
        if(mLastSeletedItemView != null){
            TextView pre = (TextView) mLastSeletedItemView.findViewById(R.id.video_title);
            pre.setTextColor(Color.BLACK);
            mLastSeletedItemView.setBackgroundColor(Color.WHITE);
        }
        TextView t = (TextView) view.findViewById(R.id.video_title);
        t.setTextColor(Color.WHITE);
        view.setBackgroundColor(Color.BLUE);



        //   t.setBackgroundColor(Color.DKGRAY);
        mLastSeletedItemView = view;
    }

    private void updateTitle(int selected, int total_item_count)
    {
        TextView titleView = (TextView)findViewById(R.id.listViewTitle);
        titleView.setText("videolist [ " + selected + "/" + total_item_count + " ] - (v" + mPrefixTitle + ")");

    }
    private void findViews() {
        mVideoListAdapter = new VideoListAdapter(videoList, mContext);

        mListView = (ListView) findViewById(R.id.fileListView);
        mListView.setAdapter(mVideoListAdapter);
        mListView.setOnItemClickListener(itemClickListener);



        // mListView.setOn

        mListView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                mSelectedPosition = position;
                decorateItem(view, position);

                updateTitle(position+1, videoList.size());
                //Toast.makeText(VideoListActivity.this, "item :" + t.getText(), Toast.LENGTH_SHORT).show();
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
                if(videoList.size() > 0) {
                    updateTitle(0, videoList.size());
                }else{
                    updateTitle(0, 0);
                }
                mSelectedPosition = -1;
            }
        });
    }

    AdapterView.OnItemClickListener itemClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> arg0, View view,
                                int position, long arg3) {

            //
//            mListView.setClickable(false);
            mListView.setEnabled(false);
            // 如果已经在播放了,那么停止播放
            if (mPlayer != null) {
                mPlayer.releaseVideoSurface();
                mPlayer.stop();
                mPlayer.destroy();
                mPlayer = null;

            }

            Video v = videoList.get(position);
            CheckBox checkBox = (CheckBox)view.findViewById(R.id.checkBox1);
            boolean isChecked = checkBox.isChecked() ;
            startPlayer(v,isChecked);
//            mListView.setClickable(true);
            mListView.setEnabled(true);
            return;
        }
    };

    private List<Video> getLocationVideoList(String rootPath) {
        List<Video> videoList=new ArrayList<Video>();

        String videoRootPath = mRootDir;
        File dir = new File(videoRootPath);
        if (!dir.isDirectory()) {
            return videoList;
        }

        File[] files = dir.listFiles();
        if (null == files) {
            return videoList;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            } else {
                String fullPath = file.getAbsolutePath().toLowerCase();
                String prefix = getExtension(fullPath);
                if (getVideoFilter().contains(prefix)) {
                    File f = new File(fullPath);
                    String fileName = f.getName();
                    long size = f.length();
                    Video video = new Video(fileName, fullPath, "",
                            (int) size, Boolean.TRUE);
                    videoList.add(video);
                }
            }
        }

        return videoList;
    }

    private ArrayList<Video> getRemoteVideoList() {

        ArrayList<Video> videoList = new ArrayList<Video>();

//        Video firstVideo = new Video();
//        firstVideo.setName("aquire path");
//        firstVideo.setLocation(Boolean.FALSE);
//        videoList.add(firstVideo);

        String listFile = mRootDir + File.separator + "videoList.txt";
        String SPACE_CHAR = "\\s+";

        File file = new File(listFile);
        if (!file.exists()) {
            return videoList;
        }

        InputStreamReader reader;
        Closeable resource = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(reader);
            resource = bufferedReader;
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                String[] content = line.split(SPACE_CHAR);
                Log.d("VideoList", "Line = " + line);
                Log.d("VideoList", "content length = " + content.length);
                if (content != null && content.length >= 4) {
                    String title = content[0];
                    String id = content[1];
                    String definition = content[2];
                    String url = content[3];

                    Video video = new Video();
                    if(content.length>4){
                        String hw=content[4];
                        int t=Integer.parseInt(hw);
                        if(t==1){
                            video.setUseHwDecoder(false);
//                            video.setInLoopPlay(true);
                        }
                    }

                    video.setName(title);
                    video.setVideoId(Long.valueOf(id));
                    video.setDefinition(definition);
                    video.setUri(url);
                    video.setLocation(Boolean.FALSE);
                    videoList.add(video);
                }
            }
            bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                resource.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return videoList;
    }


    class FilelistRefreshThread extends Thread {

        public void run() {
            if (isFirst.get()) {
                return;
            }

            init();

            List<Video> remotList = getRemoteVideoList();
            List<Video> localList = getLocationVideoList(mRootDir);
            remotList.addAll(localList);
            videoList.addAll(remotList);


            mHandler.sendMessage(mHandler.obtainMessage(0));

            mHandler.sendMessageDelayed(mHandler.obtainMessage(1),100);

            isFirst.set(Boolean.TRUE);

        }

        // TODO 为方便测试不同的手机平台的下对h264 ＋ aac的硬解的支持率添加,之后需要删除
        private void init() {
            File rootPath = new File(mRootDir);
            if (!rootPath.exists()) {
                rootPath.mkdir();
            }

            File videoListFile = new File(mRootDir, "videolist.txt");
            if (!videoListFile.exists()) {
                try {
                    FileWriter fileWriter = new FileWriter(videoListFile);
                    fileWriter.write("rtmp[标清] 3 hd rtmp://tan.cdnpe.com/app-test/video-test_sd");
                    fileWriter.flush();
                    fileWriter.close();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    class VideoListAdapter extends BaseAdapter {

        private Context mContext;
        ArrayList<Video> mDataFilelist;
        private TextView mTitle;
        private TextView mIsLocation;

        public VideoListAdapter(ArrayList<Video> videoList, Context context) {
            mDataFilelist = videoList;
            mContext = context;
        }

        public int getCount() {
            return mDataFilelist.size();
        }

        public Object getItem(int position) {
            return mDataFilelist.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if(convertView==null){
                convertView = LayoutInflater.from(mContext).inflate(R.layout.videoitem, null);
                viewHolder=new ViewHolder();
                viewHolder.checkBox=(CheckBox)convertView.findViewById(R.id.checkBox1);
                viewHolder.titleTV= (TextView) convertView.findViewById(R.id.video_title);
                viewHolder.isLocationTV=(TextView) convertView.findViewById(R.id.video_source);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder)convertView.getTag();
            }
            mTitle = viewHolder.titleTV;
            mIsLocation =viewHolder.isLocationTV;
            CheckBox checkBox =viewHolder.checkBox;

            Video v = mDataFilelist.get(position);

            checkBox.setChecked(v.isUseHwDecoder());

            boolean inLoop = v.inLoopPlay();
            String sInloop = inLoop ? "looplist" : "";

            if (v.isLocation()) {
                mTitle.setText(v.getName());

                mIsLocation.setText("local" + sInloop);
            }else {
                mTitle.setText(v.getName()+"_"+v.getVideoId()+"_"+v.getDefinition());
                mIsLocation.setText("network" + sInloop);
//                checkBox.setEnabled(false);
            }
            return convertView;
        }

    }

    static class ViewHolder
    {
        public CheckBox checkBox;
        public TextView titleTV;
        public TextView isLocationTV;
    }


    ////// util function
    private static String getExtension(String filename) {
        if ((filename != null) && (filename.length() > 0)) {
            int dot = filename.lastIndexOf('.');
            if ((dot >-1) && (dot < (filename.length() - 1))) {
                return filename.substring(dot + 1);
            }
        }
        return filename;
    }

    ///////util function
    private static HashSet<String> getVideoFilter() {
        HashSet<String> fileFilter = new HashSet<String>();
        fileFilter.add("mp4");
        fileFilter.add("mkv");
        fileFilter.add("flv");
        fileFilter.add("wmv");
        fileFilter.add("ts");
        fileFilter.add("rm");
        fileFilter.add("rmvb");
        fileFilter.add("webm");
        fileFilter.add("mov");
        fileFilter.add("vstream");
        fileFilter.add("mpeg");
        fileFilter.add("f4v");
        fileFilter.add("avi");
        fileFilter.add("mkv");
        fileFilter.add("ogv");
        fileFilter.add("dv");
        fileFilter.add("divx");
        fileFilter.add("vob");
        fileFilter.add("asf");
        fileFilter.add("3gp");
        fileFilter.add("h264");
        fileFilter.add("hevc");
        fileFilter.add("h261");
        fileFilter.add("h263");
        fileFilter.add("m3u8");
        fileFilter.add("avs");
        fileFilter.add("swf");
        fileFilter.add("m4v");
        fileFilter.add("mpg");
        return fileFilter;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //Video element
    public class Video {
        /**
         * video ID
         */
        private long videoId;
        /**
         * video name
         */
        private String name;
        /**
         * video address
         */
        private String uri;
        /**
         * video scale picture
         */
        private String pic;
        /**
         * video file
         */
        private int size;
        /**
         * video format
         */
        private String format;
        /**
         * video duration
         */
        private int druration;
        /**
         * video definition
         */
        private String definition ;
        /**
         * video location
         */
        private boolean isLocation;

        private boolean useHwDecoder = true;

        private boolean inLoopPlay = false;

        public Video(){

        }
        public Video(String name, String uri, String pic, int size,boolean isLocation) {
            this.name = name;
            this.uri = uri;
            this.pic = pic;
            this.size = size;
            this.isLocation = isLocation;
        }

        public void setInLoopPlay(boolean b ) { this.inLoopPlay= b;};
        public boolean inLoopPlay(){return this.inLoopPlay;};

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getPic() {
            return pic;
        }

        public void setPic(String pic) {
            this.pic = pic;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public int getDruration() {
            return druration;
        }

        public void setDruration(int druration) {
            this.druration = druration;
        }

        public boolean isLocation(){
            return isLocation;
        }

        public void setLocation(boolean isLocation){
            this.isLocation = isLocation;
        }

        public long getVideoId() {
            return videoId;
        }

        public void setVideoId(long videoId) {
            this.videoId = videoId;
        }

        public String getDefinition() {
            return definition;
        }

        public void setDefinition(String definition) {
            this.definition = definition;
        }

        public boolean isUseHwDecoder() {
            return useHwDecoder;
        }

        public void setUseHwDecoder(boolean useHwDecoder) {
            this.useHwDecoder = useHwDecoder;
        }
    }

}
