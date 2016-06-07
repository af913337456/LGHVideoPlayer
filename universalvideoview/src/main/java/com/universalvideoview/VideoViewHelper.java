package com.universalvideoview;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by 林冠宏 on 2016/6/6.
 *
 */

public class VideoViewHelper{

    private LinearLayout container;
    private RelativeLayout fullContainer;
    private UniversalVideoView mVideoView;
    private View view,brightView,voiceView;

    private boolean isChoiseSecondFullWay = false; /** 第二种全屏方式 */
    private UniversalMediaController MediaController;
    private Activity activity;
    private int cachedHeight;
    private String url;
    private int screenWidthPixels; /** 屏幕宽度像素 */

    public static GestureDetector gestureDetector;

    /** 亮度 和 音度 */
    private int mMaxVolume;
    public static float brightness=-1;
    public static int volume=-1;

    public VideoViewHelper(Activity activity,LinearLayout container, RelativeLayout fullContainer, String url){

        this.activity = activity;
        this.container = container;
        this.fullContainer = fullContainer;
        this.url = url;

    }

    public VideoViewHelper(Activity activity,LinearLayout container,String url){

        this.activity = activity;
        this.container = container;
        this.url = url;

    }

    /**
     * 置视频区域大小
     */
    private void setVideoAreaSize() {

        container.post(new Runnable() {
            @Override
            public void run() {
                int width = container.getWidth();
                cachedHeight = (int) (width * 405f / 720f);
                ViewGroup.LayoutParams videoLayoutParams = container.getLayoutParams();
                videoLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                videoLayoutParams.height = cachedHeight;
                container.setLayoutParams(videoLayoutParams);
                mVideoView.setVideoPath(url);
                mVideoView.requestFocus();
            }
        });
    }

