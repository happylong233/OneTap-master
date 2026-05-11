package tech.huangsh.onetap.service.wechat

enum class WeChatCallType {
    VIDEO,
    VOICE
}

enum class WeChatCallStep(val value: Int) {
    IDLE(-1),
    PREPARE_HOME(0),
    TAP_CONTACTS_TAB(1),
    TAP_CONTACTS_RESULT(2),
    TAP_PROFILE_CALL_ENTRY(3),
    TAP_SEARCH(10),
    ENTER_TARGET_NAME(11),
    TAP_CONTACT_RESULT(12),
    TAP_MORE(13),
    TAP_CALL_ENTRY(14),
    TAP_CALL_TYPE(15)
}

object WeChatCallTask {
    private const val MAX_RETRY_PER_STEP = 3

    @Volatile
    var targetName: String = ""
        private set

    @Volatile
    var callType: WeChatCallType = WeChatCallType.VIDEO
        private set

    @Volatile
    var step: WeChatCallStep = WeChatCallStep.IDLE
        private set

    @Volatile
    var running: Boolean = false
        private set

    @Volatile
    var retryCount: Int = 0
        private set

    fun start(targetName: String, callType: WeChatCallType): Boolean {
        if (targetName.isBlank()) return false
        synchronized(this) {
            if (running) return false
            this.targetName = targetName.trim()
            this.callType = callType
            this.step = WeChatCallStep.PREPARE_HOME
            this.retryCount = 0
            this.running = true
            return true
        }
    }

    fun next(nextStep: WeChatCallStep) {
        synchronized(this) {
            step = nextStep
            retryCount = 0
        }
    }

    fun canRetry(): Boolean {
        synchronized(this) {
            retryCount += 1
            return retryCount <= MAX_RETRY_PER_STEP
        }
    }

    fun reset() {
        synchronized(this) {
            targetName = ""
            callType = WeChatCallType.VIDEO
            step = WeChatCallStep.IDLE
            retryCount = 0
            running = false
        }
    }
}
