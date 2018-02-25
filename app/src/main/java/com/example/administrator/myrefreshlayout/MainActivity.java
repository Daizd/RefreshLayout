package com.example.administrator.myrefreshlayout;

import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private ListView listView;
    private MyRefreshLayout myRefreshLayout;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listView = (ListView)findViewById(R.id.listview);
        myRefreshLayout = (MyRefreshLayout)findViewById(R.id.refresh);

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                // TODO Auto-generated method stub
                return 20;//数目
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LayoutInflater inflater = getLayoutInflater();
                View view;

                if (convertView==null) {
                    //因为getView()返回的对象，adapter会自动赋给ListView
                    view = inflater.inflate(R.layout.view_post_item, null);
                }else{
                    view=convertView;
                }
                TextView tv = (TextView) view.findViewById(R.id.textView);//找到Textviewname
                return view;
            }
            @Override
            public long getItemId(int position) {//取在列表中与指定索引对应的行id
                return 0;
            }
            @Override
            public Object getItem(int position) {//获取数据集中与指定索引对应的数据项
                return null;
            }
        };

        listView.setAdapter(adapter);
        myRefreshLayout.setOnRefreshLayoutListener(new MyRefreshLayout.RefreshLayoutListener() {
            @Override
            public void onRefresh() {
                try {
                    Thread.sleep(6000);
                    Message msg = new Message();
                    msg.what = 0;
                    handler.sendMessage(msg);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, 0);
    }

    private Handler handler = new Handler() {

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    Toast.makeText(MainActivity.this, "test", Toast.LENGTH_SHORT).show();
                    myRefreshLayout.finishRefreshing();
                default:
                    break;
            }
        }

    };
}
