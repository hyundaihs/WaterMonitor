package com.android.kevin.shuizu.ui

import android.content.*
import android.net.NetworkInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WIFI_STATE_ENABLED
import android.os.Bundle
import android.view.View
import com.android.kevin.shuizu.R
import com.android.kevin.shuizu.entities.BDSB
import com.android.kevin.shuizu.entities.DeviceType
import com.android.kevin.shuizu.entities.getInterface
import com.android.kevin.shuizu.utils.CharUtil
import com.android.kevin.shuizu.utils.SocketUtil
import com.android.kevin.shuizu.utils.SocketUtil.OnMsgComing
import com.android.shuizu.myutillibrary.D
import com.android.shuizu.myutillibrary.E
import com.android.shuizu.myutillibrary.MyBaseActivity
import com.android.shuizu.myutillibrary.initActionBar
import com.android.shuizu.myutillibrary.request.MySimpleRequest
import com.android.shuizu.myutillibrary.utils.LoginErrDialog
import com.android.shuizu.myutillibrary.utils.charToHexStr
import com.android.shuizu.myutillibrary.utils.makeChecksum
import com.bigkoo.pickerview.builder.OptionsPickerBuilder
import com.bigkoo.pickerview.listener.OnOptionsSelectListener
import kotlinx.android.synthetic.main.activity_bind_device.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread


/**
 * ChaYin
 * Created by ${蔡雨峰} on 2018/8/25/025.
 */
class BindDeviceActivity : MyBaseActivity() {

    companion object {
        val WIFI_SSID = "JIASONG"
    }

    enum class InfoType {
        SUCCESS, FAILED, PROGRESS
    }

