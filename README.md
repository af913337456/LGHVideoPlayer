## 基于 android 自带的 VideoView 改装的 视频播放器

> 功能列表 (Function list)

1. 自定义底部工具栏
2. 添加断网广播事件接收的处理
3. 添加锁屏操作
4. 添加全屏功能

> 和 IJKPlayer 的对比

1,比 IJK 的体积小，功能差不多

2,IJK 不能播放缓冲区的，而这个可以，具体表现在，无论是在断网还是不断的情况下拖拉进度到已缓冲的区域，都会自动重新下载，且在断网的情况下直接不能播放。

> 用法( How to use)

```java

new VideoViewHelper
    (
            this,
            (LinearLayout) findViewById(R.id.container),
            (RelativeLayout) findViewById(R.id.full),
            "http://flv.bn.netease.com/videolib3/1605/22/auDfZ8781/HD/auDfZ8781-mobile.mp4"
    )
    .withSecondFullWay(true)
    .init();
    
```


