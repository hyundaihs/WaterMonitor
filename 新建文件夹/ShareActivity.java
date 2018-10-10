package com.hzncc.zhudao.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.hzncc.zhudao.R;
import com.hzncc.zhudao.wifi.LinkWifi;
import com.hzncc.zhudao.wifi.MScanWifi;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * ZhuDao
 * Created by 蔡雨峰 on 2017/7/25.
 */

public class ShareActivity extends Activity {
    public WifiManager mWifiManager;
    public Scanner mScanner;//自定义handler类每隔10秒自动扫描wifi信息
    public List<MScanWifi> mScanWifiList = new ArrayList<>();//自定义类存放用到的wifi信息
    public List<ScanResult> mWifiList = new ArrayList<>();//存放系统扫描到了wifi信息
    public List<WifiConfiguration> mWifiConfiguration;//wifi配置信息
    private ListView wifiList;
    private List<MScanWifi> list;
    private MyAdapter adapter;
    private LinkWifi linkWifi;
    private IntentFilter mFilter;
    /**
     * 广播接收，监听网络
     */
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            handleEvent(context, intent);
        }
    };

    private static String intToIp(int i) {
        return (i & 0xFF) + "." +
                ((i >> 8) & 0xFF) + "." +
                ((i >> 16) & 0xFF) + "." +
                (i >> 24 & 0xFF);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Service.WIFI_SERVICE);
        mScanner = new Scanner(this);
        linkWifi = new LinkWifi(this);
        init();
        initIntentFilter();
        registerListener();
        open();
    }

    private void open() {
        if (!mWifiManager.isWifiEnabled()) { // 当前wifi不可用
            mWifiManager.setWifiEnabled(true);
        }
        mWifiManager.startScan();
    }

    public void initIntentFilter() {
        mFilter = new IntentFilter();
        mFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
//        mFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
//        mFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerBroadcast();
        //用户操作activity时更新UI
        mScanner.forceScan();
    }

    @Override
    protected void onDestroy() {
        mScanner.pause();
        unregisterBroadcast(); // 注销此广播接收器
        super.onDestroy();
    }

    private void init() {
        wifiList = (ListView) findViewById(R.id.wifi_list);
        findViewById(R.id.activity_share_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        list = new ArrayList<>();
        initWifiData();
        list.addAll(mScanWifiList);
        adapter = new MyAdapter(this, list);
        wifiList.setAdapter(adapter);

    }

    public void registerListener() {
        //给item添加监听事件
        wifiList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
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
                    android.app.AlertDialog.Builder builder = new AlertDialog.Builder(ShareActivity.this);
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

                    LayoutInflater factory = LayoutInflater.from(ShareActivity.this);
                    final View inputPwdView = factory.inflate(R.layout.dialog_inputpwd,
                            null);
                    new AlertDialog.Builder(ShareActivity.this)
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
                    new AlertDialog.Builder(ShareActivity.this)
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

    public void registerBroadcast() {
        registerReceiver(mReceiver, mFilter);
    }

    public void unregisterBroadcast() {
        unregisterReceiver(mReceiver);
    }

    public void handleEvent(Context context, Intent intent) {
        final String action = intent.getAction();
        switch (action) {
            // wifi状态发生改变。
            case WifiManager.WIFI_STATE_CHANGED_ACTION:
                Log.d("Share", "WIFI_STATE");
                 /*int wifiState = intent.getIntExtra(
                    WifiManager.EXTRA_WIFI_STATE, 0);*/
                int wifiState = intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                updateWifiStateChanged(wifiState);
                break;
            case WifiManager.SCAN_RESULTS_AVAILABLE_ACTION:
                updateWifiList();
                break;
            case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                NetworkInfo.DetailedState state = info.getDetailedState();
                adapter.notifyDataSetChanged();
                break;
            case WifiManager.SUPPLICANT_STATE_CHANGED_ACTION:
//                SupplicantState supplicantState = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
//                adapter.notifyDataSetChanged();
                break;
            case WifiManager.RSSI_CHANGED_ACTION:
//                adapter.notifyDataSetChanged();
                break;
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
                list.clear();
                list.addAll(mScanWifiList);
                adapter.notifyDataSetChanged();
                break;
            case WifiManager.WIFI_STATE_ENABLING:
                list.clear();
                adapter.notifyDataSetChanged();
                break;//如果WiFi处于正在打开的状态，则清除列表
        }
    }

    /**
     * 初始化wifi信息
     * mWifiList和mWifiConfiguration
     **/
    public void initWifiData() {
        List<ScanResult> tmp = mWifiManager.getScanResults();
        if (null == tmp) {
            Log.e("ShareActivity", "wifi 列表获取为空");
            return;
        }
        mWifiList.clear();
        mWifiList.addAll(tmp);
        mWifiConfiguration = mWifiManager.getConfiguredNetworks();
        initScanWifilist();
    }

    private void updateWifiStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLING://正在打开WiFi
//                wifiSwitch.setEnabled(false);
                Log.i("ShareActivity", "正在打开WiFi");
                break;
            case WifiManager.WIFI_STATE_ENABLED://WiFi已经打开
                //setSwitchChecked(true);
//                wifiSwitch.setEnabled(true);
//                wifiSwitch.setChecked(true);
//                layout.removeAllViews();
//                layout.addView(listView);
                mScanner.resume();
                Log.i("ShareActivity", "WiFi已经打开");
                break;
            case WifiManager.WIFI_STATE_DISABLING://正在关闭WiFi
//                wifiSwitch.setEnabled(false);
                Log.i("ShareActivity", "正在关闭WiFi");
                break;
            case WifiManager.WIFI_STATE_DISABLED://WiFi已经关闭
                //setSwitchChecked(false);
//                wifiSwitch.setEnabled(true);
//                wifiSwitch.setChecked(false);
//                layout.removeAllViews();
                Log.i("ShareActivity", "WiFi已经关闭  ");
                break;
            default:
                //setSwitchChecked(false);
//                wifiSwitch.setEnabled(true);
                break;
        }
        mScanner.pause();//移除message通知
    }

    /**
     * 获取到自定义的ScanResult
     **/
    public void initScanWifilist() {
        MScanWifi mScanwifi;
        mScanWifiList.clear();
        for (int i = 0; i < mWifiList.size(); i++) {
            int level = WifiManager.calculateSignalLevel(mWifiList.get(i).level, 4);
            String mwifiName = mWifiList.get(i).SSID;
            boolean boolean1;
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

            WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getSSID() != null
                    && wifiInfo.getSSID().equals(mwifiName)) {
                mScanWifiList.add(0, mScanwifi);
            } else {
                mScanWifiList.add(mScanwifi);
            }
        }
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
        private final WeakReference<ShareActivity> mActivity;

        public Scanner(ShareActivity activity) {
            mActivity = new WeakReference<ShareActivity>(activity);
        }

        void resume() {
            if (!hasMessages(0)) {
                sendEmptyMessage(0);
            }
        }

        void sendElseDelayed(int what) {
            sendEmptyMessageDelayed(what, 1000);
        }

        void forceScan() {
            removeMessages(0);
            sendEmptyMessage(0);
        }

        void pause() {
            removeMessages(0);
        }

        @Override
        public void handleMessage(Message message) {
            if (message.what == 0) {
                mActivity.get().mWifiManager.startScan();
                sendEmptyMessageDelayed(0, 2000);//10s后再次发送message
            } else {
                mActivity.get().adapter.notifyDataSetChanged();
            }
        }
    }

    public class MyAdapter extends BaseAdapter {

        LayoutInflater inflater;
        List<MScanWifi> list;

        public MyAdapter(Context context, List<MScanWifi> list) {
            this.inflater = LayoutInflater.from(context);
            this.list = list;
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public MScanWifi getItem(int position) {
            return list.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (null == convertView) {
                viewHolder = new ViewHolder();
                convertView = inflater.inflate(R.layout.layout_wifi_item, null);
                viewHolder.wifiName = (TextView) convertView.findViewById(R.id.wifi_name);
                viewHolder.wifiState = (TextView) convertView.findViewById(R.id.wifi_state);
                viewHolder.wifiSigle = (ImageView) convertView.findViewById(R.id.wifi_sigle);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            MScanWifi scanResult = list.get(position);
            WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
            if (wifiInfo != null && wifiInfo.getSSID() != null
                    && wifiInfo.getSSID().equals(scanResult.getScanResult().SSID)) {
                viewHolder.wifiState.setText("已连接，本机IP：" + intToIp(wifiInfo.getIpAddress()));
            } else {
                viewHolder.wifiState.setText("");
            }
            viewHolder.wifiName.setText(scanResult.getScanResult().SSID);
            int sigleId;
            if (Math.abs(scanResult.getScanResult().level) > 100) {
                sigleId = scanResult.isLock() ? R.mipmap.ic_wifi_lock_signal_4
                        : R.mipmap.ic_wifi_signal_4;
            } else if (Math.abs(scanResult.getScanResult().level) > 80) {
                sigleId = scanResult.isLock() ? R.mipmap.ic_wifi_lock_signal_4
                        : R.mipmap.ic_wifi_signal_4;
            } else if (Math.abs(scanResult.getScanResult().level) > 60) {
                sigleId = scanResult.isLock() ? R.mipmap.ic_wifi_lock_signal_3
                        : R.mipmap.ic_wifi_signal_3;
            } else if (Math.abs(scanResult.getScanResult().level) > 40) {
                sigleId = scanResult.isLock() ? R.mipmap.ic_wifi_lock_signal_2
                        : R.mipmap.ic_wifi_signal_2;
            } else if (Math.abs(scanResult.getScanResult().level) > 20) {
                sigleId = scanResult.isLock() ? R.mipmap.ic_wifi_lock_signal_1
                        : R.mipmap.ic_wifi_signal_1;
            } else {
                sigleId = scanResult.isLock() ? R.mipmap.ic_wifi_lock_signal_1
                        : R.mipmap.ic_wifi_signal_1;
            }
            if (Math.abs(scanResult.getScanResult().level) <= 0) {
                viewHolder.wifiState.setText("不在范围内");
                sigleId = scanResult.isLock() ? R.mipmap.ic_wifi_lock_signal_1
                        : R.mipmap.ic_wifi_signal_1;
            }
            viewHolder.wifiSigle.setImageResource(sigleId);
            return convertView;
        }

        private class ViewHolder {
            TextView wifiName, wifiState;
            ImageView wifiSigle;
        }

    }
}