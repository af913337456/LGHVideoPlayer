/*
* Copyright (C) 2015 Author <dictfb#gmail.com>
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/


package com.universalvideoview;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.TypedArray;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.IOException;
import java.util.Map;


public class UniversalVideoView extends SurfaceView
        implements
        UniversalMediaController.MediaPlayerControl,
        OrientationDetector.OrientationChangeListener,
        UniversalMediaController.FullScreenListener

{
    private String TAG = "zzzzz";
    // settable by the client
    private Uri mUri;

    // all possible internal states
    private static final int STATE_ERROR              = -1;
    private static final int STATE_IDLE               = 0;
    private static final int STATE_PREPARING          = 1;
    private static final int STATE_PREPARED           = 2;
    private static final int STATE_PLAYING            = 3;
    private static final int STATE_PAUSED             = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;

    // mCurrentState is a VideoView object's current state.
    // mTargetState is the state that a method caller intends to reach.
    // For instance, regardless the VideoView object's current state,
    // calling pause() intends to bring the object to a target state
    // of STATE_PAUSED.
    private int mCurrentState = STATE_IDLE;
    private int mTargetState  = STATE_IDLE;

    // All the stuff we need for playing and showing a video
    private SurfaceHolder mSurfaceHolder = null;
    private MediaPlayer mMediaPlayer = null;
    private int         mAudioSession;
    private int         mVideoWidth;
    private int         mVideoHeight;
    private int         mSurfaceWidth;
    private int         mSurfaceHeight;
    private UniversalMediaController mMediaController;
    private MediaPlayer.OnCompletionListener mOnCompletionListener;
    private MediaPlayer.OnPreparedListener mOnPreparedListener;
    private int         mCurrentBufferPercentage;
    private MediaPlayer.OnErrorListener mOnErrorListener;
    private MediaPlayer.OnInfoListener mOnInfoListener;
    private int         mSeekWhenPrepared;  // recording the seek position while preparing
    private boolean     mCanPause;
    private boolean     mCanSeekBack;
    private boolean     mCanSeekForward;
    private boolean     mPreparedBeforeStart;
    private Context mContext;
    private boolean     mFitXY = false;
    private boolean     mAutoRotation = false;
    private int  mVideoViewLayoutWidth = 0;
    private int  mVideoViewLayoutHeight = 0;

    private OrientationDetector mOrientationDetector;
    private VideoViewCallback videoViewCallback;

    public UniversalVideoView(Context context) {
        this(context,null);
    }

    public UniversalVideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UniversalVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        TypedArray a = mContext.obtainStyledAttributes(attrs, R.styleable.UniversalVideoView,0,0);
        mFitXY = a.getBoolean(R.styleable.UniversalVideoView_uvv_fitXY, false);
        mAutoRotation = a.getBoolean(R.styleable.UniversalVideoView_uvv_autoRotation, false);
        a.recycle();
        initVideoView();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mFitXY) {
            onMeasureFitXY(widthMeasureSpec, heightMeasureSpec);
        } else {
            onMeasureKeepAspectRatio(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private void onMeasureFitXY(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
        int height = getDefaultSize(mVideoHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    private void onMeasureKeepAspectRatio(int widthMeasureSpec, int heightMeasureSpec) {
        //Log.i("@@@@", "onMeasure(" + MeasureSpec.toString(widthMeasureSpec) + ", "
        //        + MeasureSpec.toString(heightMeasureSpec) + ")");

        int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
        int height = getDefaultSize(mVideoHeight, heightMeasureSpec);
        if (mVideoWidth > 0 && mVideoHeight > 0) {

            int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
            int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
            int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);

            if (widthSpecMode == MeasureSpec.EXACTLY && heightSpecMode == MeasureSpec.EXACTLY) {
                // the size is fixed
                width = widthSpecSize;
                height = heightSpecSize;

                // for compatibility, we adjust size based on aspect ratio
                if ( mVideoWidth * height  < width * mVideoHeight ) {
                    //Log.i("@@@", "image too wide, correcting");
                    width = height * mVideoWidth / mVideoHeight;
                } else if ( mVideoWidth * height  > width * mVideoHeight ) {
                    //Log.i("@@@", "image too tall, correcting");
                    height = width * mVideoHeight / mVideoWidth;
                }
            } else if (widthSpecMode == MeasureSpec.EXACTLY) {
                // only the width is fixed, adjust the height to match aspect ratio if possible
                width = widthSpecSize;
                height = width * mVideoHeight / mVideoWidth;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    // couldn't match aspect ratio within the constraints
                    height = heightSpecSize;
                }
            } else if (heightSpecMode == MeasureSpec.EXACTLY) {
                // only the height is fixed, adjust the width to match aspect ratio if possible
                height = heightSpecSize;
                width = height * mVideoWidth / mVideoHeight;
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    // couldn't match aspect ratio within the constraints
                    width = widthSpecSize;
                }
            } else {
                // neither the width nor the height are fixed, try to use actual video size
                width = mVideoWidth;
                height = mVideoHeight;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    // too tall, decrease both width and height
                    height = heightSpecSize;
                    width = height * mVideoWidth / mVideoHeight;
                }
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    // too wide, decrease both width and height
                    width = widthSpecSize;
                    height = width * mVideoHeight / mVideoWidth;
                }
            }
        } else {
            // no size yet, just adopt the given spec sizes
        }
        setMeasuredDimension(width, height);
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(UniversalVideoView.class.getName());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            info.setClassName(UniversalVideoView.class.getName());
        }
    }

    public int resolveAdjustedSize(int desiredSize, int measureSpec) {
        return getDefaultSize(desiredSize, measureSpec);
    }

    private void initVideoView() {
        mVideoWidth = 0;
        mVideoHeight = 0;
        getHolder().addCallback(mSHCallback);
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        mCurrentState = STATE_IDLE;
        mTargetState  = STATE_IDLE;
    }

    @Override
    public void onOrientationChanged(int screenOrientation, OrientationDetector.Direction direction) {
        if (!mAutoRotation) {
            return;
        }

        if (direction == OrientationDetector.Direction.PORTRAIT) {
            setFullscreen(false, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); /** 竖 */

        } else if (direction == OrientationDetector.Direction.REVERSE_PORTRAIT) {
            setFullscreen(false, ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

        } else if (direction == OrientationDetector.Direction.LANDSCAPE) {
            setFullscreen(true, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE); /** 横 */

        } else if (direction == OrientationDetector.Direction.REVERSE_LANDSCAPE) {
            setFullscreen(true, ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        }
    }

    public void setFitXY(boolean fitXY) {
        mFitXY = fitXY;
    }

    public void setAutoRotation(boolean auto) {
        mAutoRotation = auto;
    }

    /**
     * Sets video path.
     *
     * @param path the path of the video.
     */
    public void setVideoPath(String path) {
        setVideoURI(Uri.parse(path));
    }

    /**
     * Sets video URI.
     *
     * @param uri the URI of the video.
     */
    public void setVideoURI(Uri uri) {
        setVideoURI(uri, null);
    }

    /**
     * Sets video URI using specific headers.
     *
     * @param uri     the URI of the video.
     * @param headers the headers for the URI request.
     *                Note that the cross domain redirection is allowed by default, but that can be
     *                changed with key/value pairs through the headers parameter with
     *                "android-allow-cross-domain-redirect" as the key and "0" or "1" as the value
     *                to disallow or allow cross domain redirection.
     */
    public void setVideoURI(Uri uri, Map<String, String> headers) {
        mUri = uri;
        mSeekWhenPrepared = 0;
        openVideo();
        requestLayout();
        invalidate();
    }


    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            mTargetState  = STATE_IDLE;
        }
    }

    public void openVideo() {
        Log.d("zzzzz","openVideo11");
        if (mUri == null || mSurfaceHolder == null) {
            // not ready for playback just yet, will try again later
            if(mUri == null){
                Log.d("zzzzz","mUri == null");
            }
            if(mSurfaceHolder == null){
                Log.d("zzzzz","mSurfaceHolder == null");
            }
            return;
        }
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        // we shouldn't clear the target state, because somebody might have
        // called start() previously
        release(false);
        try {
            mMediaPlayer = new MediaPlayer();
            Log.d("zzzzz","isAleardyInitPlayer "+isAleardyInitPlayer);
            if(!isAleardyInitPlayer){
                attachMediaController();
            }
            if (mAudioSession != 0) {
                mMediaPlayer.setAudioSessionId(mAudioSession);
            } else {
                mAudioSession = mMediaPlayer.getAudioSessionId();
            }
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mCurrentBufferPercentage = 0;
            mMediaPlayer.setDataSource(mContext, mUri);
            mMediaPlayer.setDisplay(mSurfaceHolder);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.prepareAsync();


            // we don't set the target state here either, but preserve the
            // target state that was there before.
            mCurrentState = STATE_PREPARING;
            //attachMediaController();
        } catch (IOException ex) {
            Log.d(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        }
    }

    public void setMediaController(UniversalMediaController controller) {
        if (mMediaController != null) {
            mMediaController.hide();
        }
        mMediaController = controller;

        /** 绑定个 全屏接口 */
        mMediaController.setFullScreenListener(this);

        attachMediaController();
    }

    private boolean isAleardyInitPlayer = false;
    private void attachMediaController() {
        if (mMediaPlayer != null && mMediaController != null) {
            Log.d("zzzzz","attachMediaController");
            isAleardyInitPlayer = true;
            mMediaController.setMediaPlayer(this);
            mMediaController.setEnabled(isInPlaybackState());
            mMediaController.hide();
        }else{
            if(mMediaPlayer==null){
                Log.d("zzzzz","amMediaPlayer==null");
            }
            if(mMediaController==null){
                Log.d("zzzzz","mMediaController==null");
            }

        }
    }

    MediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
            new MediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    mVideoWidth = mp.getVideoWidth();
                    mVideoHeight = mp.getVideoHeight();
                    Log.d(TAG, String.format("onVideoSizeChanged width=%d,height=%d", mVideoWidth, mVideoHeight));
                    if (mVideoWidth != 0 && mVideoHeight != 0) {
                        getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                        requestLayout();
                    }
                }
            };

    MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        public void onPrepared(MediaPlayer mp) {
            mCurrentState = STATE_PREPARED;

            mCanPause = mCanSeekBack = mCanSeekForward = true;

            mPreparedBeforeStart = true;
            if (mMediaController != null) {
                mMediaController.hideLoading();
            }

            if (mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared(mMediaPlayer);
            }
            if (mMediaController != null) {
                mMediaController.setEnabled(true);
            }
            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();

            int seekToPosition = mSeekWhenPrepared;  // mSeekWhenPrepared may be changed after seekTo() call
            if (seekToPosition != 0) {
                seekTo(seekToPosition);
            }
            if (mVideoWidth != 0 && mVideoHeight != 0) {
                //Log.i("@@@@", "video size: " + mVideoWidth +"/"+ mVideoHeight);
                getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                if (mSurfaceWidth == mVideoWidth && mSurfaceHeight == mVideoHeight) {
                    // We didn't actually change the size (it was already at the size
                    // we need), so we won't get a "surface changed" callback, so
                    // start the video here instead of in the callback.
                    if (mTargetState == STATE_PLAYING) {
                        start();
                        if (mMediaController != null) {
                            mMediaController.show();
                        }
                    } else if (!isPlaying() &&
                            (seekToPosition != 0 || getCurrentPosition() > 0)) {
                        if (mMediaController != null) {
                            // Show the media controls when we're paused into a video and make 'em stick.
                            mMediaController.show(0);
                        }
                    }
                }
            } else {
                // We don't know the video size yet, but should start anyway.
                // The video size might be reported to us later.
                if (mTargetState == STATE_PLAYING) {
                    start();
                }
            }
        }
    };

    private MediaPlayer.OnCompletionListener mCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    mCurrentState = STATE_PLAYBACK_COMPLETED;
                    mTargetState = STATE_PLAYBACK_COMPLETED;
                    if (mMediaController != null) {
                        boolean a = mMediaPlayer.isPlaying();
                        int b = mCurrentState;
                        mMediaController.showComplete();
                        //FIXME 播放完成后,视频中央会显示一个播放按钮,点击播放按钮会调用start重播,
                        // 但start后竟然又回调到这里,导致第一次点击按钮不会播放视频,需要点击第二次.
                        Log.d(TAG, String.format("a=%s,b=%d", a, b));
                    }
                    if (mOnCompletionListener != null) {
                        mOnCompletionListener.onCompletion(mMediaPlayer);
                    }
                }
            };

    private MediaPlayer.OnInfoListener mInfoListener =
            new MediaPlayer.OnInfoListener() {
                public  boolean onInfo(MediaPlayer mp, int what, int extra){
                    boolean handled = false;
                    switch (what) {
                        case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                            Log.d(TAG, "onInfo MediaPlayer.MEDIA_INFO_BUFFERING_START");
                            if (videoViewCallback != null) {
                                videoViewCallback.onBufferingStart(mMediaPlayer);
                            }
                            if (mMediaController != null) {
                                mMediaController.showLoading();
                            }
                            handled = true;
                            break;
                        case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                            Log.d(TAG, "onInfo MediaPlayer.MEDIA_INFO_BUFFERING_END");
                            if (videoViewCallback != null) {
                                videoViewCallback.onBufferingEnd(mMediaPlayer);
                            }
                            if (mMediaController != null) {
                                mMediaController.hideLoading();
                            }
                            handled = true;
                            break;
                    }
                    if (mOnInfoListener != null) {
                        return mOnInfoListener.onInfo(mp, what, extra) || handled;
                    }
                    return handled;
                }
            };

    private MediaPlayer.OnErrorListener mErrorListener =
            new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer mp, int framework_err, int impl_err) {
                    Log.d(TAG, "Error: " + framework_err + "," + impl_err);
                    mCurrentState = STATE_ERROR;
                    mTargetState = STATE_ERROR;
                    if (mMediaController != null) {
                        mMediaController.showError();
                    }

            /* If an error handler has been supplied, use it and finish. */
                    if (mOnErrorListener != null) {
                        if (mOnErrorListener.onError(mMediaPlayer, framework_err, impl_err)) {
                            return true;
                        }
                    }


                    return true;
                }
            };

    private MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
            new MediaPlayer.OnBufferingUpdateListener() {
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    mCurrentBufferPercentage = percent;
                }
            };

    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l)
    {
        mOnPreparedListener = l;
    }


    public void setOnCompletionListener(MediaPlayer.OnCompletionListener l)
    {
        mOnCompletionListener = l;
    }

    /**
     * Register a callback to be invoked when an error occurs
     * during playback or setup.  If no listener is specified,
     * or if the listener returned false, VideoView will inform
     * the user of any errors.
     *
     * @param l The callback that will be run
     */
    public void setOnErrorListener(MediaPlayer.OnErrorListener l)
    {
        mOnErrorListener = l;
    }

    /**
     * Register a callback to be invoked when an informational event
     * occurs during playback or setup.
     *
     * @param l The callback that will be run
     */
    public void setOnInfoListener(MediaPlayer.OnInfoListener l) {
        mOnInfoListener = l;
    }

    SurfaceHolder.Callback mSHCallback = new SurfaceHolder.Callback()
    {
        public void surfaceChanged(SurfaceHolder holder, int format,
                                   int w, int h)
        {
            mSurfaceWidth = w;
            mSurfaceHeight = h;
            boolean isValidState = (mTargetState == STATE_PLAYING);
            boolean hasValidSize = (mVideoWidth == w && mVideoHeight == h);
            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if (mSeekWhenPrepared != 0) {
                    seekTo((int) mSeekWhenPrepared);
                }
                start();
            }
        }

        public void surfaceCreated(SurfaceHolder holder)
        {
            Log.d("zzzzz","surfaceCreated");
            /** 下面改造为了，在切换页面回来 能在断点 继续播放 */
            mSurfaceHolder = holder;
            if(mMediaPlayer==null){
                openVideo();
                enableOrientationDetect();
            }else
                mMediaPlayer.setDisplay(mSurfaceHolder);

        }

        public void surfaceDestroyed(SurfaceHolder holder)
        {
            // after we return from this we can't use the surface any more
            mSurfaceHolder = null;
            /** 下面这两句必须注释，为了用户体验 */
            // REMOVED: if (mMediaController != null) mMediaController.hide();
            // REMOVED: release(true);
            disableOrientationDetect();
        }
    };

    private void enableOrientationDetect() {
        if (mAutoRotation && mOrientationDetector == null) {
            mOrientationDetector = new OrientationDetector(mContext);
            mOrientationDetector.setOrientationChangeListener(UniversalVideoView.this);
            mOrientationDetector.enable();
        }
    }

    private void disableOrientationDetect() {
        if (mOrientationDetector != null) {
            mOrientationDetector.disable();
        }
    }

    /*
     * release the media player in any state
     */
    public void release(boolean cleartargetstate) {
        Log.d("zzzzz","release");
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            if (cleartargetstate) {
                mTargetState  = STATE_IDLE;
            }
        }
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null) {
            toggleMediaControlsVisibility();
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        boolean isKeyCodeSupported = keyCode != KeyEvent.KEYCODE_BACK &&
                keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
                keyCode != KeyEvent.KEYCODE_VOLUME_MUTE &&
                keyCode != KeyEvent.KEYCODE_MENU &&
                keyCode != KeyEvent.KEYCODE_CALL &&
                keyCode != KeyEvent.KEYCODE_ENDCALL;
        if (isInPlaybackState() && isKeyCodeSupported && mMediaController != null) {
            if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                } else {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (!mMediaPlayer.isPlaying()) {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                }
                return true;
            } else {
                toggleMediaControlsVisibility();
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    public void toggleMediaControlsVisibility() {
        if (mMediaController.isShowing() && !mMediaController.isLocked) {
            mMediaController.hide();
        } else {
            mMediaController.show();
        }
    }


    @Override
    public void onTouchUp(View view) {
        if(OnTouchUpListener!=null){
            OnTouchUpListener.onTouchUp(view);
        }
    }

    @Override
    public void start() {
        if (!mPreparedBeforeStart && mMediaController != null) {
            mMediaController.showLoading();
        }

        if (isInPlaybackState()) {
            mMediaPlayer.start();
            mCurrentState = STATE_PLAYING;
            if (this.videoViewCallback != null) {
                this.videoViewCallback.onStart(mMediaPlayer);
            }
        }
        mTargetState = STATE_PLAYING;
    }

    public UniversalMediaController getmMediaController(){
        return mMediaController;
    }

    @Override
    public void pause() {
        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                mCurrentState = STATE_PAUSED;
                if (this.videoViewCallback != null) {
                    this.videoViewCallback.onPause(mMediaPlayer);
                }
            }
        }
        mTargetState = STATE_PAUSED;
    }

    public void suspend() {
        release(false);
    }

    /** 页面切换回来 */
    public void resume() {
        if(mTargetState == STATE_PAUSED){
            mMediaController.resumeFromOutSide();
        }
    }

    @Override
    public int getDuration() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getDuration();
        }

        return -1;
    }

    @Override
    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    @Override
    public void seekTo(int msec) {
        if (isInPlaybackState()) {
            mMediaPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    @Override
    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }

    public boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
                mCurrentState != STATE_ERROR &&
                mCurrentState != STATE_IDLE &&
                mCurrentState != STATE_PREPARING);
    }

    @Override
    public boolean canPause() {
        return mCanPause;
    }

    @Override
    public boolean canSeekBackward() {
        return mCanSeekBack;
    }

    @Override
    public boolean canSeekForward() {
        return mCanSeekForward;
    }

    @Override
    public void closePlayer() {
        release(true);
    }

    private OnFullScreenListener fullScreenListener;

    @Override
    public void setFullscreen(boolean fullscreen) {
        int screenOrientation = fullscreen ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        setFullscreen(fullscreen, screenOrientation);
    }

    @Override
    public void setFullscreen(boolean fullscreen, int screenOrientation) {
        // Activity需要设置为: android:configChanges="keyboardHidden|orientation|screenSize"
        Activity activity = (Activity) mContext;

        if (fullscreen) {
            if (mVideoViewLayoutWidth == 0 && mVideoViewLayoutHeight == 0) {
                ViewGroup.LayoutParams params = getLayoutParams();
                mVideoViewLayoutWidth = params.width;//保存全屏之前的参数
                mVideoViewLayoutHeight = params.height;
            }
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            activity.setRequestedOrientation(screenOrientation);
        } else {
            ViewGroup.LayoutParams params = getLayoutParams();
            params.width = mVideoViewLayoutWidth;//使用全屏之前的参数
            params.height = mVideoViewLayoutHeight;
            setLayoutParams(params);

            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            activity.setRequestedOrientation(screenOrientation);
        }
        mMediaController.toggleButtons(fullscreen);
        if (fullScreenListener != null) {
            fullScreenListener.onScaleChange(fullscreen);
        }
    }

    public interface OnFullScreenListener{
        void onScaleChange(boolean isFullscreen);
    }

    public void setFullScreenListener(OnFullScreenListener fullScreenListener) {
        this.fullScreenListener = fullScreenListener;
    }

    private OnTouchUpListener OnTouchUpListener;
    public interface OnTouchUpListener{
        void onTouchUp(View view);
    }

    public void setOnTouchUpListener(OnTouchUpListener OnTouchUpListener) {
        this.OnTouchUpListener = OnTouchUpListener;
    }


    public interface VideoViewCallback {

        void onPause(final MediaPlayer mediaPlayer);
        void onStart(final MediaPlayer mediaPlayer);
        void onBufferingStart(final MediaPlayer mediaPlayer);
        void onBufferingEnd(final MediaPlayer mediaPlayer);
    }

    public void setVideoViewCallback(VideoViewCallback callback) {
        this.videoViewCallback = callback;
    }


}
