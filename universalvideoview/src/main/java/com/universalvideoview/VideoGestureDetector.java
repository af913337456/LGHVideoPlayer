//package com.universalvideoview;
//
//import android.media.AudioManager;
//import android.util.Log;
//import android.view.GestureDetector;
//import android.view.MotionEvent;
//import android.view.View;
//import android.view.WindowManager;
//import android.widget.ImageView;
//import android.widget.TextView;
//
///**
// * Created by Administrator on 2016/6/6.
// *
// */
//
//public class VideoGestureDetector extends GestureDetector.SimpleOnGestureListener {
//
//    /** 亮度 和 音度 */
//    public int mMaxVolume;
//    public float brightness=-1;
//    public int volume=-1;
//    public int screenWidthPixels;
//    public int videoHeight = 1;
//
//    private boolean firstTouch;
//    private boolean volumeControl;
//    private boolean toSeek;
//
//    private AudioManager audioManager;
//
//
//    /**
//     * 双击
//     */
//    @Override
//    public boolean onDoubleTap(MotionEvent e) {
//        //videoView.toggleAspectRatio();
//
//        return true;
//    }
//
//    @Override
//    public boolean onDown(MotionEvent e) {
//        Log.d("zzzzz"," onDown");
//        firstTouch = true;
//        return super.onDown(e);
//    }
//
//    /**
//     * 滑动
//     */
//    @Override
//    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
//        Log.d("zzzzz"," onScroll");
//        float mOldX = e1.getX(), mOldY = e1.getY();
//        float deltaY = mOldY - e2.getY();
//        float deltaX = mOldX - e2.getX();
//        if (firstTouch) {
//            toSeek = Math.abs(distanceX) >= Math.abs(distanceY);
//            volumeControl=mOldX > screenWidthPixels * 0.5f;
//            firstTouch = false;
//        }
//
//        if (toSeek) {
//
//        } else {
//            float percent = deltaY / mVideoView.getHeight();
//            if (volumeControl) {
//                onVolumeSlide(percent);
//            } else {
//                onBrightnessSlide(percent);
//            }
//        }
//        return super.onScroll(e1, e2, distanceX, distanceY);
//    }
//
//    @Override
//    public boolean onSingleTapUp(MotionEvent e) {
//        Log.d("zzzzz"," onSingleTapUp");
//        if (mVideoView.isInPlaybackState()) {
//            mVideoView.toggleMediaControlsVisibility();
//        }
//        return true;
//    }
//
//    /**
//     * 滑动改变声音大小
//     *
//     * @param percent
//     */
//
//    private void onVolumeSlide(float percent) {
//        if (volume == -1) {
//            volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
//            if (volume < 0)
//                volume = 0;
//        }
//        MediaController.hide();
//
//        int index = (int) (percent * mMaxVolume) + volume;
//        if (index > mMaxVolume)
//            index = mMaxVolume;
//        else if (index < 0)
//            index = 0;
//
//        // 变更声音
//        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);
//
//        // 变更进度条
//        int i = (int) (index * 1.0 / mMaxVolume * 100);
//        String s = i + "%";
//        if (i == 0) {
//            s = "关闭";
//        }
//        // 显示
//        ((ImageView)view.findViewById(R.id.app_video_volume_icon))
//                .setImageResource(i==0?R.drawable.ic_volume_off_white_36dp:R.drawable.ic_volume_up_white_36dp);
//        view.findViewById(R.id.app_video_brightness_box).setVisibility(View.GONE);
//        view.findViewById(R.id.app_video_volume_box).setVisibility(View.VISIBLE);
//        view.findViewById(R.id.app_video_volume_box).setVisibility(View.VISIBLE);
//
//        TextView textVoice = (TextView) view.findViewById(R.id.app_video_volume);
//        textVoice.setText(s);
//        textVoice.setVisibility(View.VISIBLE);
//    }
//
//    /**
//     * 滑动改变亮度
//     *
//     * @param percent
//     */
//    private void onBrightnessSlide(float percent) {
//        if (brightness < 0) {
//            brightness = activity.getWindow().getAttributes().screenBrightness;
//            if (brightness <= 0.00f){
//                brightness = 0.50f;
//            }else if (brightness < 0.01f){
//                brightness = 0.01f;
//            }
//        }
//        Log.d(this.getClass().getSimpleName(),"brightness:"+brightness+",percent:"+ percent);
//        view.findViewById(R.id.app_video_brightness_box).setVisibility(View.VISIBLE);
//        WindowManager.LayoutParams lpa = activity.getWindow().getAttributes();
//        lpa.screenBrightness = brightness + percent;
//        if (lpa.screenBrightness > 1.0f){
//            lpa.screenBrightness = 1.0f;
//        }else if (lpa.screenBrightness < 0.01f){
//            lpa.screenBrightness = 0.01f;
//        }
//        TextView brightVoice = (TextView) view.findViewById(R.id.app_video_brightness);
//        brightVoice.setText(""+((int) (lpa.screenBrightness * 100))+"%");
//        activity.getWindow().setAttributes(lpa);
//
//    }
//
//}
