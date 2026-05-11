package tech.huangsh.onetap.service.wechat

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class WeChatAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        Log.d("WeChatAutomation", "Accessibility service connected with flags=${serviceInfo.flags}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        WeChatAutomationController.onAccessibilityEvent(this, event)
    }

    override fun onInterrupt() {
        WeChatAutomationController.cancelTask()
    }
}
