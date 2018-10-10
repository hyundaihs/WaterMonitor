package com.hzncc.zhudao.wifi;

import android.net.wifi.ScanResult;

/**
 * ZhuDao
 * Created by 蔡雨峰 on 2017/7/31.
 */

public class MScanWifi {
    //保存一个引用，在wifi连接时候用到
    private ScanResult scanResult;
    private int level;//wifi信号强度
    private String WifiName;
    private boolean isLock;//是否是锁定
    private boolean isExsit;//是否是保存过的wifi

    public MScanWifi() {
        super();
    }

    public MScanWifi(ScanResult scanResult,String WifiName,int level,Boolean isLock){
        this.WifiName=WifiName;
        this.level=level;
        this.isLock=isLock;
        this.scanResult=scanResult;
    }

    public MScanWifi(ScanResult scanResult, int level, String wifiName, boolean isLock, boolean isExsit) {
        this.scanResult = scanResult;
        this.level = level;
        WifiName = wifiName;
        this.isLock = isLock;
        this.isExsit = isExsit;
    }

    public ScanResult getScanResult() {
        return scanResult;
    }

    public void setScanResult(ScanResult scanResult) {
        this.scanResult = scanResult;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getWifiName() {
        return WifiName;
    }

    public void setWifiName(String wifiName) {
        WifiName = wifiName;
    }

    public boolean isLock() {
        return isLock;
    }

    public void setLock(boolean lock) {
        isLock = lock;
    }

    public boolean isExsit() {
        return isExsit;
    }

    public void setExsit(boolean exsit) {
        isExsit = exsit;
    }

    @Override
    public String toString() {
        return "MScanWifi{" +
                "scanResult=" + scanResult +
                ", level=" + level +
                ", WifiName='" + WifiName + '\'' +
                ", isLock=" + isLock +
                ", isExsit=" + isExsit +
                '}';
    }
}
