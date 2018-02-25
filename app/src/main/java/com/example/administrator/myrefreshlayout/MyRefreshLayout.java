package com.example.administrator.myrefreshlayout;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.zip.Inflater;

/**
 * Created by Administrator on 2018/2/13.
 */


public class MyRefreshLayout extends LinearLayout implements View.OnTouchListener {
    private View header;
    private ImageView iv_arrow;
    private ProgressBar progressBar;
    private TextView tv_description;
    private TextView tv_updated_time;
    private ListView listView;

    private SharedPreferences preference;
    private MyHandler handler;

    private long lastUpdateTime;
    private int hideHeaderHeight;
    private MarginLayoutParams headerLayoutParams;

    private RefreshLayoutListener mListener;
    
    private static final String SP_NAME = "refresh_time";
    /*
        ID用于区别不同refresh layout 所保存的时间
     */
    private int spID = 0;

    /**
     * 用于判断上次的更新时间
     */
    private static final long ONE_MINUTE = 60 * 1000;
    private static final long ONE_HOUR = 60 * ONE_MINUTE;
    private static final long ONE_DAY = 24 * ONE_HOUR;
    private static final long ONE_MONTH = 30 * ONE_DAY;
    private static final long ONE_YEAR = 12 * ONE_MONTH;

    private static final int MSG_SCROLL_BACK = 0;
    private static final int MSG_SCROLL_BACK_FINISHED = 1;

    //未达到刷新要求的下拉
    private static final int STATUS_DROPING = 0;
    //释放后刷新
    private static final int STATUS_RELEASE_TO_REFRESH = 1;
    //正在刷新
    private static final int STATUS_REFRESHING = 2;
    //刷新完成或未刷新状态
    private static final int STATUS_REFRESH_FINISHED = 3;
    //正在回滚
    private static final int STATUS_SCROLLING_BACK = 4;
    //下拉头部回滚的速度
    private static final int SCROLL_SPEED = -20;

    //拖动阻力
    private static final int DROP_RESISTANCE = 2;


    private int currentStatus = STATUS_REFRESH_FINISHED;
    private int lastStatus = currentStatus;
    private boolean ifFirstOnLayout = true;
    private boolean ifInterceptTouchEvent = false;
    private boolean ifHideHeader = false;



