@file:Suppress("unused")

package de.lemke.commonutils.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue.COMPLEX_UNIT_PX
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_OUTSIDE
import android.view.Surface.ROTATION_0
import android.view.Surface.ROTATION_180
import android.view.Surface.ROTATION_270
import android.view.Surface.ROTATION_90
import android.view.View
import android.view.View.MeasureSpec.UNSPECIFIED
import android.view.View.OnTouchListener
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.Interpolator
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.os.ConfigurationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.reflect.content.res.SeslConfigurationReflector
import de.lemke.commonutils.R
import de.lemke.commonutils.widget.TipPopup.Direction.BOTTOM_LEFT
import de.lemke.commonutils.widget.TipPopup.Direction.BOTTOM_RIGHT
import de.lemke.commonutils.widget.TipPopup.Direction.DEFAULT
import de.lemke.commonutils.widget.TipPopup.Direction.TOP_LEFT
import de.lemke.commonutils.widget.TipPopup.Direction.TOP_RIGHT
import de.lemke.commonutils.widget.TipPopup.State.EXPANDED
import de.lemke.commonutils.widget.TipPopup.State.HINT
import de.lemke.commonutils.widget.TipPopup.Type.BALLOON_ACTION
import de.lemke.commonutils.widget.TipPopup.Type.BALLOON_SIMPLE
import dev.oneuiproject.oneui.ktx.doOnEnd
import dev.oneuiproject.oneui.ktx.setListener
import dev.oneuiproject.oneui.utils.DeviceLayoutUtil
import dev.oneuiproject.oneui.utils.internal.CachedInterpolatorFactory
import dev.oneuiproject.oneui.utils.internal.CachedInterpolatorFactory.Type.ELASTIC_50
import dev.oneuiproject.oneui.utils.internal.CachedInterpolatorFactory.Type.ELASTIC_CUSTOM
import dev.oneuiproject.oneui.utils.internal.CachedInterpolatorFactory.Type.SINE_IN_OUT_33
import dev.oneuiproject.oneui.utils.internal.CachedInterpolatorFactory.Type.SINE_IN_OUT_70
import kotlin.math.ceil
import kotlin.math.floor
import dev.oneuiproject.oneui.design.R as designR

class TipPopup(private val parentView: View) {

    private val context: Context = parentView.context
    private val resources: Resources = context.resources
    private val windowManager: WindowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager

    private val actionView: Button
    var messageText: CharSequence? = null
    var messageTextColor: Int = context.getColor(R.color.commonutils_primary_text_icon_color)
    var actionClickListener: View.OnClickListener? = null
    var actionText: CharSequence? = null
    var actionTextColor: Int = context.getColor(R.color.commonutils_primary_text_icon_color)

    private var arrowDirection: Direction = DEFAULT
    private var arrowPositionX: Int = -1
    private var arrowPositionY: Int = -1
    private val arrowHeight = resources.getDimensionPixelSize(designR.dimen.oui_des_tip_popup_balloon_arrow_height)
    private val arrowWidth = resources.getDimensionPixelSize(designR.dimen.oui_des_tip_popup_balloon_arrow_width)

    private val messageView: TextView

    var backgroundColor: Int = context.getColor(designR.color.oui_des_background_color)
    private var balloonBg1: ImageView? = null
    private var balloonBg2: ImageView? = null
    private var balloonBubble: FrameLayout? = null
    private var balloonBubbleHint: ImageView? = null
    private var balloonBubbleIcon: ImageView? = null
    private var balloonContent: FrameLayout? = null
    private var balloonHeight = 0
    private var balloonPanel: FrameLayout? = null
    private var balloonPopup: TipWindow? = null
    private var balloonPopupX = 0
    private var balloonPopupY = 0
    private val balloonView: View
    private var balloonWidth = 0
    private var balloonX: Int = -1
    private var balloonY = 0
    private var bubbleBackground: ImageView? = null
    private var bubbleHeight = 0
    private var bubbleIcon: ImageView? = null
    private var bubblePopup: TipWindow? = null
    private var bubblePopupX = 0
    private var bubblePopupY = 0
    private val bubbleView: View
    private var bubbleWidth = 0
    private var bubbleX = 0
    private var bubbleY = 0

    private var displayMetrics = resources.displayMetrics
    private var forceRealDisplay = false
    private var hintDescription: CharSequence? = null

    private var initialMessageViewWidth = 0
    private var isDefaultPosition = true
    private var isMessageViewMeasured = false

    private var needToCallParentViewsOnClick = false
    private var onDismissListener: OnDismissListener? = null
    private var onStateChangeListener: OnStateChangeListener? = null

    private var scaleMargin = resources.getDimensionPixelSize(designR.dimen.oui_des_tip_popup_scale_margin)
    private var sideMargin = resources.getDimensionPixelSize(designR.dimen.oui_des_tip_popup_side_margin)

    private var state: State = HINT
    private var type: Type = BALLOON_SIMPLE

    private val displayFrame: Rect = Rect()
    private val horizontalTextMargin =
        resources.getDimensionPixelSize(designR.dimen.oui_des_tip_popup_balloon_message_margin_horizontal)
    private val verticalTextMargin =
        resources.getDimensionPixelSize(designR.dimen.oui_des_tip_popup_balloon_message_margin_vertical)

    /**
     * Choose either [BOTTOM_LEFT], [BOTTOM_RIGHT], [DEFAULT], [TOP_LEFT] or [TOP_RIGHT].
     */
    enum class Direction {
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        DEFAULT,
        TOP_LEFT,
        TOP_RIGHT
    }

    private enum class Type {
        BALLOON_SIMPLE,
        BALLOON_ACTION,
        BALLOON_CUSTOM
    }

    enum class State {
        DISMISSED,
        EXPANDED,
        HINT
    }

    fun interface OnDismissListener {
        fun onDismiss()
    }

    fun interface OnStateChangeListener {
        fun onStateChanged(i: State)
    }

    fun setOnStateChangeListener(changeListener: OnStateChangeListener?) {
        onStateChangeListener = changeListener
    }

