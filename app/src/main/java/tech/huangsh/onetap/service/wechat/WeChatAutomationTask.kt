package tech.huangsh.onetap.service.wechat

@Deprecated("Use WeChatAutomationController. Kept for source compatibility.")
object WeChatAutomationTask {
    val isIdle: Boolean get() = !WeChatAutomationController.hasActiveTask()
    var searchKeyword: String = ""
        private set
    var videoMode: Boolean = true
        private set
    var step: Int = 0
        private set
    var stepRetryCount: Int = 0

    fun tryBegin(keyword: String, video: Boolean): Boolean {
        searchKeyword = keyword
        videoMode = video
        step = 1
        return WeChatAutomationController.tryStartTask(keyword, video)
    }

    fun refreshTimeout() = Unit
    fun advanceStep(next: Int) {
        step = next
    }

    fun forceReset(reason: String = "") {
        searchKeyword = ""
        step = 0
        WeChatAutomationController.cancelTask()
    }

    fun callActionLabel(): String = if (videoMode) "视频通话" else "语音通话"
}
