package tech.huangsh.onetap.service.wechat

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

object WeChatAutomationController {
    private const val WECHAT_PKG = "com.tencent.mm"
    private const val TASK_TIMEOUT_MS = 30_000L
    private const val RETRY_DELAY_MS = 700L
    private const val PREPARE_HOME_DELAY_MS = 700L
    private const val WAIT_WECHAT_DELAY_MS = 1000L
    private const val MAX_HOME_BACK_COUNT = 8
    private const val MAX_WECHAT_RELAUNCH_COUNT = 3
    private const val TAG = "WeChatAutomation"
    private const val INVALID_NODE_RETRY_LIMIT = 2
    private const val SEARCH_COORDINATES_LIMIT = 5
    private const val CONTACT_RESULT_COORDINATES_LIMIT = 5

    private const val TEXT_SEARCH = "\u641c\u7d22"
    private const val TEXT_WECHAT = "\u5fae\u4fe1"
    private const val TEXT_CONTACTS = "\u901a\u8baf\u5f55"
    private const val TEXT_DISCOVER = "\u53d1\u73b0"
    private const val TEXT_ME = "\u6211"
    private const val TEXT_MORE = "\u66f4\u591a"
    private const val TEXT_MORE_BUTTON = "\u66f4\u591a\u529f\u80fd\u6309\u94ae"
    private const val TEXT_PLUS = "\u52a0\u53f7"
    private const val TEXT_VIDEO_CALL = "\u89c6\u9891\u901a\u8bdd"
    private const val TEXT_AUDIO_VIDEO_CALL = "\u97f3\u89c6\u9891\u901a\u8bdd"
    private const val TEXT_VOICE_CALL = "\u8bed\u97f3\u901a\u8bdd"
    private const val MSG_ENTER_WECHAT_FAILED = "\u65e0\u6cd5\u8fdb\u5165\u5fae\u4fe1\uff0c\u8bf7\u624b\u52a8\u6253\u5f00\u5fae\u4fe1\u540e\u91cd\u8bd5"
    private const val MSG_HOME_FAILED = "\u65e0\u6cd5\u56de\u5230\u5fae\u4fe1\u9996\u9875\uff0c\u8bf7\u624b\u52a8\u56de\u5230\u5fae\u4fe1\u9996\u9875\u540e\u91cd\u8bd5"
    private const val MSG_AUTOMATION_FAILED = "\u5fae\u4fe1\u81ea\u52a8\u901a\u8bdd\u5931\u8d25\uff0c\u8bf7\u786e\u8ba4\u5fae\u4fe1\u7248\u672c\u3001\u8054\u7cfb\u4eba\u6635\u79f0\u3001\u65e0\u969c\u788d\u6743\u9650\u662f\u5426\u6b63\u5e38"

    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private var pendingRetryService: AccessibilityService? = null
    private var activeService: AccessibilityService? = null
    private var homeBackCount = 0
    private var relaunchWechatCount = 0
    private var invalidSearchNodeCount = 0
    private var searchCoordinateAttempt = 0
    private var contactResultCoordinateAttempt = 0
    private var pasteInputAttempt = 0
    private var lastActivityName = ""
    private var nextAllowedRunAt = 0L

    @Volatile
    private var isProcessing = false

    var onFailure: ((String) -> Unit)? = null
    var onOpenedCallUi: ((Boolean) -> Unit)? = null

    fun hasActiveTask(): Boolean = WeChatCallTask.running