    init {
        debugLog("displayMetrics = $displayMetrics")

        initInterpolator()

        LayoutInflater.from(context).apply {
            bubbleView = inflate(designR.layout.oui_des_tip_popup_bubble, null)
            balloonView = inflate(designR.layout.oui_des_tip_popup_balloon, null).also {
                messageView =
                    (it.findViewById<TextView>(designR.id.oui_des_tip_popup_message)).apply { isVisible = false }
                actionView = (it.findViewById<Button>(designR.id.oui_des_tip_popup_action)).apply { isVisible = false }
            }
        }

        initBubblePopup()
        initBalloonPopup()

        bubblePopup!!.setOnDismissListener {
            if (state == HINT) {
                state = State.DISMISSED
                onStateChangeListener?.onStateChanged(state)
                onDismissListener?.onDismiss()
                debugLog("mIsShowing : $isShowing")
                handler?.removeCallbacksAndMessages(null)
                handler = null

                debugLog("onDismiss - BubblePopup")
            }
        }

        balloonPopup!!.setOnDismissListener {
            state = State.DISMISSED
            onStateChangeListener?.onStateChanged(state)
            onDismissListener?.onDismiss()
            debugLog("mIsShowing : $isShowing")
            debugLog("onDismiss - BalloonPopup")
            dismissBubble(false)
            handler?.removeCallbacksAndMessages(null)
            handler = null

        }

        balloonView.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfo,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(
                    AccessibilityAction(
                        ACTION_CLICK,
                        context.getString(designR.string.oui_des_common_close)
                    )
                )
            }
        }
    }

    @SuppressLint("PrivateResource", "RestrictedApi")
    private fun initInterpolator() {
        if (INTERPOLATOR_SINE_IN_OUT_33 == null) {
            INTERPOLATOR_SINE_IN_OUT_33 = CachedInterpolatorFactory.getOrCreate(SINE_IN_OUT_33)
        }
        if (INTERPOLATOR_SINE_IN_OUT_70 == null) {
            INTERPOLATOR_SINE_IN_OUT_70 = CachedInterpolatorFactory.getOrCreate(SINE_IN_OUT_70)
        }
        if (INTERPOLATOR_ELASTIC_50 == null) {
            INTERPOLATOR_ELASTIC_50 = CachedInterpolatorFactory.getOrCreate(ELASTIC_50)
        }
        if (INTERPOLATOR_ELASTIC_CUSTOM == null) {
            INTERPOLATOR_ELASTIC_CUSTOM = CachedInterpolatorFactory.getOrCreate(ELASTIC_CUSTOM)
        }
    }

    private fun initBubblePopup() {
        bubbleBackground = bubbleView.findViewById(designR.id.oui_des_tip_popup_bubble_bg)
        bubbleIcon = bubbleView.findViewById(designR.id.oui_des_tip_popup_bubble_icon)

        bubbleWidth = resources.getDimensionPixelSize(designR.dimen.oui_des_tip_popup_bubble_width)
        bubbleHeight = resources.getDimensionPixelSize(designR.dimen.oui_des_tip_popup_bubble_height)

        bubblePopup = TipWindowBubble(bubbleView, bubbleWidth, bubbleHeight, false).apply {
            isTouchable = true
            isOutsideTouchable = true
            isAttachedInDecor = false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initBalloonPopup() {
        balloonBubble = balloonView.findViewById<FrameLayout>(designR.id.oui_des_tip_popup_balloon_bubble).apply { isVisible = true }
        balloonBubbleHint = balloonView.findViewById(designR.id.oui_des_tip_popup_balloon_bubble_hint)
        balloonBubbleIcon = balloonView.findViewById(designR.id.oui_des_tip_popup_balloon_bubble_icon)
        balloonPanel = balloonView.findViewById<FrameLayout>(designR.id.oui_des_tip_popup_balloon_panel).apply { isVisible = false }
        balloonContent = balloonView.findViewById(designR.id.oui_des_tip_popup_balloon_content)
        balloonBg1 = balloonView.findViewById(designR.id.oui_des_tip_popup_balloon_bg_01)
        balloonBg2 = balloonView.findViewById(designR.id.oui_des_tip_popup_balloon_bg_02)

        balloonPopup = TipWindowBalloon(balloonView, balloonWidth, balloonHeight, true).apply {
            isFocusable = true
            isTouchable = true
            isOutsideTouchable = true
            isAttachedInDecor = false
            setTouchInterceptor { _, event ->
                if (needToCallParentViewsOnClick && parentView.hasOnClickListeners()
                    && (event.action == ACTION_DOWN || event.action == ACTION_OUTSIDE)
                ) {
                    val parentViewBounds = Rect()
                    val outLocation = IntArray(2)
                    parentView.getLocationOnScreen(outLocation)
                    parentViewBounds[outLocation[0], outLocation[1], outLocation[0] + parentView.width] =
                        outLocation[1] + parentView.height
                    val isTouchContainedInParentView =
                        parentViewBounds.contains(event.rawX.toInt(), event.rawY.toInt())
                    if (isTouchContainedInParentView) {
                        debugLog("callOnClick for parent view")
                        parentView.callOnClick()
                    }
                }
                false
            }
        }
    }

    fun show(direction: Direction = DEFAULT) {
        setInternal()
        if (arrowPositionX == -1 || arrowPositionY == -1) {
            calculateArrowPosition()
        }
        if (direction == DEFAULT) {
            calculateArrowDirection(arrowPositionX, arrowPositionY)
        } else {
            arrowDirection = direction
        }
        calculatePopupSize()
        calculatePopupPosition()
        setBubblePanel()
        setBalloonPanel()
        showInternal()
    }

    fun setMessage(message: CharSequence?) {
        messageText = message
    }

    fun setAction(actionText: CharSequence?, listener: View.OnClickListener?) {
        this@TipPopup.actionText = actionText
        actionClickListener = listener
    }

    fun semCallParentViewsOnClick(needToCall: Boolean) {
        needToCallParentViewsOnClick = needToCall
    }

    val isShowing: Boolean
        get() = bubblePopup?.isShowing == true || balloonPopup?.isShowing == true

    fun dismiss(withAnimation: Boolean) {
        val tipWindow = bubblePopup
        tipWindow?.setUseDismissAnimation(withAnimation)
        debugLog("bubblePopup.mIsDismissing = " + bubblePopup?.mIsDismissing)
        bubblePopup?.dismiss()
        balloonPopup?.setUseDismissAnimation(withAnimation)
        debugLog("balloonPopup.mIsDismissing = " + balloonPopup?.mIsDismissing)
        balloonPopup?.dismiss()
        handler?.removeCallbacksAndMessages(null)
        handler = null
    }

    fun setExpanded(expanded: Boolean) {
        if (expanded) {
            state = EXPANDED
            scaleMargin = 0
            return
        }
        scaleMargin = resources.getDimensionPixelSize(designR.dimen.oui_des_tip_popup_scale_margin)
    }

    fun setTargetPosition(x: Int, y: Int) {
        if (x < 0 || y < 0) {
            return
        }
        isDefaultPosition = false
        arrowPositionX = x
        arrowPositionY = y
    }

    fun setHintDescription(hintDescription: CharSequence?) {
        this@TipPopup.hintDescription = hintDescription
    }

    @JvmOverloads
    fun update(direction: Direction = arrowDirection, resetHintTimer: Boolean = false) {
        if (!isShowing/* || parentView == null*/) {
            return
        }
        setInternal()
        balloonX = -1
        balloonY = -1
        if (isDefaultPosition) {
            debugLog("update - default position")
            calculateArrowPosition()
        }
        if (direction == DEFAULT) {
            calculateArrowDirection(arrowPositionX, arrowPositionY)
        } else {
            arrowDirection = direction
        }
        calculatePopupSize()
        calculatePopupPosition()
        setBubblePanel()
        setBalloonPanel()

        if (state == HINT) {
            bubblePopup!!.update(bubblePopupX, bubblePopupY, bubblePopup!!.width, bubblePopup!!.height)
            if (resetHintTimer) {
                debugLog("Timer Reset!")
                scheduleTimeout()
            }
        } else if (state == EXPANDED) {
            balloonPopup!!.update(
                balloonPopupX,
                balloonPopupY,
                balloonPopup!!.width,
                balloonPopup!!.height
            )
        }
    }

    fun setOutsideTouchEnabled(enabled: Boolean) {
        bubblePopup!!.isFocusable = enabled
        bubblePopup!!.isOutsideTouchable = enabled
        balloonPopup!!.isFocusable = enabled
        balloonPopup!!.isOutsideTouchable = enabled
        debugLog("outside enabled : $enabled")
    }

    fun setPopupWindowClippingEnabled(enabled: Boolean) {
        bubblePopup!!.isClippingEnabled = enabled
        balloonPopup!!.isClippingEnabled = enabled
        forceRealDisplay = !enabled
        sideMargin = if (enabled) resources.getDimensionPixelSize(designR.dimen.oui_des_tip_popup_side_margin) else 0
        debugLog("clipping enabled : $enabled")
    }

    private fun setInternal() {
        if (handler == null) {
            handler = object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(message: Message) {
                    when (message.what) {
                        MSG_TIMEOUT -> dismissBubble(true)
                        MSG_DISMISS -> dismissBubble(false)
                        MSG_SCALE_UP -> animateScaleUp()
                    }
                }
            }
        }
        val currentFontScale = resources.configuration.fontScale
        val messageTextSize =
            resources.getDimensionPixelOffset(designR.dimen.oui_des_tip_popup_balloon_message_text_size)
        val actionTextSize = resources.getDimensionPixelOffset(designR.dimen.oui_des_tip_popup_balloon_action_text_size)
        if (currentFontScale > 1.2f) {
            messageView.setTextSize(COMPLEX_UNIT_PX, floor(ceil(messageTextSize / currentFontScale) * 1.2f))
            actionView.setTextSize(COMPLEX_UNIT_PX, floor(ceil(actionTextSize / currentFontScale) * 1.2f))
        }
        messageView.text = messageText
        if (TextUtils.isEmpty(actionText) || actionClickListener == null) {
            actionView.isVisible = false
            actionView.setOnClickListener(null)
            type = BALLOON_SIMPLE
        } else {
            actionView.isVisible = true
            actionView.text = actionText
            actionView.setOnClickListener { view ->
                actionClickListener?.onClick(view)
                dismiss(true)
            }
            type = BALLOON_ACTION
        }
        bubbleIcon?.contentDescription = hintDescription
        if (bubbleIcon == null || bubbleBackground == null || balloonBubble == null || balloonBg1 == null || balloonBg2 == null) return
        messageView.setTextColor(messageTextColor)
        actionView.setTextColor(actionTextColor)
        bubbleBackground!!.setColorFilter(backgroundColor)
        balloonBubbleHint!!.setColorFilter(backgroundColor)
        balloonBg1!!.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        balloonBg2!!.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        actionView.backgroundTintList = ColorStateList.valueOf(backgroundColor)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showInternal() {
        if (state != EXPANDED) {
            state = HINT
            onStateChangeListener?.onStateChanged(HINT)?.also {
                debugLog("mIsShowing : $isShowing")
            }

            bubblePopup?.showAtLocation(parentView, 0, bubblePopupX, bubblePopupY)?.also {
                animateViewIn()
            }

            bubbleView.setOnTouchListener { _, _ ->
                state = EXPANDED
                onStateChangeListener?.onStateChanged(state)
                balloonPopup?.showAtLocation(
                    parentView,
                    0,
                    balloonPopupX,
                    balloonPopupY
                )
                handler?.apply {
                    removeMessages(0)
                    sendMessageDelayed(Message.obtain(handler, 1), 10L)
                    sendMessageDelayed(Message.obtain(handler, 2), 20L)
                }
                false
            }
        } else {
            balloonBubble!!.isVisible = false
            balloonPanel!!.isVisible = true
            messageView.isVisible = true
            onStateChangeListener?.onStateChanged(state)
            balloonPopup?.showAtLocation(parentView, 0, balloonPopupX, balloonPopupY)
            animateBalloonScaleUp()
        }
        balloonView.setOnTouchListener(object : OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (type == BALLOON_SIMPLE) {
                    dismiss(true)
                    return false
                }
                return false
            }
        })
    }

    private fun setBubblePanel() {
        if (bubblePopup == null) {
            return
        }
        val paramBubblePanel = bubbleBackground!!.layoutParams as FrameLayout.LayoutParams
        when (arrowDirection) {
            TOP_LEFT -> {
                val tipWindow = bubblePopup
                tipWindow!!.setPivot(tipWindow.width.toFloat(), bubblePopup!!.height.toFloat())
                paramBubblePanel.gravity = 85
                val i = bubbleX
                val i2 = scaleMargin
                bubblePopupX = i - (i2 * 2)
                bubblePopupY = bubbleY - (i2 * 2)
                bubbleBackground!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_bg03)
                if (isRTL && locale != "iw_IL") {
                    bubbleIcon!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_icon_rtl)
                } else {
                    bubbleIcon!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_icon)
                }
            }

            TOP_RIGHT -> {
                val tipWindow2 = bubblePopup
                tipWindow2!!.setPivot(0.0f, tipWindow2.height.toFloat())
                paramBubblePanel.gravity = 83
                bubblePopupX = bubbleX
                bubblePopupY = bubbleY - (scaleMargin * 2)
                bubbleBackground!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_bg04)
                if (isRTL && locale != "iw_IL") {
                    bubbleIcon!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_icon_rtl)
                } else {
                    bubbleIcon!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_icon)
                }
            }

            BOTTOM_LEFT -> {
                val tipWindow3 = bubblePopup
                tipWindow3!!.setPivot(tipWindow3.width.toFloat(), 0.0f)
                paramBubblePanel.gravity = 53
                bubblePopupX = bubbleX - (scaleMargin * 2)
                bubblePopupY = bubbleY
                bubbleBackground!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_bg01)
                if (isRTL && locale != "iw_IL") {
                    bubbleIcon!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_icon_rtl)
                } else {
                    bubbleIcon!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_icon)
                }
            }

            BOTTOM_RIGHT -> {
                bubblePopup!!.setPivot(0.0f, 0.0f)
                paramBubblePanel.gravity = 51
                bubblePopupX = bubbleX
                bubblePopupY = bubbleY
                bubbleBackground!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_bg02)
                if (isRTL && locale != "iw_IL") {
                    bubbleIcon!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_icon_rtl)
                } else {
                    bubbleIcon!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_icon)
                }
            }

            DEFAULT -> {}
        }
        bubbleBackground!!.layoutParams = paramBubblePanel
        bubbleIcon!!.layoutParams = paramBubblePanel
        bubblePopup!!.width = bubbleWidth + (scaleMargin * 2)
        bubblePopup!!.height = bubbleHeight + (scaleMargin * 2)
    }

    private fun setBalloonPanel() {
        val scaleFactor: Int
        val f: Float
        val paramBalloonContent: FrameLayout.LayoutParams
        val f2: Float
        if (balloonPopup != null) {
            debugLog("setBalloonPanel()")
            val i = bubbleX
            val i2 = balloonX
            val leftMargin = i - i2
            val rightMargin = (i2 + balloonWidth) - i
            val i3 = bubbleY
            val i4 = balloonY
            val topMargin = i3 - i4
            val bottomMargin = (i4 + balloonHeight) - (i3 + bubbleHeight)
            val realMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(realMetrics)
            val scaleFactor2 = ceil(realMetrics.density.toDouble()).toInt()
            val minBackgroundWidth =
                resources.getDimensionPixelSize(designR.dimen.oui_des_tip_popup_balloon_background_minwidth)
            debugLog("leftMargin[$leftMargin]")
            debugLog("rightMargin[$rightMargin] balloonWidth[$balloonWidth]")
            val horizontalContentMargin =
                horizontalTextMargin - resources.getDimensionPixelSize(designR.dimen.oui_des_tip_popup_button_padding_horizontal)
            val verticalButtonPadding = if (actionView.visibility == 0) resources.getDimensionPixelSize(
                designR.dimen.oui_des_tip_popup_button_padding_vertical
            ) else 0
            val paramBalloonBubble = balloonBubble!!.layoutParams as FrameLayout.LayoutParams
            val paramBalloonPanel = balloonPanel!!.layoutParams as FrameLayout.LayoutParams
            val paramBalloonContent2 = balloonContent!!.layoutParams as FrameLayout.LayoutParams
            val paramBalloonBg1 = balloonBg1!!.layoutParams as FrameLayout.LayoutParams
            val paramBalloonBg2 = balloonBg2!!.layoutParams as FrameLayout.LayoutParams
            if (Color.alpha(backgroundColor) < 255) {
                debugLog("Updating scaleFactor to 0 because transparency is applied to background.")
                scaleFactor = 0
            } else {
                scaleFactor = scaleFactor2
            }
            when (arrowDirection) {
                TOP_LEFT -> {
                    val tipWindow = balloonPopup
                    val i5 = arrowPositionX - balloonX
                    val i6 = scaleMargin
                    tipWindow!!.setPivot((i5 + i6).toFloat(), (balloonHeight + i6).toFloat())
                    balloonBubbleHint!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_bg03)
                    balloonBubbleIcon!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_icon)
                    f = 180.0f
                    balloonBg1!!.rotationX = f
                    balloonBg2!!.rotationX = f
                    paramBalloonBg2.gravity = 85
                    paramBalloonBg1.gravity = 85
                    paramBalloonBubble.gravity = 85
                    val i7 = bubbleWidth
                    if (rightMargin - i7 < minBackgroundWidth) {
                        val scaledLeftMargin = balloonWidth - minBackgroundWidth
                        paramBalloonBg1.setMargins(0, 0, minBackgroundWidth, 0)
                        paramBalloonBg2.setMargins(scaledLeftMargin - scaleFactor, 0, 0, 0)
                        debugLog("Right Margin is less then minimum background width!")
                        debugLog("updated !! leftMargin[$scaledLeftMargin],  rightMargin[$minBackgroundWidth]")
                    } else {
                        paramBalloonBg1.setMargins(0, 0, rightMargin - i7, 0)
                        paramBalloonBg2.setMargins((bubbleWidth + leftMargin) - scaleFactor, 0, 0, 0)
                    }
                    val i8 = verticalTextMargin
                    paramBalloonContent = paramBalloonContent2
                    paramBalloonContent.setMargins(
                        horizontalContentMargin,
                        i8,
                        horizontalContentMargin,
                        (arrowHeight + i8) - verticalButtonPadding
                    )
                }

                TOP_RIGHT -> {
                    val tipWindow2 = balloonPopup
                    val i9 = arrowPositionX - balloonX
                    val i10 = scaleMargin
                    tipWindow2!!.setPivot((i9 + i10).toFloat(), (balloonHeight + i10).toFloat())
                    balloonBubbleHint!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_bg04)
                    balloonBubbleIcon!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_icon)
                    f2 = 180.0f
                    balloonBg1!!.rotation = f2
                    balloonBg2!!.rotation = f2
                    paramBalloonBg2.gravity = 83
                    paramBalloonBg1.gravity = 83
                    paramBalloonBubble.gravity = 83
                    if (leftMargin < minBackgroundWidth) {
                        val scaledRightMargin = balloonWidth - minBackgroundWidth
                        paramBalloonBg1.setMargins(minBackgroundWidth, 0, 0, 0)
                        paramBalloonBg2.setMargins(0, 0, scaledRightMargin - scaleFactor, 0)
                        debugLog("Left Margin is less then minimum background width!")
                        debugLog("updated !! leftMargin[$minBackgroundWidth],  rightMargin[]")
                    } else {
                        paramBalloonBg1.setMargins(leftMargin, 0, 0, 0)
                        paramBalloonBg2.setMargins(0, 0, rightMargin - scaleFactor, 0)
                    }
                    val i11 = verticalTextMargin
                    paramBalloonContent = paramBalloonContent2
                    paramBalloonContent.setMargins(
                        horizontalContentMargin,
                        i11,
                        horizontalContentMargin,
                        (arrowHeight + i11) - verticalButtonPadding
                    )
                }

                BOTTOM_LEFT -> {
                    val tipWindow3 = balloonPopup
                    val i12 = arrowPositionX - balloonX
                    val i13 = scaleMargin
                    tipWindow3!!.setPivot((i12 + i13).toFloat(), i13.toFloat())
                    balloonBubbleHint!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_bg01)
                    balloonBubbleIcon!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_icon)
                    paramBalloonBg2.gravity = 53
                    paramBalloonBg1.gravity = 53
                    paramBalloonBubble.gravity = 53
                    paramBalloonBg1.setMargins(0, 0, rightMargin - bubbleWidth, 0)
                    paramBalloonBg2.setMargins((bubbleWidth + leftMargin) - scaleFactor, 0, 0, 0)
                    val i14 = arrowHeight
                    val i15 = verticalTextMargin
                    paramBalloonContent2.setMargins(
                        horizontalContentMargin,
                        i14 + i15,
                        horizontalContentMargin,
                        i15 - verticalButtonPadding
                    )
                    paramBalloonContent = paramBalloonContent2
                }

                BOTTOM_RIGHT -> {
                    val tipWindow4 = balloonPopup
                    val i16 = arrowPositionX - balloonX
                    val i17 = scaleMargin
                    tipWindow4!!.setPivot((i16 + i17).toFloat(), i17.toFloat())
                    balloonBubbleHint!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_bg02)
                    balloonBubbleIcon!!.setImageResource(designR.drawable.oui_des_tip_popup_hint_icon)
                    balloonBg1!!.rotationY = 180.0f
                    balloonBg2!!.rotationY = 180.0f
                    paramBalloonBg2.gravity = 51
                    paramBalloonBg1.gravity = 51
                    paramBalloonBubble.gravity = 51
                    paramBalloonBg1.setMargins(leftMargin, 0, 0, 0)
                    paramBalloonBg2.setMargins(0, 0, rightMargin - scaleFactor, 0)
                    val i18 = arrowHeight
                    val i19 = verticalTextMargin
                    paramBalloonContent2.setMargins(
                        horizontalContentMargin,
                        i18 + i19,
                        horizontalContentMargin,
                        i19 - verticalButtonPadding
                    )
                    paramBalloonContent = paramBalloonContent2
                }

                else -> paramBalloonContent = paramBalloonContent2
            }
            val i20 = scaleMargin
            paramBalloonBubble.setMargins(
                leftMargin + i20,
                topMargin + i20,
                (rightMargin - bubbleWidth) + i20,
                bottomMargin + i20
            )
            val balloonPanelMargin = scaleMargin
            paramBalloonPanel.setMargins(
                balloonPanelMargin,
                balloonPanelMargin,
                balloonPanelMargin,
                balloonPanelMargin
            )
            val i21 = balloonX
            val i22 = scaleMargin
            balloonPopupX = i21 - i22
            balloonPopupY = balloonY - i22
            balloonBubble!!.layoutParams = paramBalloonBubble
            balloonPanel!!.layoutParams = paramBalloonPanel
            balloonBg1!!.layoutParams = paramBalloonBg1
            balloonBg2!!.layoutParams = paramBalloonBg2
            balloonContent!!.layoutParams = paramBalloonContent
            balloonPopup!!.width = balloonWidth + (scaleMargin * 2)
            balloonPopup!!.height = balloonHeight + (scaleMargin * 2)
        }
    }

    private fun calculateArrowDirection(arrowX: Int, arrowY: Int) {
        if (isDefaultPosition) {
            val location = IntArray(2)
            parentView.getLocationInWindow(location)
            val parentY = location[1] + (parentView.height / 2)

            arrowDirection = if (arrowX * 2 <= displayMetrics.widthPixels) {
                if (arrowY <= parentY) {
                    TOP_RIGHT
                } else {
                    BOTTOM_RIGHT
                }
            } else if (arrowY <= parentY) {
                TOP_LEFT
            } else {
                BOTTOM_LEFT
            }
        } else if (arrowX * 2 <= displayMetrics.widthPixels && arrowY * 2 <= displayMetrics.heightPixels) {
            arrowDirection = BOTTOM_RIGHT
        } else if (arrowX * 2 > displayMetrics.widthPixels && arrowY * 2 <= displayMetrics.heightPixels) {
            arrowDirection = TOP_LEFT
        } else if (arrowX * 2 <= displayMetrics.widthPixels && arrowY * 2 > displayMetrics.heightPixels) {
            arrowDirection = TOP_RIGHT
        } else if (arrowX * 2 > displayMetrics.widthPixels && arrowY * 2 > displayMetrics.heightPixels) {
            arrowDirection = TOP_LEFT
        }
        debugLog("calculateArrowDirection : arrow position ($arrowX, $arrowY) / arrowDirection = $arrowDirection")
    }

    private fun calculateArrowPosition() {
        val location = IntArray(2)
        parentView.getLocationInWindow(location)
        debugLog("calculateArrowPosition anchor location : " + location[0] + ", " + location[1])

        arrowPositionX = location[0] + (parentView.width / 2)
        val y = location[1] + (parentView.height / 2)
        arrowPositionY = if (y * 2 <= displayMetrics.heightPixels) {
            (parentView.height / 2) + y
        } else {
            y - (parentView.height / 2)
        }
        debugLog("calculateArrowPosition mArrowPosition : $arrowPositionX, $arrowPositionY")
    }

    private fun calculatePopupSize() {
        displayMetrics = resources.displayMetrics

        val balloonMaxWidth = if (DeviceLayoutUtil.isDeskTopMode(resources)) {
            val windowWidthInDexMode = parentView.rootView.run {
                val windowLocation = IntArray(2)
                getLocationOnScreen(windowLocation)
                measuredWidth + minOf(windowLocation[0], 0)
            }
            debugLog("Window width in DexMode $windowWidthInDexMode")
            when {
                windowWidthInDexMode <= 480 -> (windowWidthInDexMode * 0.83f).toInt()
                windowWidthInDexMode <= 960 -> (windowWidthInDexMode * 0.6f).toInt()
                windowWidthInDexMode <= 1280 -> (windowWidthInDexMode * 0.45f).toInt()
                else -> (windowWidthInDexMode * 0.25f).toInt()
            }
        } else {
            val screenWidthDp = resources.configuration.screenWidthDp
            val screenWidthPixels = displayMetrics.widthPixels

            debugLog("screen width DP $screenWidthDp")
            when {
                screenWidthDp <= 480 -> (screenWidthPixels * 0.83f).toInt()
                screenWidthDp <= 960 -> (screenWidthPixels * 0.6f).toInt()
                screenWidthDp <= 1280 -> (screenWidthPixels * 0.45f).toInt()
                else -> (screenWidthPixels * 0.25f).toInt()
            }
        }

        if (!isMessageViewMeasured) {
            initialMessageViewWidth = messageView.run {
                measure(0, 0)
                measuredWidth
            }
            isMessageViewMeasured = true
        }

        val balloonMinWidth = arrowWidth + (horizontalTextMargin * 2)

        balloonWidth = (initialMessageViewWidth + (horizontalTextMargin * 2))
            .coerceIn(balloonMinWidth, balloonMaxWidth)

        balloonHeight = messageView.run {
            width = balloonWidth - (horizontalTextMargin * 2)
            measure(0, 0)
            measuredHeight + (verticalTextMargin * 2) + arrowHeight
        }

        if (type == BALLOON_ACTION) {
            actionView.apply {
                measure(UNSPECIFIED, UNSPECIFIED)
                balloonWidth = balloonWidth.coerceAtLeast(
                    measuredWidth + (resources.getDimensionPixelSize(designR.dimen.oui_des_tip_popup_button_padding_horizontal) * 2)
                )
                balloonHeight += (measuredHeight - resources.getDimensionPixelSize(designR.dimen.oui_des_tip_popup_button_padding_vertical))
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun calculatePopupPosition() {
        getDisplayFrame(displayFrame)

        if (balloonX < 0) {
            balloonX = if (arrowDirection == BOTTOM_RIGHT || arrowDirection == TOP_RIGHT) {
                (arrowPositionX + arrowWidth) - (balloonWidth / 2)
            } else {
                (arrowPositionX - arrowWidth) - (balloonWidth / 2)
            }
        }

        arrowPositionX = if (arrowDirection == BOTTOM_RIGHT || arrowDirection == TOP_RIGHT) {
            arrowPositionX.coerceIn(
                displayFrame.left + sideMargin + horizontalTextMargin,
                (displayFrame.right - sideMargin) - horizontalTextMargin - arrowWidth
            )
        } else {
            arrowPositionX.coerceIn(
                displayFrame.left + sideMargin + horizontalTextMargin + arrowWidth,
                (displayFrame.right - sideMargin) - horizontalTextMargin
            )
        }

        balloonX = if (SeslConfigurationReflector.isDexEnabled(context.resources.configuration)) {
            val windowLocation = IntArray(2)
            val windowWidthInDexMode = parentView.rootView.run {
                getLocationOnScreen(windowLocation)
                if (windowLocation[0] < 0) {
                    measuredWidth + windowLocation[0]
                } else {
                    measuredWidth
                }
            }
            balloonX.coerceIn(
                displayFrame.left + sideMargin,
                (windowWidthInDexMode - sideMargin) - balloonWidth - minOf(windowLocation[0], 0)
            )
        } else {
            balloonX.coerceIn(
                displayFrame.left + sideMargin,
                (displayFrame.right - sideMargin) - balloonWidth
            )
        }

        when (arrowDirection) {
            TOP_LEFT -> {
                bubbleX = arrowPositionX - bubbleWidth
                bubbleY = arrowPositionY - bubbleHeight
                balloonY = arrowPositionY - balloonHeight
            }

            TOP_RIGHT -> {
                bubbleX = arrowPositionX
                bubbleY = arrowPositionY - bubbleHeight
                balloonY = arrowPositionY - balloonHeight
            }

            BOTTOM_LEFT -> {
                bubbleX = arrowPositionX - bubbleWidth
                bubbleY = arrowPositionY
                balloonY = arrowPositionY
            }

            BOTTOM_RIGHT -> {
                bubbleX = arrowPositionX
                bubbleY = arrowPositionY
                balloonY = arrowPositionY
            }

            DEFAULT -> Unit
        }

        debugLog("QuestionPopup : $bubbleX, $bubbleY, $bubbleWidth, $bubbleHeight")
        debugLog("BalloonPopup : $balloonX, $balloonY, $balloonWidth, $balloonHeight")
    }

    private fun dismissBubble(withAnimation: Boolean) {
        bubblePopup?.apply {
            setUseDismissAnimation(withAnimation)
            dismiss()
        }
    }

    private fun scheduleTimeout() {
        handler?.apply {
            removeMessages(0)
            sendMessageDelayed(Message.obtain(this, 0), TIMEOUT_DURATION_MS)
        }
    }

    private fun animateViewIn() {
        val pivotX: Float
        val pivotY: Float
        when (arrowDirection) {
            TOP_LEFT -> {
                pivotX = 1.0f
                pivotY = 1.0f
            }

            TOP_RIGHT -> {
                pivotX = 0.0f
                pivotY = 1.0f
            }

            BOTTOM_LEFT -> {
                pivotX = 1.0f
                pivotY = 0.0f
            }

            BOTTOM_RIGHT,
            DEFAULT,
                -> {
                pivotX = 0.0f
                pivotY = 0.0f
            }
        }

        val animScale = ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f, 1, pivotX, 1, pivotY).apply {
            interpolator = INTERPOLATOR_ELASTIC_50
            duration = 500L
            doOnEnd {
                scheduleTimeout()
                animateBounce()
            }
        }
        bubbleView.startAnimation(animScale)
    }

    private fun animateBounce() {
        val pivotX: Float
        val pivotY: Float
        when (arrowDirection) {
            TOP_LEFT -> {
                pivotX = bubblePopup!!.width.toFloat()
                pivotY = bubblePopup!!.height.toFloat()
            }

            TOP_RIGHT -> {
                pivotX = 0.0f
                pivotY = bubblePopup!!.height.toFloat()
            }

            BOTTOM_LEFT -> {
                pivotX = bubblePopup!!.width.toFloat()
                pivotY = 0.0f
            }

            BOTTOM_RIGHT, DEFAULT -> {
                pivotX = 0.0f
                pivotY = 0.0f
            }
        }

        val animationSet = AnimationSet(false)

        val scaleAnimation1 = ScaleAnimation(1.0f, 1.2f, 1.0f, 1.2f, 0, pivotX, 0, pivotY).apply {
            duration = ANIMATION_DURATION_BOUNCE_SCALE1
            interpolator = INTERPOLATOR_SINE_IN_OUT_70
        }

        val scaleAnimation2 = ScaleAnimation(1.0f, 0.833f, 1.0f, 0.833f, 0, pivotX, 0, pivotY).apply {
            startOffset = ANIMATION_DURATION_BOUNCE_SCALE1
            duration = ANIMATION_DURATION_BOUNCE_SCALE2
            interpolator = INTERPOLATOR_SINE_IN_OUT_33
            var count = 0
            setListener(
                onStart = { count++ },
                onEnd = {
                    debugLog("repeat count $count")
                    bubbleView.startAnimation(animationSet)
                }
            )
        }

        animationSet.addAnimation(scaleAnimation1)
        animationSet.addAnimation(scaleAnimation2)
        animationSet.startOffset = ANIMATION_OFFSET_BOUNCE_SCALE
        bubbleView.startAnimation(animationSet)
    }

    private fun animateScaleUp() {
        val deltaHintY: Float
        val pivotHintX: Float
        val pivotHintY: Float
        when (arrowDirection) {
            TOP_LEFT -> {
                pivotHintX = balloonBubble!!.width.toFloat()
                pivotHintY = balloonBubble!!.height.toFloat()
                deltaHintY = 0.0f - (arrowHeight / 2.0f)
            }

            TOP_RIGHT -> {
                pivotHintX = 0.0f
                pivotHintY = balloonBubble!!.height.toFloat()
                deltaHintY = 0.0f - (arrowHeight / 2.0f)
            }

            BOTTOM_LEFT -> {
                pivotHintX = balloonBubble!!.width.toFloat()
                pivotHintY = 0.0f
                deltaHintY = arrowHeight / 2.0f
            }

            BOTTOM_RIGHT -> {
                pivotHintX = 0.0f
                pivotHintY = 0.0f
                deltaHintY = arrowHeight / 2.0f
            }

            DEFAULT -> {
                pivotHintX = 0.0f
                pivotHintY = 0.0f
                deltaHintY = arrowHeight / 2.0f
            }
        }

        val animationBubble = AnimationSet(false).also {
            it.addAnimation(
                TranslateAnimation(0, 0.0f, 0, 0.0f, 0, 0.0f, 0, deltaHintY).apply {
                    duration = ANIMATION_DURATION_EXPAND_SCALE
                    interpolator = INTERPOLATOR_ELASTIC_CUSTOM
                })
            it.addAnimation(
                ScaleAnimation(1.0f, 1.5f, 1.0f, 1.5f, 0, pivotHintX, 0, pivotHintY).apply {
                    duration = ANIMATION_DURATION_EXPAND_SCALE
                    interpolator = INTERPOLATOR_ELASTIC_50
                }
            )
            it.addAnimation(
                AlphaAnimation(0.0f, 1.0f).apply {
                    duration = ANIMATION_DURATION_EXPAND_TEXT
                    interpolator = INTERPOLATOR_SINE_IN_OUT_70
                }
            )
            it.setListener(
                onStart = { balloonPanel!!.isVisible = true },
                onEnd = { balloonBubble!!.isVisible = false }
            )
        }

        balloonBubble!!.startAnimation(animationBubble)
        animateBalloonScaleUp()
    }

    private fun animateBalloonScaleUp() {
        val pivotPanelX: Float
        val pivotPanelY: Float
        val questionHeight = resources.getDimensionPixelSize(designR.dimen.oui_des_tip_popup_bubble_height)
        val panelScale = (questionHeight / balloonHeight).toFloat()

        when (arrowDirection) {
            TOP_RIGHT -> {
                pivotPanelX = (arrowPositionX - balloonX).toFloat()
                pivotPanelY = balloonHeight.toFloat()
            }

            BOTTOM_LEFT -> {
                pivotPanelX = (arrowPositionX - balloonX).toFloat()
                pivotPanelY = 0.0f
            }

            BOTTOM_RIGHT -> {
                pivotPanelX = (bubbleX - balloonX).toFloat()
                pivotPanelY = 0.0f
            }

            else -> {
                pivotPanelX = 0.0f
                pivotPanelY = 0.0f
            }
        }

        val animationPanel = AnimationSet(false).apply {
            addAnimation(
                ScaleAnimation(0.32f, 1.0f, panelScale, 1.0f, 0, pivotPanelX, 0, pivotPanelY).apply {
                    interpolator = INTERPOLATOR_ELASTIC_CUSTOM
                    duration = ANIMATION_DURATION_SHOW_SCALE
                }
            )
            addAnimation(
                AlphaAnimation(0.0f, 1.0f).apply {
                    interpolator = INTERPOLATOR_SINE_IN_OUT_70
                    duration = ANIMATION_DURATION_EXPAND_ALPHA
                }
            )
        }
        balloonPanel!!.startAnimation(animationPanel)

        val animationText: Animation = AlphaAnimation(0.0f, 1.0f).apply {
            interpolator = INTERPOLATOR_SINE_IN_OUT_33
            startOffset = ANIMATION_OFFSET_EXPAND_TEXT
            duration = ANIMATION_DURATION_EXPAND_TEXT
            setListener(
                onStart = { messageView.isVisible = true },
                onEnd = { dismissBubble(false) }
            )
        }
        messageView.startAnimation(animationText)
        actionView.startAnimation(animationText)
    }

    private val isNavigationBarHide: Boolean
        get() = Settings.Global.getInt(context.contentResolver, "navigationbar_hide_bar_enabled", 0) == 1

    private val navigationBarHeight: Int get() = DeviceLayoutUtil.getNavigationBarHeight(resources)

    private val isTablet: Boolean get() = DeviceLayoutUtil.isTabletLayout(resources)

    private fun getDisplayFrame(screenRect: Rect) {
        //var displayCutout: DisplayCutout
        val navigationBarHeight = navigationBarHeight
        val navigationBarHide = isNavigationBarHide

        @Suppress("DEPRECATION")
        val displayRotation = windowManager.defaultDisplay.rotation

        val realMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(realMetrics)

        debugLog("realMetrics = $realMetrics")
        debugLog("is tablet? = $isTablet")

        if (forceRealDisplay) {
            screenRect.left = 0
            screenRect.top = 0
            screenRect.right = realMetrics.widthPixels
            screenRect.bottom = realMetrics.heightPixels
            debugLog("Screen Rect = $screenRect forceRealDisplay = $forceRealDisplay")
            return
        }
        screenRect.left = 0
        screenRect.top = 0
        screenRect.right = displayMetrics.widthPixels
        screenRect.bottom = displayMetrics.heightPixels

        val bounds = Rect()
        parentView.rootView!!.getWindowVisibleDisplayFrame(bounds)
        debugLog("Bounds = $bounds")
        if (isTablet) {
            debugLog("tablet")
            if ((realMetrics.widthPixels == displayMetrics.widthPixels)
                && (realMetrics.heightPixels - displayMetrics.heightPixels == navigationBarHeight)
                && navigationBarHide
            ) {
                screenRect.bottom += navigationBarHeight
            }
        } else {
            debugLog("phone")
            when (displayRotation) {
                ROTATION_0 -> {
                    if (realMetrics.widthPixels == displayMetrics.widthPixels
                        && realMetrics.heightPixels - displayMetrics.heightPixels == navigationBarHeight
                        && navigationBarHide
                    ) {
                        screenRect.bottom += navigationBarHeight
                    }
                }

                ROTATION_90 -> {
                    if (realMetrics.heightPixels == displayMetrics.heightPixels
                        && realMetrics.widthPixels - displayMetrics.widthPixels == navigationBarHeight
                        && navigationBarHide
                    ) {
                        screenRect.right += navigationBarHeight
                    }
                    val windowInsets = ViewCompat.getRootWindowInsets(parentView)
                    if (windowInsets != null && SDK_INT >= Build.VERSION_CODES.P
                        && windowInsets.displayCutout != null
                    ) {
                        with(windowInsets.displayCutout!!) {
                            screenRect.left += safeInsetLeft
                            screenRect.right += safeInsetLeft
                            debugLog("displayCutout.getSafeInsetLeft() :  $safeInsetLeft")
                        }
                    }
                }

                ROTATION_180 -> {
                    if (realMetrics.widthPixels == displayMetrics.widthPixels
                        && realMetrics.heightPixels - displayMetrics.heightPixels == navigationBarHeight
                    ) {
                        if (navigationBarHide) {
                            screenRect.bottom += navigationBarHeight
                        } else {
                            screenRect.top += navigationBarHeight
                            screenRect.bottom += navigationBarHeight
                        }
                    } else if (realMetrics.widthPixels == displayMetrics.widthPixels && bounds.top == navigationBarHeight) {
                        debugLog("Top Docked")
                        screenRect.top += navigationBarHeight
                        screenRect.bottom += navigationBarHeight
                    }
                }

                ROTATION_270 -> {
                    if (realMetrics.heightPixels == displayMetrics.heightPixels
                        && realMetrics.widthPixels - displayMetrics.widthPixels == navigationBarHeight
                    ) {
                        if (navigationBarHide) {
                            screenRect.right += navigationBarHeight
                        } else {
                            screenRect.left += navigationBarHeight
                            screenRect.right += navigationBarHeight
                        }
                    } else if (realMetrics.heightPixels == displayMetrics.heightPixels && bounds.left == navigationBarHeight) {
                        debugLog("Left Docked")
                        screenRect.left += navigationBarHeight
                        screenRect.right += navigationBarHeight
                    }
                }
            }
        }
        debugLog("Screen Rect = $screenRect")
    }

    fun setOnDismissListener(onDismissListener: OnDismissListener?) {
        this@TipPopup.onDismissListener = onDismissListener
    }

    open class TipWindow(
        contentView: View,
        width: Int,
        height: Int,
        focusable: Boolean,
    ) : PopupWindow(contentView, width, height, focusable) {
        var mIsDismissing = false
        private var mIsUsingDismissAnimation = true
        protected var mPivotX = 0.0f
        protected var mPivotY = 0.0f

        fun setUseDismissAnimation(useAnimation: Boolean) {
            mIsUsingDismissAnimation = useAnimation
        }

        fun setPivot(pivotX: Float, pivotY: Float) {
            mPivotX = pivotX
            mPivotY = pivotY
        }

        override fun dismiss() {
            if (mIsUsingDismissAnimation && !mIsDismissing) {
                animateViewOut()
            } else {
                super.dismiss()
            }
        }

        fun dismissFinal() = super.dismiss()

        open fun animateViewOut() {}
    }

    private class TipWindowBubble(contentView: View, width: Int, height: Int, focusable: Boolean) :
        TipWindow(contentView, width, height, focusable) {

        override fun animateViewOut() {
            val animationSet = AnimationSet(true).apply {
                addAnimation(
                    ScaleAnimation(1.0f, 0.81f, 1.0f, 0.81f, 0, mPivotX, 0, mPivotY).apply {
                        interpolator = INTERPOLATOR_ELASTIC_CUSTOM
                        duration = ANIMATION_DURATION_DISMISS_SCALE
                    })
                addAnimation(
                    AlphaAnimation(1.0f, 0.0f).apply {
                        interpolator = INTERPOLATOR_SINE_IN_OUT_33
                        duration = ANIMATION_DURATION_DISMISS_ALPHA
                    })
                setListener(
                    onStart = { mIsDismissing = true },
                    onEnd = { dismissFinal() }
                )
            }
            contentView.startAnimation(animationSet)
        }
    }


    private class TipWindowBalloon(contentView: View, width: Int, height: Int, focusable: Boolean) :
        TipWindow(contentView, width, height, focusable) {

        override fun animateViewOut() {
            val messageView = contentView.findViewById<View>(designR.id.oui_des_tip_popup_message)

            val animAlpha = AlphaAnimation(1.0f, 0.0f).apply {
                duration = ANIMATION_DURATION_EXPAND_SCALE
            }

            val animationSet = AnimationSet(true).apply {
                addAnimation(
                    ScaleAnimation(1.0f, 0.32f, 1.0f, 0.32f, 0, mPivotX, 0, mPivotY).apply {
                        duration = ANIMATION_DURATION_DISMISS_SCALE
                        interpolator = INTERPOLATOR_ELASTIC_CUSTOM
                    }
                )
                addAnimation(animAlpha)
                setListener(
                    onStart = { mIsDismissing = true },
                    onEnd = { dismissFinal() }
                )
            }
            contentView.startAnimation(animationSet)
            messageView.startAnimation(animAlpha)
        }
    }

    private val isRTL: Boolean get() = context.resources.configuration.layoutDirection == 1

    private val locale: String get() = ConfigurationCompat.getLocales(context.resources.configuration).get(0).toString()

    private fun debugLog(msg: String) {
        Log.d(TAG, " #### $msg")
    }

    fun semGetBubblePopupWindow(): PopupWindow? = bubblePopup

    fun semGetBalloonPopupWindow(): PopupWindow? = balloonPopup

    companion object {
        private const val ANIMATION_DURATION_BOUNCE_SCALE1 = 167L
        private const val ANIMATION_DURATION_BOUNCE_SCALE2 = 250L
        private const val ANIMATION_DURATION_DISMISS_ALPHA = 167L
        private const val ANIMATION_DURATION_DISMISS_SCALE = 167L
        private const val ANIMATION_DURATION_EXPAND_ALPHA = 83L
        private const val ANIMATION_DURATION_EXPAND_SCALE = 500L
        private const val ANIMATION_DURATION_EXPAND_TEXT = 167L
        private const val ANIMATION_DURATION_SHOW_SCALE = 500L
        private const val ANIMATION_OFFSET_BOUNCE_SCALE = 3000L
        private const val ANIMATION_OFFSET_EXPAND_TEXT = 333L

        private const val MSG_DISMISS = 1
        private const val MSG_SCALE_UP = 2
        private const val MSG_TIMEOUT = 0
        private const val TAG = "SemTipPopup"
        private const val TIMEOUT_DURATION_MS = 7100L
        private var handler: Handler? = null
        private var INTERPOLATOR_SINE_IN_OUT_33: Interpolator? = null
        private var INTERPOLATOR_SINE_IN_OUT_70: Interpolator? = null
        private var INTERPOLATOR_ELASTIC_50: Interpolator? = null
        private var INTERPOLATOR_ELASTIC_CUSTOM: Interpolator? = null
    }
}