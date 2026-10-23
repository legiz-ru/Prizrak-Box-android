package com.github.kr328.clash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.util.XiaomiAutoStart
import com.github.kr328.clash.util.startClashService

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // The first sign the broadcast came at all: on MIUI / HyperOS
                // without their Autostart switch it never does.
                Log.i("Auto restart: ${intent.action}, start=${StatusProvider.shouldStartClashOnBoot}" +
                    if (XiaomiAutoStart.isXiaomi) ", system autostart=${XiaomiAutoStart.state(context)}" else "")
                if (StatusProvider.shouldStartClashOnBoot)
                    context.startClashService()
            }
        }
    }
}