    public MyRefreshLayout(Context context, AttributeSet attrs){
        super(context, attrs);
        initView(context);
        initData(context);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed && ifFirstOnLayout) {
            hideHeaderHeight = -header.getHeight();
            headerLayoutParams = (MarginLayoutParams) header.getLayoutParams();
            headerLayoutParams.topMargin = hideHeaderHeight;
            listView = (ListView) getChildAt(1);
            listView.setOnTouchListener(this);
            ifFirstOnLayout = false;
        }
    }

    private void initView(Context context){
        LayoutInflater  inflater = LayoutInflater.from(context);
        header = inflater.inflate(R.layout.view_refresh_layout, null, true);
        iv_arrow = (ImageView)header.findViewById(R.id.iv_arrow);
        progressBar = (ProgressBar)header.findViewById(R.id.progress_bar);
        tv_description = (TextView)header.findViewById(R.id.tv_description);
        tv_updated_time = (TextView)header.findViewById(R.id.tv_updated_time);
        setOrientation(VERTICAL);
        addView(header, 0);
    }

    private void initData(Context context){
        preference = PreferenceManager.getDefaultSharedPreferences(context);
        setUpdateTime();
        handler = new MyHandler(this);
    }


    private boolean ifFirst = true;
    private float yStart = 0;

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!isAbleToPull())
            return false;

        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                break;
            case MotionEvent.ACTION_MOVE:
                float y = event.getRawY();
                if (ifFirst){
                    ifFirst = false;
                    yStart = y;
                }
                int moveDistant = (int)(y - yStart);
                System.out.println(moveDistant);
                //如果header已经隐藏
                if (headerLayoutParams.topMargin <= hideHeaderHeight && moveDistant < 0 ){
                    ifFirst = true;
                    //有可能滑动距离过大，需要恢复
                    headerLayoutParams.topMargin = hideHeaderHeight;
                    header.setLayoutParams(headerLayoutParams);
                    return false;
                }
                System.out.println(moveDistant);
                headerLayoutParams.topMargin += (moveDistant);
                header.setLayoutParams(headerLayoutParams);
                System.out.println("margin:"+ headerLayoutParams.topMargin);
                changeLayoutStatus(headerLayoutParams.topMargin);
                yStart = y;
                break;
            default:
                ifFirst = true;
                if (headerLayoutParams.topMargin >= 0){
                    ifHideHeader = false;
                    scrollBackTask();
                    if (currentStatus != STATUS_REFRESHING)
                        new RefreshTask().execute();
                    break;
                }
                if (currentStatus != STATUS_REFRESHING){
                    scrollBackTask();
                    ifHideHeader = true;
                }

                break;
        }

        updateHeaderView();
        if (currentStatus == STATUS_DROPING
                || currentStatus == STATUS_RELEASE_TO_REFRESH) {
            listView.setPressed(false);
            listView.setFocusable(false);
            listView.setFocusableInTouchMode(false);
        }
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ifInterceptTouchEvent)
            return true;
        return super.onInterceptTouchEvent(ev);
    }

    private void updateHeaderView() {
        if (lastStatus == currentStatus)
            return;
        lastStatus = currentStatus;
        switch (currentStatus){
            case STATUS_DROPING:
                tv_description.setText(getResources().getString(R.string.pull_to_refresh));
                iv_arrow.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                rotateArrow();
                break;
            case STATUS_RELEASE_TO_REFRESH:
                tv_description.setText(getResources().getString(R.string.release_to_refresh));
                iv_arrow.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                rotateArrow();
                break;
            case STATUS_REFRESHING:
                tv_description.setText(getResources().getString(R.string.refreshing));
                progressBar.setVisibility(View.VISIBLE);
                iv_arrow.clearAnimation();
                iv_arrow.setVisibility(View.GONE);
                break;
        }
        setUpdateTime();
    }

    private void rotateArrow() {
        float pivotX = iv_arrow.getWidth() / 2f;
        float pivotY = iv_arrow.getHeight() / 2f;
        float fromDegrees = 0f;
        float toDegrees = 0f;
        if (currentStatus == STATUS_DROPING) {
            fromDegrees = 180f;
            toDegrees = 360f;
        } else if (currentStatus == STATUS_RELEASE_TO_REFRESH) {
            fromDegrees = 0f;
            toDegrees = 180f;
        }
        RotateAnimation animation = new RotateAnimation(fromDegrees, toDegrees, pivotX, pivotY);
        animation.setDuration(100);
        animation.setFillAfter(true);
        iv_arrow.startAnimation(animation);
    }

    class RefreshTask extends AsyncTask<Void, Integer, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            if (mListener != null && currentStatus != STATUS_REFRESHING) {
                currentStatus = STATUS_REFRESHING;
                mListener.onRefresh();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            currentStatus = STATUS_REFRESH_FINISHED;
        }
    }

    private void changeLayoutStatus(int topMargin){
        //正在刷新的话不能改变状态
        if (currentStatus == STATUS_REFRESHING)
            return;
        if (headerLayoutParams.topMargin > 0){
            currentStatus = STATUS_RELEASE_TO_REFRESH;
        } else {
            currentStatus = STATUS_DROPING;
        }
    }

    private boolean isAbleToPull(){
//        if (listView.getChildCount() <= 0){
//            return true;
//        }
        View firstChild = listView.getChildAt(0);
        // 如果ListView中没有元素，也应该允许下拉刷新
        if (firstChild == null)
            return true;
        int firstVisiblePos = listView.getFirstVisiblePosition();
        if (firstVisiblePos == 0 && firstChild.getTop() == 0) {
            // 如果首个元素的上边缘，距离父布局值为0，就说明ListView滚动到了最顶部，此时应该允许下拉刷新
            return true;
        }
        return false;
    }


    private void setUpdateTime(){
        lastUpdateTime = preference.getLong(SP_NAME + spID, -1);
        long currentTime = System.currentTimeMillis();
        long timePassed;
        long timeIntoFormat;
        String showText;
        if (lastUpdateTime == -1){
            showText = getResources().getString(R.string.not_updated_yet);
            return;
        }
        timePassed = currentTime - lastUpdateTime;
        if (timePassed < 0) {
            showText = getResources().getString(R.string.time_error);
        } else if (timePassed < ONE_MINUTE) {
            showText = getResources().getString(R.string.updated_just_now);
        } else if (timePassed < ONE_HOUR) {
            timeIntoFormat = timePassed / ONE_MINUTE;
            String value = timeIntoFormat + "分钟";
            showText = String.format(getResources().getString(R.string.updated_at), value);
        } else if (timePassed < ONE_DAY) {
            timeIntoFormat = timePassed / ONE_HOUR;
            String value = timeIntoFormat + "小时";
            showText = String.format(getResources().getString(R.string.updated_at), value);
        } else if (timePassed < ONE_MONTH) {
            timeIntoFormat = timePassed / ONE_DAY;
            String value = timeIntoFormat + "天";
            showText = String.format(getResources().getString(R.string.updated_at), value);
        } else if (timePassed < ONE_YEAR) {
            timeIntoFormat = timePassed / ONE_MONTH;
            String value = timeIntoFormat + "个月";
            showText = String.format(getResources().getString(R.string.updated_at), value);
        } else {
            timeIntoFormat = timePassed / ONE_YEAR;
            String value = timeIntoFormat + "年";
            showText = String.format(getResources().getString(R.string.updated_at), value);
        }
        tv_updated_time.setText(showText);
    }
    
    public interface RefreshLayoutListener {
        void onRefresh();
    }

    public void setOnRefreshLayoutListener(RefreshLayoutListener listener, int id){
        mListener = listener;
        spID = id;
    }

    public void finishRefreshing() {
        ifHideHeader = true;
        currentStatus = STATUS_REFRESH_FINISHED;
        preference.edit().putLong(SP_NAME + spID, System.currentTimeMillis()).commit();
        scrollBackTask();
    }

    private void scrollBackTask(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                ifInterceptTouchEvent = true;
                int targetHeaderHeight = 0;
                if (ifHideHeader){
                    targetHeaderHeight = hideHeaderHeight;
                }
                int topMargin = headerLayoutParams.topMargin;
                Bundle bundle = new Bundle();
                while (topMargin > targetHeaderHeight) {
                    topMargin = topMargin + SCROLL_SPEED;
                    bundle.putInt("topMargin", topMargin);
                    Message msg = new Message();
                    msg.setData(bundle);
                    msg.what = MSG_SCROLL_BACK;
                    handler.sendMessage(msg);
                    try {
                        Thread.sleep(10);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
                topMargin = targetHeaderHeight;
                ifInterceptTouchEvent = false;
                bundle.putInt("topMargin", topMargin);
                Message msg = new Message();
                msg.setData(bundle);
                msg.what = MSG_SCROLL_BACK_FINISHED;
                handler.sendMessage(msg);
            }
        }).start();
    }

    private static class MyHandler extends android.os.Handler {
        private final WeakReference<MyRefreshLayout> mLayout;

        public MyHandler(MyRefreshLayout layout) {
            mLayout = new WeakReference<MyRefreshLayout>(layout);
        }

        @Override
        public void handleMessage(Message msg) {
            if (mLayout.get() == null) {
                return;
            }
            mLayout.get().handleRefresh(msg);
        }
    }

    private void handleRefresh(Message msg){
        switch (msg.what){
            case MSG_SCROLL_BACK:
                Bundle bundle = msg.getData();
                headerLayoutParams.topMargin = bundle.getInt("topMargin");
                header.setLayoutParams(headerLayoutParams);
                System.out.println("margin:"+ headerLayoutParams.topMargin);
                break;
            case MSG_SCROLL_BACK_FINISHED:
                bundle = msg.getData();
                headerLayoutParams.topMargin = bundle.getInt("topMargin");
                header.setLayoutParams(headerLayoutParams);
                System.out.println("margin:"+ headerLayoutParams.topMargin);
                updateHeaderView();
                break;

        }
    }
}