    fun setTargetClipboard(context: Context, targetName: String): Boolean {
        val target = targetName.trim()
        if (target.isEmpty()) return false
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("onetap_wechat_target", target))
            val readBack = clipboard.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
            val match = readBack == target
            Log.d(TAG, "setTargetClipboard target=<$target> readBack=<$readBack> match=$match")
            match
        } catch (e: Throwable) {
            Log.e(TAG, "setTargetClipboard failed", e)
            false
        }
    }

    fun tryStartTask(keyword: String, video: Boolean): Boolean {
        val callType = if (video) WeChatCallType.VIDEO else WeChatCallType.VOICE
        if (!WeChatCallTask.start(keyword, callType)) return false

        homeBackCount = 0
        relaunchWechatCount = 0
        invalidSearchNodeCount = 0
        searchCoordinateAttempt = 0
        contactResultCoordinateAttempt = 0
        pasteInputAttempt = 0
        lastActivityName = ""
        nextAllowedRunAt = 0L
        isProcessing = false
        cancelTimeout()
        timeoutRunnable = Runnable { failAndReset(activeService, "timeout") }
        handler.postDelayed(timeoutRunnable!!, TASK_TIMEOUT_MS)
        Log.d(TAG, "Task started target=${WeChatCallTask.targetName} callType=${WeChatCallTask.callType}")
        return true
    }

    fun cancelTask() {
        cancelTimeout()
        pendingRetryService = null
        activeService = null
        homeBackCount = 0
        relaunchWechatCount = 0
        invalidSearchNodeCount = 0
        searchCoordinateAttempt = 0
        contactResultCoordinateAttempt = 0
        pasteInputAttempt = 0
        lastActivityName = ""
        nextAllowedRunAt = 0L
        isProcessing = false
        WeChatCallTask.reset()
    }

    fun onAccessibilityEvent(service: AccessibilityService, event: AccessibilityEvent?) {
        if (!WeChatCallTask.running) return
        activeService = service
        val now = System.currentTimeMillis()
        if (now < nextAllowedRunAt) {
            Log.d(TAG, "skip event before nextAllowedRunAt step=${WeChatCallTask.step.value} waitMs=${nextAllowedRunAt - now}")
            return
        }

        val eventPackageName = event?.packageName?.toString()
        val className = event?.className?.toString().orEmpty()
        if (className.isNotBlank()) lastActivityName = className
        val root = service.rootInActiveWindow
        val rootPackageName = root?.packageName?.toString()
        Log.d(TAG, "event step=${WeChatCallTask.step.value}, pkg=$eventPackageName, rootPkg=$rootPackageName, class=$className")

        if (WeChatCallTask.step == WeChatCallStep.PREPARE_HOME) {
            if (!beginProcessing("prepareHome")) return
            prepareHome(service, root, eventPackageName, rootPackageName, className)
            return
        }

        if (root == null) {
            retryOrFail(service, "no active window")
            return
        }
        if (eventPackageName != null && eventPackageName != WECHAT_PKG) return
        if (rootPackageName != null && rootPackageName != WECHAT_PKG) {
            retryOrFail(service, "root is not WeChat")
            return
        }

        if (!beginProcessing("step=${WeChatCallTask.step.value}")) return
        runStep(service, root)
    }

    private fun runStep(service: AccessibilityService, root: AccessibilityNodeInfo) {
        when (WeChatCallTask.step) {
            WeChatCallStep.PREPARE_HOME -> finishProcessing()
            WeChatCallStep.TAP_CONTACTS_TAB -> tapContactsTab(service, root)
            WeChatCallStep.TAP_CONTACTS_RESULT -> tapContactFromContacts(service, root)
            WeChatCallStep.TAP_PROFILE_CALL_ENTRY -> tapProfileCallEntry(service, root)
            WeChatCallStep.TAP_SEARCH -> tapSearch(service, root)
            WeChatCallStep.ENTER_TARGET_NAME -> enterTargetName(service, root)
            WeChatCallStep.TAP_CONTACT_RESULT -> tapContactResult(service, root)
            WeChatCallStep.TAP_MORE -> tapMore(service, root)
            WeChatCallStep.TAP_CALL_ENTRY -> tapCallEntry(service, root)
            WeChatCallStep.TAP_CALL_TYPE -> tapCallType(service, root)
            WeChatCallStep.IDLE -> finishProcessing()
        }
    }

    private fun prepareHome(
        service: AccessibilityService,
        root: AccessibilityNodeInfo?,
        eventPackageName: String?,
        rootPackageName: String?,
        currentActivity: String
    ) {
        Log.d(TAG, "prepareHome: pkg=$eventPackageName rootPkg=$rootPackageName activity=$currentActivity backCount=$homeBackCount relaunchCount=$relaunchWechatCount")

        if (root == null) {
            val currentPackage = eventPackageName ?: rootPackageName
            if (currentPackage != null && currentPackage != WECHAT_PKG) {
                waitOrRelaunchWechat(service)
            } else {
                finishProcessing()
                schedulePrepareHomeCheck(service)
            }
            return
        }

        val currentPackage = eventPackageName ?: rootPackageName ?: root.packageName?.toString()
        if (currentPackage != WECHAT_PKG) {
            waitOrRelaunchWechat(service)
            return
        }

        if (currentActivity.contains("dialog", ignoreCase = true)) {
            performBackToPrepareAgain(service)
            return
        }

        closeKnownDialog(root)?.let {
            if (clickNodeOrParent(it)) {
                finishProcessing()
                schedulePrepareHomeCheck(service)
                return
            }
        }

        if (isWechatMainFrame(root)) {
            val firstTab = findFirstWechatTab(root)
            val clicked = clickNodeOrParentOrBounds(firstTab, service) || clickWechatBottomTab(root, service, 0)
            Log.d(TAG, "prepareHome: main frame detected, clicked first tab=$clicked")
            homeBackCount = 0
            relaunchWechatCount = 0
            handler.postDelayed({
                WeChatCallTask.next(WeChatCallStep.TAP_SEARCH)
                finishProcessing()
                onAccessibilityEvent(service, null)
            }, 500L)
            return
        }

        performBackToPrepareAgain(service)
    }

    private fun tapContactsTab(service: AccessibilityService, root: AccessibilityNodeInfo) {
        val contacts = findNodeByText(root, TEXT_CONTACTS) ?: findNodeByDescription(root, TEXT_CONTACTS)
        Log.d(TAG, "tapContactsTab: found=${contacts != null}")
        if (clickNodeAncestor(contacts, 2) || clickNodeOrParentOrBounds(contacts, service) || clickWechatBottomTab(root, service, 1)) {
            WeChatCallTask.next(WeChatCallStep.TAP_CONTACTS_RESULT)
            finishAndSchedule(service, 800L)
        } else {
            Log.d(TAG, "tapContactsTab failed, fallback to search path")
            WeChatCallTask.next(WeChatCallStep.TAP_SEARCH)
            finishAndSchedule(service)
        }
    }

    private fun tapContactFromContacts(service: AccessibilityService, root: AccessibilityNodeInfo) {
        val contact = findNodeByText(root, WeChatCallTask.targetName)
            ?: findNodeByDescription(root, WeChatCallTask.targetName)
        Log.d(TAG, "tapContactFromContacts: target=${WeChatCallTask.targetName} found=${contact != null}")
        if (clickNodeAncestor(contact, 6) || clickNodeOrParentOrBounds(contact, service)) {
            WeChatCallTask.next(WeChatCallStep.TAP_PROFILE_CALL_ENTRY)
            finishAndSchedule(service, 900L)
            return
        }

        val list = findScrollableRecycler(root)
        if (list != null && WeChatCallTask.canRetry()) {
            Log.d(TAG, "tapContactFromContacts: scroll contacts list retry=${WeChatCallTask.retryCount}")
            list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            finishAndSchedule(service, 900L)
        } else {
            Log.d(TAG, "tapContactFromContacts failed, fallback to search path")
            WeChatCallTask.next(WeChatCallStep.TAP_SEARCH)
            finishAndSchedule(service)
        }
    }

    private fun tapProfileCallEntry(service: AccessibilityService, root: AccessibilityNodeInfo) {
        val callEntry = findNodeByText(root, TEXT_AUDIO_VIDEO_CALL)
            ?: findNodeByDescription(root, TEXT_AUDIO_VIDEO_CALL)
            ?: findNodeByText(root, TEXT_VIDEO_CALL)
            ?: findNodeByDescription(root, TEXT_VIDEO_CALL)
        Log.d(TAG, "tapProfileCallEntry: found=${callEntry != null}")
        if (clickNodeAncestor(callEntry, 2) || clickNodeOrParentOrBounds(callEntry, service)) {
            WeChatCallTask.next(WeChatCallStep.TAP_CALL_TYPE)
            finishAndSchedule(service, 800L)
        } else {
            Log.d(TAG, "tapProfileCallEntry failed, fallback to search path")
            WeChatCallTask.next(WeChatCallStep.TAP_SEARCH)
            finishAndSchedule(service)
        }
    }

    private fun tapSearch(service: AccessibilityService, root: AccessibilityNodeInfo) {
        if (findEditable(root) != null) {
            searchCoordinateAttempt = 0
            WeChatCallTask.next(WeChatCallStep.ENTER_TARGET_NAME)
            finishAndSchedule(service)
            return
        }

        val search = findByViewId(root, WeChatId.SEARCH.id)
            ?.takeIf { hasValidBoundsInParents(it) }
            ?: findVisibleNodeByText(root, TEXT_SEARCH)
            ?: findVisibleNodeByDescription(root, TEXT_SEARCH)
            ?: findNodeByText(root, TEXT_SEARCH)
            ?: findNodeByDescription(root, TEXT_SEARCH)
        Log.d(TAG, "tapSearch: found=${search != null}")
        if (clickNodeOrParentOrBounds(search, service)) {
            invalidSearchNodeCount = 0
            searchCoordinateAttempt = 0
            WeChatCallTask.next(WeChatCallStep.ENTER_TARGET_NAME)
            finishAndSchedule(service, 1000L)
        } else if (searchCoordinateAttempt < SEARCH_COORDINATES_LIMIT && clickLikelySearchArea(root, service, searchCoordinateAttempt)) {
            invalidSearchNodeCount += 1
            searchCoordinateAttempt += 1
            Log.d(TAG, "tapSearch: clicked likely search area, invalidSearchNodeCount=$invalidSearchNodeCount coordinateAttempt=$searchCoordinateAttempt")
            WeChatCallTask.next(WeChatCallStep.ENTER_TARGET_NAME)
            finishAndSchedule(service, 1000L)
        } else {
            finishProcessing()
            retryOrFail(service, "search button not found")
        }
    }

    private fun enterTargetName(service: AccessibilityService, root: AccessibilityNodeInfo) {
        if (isLikelyTopRightPopup(root, service)) {
            Log.d(TAG, "enterTargetName: top-right popup detected, close and retry search")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            WeChatCallTask.next(WeChatCallStep.TAP_SEARCH)
            finishAndSchedule(service, 600L)
            return
        }

        dumpAllWindows(service)
        dumpInputCandidates(root)
        val inputById = findByViewId(root, WeChatId.INPUT.id)
        val input = findRealSearchInputAcrossWindows(service)
        val searchPageOpened = isWechatSearchPageOpened(root)
        Log.d(TAG, "enterTargetName: realInput=${input != null} byId=${inputById != null} searchPage=$searchPageOpened class=${input?.className} editable=${input?.isEditable}")
        if (input == null && searchPageOpened) {
            Log.w(TAG, "enterTargetName: search page opened but real input not exposed, use coordinate paste fallback")
            enterByCoordinatePasteOrKeyboard(service, WeChatCallTask.targetName)
            finishProcessing()
            return
        }
        if (input == null && searchCoordinateAttempt in 1 until SEARCH_COORDINATES_LIMIT) {
            Log.d(TAG, "enterTargetName: search page not opened, retry tap search with coordinateAttempt=$searchCoordinateAttempt")
            WeChatCallTask.next(WeChatCallStep.TAP_SEARCH)
            finishAndSchedule(service, 500L)
            return
        }
        clickNodeOrParentOrBounds(input, service)
        if (setText(input, WeChatCallTask.targetName)) {
            searchCoordinateAttempt = 0
            WeChatCallTask.next(WeChatCallStep.TAP_CONTACT_RESULT)
            finishAndSchedule(service, 1600L)
        } else {
            finishProcessing()
            retryOrFail(service, "search input not found")
        }
    }

    private fun tapContactResult(service: AccessibilityService, root: AccessibilityNodeInfo) {
        val byList = findByViewId(root, WeChatId.LIST.id)
        val byName = findNodeByText(root, WeChatCallTask.targetName)
            ?: findNodeByDescription(root, WeChatCallTask.targetName)
        val result = byList ?: byName
        val inChat = isLikelyChatPage(root)
        Log.d(TAG, "tapContactResult: listFound=${byList != null} nameFound=${byName != null} inChat=$inChat activity=$lastActivityName")
        if (byList == null && byName == null && pasteInputAttempt < 1 && !pageContainsText(root, WeChatCallTask.targetName)) {
            val input = findRealSearchInputAcrossWindows(service)
            if (input != null) {
                pasteInputAttempt += 1
                Log.d(TAG, "tapContactResult: target text not verified, fallback paste input")
                pasteToRealInput(service, input, WeChatCallTask.targetName)
                finishAndSchedule(service, 2000L)
                return
            }
        }
        if (inChat) {
            WeChatCallTask.next(WeChatCallStep.TAP_MORE)
            finishAndSchedule(service, 700L)
            return
        }
        if (clickNodeOrParentOrBounds(result, service)) {
            contactResultCoordinateAttempt = 0
            WeChatCallTask.next(WeChatCallStep.TAP_MORE)
            finishAndSchedule(service, 1000L)
        } else if (byName != null &&
            contactResultCoordinateAttempt < CONTACT_RESULT_COORDINATES_LIMIT &&
            clickLikelyContactResult(root, service, contactResultCoordinateAttempt)
        ) {
            contactResultCoordinateAttempt += 1
            Log.d(TAG, "tapContactResult: clicked likely result row attempt=$contactResultCoordinateAttempt")
            WeChatCallTask.next(WeChatCallStep.TAP_MORE)
            finishAndSchedule(service, 1200L)
        } else {
            finishProcessing()
            retryOrFail(service, "contact result not found")
        }
    }

    private fun tapMore(service: AccessibilityService, root: AccessibilityNodeInfo) {
        val more = findByViewId(root, WeChatId.MORE.id)
            ?: findNodeByDescription(root, TEXT_MORE_BUTTON)
            ?: findNodeByDescription(root, TEXT_MORE)
            ?: findNodeByDescription(root, TEXT_PLUS)
            ?: findNodeByText(root, "+")
            ?: findNodeByText(root, TEXT_MORE)
        Log.d(TAG, "tapMore: found=${more != null}")
        if (clickNodeOrParentOrBounds(more, service) || clickLikelyChatMore(service)) {
            WeChatCallTask.next(WeChatCallStep.TAP_CALL_ENTRY)
            finishAndSchedule(service, 1000L)
        } else {
            finishProcessing()
            retryOrFail(service, "more button not found")
        }
    }

    private fun tapCallEntry(service: AccessibilityService, root: AccessibilityNodeInfo) {
        val callEntry = listOf(TEXT_VIDEO_CALL, TEXT_AUDIO_VIDEO_CALL, TEXT_VOICE_CALL)
            .firstNotNullOfOrNull { findNodeByText(root, it) ?: findNodeByDescription(root, it) }
        Log.d(TAG, "tapCallEntry: found=${callEntry != null}")
        if (clickNodeOrParentOrBounds(callEntry, service)) {
            WeChatCallTask.next(WeChatCallStep.TAP_CALL_TYPE)
            finishAndSchedule(service)
        } else {
            finishProcessing()
            retryOrFail(service, "call entry not found")
        }
    }

    private fun tapCallType(service: AccessibilityService, root: AccessibilityNodeInfo) {
        val label = if (WeChatCallTask.callType == WeChatCallType.VIDEO) TEXT_VIDEO_CALL else TEXT_VOICE_CALL
        val option = findNodeByText(root, label) ?: findNodeByDescription(root, label)
        Log.d(TAG, "tapCallType: label=$label found=${option != null}")
        if (clickNodeOrParentOrBounds(option, service)) {
            finishProcessing()
            successFinish()
        } else if (WeChatCallTask.callType == WeChatCallType.VIDEO) {
            finishProcessing()
            successFinish()
        } else {
            finishProcessing()
            retryOrFail(service, "call type option not found")
        }
    }

    private fun waitOrRelaunchWechat(service: AccessibilityService) {
        if (relaunchWechatCount < MAX_WECHAT_RELAUNCH_COUNT) {
            relaunchWechatCount += 1
            val launched = launchWechat(service)
            Log.d(TAG, "prepareHome: wait/relaunch count=$relaunchWechatCount launched=$launched")
        } else {
            Log.d(TAG, "prepareHome: relaunch limit reached, keep waiting until timeout")
        }
        finishProcessing()
        schedulePrepareHomeCheck(service, WAIT_WECHAT_DELAY_MS)
    }

    private fun launchWechat(service: AccessibilityService): Boolean {
        try {
            val intent = service.packageManager.getLaunchIntentForPackage(WECHAT_PKG)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                service.startActivity(intent)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "default WeChat launch failed", e)
        }

        listOf(
            WeChatActivity.INDEX.id,
            "com.tencent.mm.ui.main.MainUI",
            "com.tencent.mm.appbrand.ui.LAUNCHUI"
        ).forEach { className ->
            try {
                service.startActivity(
                    Intent().apply {
                        setClassName(WECHAT_PKG, className)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                return true
            } catch (e: Exception) {
                Log.e(TAG, "WeChat launch failed: $className", e)
            }
        }
        return false
    }

    private fun performBackToPrepareAgain(service: AccessibilityService) {
        if (homeBackCount < MAX_HOME_BACK_COUNT) {
            homeBackCount += 1
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            finishProcessing()
            schedulePrepareHomeCheck(service)
        } else {
            finishProcessing()
            failAndReset(service, MSG_HOME_FAILED)
        }
    }

    private fun closeKnownDialog(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return listOf(
            "\u53d6\u6d88",
            "\u5173\u95ed",
            "\u6211\u77e5\u9053\u4e86",
            "\u77e5\u9053\u4e86",
            "\u7a0d\u540e\u518d\u8bf4",
            "\u6682\u4e0d",
            "\u4ee5\u540e\u518d\u8bf4"
        ).firstNotNullOfOrNull { findNodeByText(root, it) ?: findNodeByDescription(root, it) }
    }

    private fun beginProcessing(reason: String): Boolean {
        if (isProcessing) {
            Log.d(TAG, "skip re-entry while processing $reason")
            return false
        }
        isProcessing = true
        return true
    }

    private fun finishProcessing() {
        isProcessing = false
    }

    private fun retryOrFail(service: AccessibilityService, reason: String) {
        if (WeChatCallTask.canRetry()) {
            Log.d(TAG, "retry step=${WeChatCallTask.step.value}, retry=${WeChatCallTask.retryCount}, reason=$reason")
            finishProcessing()
            scheduleRetry(service)
        } else {
            finishProcessing()
            failAndReset(service, reason)
        }
    }

    private fun finishAndSchedule(service: AccessibilityService, delayMs: Long = RETRY_DELAY_MS) {
        finishProcessing()
        scheduleRetry(service, delayMs)
    }

    private fun scheduleRetry(service: AccessibilityService, delayMs: Long = RETRY_DELAY_MS) {
        pendingRetryService = service
        nextAllowedRunAt = System.currentTimeMillis() + delayMs
        handler.postDelayed({
            val s = pendingRetryService ?: return@postDelayed
            pendingRetryService = null
            nextAllowedRunAt = 0L
            onAccessibilityEvent(s, null)
        }, delayMs)
    }

    private fun schedulePrepareHomeCheck(service: AccessibilityService, delayMs: Long = PREPARE_HOME_DELAY_MS) {
        pendingRetryService = service
        nextAllowedRunAt = System.currentTimeMillis() + delayMs
        handler.postDelayed({
            val s = pendingRetryService ?: return@postDelayed
            pendingRetryService = null
            nextAllowedRunAt = 0L
            onAccessibilityEvent(s, null)
        }, delayMs)
    }

    private fun successFinish() {
        val isVideo = WeChatCallTask.callType == WeChatCallType.VIDEO
        cancelTimeout()
        activeService = null
        homeBackCount = 0
        relaunchWechatCount = 0
        invalidSearchNodeCount = 0
        searchCoordinateAttempt = 0
        contactResultCoordinateAttempt = 0
        pasteInputAttempt = 0
        nextAllowedRunAt = 0L
        isProcessing = false
        WeChatCallTask.reset()
        onOpenedCallUi?.invoke(isVideo)
    }

    private fun failAndReset(service: AccessibilityService?, reason: String) {
        Log.w(TAG, "Task failed reason=$reason step=${WeChatCallTask.step.value} target=${WeChatCallTask.targetName}")
        cancelTimeout()
        pendingRetryService = null
        activeService = null
        homeBackCount = 0
        relaunchWechatCount = 0
        searchCoordinateAttempt = 0
        contactResultCoordinateAttempt = 0
        pasteInputAttempt = 0
        nextAllowedRunAt = 0L
        isProcessing = false
        WeChatCallTask.reset()
        val message = if (reason.startsWith("\u65e0\u6cd5")) reason else MSG_AUTOMATION_FAILED
        service?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
        onFailure?.invoke(message)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun findByViewId(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull().orEmpty()
        Log.d(TAG, "findByViewId id=$id count=${nodes.size}")
        return nodes.firstOrNull()
    }

    private fun findByViewIdAll(root: AccessibilityNodeInfo, id: String): List<AccessibilityNodeInfo> {
        val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull().orEmpty()
        Log.d(TAG, "findByViewIdAll id=$id count=${nodes.size}")
        return nodes
    }

    private fun isWechatMainFrame(root: AccessibilityNodeInfo): Boolean {
        val idTabs = findByViewIdAll(root, WeChatId.TABLES.id)
        if (idTabs.isNotEmpty()) return true

        val hitCount = listOf(TEXT_WECHAT, TEXT_CONTACTS, TEXT_DISCOVER, TEXT_ME).count {
            findNodeByText(root, it) != null || findNodeByDescription(root, it) != null
        }
        Log.d(TAG, "isWechatMainFrame: tab text hitCount=$hitCount")
        return hitCount >= 3
    }

    private fun findFirstWechatTab(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val idTabs = findByViewIdAll(root, WeChatId.TABLES.id)
        if (idTabs.isNotEmpty()) return idTabs.first()
        return findNodeByText(root, TEXT_WECHAT) ?: findNodeByDescription(root, TEXT_WECHAT)
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val direct = root.findAccessibilityNodeInfosByText(text)
        if (direct.isNotEmpty()) return direct.firstOrNull()
        return findNode(root) {
            val nodeText = it.text?.toString().orEmpty()
            nodeText == text || nodeText.contains(text)
        }
    }

    private fun findNodeByDescription(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        return findNode(root) {
            val description = it.contentDescription?.toString().orEmpty()
            description == text || description.contains(text)
        }
    }

    private fun findVisibleNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        return findNode(root) {
            val nodeText = it.text?.toString().orEmpty()
            (nodeText == text || nodeText.contains(text)) && hasValidBoundsInParents(it)
        }
    }

    private fun findVisibleNodeByDescription(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        return findNode(root) {
            val description = it.contentDescription?.toString().orEmpty()
            (description == text || description.contains(text)) && hasValidBoundsInParents(it)
        }
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            findEditable(node.getChild(i) ?: continue)?.let { return it }
        }
        return null
    }

    private fun findRealSearchInput(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        root ?: return null
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { focus ->
            if (isRealInputNode(focus)) return focus
        }
        return findNode(root) { isRealInputNode(it) }
    }

    private fun findRealSearchInputAcrossWindows(service: AccessibilityService): AccessibilityNodeInfo? {
        getWindowRoots(service).forEach { root ->
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { focus ->
                if (isRealInputNode(focus)) return focus
            }
            findRealSearchInput(root)?.let { return it }
        }
        return null
    }

    private fun isWechatSearchPageOpened(root: AccessibilityNodeInfo?): Boolean {
        root ?: return false
        val hasSearchId = runCatching {
            root.findAccessibilityNodeInfosByViewId(WeChatId.INPUT.id).isNotEmpty()
        }.getOrDefault(false)
        val hasSearchPageText = listOf(
            "\u641c\u7d22\u672c\u5730",
            "\u7f51\u7edc\u7ed3\u679c",
            "AI\u641c\u7d22",
            "\u6700\u8fd1\u5728\u641c",
            "\u6df1\u5ea6\u601d\u8003"
        ).any { pageContainsText(root, it) }
        Log.d(TAG, "isWechatSearchPageOpened: hasD98=$hasSearchId hasText=$hasSearchPageText")
        return hasSearchId || hasSearchPageText
    }

    private fun isRealInputNode(node: AccessibilityNodeInfo?): Boolean {
        node ?: return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty || rect.width() <= 0 || rect.height() <= 0) return false

        val className = node.className?.toString().orEmpty()
        val hasSetTextAction = node.actionList.any {
            it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT.id
        }
        return node.isEditable || className.contains("EditText", ignoreCase = true) || hasSetTextAction
    }

    private fun pageContainsText(node: AccessibilityNodeInfo?, keyword: String): Boolean {
        node ?: return false
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val hint = node.hintText?.toString().orEmpty()
        if (text.contains(keyword) || desc.contains(keyword) || hint.contains(keyword)) return true
        for (i in 0 until node.childCount) {
            if (pageContainsText(node.getChild(i), keyword)) return true
        }
        return false
    }

    private fun enterByCoordinatePasteOrKeyboard(
        service: AccessibilityService,
        targetName: String
    ): Boolean {
        if (!setTargetClipboard(service, targetName)) {
            failAndReset(service, "\u526a\u8d34\u677f\u5199\u5165\u5931\u8d25")
            return false
        }
        val screen = getScreenSize(service)
        val inputX = screen.x * 0.42f
        val inputY = screen.y * 0.09f
        Log.d(TAG, "enterByCoordinatePasteOrKeyboard: focus coordinate x=$inputX y=$inputY")
        performClick(service, inputX, inputY)

        handler.postDelayed({
            val pastedByFocus = pasteToFocusedInputAcrossWindows(service)
            Log.d(TAG, "enterByCoordinatePasteOrKeyboard: paste focused result=$pastedByFocus")
            if (!pastedByFocus) {
                longPressAndClickPaste(service, inputX, inputY)
            } else {
                WeChatCallTask.next(WeChatCallStep.TAP_CONTACT_RESULT)
                scheduleRetry(service, 1500L)
            }
        }, 350L)
        return true
    }

    private fun copyToClipboard(service: AccessibilityService, text: String) {
        setTargetClipboard(service, text)
    }

    private fun pasteToFocusedInputAcrossWindows(service: AccessibilityService): Boolean {
        getWindowRoots(service).forEach { root ->
            val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focus != null) {
                val rect = Rect()
                focus.getBoundsInScreen(rect)
                Log.d(TAG, "pasteToFocusedInputAcrossWindows: class=${focus.className} editable=${focus.isEditable} rect=$rect text=${focus.text}")
                if (!isRealInputNode(focus)) {
                    Log.w(TAG, "pasteToFocusedInputAcrossWindows: focused input invalid, ignore ACTION_PASTE")
                    return@forEach
                }
                focus.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                focus.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                val pasteOk = focus.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                val setOk = setText(focus, WeChatCallTask.targetName)
                if (pasteOk || setOk) return true
            }
        }
        return false
    }

    private fun longPressAndClickPaste(
        service: AccessibilityService,
        x: Float,
        y: Float
    ): Boolean {
        Log.d(TAG, "longPressAndClickPaste x=$x y=$y")
        val path = Path().apply { moveTo(x, y) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(
                android.accessibilityservice.GestureDescription.StrokeDescription(
                    path,
                    0,
                    700
                )
            )
            .build()
        return service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    handler.postDelayed({
                        val pasteNode = findNodeByTextAcrossWindows(service, "\u7c98\u8d34")
                        val clicked = if (pasteNode != null) {
                            Log.d(TAG, "longPressAndClickPaste: paste menu found")
                            clickNodeOrParentOrBounds(pasteNode, service)
                        } else {
                            Log.w(TAG, "longPressAndClickPaste: paste menu not found")
                            clickPasteMenuByCoordinate(service, x, y)
                        }
                        Log.d(TAG, "longPressAndClickPaste: click paste result=$clicked")
                        if (clicked) {
                            WeChatCallTask.next(WeChatCallStep.TAP_CONTACT_RESULT)
                            scheduleRetry(service, 1500L)
                        }
                    }, 350L)
                }

                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    Log.w(TAG, "longPressAndClickPaste: cancelled")
                }
            },
            null
        )
    }

    private fun clickPasteMenuByCoordinate(
        service: AccessibilityService,
        inputX: Float,
        inputY: Float
    ): Boolean {
        val screen = getScreenSize(service)
        val candidates = listOf(
            screen.x * 0.16f to screen.y * 0.155f,
            screen.x * 0.22f to screen.y * 0.155f,
            inputX - screen.x * 0.25f to inputY + screen.y * 0.07f,
            inputX - screen.x * 0.18f to inputY + screen.y * 0.07f
        )
        candidates.forEachIndexed { index, point ->
            val (cx, cy) = point
            Log.d(TAG, "clickPasteMenuByCoordinate attempt=$index x=$cx y=$cy")
            if (performClick(service, cx, cy)) return true
        }
        return false
    }

    private fun findNodeByTextAcrossWindows(
        service: AccessibilityService,
        text: String
    ): AccessibilityNodeInfo? {
        getWindowRoots(service).forEach { root ->
            findNodeByText(root, text)?.let { return it }
            findNodeByDescription(root, text)?.let { return it }
        }
        return null
    }

    private fun getWindowRoots(service: AccessibilityService): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        service.rootInActiveWindow?.let { roots += it }
        runCatching {
            service.windows.forEach { window ->
                window.root?.let { roots += it }
            }
        }.onFailure {
            Log.w(TAG, "getWindowRoots failed", it)
        }
        return roots.distinctBy { System.identityHashCode(it) }
    }

    private fun dumpAllWindows(service: AccessibilityService) {
        Log.d(TAG, "dumpAllWindows: root=${service.rootInActiveWindow?.packageName}")
        runCatching {
            service.windows.forEachIndexed { index, window ->
                val root = window.root
                val summary = collectNodeSummary(root, 30)
                Log.d(
                    TAG,
                    "Window[$index] type=${window.type} pkg=${root?.packageName} class=${root?.className} text=$summary"
                )
            }
        }.onFailure {
            Log.w(TAG, "dumpAllWindows failed", it)
        }
    }

    private fun collectNodeSummary(
        node: AccessibilityNodeInfo?,
        limit: Int
    ): String {
        val values = mutableListOf<String>()
        fun walk(current: AccessibilityNodeInfo?) {
            if (current == null || values.size >= limit) return
            val text = current.text?.toString().orEmpty()
            val hint = current.hintText?.toString().orEmpty()
            val desc = current.contentDescription?.toString().orEmpty()
            listOf(text, hint, desc)
                .filter { it.isNotBlank() }
                .forEach {
                    if (values.size < limit) values += it.take(20)
                }
            for (i in 0 until current.childCount) {
                walk(current.getChild(i))
                if (values.size >= limit) return
            }
        }
        walk(node)
        return values.joinToString("|")
    }

    private fun dumpInputCandidates(node: AccessibilityNodeInfo?, depth: Int = 0) {
        node ?: return
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val className = node.className?.toString().orEmpty()
        val text = node.text?.toString().orEmpty()
        val hint = node.hintText?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
        val hasSetTextAction = node.actionList.any {
            it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT.id
        }
        if (
            node.isEditable ||
            className.contains("EditText", ignoreCase = true) ||
            hasSetTextAction ||
            hint.contains(TEXT_SEARCH) ||
            text.contains(TEXT_SEARCH) ||
            desc.contains(TEXT_SEARCH) ||
            viewId.contains("search", ignoreCase = true)
        ) {
            Log.d(
                TAG,
                "InputCandidate depth=$depth id=$viewId class=$className editable=${node.isEditable} " +
                    "focused=${node.isFocused} focusable=${node.isFocusable} clickable=${node.isClickable} " +
                    "text=$text hint=$hint desc=$desc rect=$rect actions=${node.actionList.map { it.id }}"
            )
        }
        for (i in 0 until node.childCount) {
            dumpInputCandidates(node.getChild(i), depth + 1)
        }
    }

    private fun findEditableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isEditable) return current
            current = current.parent
        }
        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            val rect = Rect()
            current.getBoundsInScreen(rect)
            if (current.isClickable && !rect.isEmpty) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }

    private fun clickNodeOrParentOrBounds(
        node: AccessibilityNodeInfo?,
        service: AccessibilityService
    ): Boolean {
        var current = node
        var firstValidRect: Rect? = null
        var depth = 0
        while (current != null) {
            val rect = Rect()
            current.getBoundsInScreen(rect)
            Log.d(
                TAG,
                "clickNodeOrParentOrBounds depth=$depth clickable=${current.isClickable} rect=$rect class=${current.className}"
            )
            if (!rect.isEmpty && firstValidRect == null) {
                firstValidRect = Rect(rect)
            }
            if (current.isClickable && !rect.isEmpty) {
                val actionResult = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "clickNodeOrParentOrBounds actionResult=$actionResult depth=$depth")
                if (actionResult) return true
                firstValidRect = Rect(rect)
            }
            current = current.parent
            depth += 1
        }
        val rect = firstValidRect ?: return false
        return performClick(service, rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    private fun hasValidBoundsInParents(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            val rect = Rect()
            current.getBoundsInScreen(rect)
            if (!rect.isEmpty && rect.width() > 2 && rect.height() > 2) return true
            current = current.parent
        }
        return false
    }

    private fun clickLikelySearchArea(
        root: AccessibilityNodeInfo,
        service: AccessibilityService,
        attempt: Int
    ): Boolean {
        val rect = Rect()
        root.getBoundsInScreen(rect)
        val screen = getScreenSize(service)
        val width = screen.x
        val top = 0
        val candidates = listOf(
            width - 210f to top + 150f,
            width - 260f to top + 150f,
            width - 310f to top + 150f,
            width - 210f to top + 105f,
            width - 360f to top + 150f
        )
        val (x, y) = candidates.getOrNull(attempt) ?: return false
        Log.d(TAG, "tapSearch: fallback search coordinate attempt=$attempt x=$x y=$y rootRect=$rect")
        return performClick(service, x, y)
    }

    private fun clickWechatBottomTab(
        root: AccessibilityNodeInfo,
        service: AccessibilityService,
        index: Int
    ): Boolean {
        val rect = Rect()
        root.getBoundsInScreen(rect)
        val screen = getScreenSize(service)
        val width = screen.x
        val height = screen.y
        val tabWidth = width / 4f
        val x = tabWidth * index + tabWidth / 2f
        val y = height - 72f
        Log.d(TAG, "clickWechatBottomTab: index=$index x=$x y=$y rootRect=$rect")
        return performClick(service, x, y)
    }

    private fun clickLikelyContactResult(
        root: AccessibilityNodeInfo,
        service: AccessibilityService,
        attempt: Int
    ): Boolean {
        val rect = Rect()
        root.getBoundsInScreen(rect)
        val screen = getScreenSize(service)
        val width = screen.x
        val top = if (rect.top > 0) rect.top else 0
        val candidates = listOf(
            width / 2f to top + 320f,
            width / 2f to top + 410f,
            width / 2f to top + 500f,
            width / 2f to top + 590f,
            width / 2f to top + 680f
        )
        val (x, y) = candidates.getOrNull(attempt) ?: return false
        Log.d(TAG, "tapContactResult: fallback result coordinate attempt=$attempt x=$x y=$y rootRect=$rect")
        return performClick(service, x, y)
    }

    private fun clickLikelyChatMore(service: AccessibilityService): Boolean {
        val screen = getScreenSize(service)
        val width = screen.x.toFloat()
        val height = screen.y.toFloat()
        val candidates = listOf(
            width - 70f to height - 165f,
            width - 100f to height - 210f,
            width - 150f to height - 165f
        )
        candidates.forEachIndexed { index, point ->
            val (x, y) = point
            Log.d(TAG, "tapMore: fallback more coordinate attempt=$index x=$x y=$y")
            if (performClick(service, x, y)) return true
        }
        return false
    }

    private fun isLikelyChatPage(root: AccessibilityNodeInfo): Boolean {
        if (lastActivityName == WeChatActivity.CHAT.id || lastActivityName.contains("Chatting", ignoreCase = true)) {
            return true
        }
        val more = findByViewId(root, WeChatId.MORE.id)
            ?: findNodeByDescription(root, TEXT_MORE_BUTTON)
            ?: findNodeByDescription(root, TEXT_PLUS)
            ?: findNodeByText(root, "+")
        return more != null && !isWechatMainFrame(root)
    }

    private fun isLikelyTopRightPopup(root: AccessibilityNodeInfo, service: AccessibilityService): Boolean {
        val rect = Rect()
        root.getBoundsInScreen(rect)
        val screen = getScreenSize(service)
        return !rect.isEmpty &&
            rect.left > screen.x * 0.45f &&
            rect.top in 120..500 &&
            rect.width() < screen.x * 0.6f &&
            rect.height() < screen.y * 0.4f
    }

    private fun getScreenSize(service: AccessibilityService): Point {
        val metrics = service.resources.displayMetrics
        return Point(metrics.widthPixels, metrics.heightPixels)
    }

    private fun clickNodeAncestor(node: AccessibilityNodeInfo?, parentCount: Int): Boolean {
        var current = node
        repeat(parentCount) {
            current = current?.parent
        }
        return current?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun findScrollableRecycler(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNode(root) { node ->
            node.isScrollable && node.className?.toString()?.contains("RecyclerView") == true
        }
    }

    private fun clickByBounds(node: AccessibilityNodeInfo?, service: AccessibilityService): Boolean {
        val target = node ?: return false
        val rect = Rect()
        target.getBoundsInScreen(rect)
        if (rect.isEmpty) return false
        return performClick(service, rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    private fun performClick(service: AccessibilityService, x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 1f, y + 1f)
        }
        val result = service.dispatchGesture(
            android.accessibilityservice.GestureDescription.Builder()
                .addStroke(
                    android.accessibilityservice.GestureDescription.StrokeDescription(
                        path,
                        0,
                        120
                    )
                )
                .build(),
            null,
            null
        )
        Log.d(TAG, "performClick x=$x y=$y result=$result")
        return result
    }

    private fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        val target = node ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
        return target.parent?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) == true
    }

    private fun setTextDeep(node: AccessibilityNodeInfo?, text: String): Boolean {
        val target = node ?: return false
        if (setText(target, text)) {
            Log.d(TAG, "setTextDeep: success on target class=${target.className} editable=${target.isEditable}")
            return true
        }
        for (i in 0 until target.childCount) {
            val child = target.getChild(i) ?: continue
            if (setText(child, text)) {
                Log.d(TAG, "setTextDeep: success on child index=$i class=${child.className} editable=${child.isEditable}")
                return true
            }
        }
        var parent = target.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (setText(parent, text)) {
                Log.d(TAG, "setTextDeep: success on parent depth=$depth class=${parent.className} editable=${parent.isEditable}")
                return true
            }
            parent = parent.parent
            depth += 1
        }
        Log.d(TAG, "setTextDeep: failed targetClass=${target.className} editable=${target.isEditable}")
        return false
    }

    private fun pasteToRealInput(
        service: AccessibilityService,
        node: AccessibilityNodeInfo?,
        text: String
    ): Boolean {
        val target = node ?: return false
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("wechat_target", text))

        val rect = Rect()
        target.getBoundsInScreen(rect)
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val setResult = setText(target, text)
        val pasteResult = target.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        Log.d(
            TAG,
            "pasteToRealInput: class=${target.className} editable=${target.isEditable} rect=$rect setResult=$setResult pasteResult=$pasteResult afterText=${target.text}"
        )
        return setResult || pasteResult
    }

    private fun findNode(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            findNode(node.getChild(i) ?: continue, predicate)?.let { return it }
        }
        return null
    }
}
