package com.linkall.android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.linkall.android.data.model.MouseAction
import com.linkall.android.data.model.MouseButton

/**
 * 被控端无障碍服务：接收控制端指令并注入为本地键鼠事件
 *
 * 功能：
 * - 鼠标移动/点击：使用 AccessibilityService dispatchGesture API（API 24+）
 * - 鼠标按键映射：左键=点击，右键=返回手势，中键=Home
 * - 滚轮：暂不支持原生注入（Android 限制）
 *
 * 仅在用户主动开启服务后生效，关闭后立即停止注入。
 */
class LinkALLAccessibilityService : AccessibilityService() {

    companion object {
        /** 单例引用：供 UI 检查服务是否已连接 */
        @Volatile var instance: LinkALLAccessibilityService? = null
            private set

        /** 无障碍服务是否已启用 */
        val isEnabled: Boolean get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不处理本地事件，仅用于注入
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    /**
     * 在指定坐标执行点击手势
     */
    fun performClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y); lineTo(x + 0.1f, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * 在指定坐标执行长按
     */
    fun performLongPress(x: Float, y: Float, durationMs: Long = 500) {
        val path = Path().apply { moveTo(x, y); lineTo(x + 0.1f, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * 执行拖拽（从起点到终点）
     */
    fun performDrag(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 300) {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * 执行双击
     */
    fun performDoubleClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y); lineTo(x + 0.1f, y) }
        val stroke1 = GestureDescription.StrokeDescription(path, 0, 50)
        val stroke2 = GestureDescription.StrokeDescription(path, 100, 50)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * 处理鼠标事件（来自控制端 DataChannel）
     * @return true=已处理
     */
    fun handleMouse(action: MouseAction, button: MouseButton?, x: Float?, y: Float?): Boolean {
        if (x == null || y == null) return false
        return when (action) {
            MouseAction.CLICK -> {
                when (button) {
                    MouseButton.LEFT -> performClick(x, y)
                    MouseButton.RIGHT -> performGlobalBack()
                    MouseButton.MIDDLE -> performGlobalHome()
                    MouseButton.BACK -> performGlobalBack()
                    MouseButton.FORWARD -> performGlobalRecents()
                    else -> performClick(x, y)
                }
                true
            }
            MouseAction.DOUBLE_CLICK -> { performDoubleClick(x, y); true }
            MouseAction.DOWN -> { /* 按下：开始拖拽轨迹 */ true }
            MouseAction.UP -> { /* 抬起：结束拖拽 */ true }
            MouseAction.MOVE -> { /* 移动：暂不注入 */ true }
        }
    }

    private fun performGlobalBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    private fun performGlobalHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    private fun performGlobalRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
}
