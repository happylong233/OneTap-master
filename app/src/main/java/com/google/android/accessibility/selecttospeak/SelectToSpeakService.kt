package com.google.android.accessibility.selecttospeak

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import tech.huangsh.onetap.service.wechat.WeChatAutomationController

/**
 * 微信通话辅助：仅在存在自动化任务且包名为微信时处理界面。
 */
class SelectToSpeakService : AccessibilityService() {

    override fun onInterrupt() {
        WeChatAutomationController.cancelTask()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!WeChatAutomationController.hasActiveTask()) return
        val pkg = event?.packageName?.toString() ?: return
        if (pkg != WECHAT) return
        WeChatAutomationController.onAccessibilityEvent(this, event)
    }

    companion object {
        private const val WECHAT = "com.tencent.mm"
    }
}