    val mWifiMangaer: WifiManager by lazy {
        application.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    var socketUtil: SocketUtil? = null
    val options1Items = arrayListOf<String>("水质监测器", "加热棒", "水泵", "断电报警器", "水位报警")
    var checkDevice: String = ""
    var flag = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bind_device)
        initActionBar(this, "添加新设备")
        chooseDevice.setOnClickListener {
            //条件选择器
            val pvOptions = OptionsPickerBuilder(this@BindDeviceActivity,
                    OnOptionsSelectListener { options1, option2, options3, v ->
                        //返回的分别是三个级别的选中位置
                        val tx = (options1Items.get(options1))
                        chooseDevice.text = tx
                        checkDevice = when (options1) {
                            0 -> DeviceType.TR.toString()
                            1 -> DeviceType.HT.toString()
                            2 -> DeviceType.WP.toString()
                            3 -> DeviceType.PF.toString()
                            4 -> DeviceType.WL.toString()
                            else -> DeviceType.TR.toString()
                        }
                        search.visibility = View.VISIBLE
                    }).build<String>()
            pvOptions.setPicker(options1Items)
            pvOptions.show()
        }
        search.setOnClickListener {
            searchWifi()
        }
        connect.setOnClickListener {
            sendData(createMsg(wifiAccount.text.toString(), wifiPassword.text.toString()))
        }
    }

    private fun setLoadText(str: String) {
        loadLayout.visibility = View.VISIBLE
        bind_info.text = str
    }

    private fun hideLoading() {
        loadLayout.visibility = View.INVISIBLE
    }

    private fun showWifiLayout() {
        accountLayout.visibility = View.VISIBLE
        connect.visibility = View.VISIBLE
    }

    private fun hideWifiLayout() {
        accountLayout.visibility = View.GONE
        connect.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        flag = false
    }


    private fun sendData(byteArray: ByteArray) {
        if (socketUtil!!.isOpened) {
            socketUtil!!.sendMsg(byteArray)
        }
    }

    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)//是否可用
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)//搜索结果
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)//连接状态改变
        intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)//连接中各种状态
        registerReceiver(receiver, intentFilter)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(receiver)
    }

    var isWifiFind = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            when (action) {
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    val wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0)
                    if (wifiState == WIFI_STATE_ENABLED && checkDevice != "" && !isWifiFind) {
                        mWifiMangaer.startScan()
                    }
                }
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    if (!isWifiFind) {
                        isWifiFind = true
                        val scanResults = mWifiMangaer.scanResults
                        val rel = isFind(scanResults)
                        if (rel >= 0) {
                            dealSearchResult(scanResults[rel].SSID)
                        } else {
                            isWifiFind = false
                        }
                    }
                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    //拿到NetworkInfo
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                    //判断连接上了哈
                    if (null != networkInfo && networkInfo.isConnected) {
                        //连接上了,就把wifi的信息传出去
                        val wifiInfo = intent.getParcelableExtra<WifiInfo>(WifiManager.EXTRA_WIFI_INFO)
                        if (wifiInfo != null && wifiInfo.ssid.replace("\"", "") == WIFI_SSID) {
                            //把结果回传出去
                            connectDevice()
                            waitRegister = false
                        } else {
                            if (waitRegister) {
                                setLoadText("注册信息 ${wifiInfo.ssid}")
                                socketUtil?.release()
                                registerDevice(deviceIDS)
                            }
                        }
                    }
                }
            }
        }
    }

    private var connectCount = 0
    private var waitRegister = false
    private var deviceIDS = ""

    private var isCallConnect = false

    private fun connectDevice() {
        if (isCallConnect) {
            return
        } else {
            isCallConnect = true
        }
        setLoadText("正在连接设备")
        if (null == socketUtil) {
            socketUtil = SocketUtil("192.168.4.1", 8899, object : OnMsgComing {
                override fun onInitSocket(isSuccess: Boolean) {
                    if (isSuccess) {
                        toast("设备连接成功,请输入Wifi信息")
                        showWifiLayout()
                        hideLoading()
                        connectCount = 0
                    } else {
                        toast("设备连接失败,请重新搜索")
                        isWifiFind = false
                    }
                }

                override fun onMsgCome(byteArray: ByteArray) {
                    setLoadText("发送关闭热点信息")
                    doAsync {
                        uiThread {
                            sendData(getCloseWifiMsg())
                        }
                        waitRegister = true
                        val temp = String(byteArray)
                        deviceIDS = temp.substring(0, 3) + temp.substring(4, temp.lastIndex)
                    }
                }
            })
        }
        if (!socketUtil!!.isOpened) {
            socketUtil!!.init()
        }
        isCallConnect = false
    }

    private fun isFind(scanResults: List<ScanResult>): Int {
        for (i in 0 until scanResults.size) {
            if (scanResults[i].SSID == WIFI_SSID) {
                return i
            }
        }
        return -1
    }

    private var isCall = false

    private fun dealSearchResult(SSID: String) {
        if (isCall) {
            return
        } else {
            isCall = true
        }
        doAsync {
            uiThread {
                setLoadText("正在连接设备热点")
            }
            var netWorkId = checkSavedWifiSSID()
            if (netWorkId == -1) {
                netWorkId = mWifiMangaer.addNetwork(createWifiInfo(SSID, "", 1))
            }
            if (netWorkId >= 0) {
                mWifiMangaer.disconnect()
                Thread.sleep(500)
                if (!mWifiMangaer.enableNetwork(netWorkId, true)) {
                    uiThread {
                        toast("设备连接失败,请手动连接wifi")
                        hideLoading()
                        hideWifiLayout()
                        isWifiFind = false
                        isCall = false
                    }
                }
                isCall = false
            } else {
                uiThread {
                    toast("设备连接失败,请重新搜索")
                    hideLoading()
                    hideWifiLayout()
                    isWifiFind = false
                    isCall = false
                }
            }
        }
    }


    fun registerDevice(id: String) {
        doAsync {
            flag = true
            var count = 0
            while (flag) {
                Thread.sleep(5000)
                val map = mapOf(Pair("acccardtype_id", 0.toString()),
                        Pair("card_id", id))
                MySimpleRequest(object : MySimpleRequest.RequestCallBack {
                    override fun onSuccess(context: Context, result: String) {
                        flag = false
                        toast("设备注册成功")
                        finish()
                    }

                    override fun onError(context: Context, error: String) {
                        if (count >= 10) {
                            flag = false
                            context.toast(error)
                            finish()
                        }
                    }

                    override fun onLoginErr(context: Context) {
                        flag = false
                        context.LoginErrDialog(DialogInterface.OnClickListener { _, _ ->
                            val intent = Intent(context, LoginActivity::class.java)
                            startActivity(intent)
                        })
                    }

                }, false).postRequest(this@BindDeviceActivity, getInterface(BDSB), map)
                count++
            }
        }
    }

    private fun checkSavedWifiSSID(): Int {
        // 获取已保存wifi配置链表
        val configs = mWifiMangaer.configuredNetworks
        // 显示输出
        for (config in configs) {
            if (config.SSID.replace("\"", "") == WIFI_SSID) {
                return config.networkId
            }
        }
        return -1
    }

    private fun searchWifi() {
        setLoadText("正在搜索设备")
        if (mWifiMangaer.isWifiEnabled) {
            mWifiMangaer.startScan()
        } else {
            //开启wifi
            mWifiMangaer.isWifiEnabled = true
        }
    }

    private fun getCloseWifiMsg(): ByteArray {
        val sb = StringBuffer()
        sb.append(charToHexStr(checkDevice[0]))
        sb.append(charToHexStr(checkDevice[1]))
        sb.append(charToHexStr('-'))
        sb.append(charToHexStr('1'))
        sb.append(makeChecksum(sb.toString()))
        D("closeWifi = $sb")
        return CharUtil.string2bytes(sb.toString())
    }


    private fun createMsg(wifiAccount: String, wifiPassword: String): ByteArray {
        val sb = StringBuffer()
        sb.append(charToHexStr(checkDevice[0]))
        sb.append(charToHexStr(checkDevice[1]))
        sb.append(charToHexStr('-'))
        sb.append(charToHexStr('0'))
        for (i in 0 until wifiAccount.length) {
            sb.append(charToHexStr(wifiAccount[i]))
        }
        sb.append("0d")
        for (i in 0 until wifiPassword.length) {
            sb.append(charToHexStr(wifiPassword[i]))
        }
        sb.append("0d")
        sb.append(getCheckSum(wifiAccount, wifiPassword))
        D("createMsg = $sb")
        return CharUtil.string2bytes(sb.toString())
    }

    private fun getCheckSum(wifiAccount: String, wifiPassword: String): String {
        val sb = StringBuffer()
        sb.append(charToHexStr(checkDevice[0]))
        sb.append(charToHexStr(checkDevice[1]))
        sb.append(charToHexStr('-'))
        sb.append(charToHexStr('0'))
        for (i in 0 until wifiAccount.length) {
            sb.append(charToHexStr(wifiAccount[i]))
        }
        sb.append("0d")
        for (i in 0 until wifiPassword.length) {
            sb.append(charToHexStr(wifiPassword[i]))
        }
        sb.append("0d")
        return makeChecksum(sb.toString())
    }

    // 查看以前是否也配置过这个网络
    private fun isExsits(SSID: String): WifiConfiguration? {
        val existingConfigs = mWifiMangaer.configuredNetworks
        for (existingConfig in existingConfigs) {
            if (existingConfig.SSID == "\"" + SSID + "\"") {
                return existingConfig
            }
        }
        return null
    }

    fun createWifiInfo(SSID: String, Password: String, Type: Int): WifiConfiguration {
        E("create = $SSID")
        val configuration = WifiConfiguration()
        configuration.allowedAuthAlgorithms.clear()
        configuration.allowedGroupCiphers.clear()
        configuration.allowedKeyManagement.clear()
        configuration.allowedPairwiseCiphers.clear()
        configuration.allowedProtocols.clear()
        configuration.SSID = "\"" + SSID + "\""

        val tempConfig = this.isExsits(SSID)
        if (tempConfig != null) {
            mWifiMangaer.removeNetwork(tempConfig.networkId)
        }

        when (Type) {
            1//不加密
            -> {
                configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
            2//wep加密
            -> {
                configuration.hiddenSSID = true
                configuration.wepKeys[0] = "\"" + Password + "\""
                configuration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                configuration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                configuration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
                configuration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                configuration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
                configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            }
            3 //wpa加密
            -> {

                configuration.preSharedKey = "\"" + Password + "\""
                configuration.hiddenSSID = true
                configuration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                configuration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
                configuration.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                configuration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                configuration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                configuration.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                configuration.status = WifiConfiguration.Status.ENABLED
            }
        }
        return configuration
    }
}