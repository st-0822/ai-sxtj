package com.ai.phone.control

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * 设备管理员接收器：激活后可调用 lockNow() 真锁屏。
 * 用户在 设置 → 安全 → 设备管理员 中手动激活本应用。
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context?, intent: Intent?) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context?, intent: Intent?) {
        super.onDisabled(context, intent)
    }
}
