
package com.alivc.testmediaplayer;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.*;
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
import com.alivc.player.MediaPlayer;
import com.alivc.player.NDKCallback;

import java.util.List;

public class PlayerActivity extends Activity {

    public static final String TAG = "PlayerActivity";

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
    private GestureDetector mGestureDetector;
    private int mPosition = 0;
    private int mVolumn = 50;
    private MediaPlayer.VideoScalingMode mScalingMode = MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING;
    private boolean mMute = false;

    private PlayerControl mPlayerControl = null;

    private PowerManager.WakeLock mWakeLock = null;

    private StatusListener mStatusListener = null;

    private boolean isLastWifiConnected = false;

    // 标记播放器是否已经停止
    private boolean isStopPlayer = false;
    // 标记播放器是否已经暂停
    private boolean isPausePlayer = false;
    private boolean isPausedByUser = false;
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
                    mPosition = mPlayer.getCurrentPosition();
                    // 重点:新增接口,此处必须要将之前的surface释放掉
                    mPlayer.releaseVideoSurface();
                    mPlayer.stop();
                    mPlayer.destroy();
                    mPlayer = null;
                }
                dialog();
            }
        }
    };

    protected void dialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
        builder.setMessage("确认继续播放吗？");

        builder.setTitle("提示");

        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                initSurface();

            }
        });

        builder.setNegativeButton("退出", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                PlayerActivity.this.finish();
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

        setContentView(R.layout.activity_play);
        mPlayingIndex = -1;

        if (TEST == 1) {
            mPlayerControl = new PlayerControl(this);
            mPlayerControl.setControllerListener(mController);
        }
        acquireWakeLock();

        init();
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

    public void switchScalingMode(View view) {
        if (mPlayer != null) {
            if (mScalingMode == MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING) {
                mPlayer.setVideoScalingMode(MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                mScalingMode = MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT;
            } else {
                mPlayer.setVideoScalingMode(MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                mScalingMode = MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING;
            }
        }
    }

    public void switchMute(View view) {
        if (mPlayer != null) {
            if (mMute == false) {
                mMute = true;
                mPlayer.setMuteMode(true);
            } else {
                mMute = false;
                mPlayer.setMuteMode(false);
            }
        }
    }

    public void gotoActivity(View view) {
        startActivity(new Intent(this, BlankActivity.class));
    }

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
    private boolean initSurface() {
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
                        if (mPlayer != null && mPlayer.getDuration() > 0)
                            pause();

                    }
                    return false;
                }
                return false;
            }
        });

        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(mSurfaceHolderCB);

        getPlayUrl();

        return true;
    }

    private boolean init() {
        mTipLayout = (LinearLayout) findViewById(R.id.LayoutTip);
        mSeekBar = (SeekBar) findViewById(R.id.progress);
        mTipView = (TextView) findViewById(R.id.text_tip);
        mCurDurationView = (TextView) findViewById(R.id.current_duration);
        mErrInfoView = (TextView) findViewById(R.id.error_info);
        mDecoderTypeView = (TextView) findViewById(R.id.decoder_type);

        initSurface();

        return true;
    }

    private int getVideoSourcePath(int curIndex, StringBuilder sURI, StringBuilder sTitle) {
        //clear all now
        sURI.delete(0, sURI.length());
        sTitle.delete(0, sTitle.length());

        Bundle bundle = (Bundle) getIntent().getExtras();
        int selected = -1;
        if (curIndex == -1) { //we play the selected item
            sTitle.append(bundle.getString("TITLE"));
            sURI.append(bundle.getString("URI"));
        }
        Bundle loopBundle = bundle.getBundle("loopList");
        if (loopBundle != null) {
            int count = loopBundle.getInt("ItemCount");
            if (curIndex == -1) {
                selected = loopBundle.getInt("SelectedIndex");
            } else {
                selected = curIndex + 1;
                selected = (selected == count ? 0 : selected);
                sURI.append(loopBundle.getString("URI" + selected));
                sTitle.append(loopBundle.getString("TITLE" + selected));
            }
        }
        return selected;
    }

    ;

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
            mPlayer.setDefaultDecoder(1);
            // 重点: 在调试阶段可以使用以下方法打开native log
            mPlayer.enableNativeLog();

            if (mPosition != 0) {
                mPlayer.seekTo(mPosition);
            }
        }

        TextView vt = (TextView) findViewById(R.id.video_title);
        vt.setText(msTitle.toString());
        vt.setVisibility(View.VISIBLE);


        mPlayer.prepareAndPlay(msURI.toString());
        if (mStatusListener != null)
            mStatusListener.notifyStatus(STATUS_START);

        new Handler().postDelayed(new Runnable() {
            public void run() {
                mDecoderTypeView.setText(NDKCallback.getDecoderType() == 0 ? "HardDeCoder" : "SoftDecoder");
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
            isPausedByUser = true;
            if (mStatusListener != null)
                mStatusListener.notifyStatus(STATUS_PAUSE);
            show_pause_ui(true, false);
            show_progress_ui(true);
        }
    }

    //start the video
    private void start() {

        if (mPlayer != null) {
            isPausePlayer = false;
            isPausedByUser = false;
            isStopPlayer = false;
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

    private void getPlayUrl() {
        mPlayingIndex = getVideoSourcePath(mPlayingIndex, msURI, msTitle);
    }

    /**
     * 准备完成监听器:调度更新进度
     */
    private class VideoPreparedListener implements MediaPlayer.MediaPlayerPreparedListener {

        @Override
        public void onPrepared() {
            Log.d(TAG, "onPrepared");
            if (mPlayer != null) {
                mPlayer.setVideoScalingMode(MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
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
            int errCode = 0;

            if (mPlayer == null) {
                return;
            }

            errCode = mPlayer.getErrorCode();
            switch (errCode) {
                case MediaPlayer.ALIVC_ERR_LOADING_TIMEOUT:
                    report_error("缓冲超时,请确认网络连接正常后重试", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_INPUTFILE:
                    report_error("no input file", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_VIEW:
                    report_error("no surface", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_INVALID_INPUTFILE:
                    report_error("视频资源或者网络不可用", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_SUPPORT_CODEC:
                    report_error("no codec", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_FUNCTION_DENIED:
                    report_error("no priority", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_UNKNOWN:
                    report_error("unknown error", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_NETWORK:
                    report_error("视频资源或者网络不可用", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_ILLEGALSTATUS:
                    report_error("illegal call", true);
                    break;
                case MediaPlayer.ALIVC_ERR_NOTAUTH:
                    report_error("auth failed", true);
                    break;
                case MediaPlayer.ALIVC_ERR_READD:
                    report_error("资源访问失败,请重试", true);
                    mPlayer.reset();
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

            AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
            builder.setMessage("播放结束");

            builder.setTitle("提示");


            builder.setNegativeButton("退出", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    PlayerActivity.this.finish();
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

    private class VideoStoppedListener implements MediaPlayer.MediaPlayerStopedListener {
        @Override
        public void onStopped() {
            Log.d(TAG, "onVideoStopped.");
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
            if (!isPausedByUser) {
                isPausePlayer = false;
                mPlayer.play();
                // 更新ui
                show_pause_ui(false, false);
                show_progress_ui(false);
            }
        }
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
        Log.e(TAG, "onPause." + isStopPlayer + " " + isPausePlayer + " " + (mPlayer == null));
        super.onPause();
        // 重点:播放器没有停止,也没有暂停的时候,在activity的pause的时候也需要pause
        if (!isStopPlayer && !isPausePlayer && mPlayer != null) {
            Log.e(TAG, "onPause mpayer.");
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

}
