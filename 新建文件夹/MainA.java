package com.hzncc.zhudao.wifi;

/**
 * ZhuDao
 * Created by 蔡雨峰 on 2017/7/31.
 */


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import com.hzncc.zhudao.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class MainA extends Activity {
    public WifiManager mWifiManager;
    public List<MScanWifi> mScanWifiList;//自定义类存放用到的wifi信息
    public List<ScanResult> mWifiList;//存放系统扫描到了wifi信息
    public List<WifiConfiguration> mWifiConfiguration;//wifi配置信息
    public Context context = null;
    public Scanner mScanner;//自定义handler类每隔10秒自动扫描wifi信息
    public View view;
    public TextView wifi_status_txt;
    public Switch wifiSwitch;
    public ListView listView;
    public LayoutInflater Inflater;
    private IntentFilter mFilter;
    private LinkWifi linkWifi;
    private LinearLayout layout;
    /**
     * 广播接收，监听网络
     */
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            handleEvent(context, intent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
        context = this;
        Inflater = LayoutInflater.from(context);
        mWifiManager = (WifiManager) context.getSystemService(Service.WIFI_SERVICE);
        mScanner = new Scanner(this);
        linkWifi = new LinkWifi(context);
        initView();
        initIntentFilter();
        registerListener();
        registerBroadcast();
    }

    public void initIntentFilter() {
        mFilter = new IntentFilter();
        mFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //用户操作activity时更新UI
        mScanner.forceScan();
    }

    /*@Override
    protected void onPause() {
        super.onPause();
        //设备激进入休眠状态时执行
        unregisterBroadcast();
    }*/
    @Override
    protected void onDestroy() {
        super.onDestroy();
        context.unregisterReceiver(mReceiver); // 注销此广播接收器
    }

    public void initView() {
        //获得要加载listview的布局
//        layout = (LinearLayout) findViewById(R.id.ListView_LinearLayout);
        //动态获得listview布局
//        listView = (ListView) Inflater.inflate(
//                R.layout.my_listview, null).findViewById(R.id.mlistview);
//        wifi_status_txt = (TextView) findViewById(R.id.switch_txt);
//        wifiSwitch = (Switch) findViewById(R.id.switch_status);
        layout.addView(listView);
    }

    public void registerListener() {
        // TODO Auto-generated method stub
        wifiSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // TODO Auto-generated method stub
                if (buttonView.isChecked()) {
                    wifi_status_txt.setText("开启");

                    if (!mWifiManager.isWifiEnabled()) { // 当前wifi不可用
                        mWifiManager.setWifiEnabled(true);
                    }
                    mWifiManager.startScan();

                } else {
                    wifi_status_txt.setText("关闭");
                    if (mWifiManager.isWifiEnabled()) {
                        mWifiManager.setWifiEnabled(false);
                    }

                }
            }
        });
        //给item添加监听事件
        listView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // TODO Auto-generated method stub
                // 本机已经配置过的wifi
                final ScanResult wifi = mScanWifiList.get(position).getScanResult();
                final WifiConfiguration wifiConfig = linkWifi.IsExsits(wifi.SSID);
                if (wifiConfig != null) {
                    final int netID = wifiConfig.networkId;
                    String actionStr;
                    // 如果目前连接了此网络
                    if (mWifiManager.getConnectionInfo().getNetworkId() == netID) {
                        actionStr = "断开";
                    } else {
                        actionStr = "连接";
                    }
                    android.app.AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("提示");
                    builder.setMessage("请选择你要进行的操作？");
                    builder.setPositiveButton(actionStr,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int whichButton) {

                                    if (mWifiManager.getConnectionInfo()
                                            .getNetworkId() == netID) {
                                        mWifiManager.disconnect();
                                    } else {

                                        linkWifi.setMaxPriority(wifiConfig);
                                        linkWifi.ConnectToNetID(wifiConfig.networkId);
                                    }

                                }
                            });
                    builder.setNeutralButton("忘记",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int whichButton) {
                                    mWifiManager.removeNetwork(netID);
                                    return;
                                }
                            });
                    builder.setNegativeButton("取消",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int whichButton) {
                                    return;
                                }
                            });
                    builder.show();

                    return;

                }
                if (mScanWifiList.get(position).isLock()) {
                    // 有密码，提示输入密码进行连接

                    // final String encryption = capabilities;

                    LayoutInflater factory = LayoutInflater.from(context);
                    final View inputPwdView = factory.inflate(R.layout.dialog_inputpwd,
                            null);
                    new AlertDialog.Builder(context)
                            .setTitle("请输入该无线的连接密码")
                            .setMessage("无线SSID：" + wifi.SSID)
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .setView(inputPwdView)
                            .setPositiveButton("确定",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog,
                                                            int which) {
                                            EditText pwd = (EditText) inputPwdView
                                                    .findViewById(R.id.etPassWord);
                                            String wifipwd = pwd.getText().toString();

                                            // 此处加入连接wifi代码
                                            int netID = linkWifi.CreateWifiInfo2(
                                                    wifi, wifipwd);

                                            linkWifi.ConnectToNetID(netID);
                                        }
                                    })
                            .setNegativeButton("取消",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog,
                                                            int which) {
                                        }
                                    }).setCancelable(false).show();

                } else {
                    // 无密码
                    new AlertDialog.Builder(context)
                            .setTitle("提示")
                            .setMessage("你选择的wifi无密码，可能不安全，确定继续连接？")
                            .setPositiveButton("确定",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog,
                                                            int whichButton) {

                                            // 此处加入连接wifi代码
                                            int netID = linkWifi.CreateWifiInfo2(
                                                    wifi, "");

                                            linkWifi.ConnectToNetID(netID);
                                        }
                                    })
                            .setNegativeButton("取消",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog,
                                                            int whichButton) {
                                            return;
                                        }
                                    }).show();

                }

            }

        });
    }

    /**
     * 获取到自定义的ScanResult
     **/
    public void initScanWifilist() {
        MScanWifi mScanwifi;
        mScanWifiList = new ArrayList<MScanWifi>();
        for (int i = 0; i < mWifiList.size(); i++) {
            int level = WifiManager.calculateSignalLevel(mWifiList.get(i).level, 4);
            String mwifiName = mWifiList.get(i).SSID;
            boolean boolean1 = false;
            if (mWifiList.get(i).capabilities.contains("WEP") || mWifiList.get(i).capabilities.contains("PSK") ||
                    mWifiList.get(i).capabilities.contains("EAP")) {
                boolean1 = true;
            } else {
                boolean1 = false;
            }
            mScanwifi = new MScanWifi(mWifiList.get(i), mwifiName, level, boolean1);
            if (linkWifi.IsExsits(mwifiName) != null) {
                mScanwifi.setExsit(true);
            } else {
                mScanwifi.setExsit(false);
            }
            mScanWifiList.add(mScanwifi);
        }
    }

    public void registerBroadcast() {
        context.registerReceiver(mReceiver, mFilter);
    }

    public void unregisterBroadcast() {
        context.unregisterReceiver(mReceiver);
    }

    public void handleEvent(Context context, Intent intent) {
        // TODO Auto-generated method stub
        final String action = intent.getAction();
        // wifi状态发生改变。
        if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            /*int wifiState = intent.getIntExtra(
                    WifiManager.EXTRA_WIFI_STATE, 0);*/
            int wifiState = intent.getIntExtra(
                    WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
            updateWifiStateChanged(wifiState);
        } else if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
            updateWifiList();

        }
    }

    /**
     * 更新WiFi列表UI
     **/
    public void updateWifiList() {

        final int wifiState = mWifiManager.getWifiState();
        //获取WiFi列表并显示
        switch (wifiState) {
            case WifiManager.WIFI_STATE_ENABLED:
                //wifi处于开启状态
                initWifiData();
//                listView.setAdapter(new CommonAdapter<MScanWifi>(context,
//                        mScanWifiList, R.layout.listitems) {
//                    @Override
//                    public void convert(RecyclerView.ViewHolder helper, MScanWifi item) {
//                        // TODO Auto-generated method stub
//                        helper.setText(R.id.tv1, item.getWifiName());
//                        //Log.i("1111111", item.getWifiName()+"是否开放"+item.getIsLock());
//                        if (item.getIsLock()) {
//                            helper.setImageResource(R.id.img_wifi_level, R.drawable.wifi_signal_lock, item.getLevel());
//                        } else {
//                            helper.setImageResource(R.id.img_wifi_level, R.drawable.wifi_signal_open, item.getLevel());
//                        }
//                        if (item.getIsExsit()) {
//                            TextView view = helper.getView(R.id.tv2);
//                            view.setText("已保存");
//                            view.setVisibility(View.VISIBLE);
//                        }
//                    }
//                });
                break;
            case WifiManager.WIFI_STATE_ENABLING:
                listView.setAdapter(null);
                break;//如果WiFi处于正在打开的状态，则清除列表
        }
    }

    /**
     * 初始化wifi信息
     * mWifiList和mWifiConfiguration
     **/
    public void initWifiData() {
        // TODO Auto-generated method stub
        mWifiList = mWifiManager.getScanResults();
        mWifiConfiguration = mWifiManager.getConfiguredNetworks();
        initScanWifilist();
    }

    private void updateWifiStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLING://正在打开WiFi
                wifiSwitch.setEnabled(false);
                Log.i("aaaaaa", "正在打开WiFi");
                break;
            case WifiManager.WIFI_STATE_ENABLED://WiFi已经打开
                //setSwitchChecked(true);
                wifiSwitch.setEnabled(true);
                wifiSwitch.setChecked(true);
                layout.removeAllViews();
                layout.addView(listView);
                mScanner.resume();
                Log.i("aaaaaa", "WiFi已经打开");
                break;
            case WifiManager.WIFI_STATE_DISABLING://正在关闭WiFi
                wifiSwitch.setEnabled(false);
                Log.i("aaaaaa", "正在关闭WiFi");
                break;
            case WifiManager.WIFI_STATE_DISABLED://WiFi已经关闭
                //setSwitchChecked(false);
                wifiSwitch.setEnabled(true);
                wifiSwitch.setChecked(false);
                layout.removeAllViews();
                Log.i("aaaaaa", "WiFi已经关闭  ");
                break;
            default:
                //setSwitchChecked(false);
                wifiSwitch.setEnabled(true);
                break;
        }
        mScanner.pause();//移除message通知
    }

    /**
     * 这个类使用startScan()方法开始扫描wifi
     * WiFi扫描结束时系统会发送该广播，
     * 用户可以监听该广播通过调用WifiManager
     * 的getScanResults方法来获取到扫描结果
     *
     * @author zwh
     */
    public static class Scanner extends Handler {
        private final WeakReference<MainA> mActivity;
        private int mRetry = 0;

        public Scanner(MainA activity) {
            mActivity = new WeakReference<MainA>(activity);
        }

        void resume() {
            if (!hasMessages(0)) {
                sendEmptyMessage(0);
            }
        }

        void forceScan() {
            removeMessages(0);
            sendEmptyMessage(0);
        }

        void pause() {
            mRetry = 0;
            removeMessages(0);
        }

        @Override
        public void handleMessage(Message message) {
            if (mActivity.get().mWifiManager.startScan()) {
                mRetry = 0;
            } else if (++mRetry >= 3) {
                mRetry = 0;
                return;
            }
            sendEmptyMessageDelayed(0, 10 * 1000);//10s后再次发送message
        }
    }

}