    public void init(){
        view =  LayoutInflater.from(activity).inflate(R.layout.video_layout,container,false);

        brightView = view.findViewById(R.id.app_video_brightness_box);

        voiceView = view.findViewById(R.id.app_video_volume_box);

        MediaController = (UniversalMediaController) view.findViewById(R.id.media_controller);

        MediaController.setFirstPlayListener(new UniversalMediaController.FirstPlayListener() {
            @Override
            public void onFirstPlay() {
                Log.d("zzzzz","first play");
                /** 如果自己添加 首次 播放 按钮，点击事件 要包含下面的 2 行 */
                mVideoView.setVideoPath(url);
                mVideoView.start();
            }
        });

        mVideoView = (UniversalVideoView) view.findViewById(R.id.videoView);

        mVideoView.setMediaController(MediaController);

        //mVideoView.openVideo();

        /** 控件管理者 执行 onTouch 的时候 up 时间回调 */
        mVideoView.setOnTouchUpListener(new UniversalVideoView.OnTouchUpListener() {
            @Override
            public void onTouchUp(View touchView) {
                /** 隐藏掉 声音 和 亮度 的 */
                view.findViewById(R.id.app_video_brightness_box).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        brightView.setVisibility(View.GONE);
                        voiceView.setVisibility(View.GONE);
                    }
                },1500);
            }
        });

        mVideoView.setFullScreenListener(new UniversalVideoView.OnFullScreenListener() {
            @Override
            public void onScaleChange(boolean isFullscreen) {
                switchTitleBar(!isFullscreen);
                if(isChoiseSecondFullWay){
                    if(fullContainer==null){
                        Toast.makeText(activity,"第二种的全屏方式必须传入 fullContainerView ！",Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if(isFullscreen){ /** 横屏 */
                        container.removeView(view);
                        fullContainer.addView(view);
                        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                                RelativeLayout.LayoutParams.MATCH_PARENT,
                                RelativeLayout.LayoutParams.MATCH_PARENT);
                        view.setLayoutParams(lp);

                    }else{
                        fullContainer.removeView(view);
                        container.addView(view);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,250*3);
                        view.setLayoutParams(lp);
                    }
                }
            }
        });

        audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        mMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        screenWidthPixels = activity.getResources().getDisplayMetrics().widthPixels;

        gestureDetector = new GestureDetector(activity, new PlayerGestureListener());

        //setVideoAreaSize();

        /** 下面注册断网广播 */
        IntentFilter mFilter = new IntentFilter();
        mFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        activity.registerReceiver(
                new NetWorkChangeBroadcastReceiver(),
                mFilter
        );

        container.addView(view);
    }


    public VideoViewHelper withVideoViewCallback(UniversalVideoView.VideoViewCallback VideoViewCallback){
        mVideoView.setVideoViewCallback(VideoViewCallback);
        return this;
    }

    public VideoViewHelper withSecondFullWay(boolean SecondFullWay){
        this.isChoiseSecondFullWay = SecondFullWay;
        return this;
    }

    public void pause(){
        if(mVideoView != null) {
            mVideoView.getmMediaController().pauseFromOutSide();
        }
    }

    public void resume(){
        if(mVideoView != null){
            mVideoView.getmMediaController().resumeFromOutSide();
        }
    }

    public void destroy(){
        if(mVideoView!=null){
            mVideoView.release(true);
            mVideoView = null;
            System.gc();
        }
    }

    /** 隐藏 顶部 控件栏 的在 回调里面设置了 */
    /** 隐藏 顶部 bar */
    private void switchTitleBar(boolean show) {
        android.support.v7.app.ActionBar supportActionBar = ((AppCompatActivity)activity).getSupportActionBar();
        if (supportActionBar != null) {
            if (show) {
                supportActionBar.show();
            } else {
                supportActionBar.hide();
            }
        }
    }

    public class PlayerGestureListener extends GestureDetector.SimpleOnGestureListener {

        private boolean firstTouch;
        private boolean volumeControl;
        private boolean toSeek;

        /**
         * 双击
         */
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            //videoView.toggleAspectRatio();

            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            Log.d("zzzzz"," onDown");
            firstTouch = true;
            return super.onDown(e);
        }

        /**
         * 滑动
         */
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            Log.d("zzzzz"," onScroll");
            float mOldX = e1.getX(), mOldY = e1.getY();
            float deltaY = mOldY - e2.getY();
            float deltaX = mOldX - e2.getX();
            if (firstTouch) {
                toSeek = Math.abs(distanceX) >= Math.abs(distanceY);
                volumeControl=mOldX > screenWidthPixels * 0.5f;
                firstTouch = false;
            }

            if (toSeek) {

            } else {
                float percent = deltaY / mVideoView.getHeight();
                if (volumeControl) {
                    onVolumeSlide(percent);
                } else {
                    onBrightnessSlide(percent);
                }
            }
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            Log.d("zzzzz"," onSingleTapUp");
            if (mVideoView.isInPlaybackState()) {
                mVideoView.toggleMediaControlsVisibility();
            }

            return true;
        }

    }

    /**
     * 滑动改变声音大小
     *
     * @param percent
     */
    private AudioManager audioManager;
    private void onVolumeSlide(float percent) {
        if (volume == -1) {
            volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (volume < 0)
                volume = 0;
        }


        int index = (int) (percent * mMaxVolume) + volume;
        if (index > mMaxVolume)
            index = mMaxVolume;
        else if (index < 0)
            index = 0;

        // 变更声音
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);

        // 变更进度条
        int i = (int) (index * 1.0 / mMaxVolume * 100);
        String s = i + "%";
        if (i == 0) {
            s = "关闭";
        }
        // 显示
        ((ImageView)view.findViewById(R.id.app_video_volume_icon))
                .setImageResource(i==0?R.drawable.ic_volume_off_white_36dp:R.drawable.ic_volume_up_white_36dp);

        brightView.setVisibility(View.GONE);
        voiceView.setVisibility(View.VISIBLE);

        TextView textVoice = (TextView) view.findViewById(R.id.app_video_volume);
        textVoice.setText(s);
        textVoice.setVisibility(View.VISIBLE);
    }

    /**
     * 滑动改变亮度
     *
     * @param percent
     */
    private void onBrightnessSlide(float percent) {
        if (brightness < 0) {
            brightness = activity.getWindow().getAttributes().screenBrightness;
            if (brightness <= 0.00f){
                brightness = 0.50f;
            }else if (brightness < 0.01f){
                brightness = 0.01f;
            }
        }
        Log.d(this.getClass().getSimpleName(),"brightness:"+brightness+",percent:"+ percent);

        voiceView.setVisibility(View.GONE);
        brightView.setVisibility(View.VISIBLE);

        WindowManager.LayoutParams lpa = activity.getWindow().getAttributes();
        lpa.screenBrightness = brightness + percent;
        if (lpa.screenBrightness > 1.0f){
            lpa.screenBrightness = 1.0f;
        }else if (lpa.screenBrightness < 0.01f){
            lpa.screenBrightness = 0.01f;
        }
        TextView brightVoice = (TextView) view.findViewById(R.id.app_video_brightness);
        brightVoice.setText(""+((int) (lpa.screenBrightness * 100))+"%");
        activity.getWindow().setAttributes(lpa);

    }

    /** 断网广播接收 */
    private boolean isNetErr = false;
    public class NetWorkChangeBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if(((ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo()==null){

                /** 断网 */
                isNetErr = true;
                if(MediaController.mProgress.getSecondaryProgress() > MediaController.mProgress.getProgress()){
                    Toast.makeText(context,"检测到您已断网!",Toast.LENGTH_LONG).show();
                }else{
                    if(mVideoView.canPause()){
                        mVideoView.pause();
                    }
                }

                /** 还一种情况是还在缓冲就断网了 */
//                if(mVideoView.isPlaying()){
//                    if(mVideoView.canPause()){
//                        Log.d("zzzzz","net change videoView.pause();");
//                        mVideoView.pause();
//                    }
//                }
            }else{ /** 恢复 */
                if(isNetErr){
                    isNetErr = false;
                    Log.d("zzzzz","net change doPauseResume");
                    MediaController.showLoading();
                    MediaController.doPauseResume();
                }
            }
        }
    }

}
