package com.universalvideoviewsample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.universalvideoview.VideoViewHelper;

/**
 * Created by Administrator on 2016/6/6.
 *
 */

public class SecondMain extends AppCompatActivity{

    private static final String TAG = "zzzzz";
    private static final String VIDEO_URL = "http://flv.bn.netease.com/videolib3/1605/22/auDfZ8781/HD/auDfZ8781-mobile.mp4";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.second);
        Log.d("zzzzz","create");

        new VideoViewHelper
                (
                        this,
                        (LinearLayout) findViewById(R.id.container),
                        (RelativeLayout) findViewById(R.id.full),
                        VIDEO_URL
                )
                .withSecondFullWay(true)
                .init();

    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause ");

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }



}
