/*
 * Copyright (c) 2017-2020 DarkCompet. All rights reserved.
 */
package tool.compet.compassview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import tool.compet.compassview.DkCompassHelper.calcDisplayAngle
import tool.compet.compassview.DkCompassHelper.newArrowAt
import tool.compet.compassview.DkCompassHelper.point2degrees
import tool.compet.core.DkConfig
import tool.compet.core.DkLogcats
import tool.compet.core.DkMaths
import tool.compet.core.sizeDk
import tool.compet.gesturedetector.*
import tool.compet.graphics.DkColors
import tool.compet.stream.DkObservable
import tool.compet.view.DkViews
import java.util.*
import kotlin.math.max
import kotlin.math.min

class DkCompassView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), OnTouchListener {

	private val controller = CompassController()
	private val gestureDetector: DkGestureDetector
	private val countdownAnimator = ValueAnimator()
	var listener: Listener? = null

	var mode = MODE_NORMAL
	private val tmpRect = Rect()

	// Board
	private var boardWidth = 0
	private var boardHeight = 0
	private var oldBoardWidth = 0
	private var oldBoardHeight = 0
	private var boardCenterX = 0
	private var boardCenterY = 0
	private var boardInnerRadius = 0

	// Compass
	private var compass: Bitmap? = null
	private var isRequestBuildCompass = false
	private var isBuildingCompass = false
	private var isFitCompassInsideBoard = false
	private var isBoardSizeChanged = false
	var compassColor = 0
		private set
	var compassSemiColor = 0
		private set
	private val compassBitmapMatrix = Matrix()
	private var compassCx = 0f
	private var compassCy = 0f
	private var compassDegreesInNormalMode = 0f
	private var compassDegreesInRotateMode = 0f
	private var compassDegreesInPointMode = 0f
	private var compassBitmapZoomLevel = 1f
	private var isCompassMovable = true
	private var nextAnimateDegrees = 0f
	private var lastAnimatedDegrees = 0f
	private lateinit var buildCompassRings: List<DkCompassRing>
	private lateinit var buildCompassLocale: Locale
	private val compassPaint: Paint
	private val linePaint: Paint
	private val fillPaint: Paint
	private val textPaint: Paint

	/**
	 * North, East, South, West poles.
	 * Array contains 4 elements, for eg,. ['N', 'E', 'S', 'W']
	 */
	private var poleShortNames = arrayOf(context.getString(R.string.compass_north_short), context.getString(R.string.compass_east_short), context.getString(R.string.compass_south_short), context.getString(R.string.compass_west_short))

	/**
	 * Contains 4 elements, for eg,. ['North', 'East', 'South', 'West']
	 */
	private var poleLongNames = arrayOf(context.getString(R.string.north), context.getString(R.string.east), context.getString(R.string.south), context.getString(R.string.west))

	// 24 lines
	var isShow24PointerLines = false

	// Rotator (circle + arrow)
	private var handlerColor = 0
	var isShowRotator = true
	private var distFromRotatorCenterToBoardCenter = 0f
	private var rotatorRadius = 0.0
	private var rotatorRotatedDegrees = 0.0
	private var isTouchInsideRotator = false
	private var rotatorDisabledCountDown: Long = 800
	private var rotationFactor = 0.1f

	// Pointer
	private var pointerDegrees = 0f
	var isShowPointer = true
	private var pointerStopY = 0f

	// Count down for centerize compass
	private var hasCmpCenterLeftCountDown = false

	// Navigation Ox, Oy
	private var naviArrowStartY = 0
	private var naviArrowStopY = 0
	private var naviArrow: Path? = null

	// Touch event
	private var touchStartDegrees = 0f
	private var lastStopTime: Long = 0
	private var isAdjustCompass = false

	// Highlight, move compass
	private val MSG_ALLOW_MOVE_COMPASS = 1
	private val MSG_TURN_OFF_HIGH_LIGHT = 2
	private val handler = object : Handler(Looper.getMainLooper()) {
		override fun handleMessage(msg: Message) {
			when (msg.what) {
				MSG_ALLOW_MOVE_COMPASS -> {
					isCompassMovable = true
				}
				MSG_TURN_OFF_HIGH_LIGHT -> {
					handlerColor = compassSemiColor
					invalidate()
				}
			}
		}
	}
	private val gestureDetectorListener: DkGestureDetector.Listener = object : DkGestureDetector.Listener {
		override fun onTap(detector: TheTapDetector): Boolean {
			return false
		}

		override fun onFly(detector: TheFlyDetector): Boolean {
			return false
		}

		override fun onDoubleTap(detector: TheDoubleDetector): Boolean {
			return false
		}

		override fun onDrag(detector: TheDragDetector): Boolean {
			if (!isAdjustCompass) {
				return true
			}
			if (isCompassMovable && !isTouchInsideRotator) {
				if (isShouldStartCountDown) {
					if (!countdownAnimator.isRunning) {
						countdownAnimator.start()
					}
				}
				else {
					postTranslateCompassMatrix(detector.dx().toDouble(), detector.dy().toDouble())
				}
				invalidate()
				return true
			}
			return false
		}

		override fun onScale(detector: TheScaleDetector): Boolean {
			if (!isAdjustCompass) {
				return true
			}
			if (!isTouchInsideRotator) {
				val scaleFactor = detector.scaleFactor()
				compassBitmapZoomLevel += (scaleFactor - 1.0).toFloat()
				compassBitmapMatrix.postScale(scaleFactor, scaleFactor, compassCx, compassCy)
				invalidate()
				return true
			}
			return false
		}

		override fun onRotate(detector: TheRotateDetector): Boolean {
			if (!isAdjustCompass) {
				return true
			}
			if (isTouchInsideRotator) {
				postRotateCompassMatrix(lastAnimatedDegrees + detector.rotation())
				invalidate()
				return true
			}
			return false
		}
	}

	companion object {
		// Compass modes: normal, rotator and pointer.
		const val MODE_NORMAL = 0
		const val MODE_ROTATE = 1
		const val MODE_POINT = 2
		private val DEFAULT_WORD_TEXT_SIZE = 12 * DkConfig.scaledDensity()
	}

	init {
		countdownAnimator.duration = 1000
		compassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			this.colorFilter = PorterDuffColorFilter(compassColor, PorterDuff.Mode.SRC_ATOP)
		}
		linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			this.strokeWidth = 3f
			this.style = Paint.Style.STROKE
		}
		textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			this.textSize = DEFAULT_WORD_TEXT_SIZE
		}
		fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			this.strokeWidth = 3f
			this.style = Paint.Style.FILL_AND_STROKE
		}
		gestureDetector = DkGestureDetector(context, gestureDetectorListener).apply {
			this.disableGestures(DkGestureDetector.FLAG_DOUBLE_TAP)
		}
		setOnTouchListener(this)
	}

	interface Listener {
		fun onRotatorElapsed(delta: Float)
		fun onPointerChanged(pointerDegreesOnNotRotatedCompass: Float)

		/**
		 * Call when all fingers were released. This is helpful when listener
		 * want to detect action up, action cancel...
		 */
		fun onReleased()
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)

		isBoardSizeChanged = true
		boardWidth = w
		boardHeight = h
		oldBoardWidth = oldw
		oldBoardHeight = oldh

		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		// Build compass if we got a request before
		if (isRequestBuildCompass) {
			isRequestBuildCompass = false
			buildCompassActual(buildCompassLocale)
			return
		}
		// Wait until compass is built
		if (isBuildingCompass) {
			return
		}

		// Fit compass inside board
		// omit event for layout-size-change
		if (isFitCompassInsideBoard) {
			isFitCompassInsideBoard = false
			isBoardSizeChanged = false
			updateMaterialsOnBoardSizeChanged()
			fitCompassInsideBoard()
		}

		// Translate compass
		if (isBoardSizeChanged) {
			isBoardSizeChanged = false
			updateMaterialsOnBoardSizeChanged()
			val newCx = compassCx * boardWidth / oldBoardWidth
			val newCy = compassCy * boardHeight / oldBoardHeight
			postTranslateCompassMatrix((newCx - compassCx).toDouble(), (newCy - compassCy).toDouble())
		}
		val boardCx = boardCenterX
		val boardCy = boardCenterY
		val handlerRadius = rotatorRadius.toFloat()
		val handlerToCenter = distFromRotatorCenterToBoardCenter
		val animateDegrees = nextAnimateDegrees

		// Reset before draw
		linePaint.color = compassColor
		fillPaint.color = compassColor

		// Draw Ox, Oy navigation-axis
		canvas.drawLine(
			boardCx.toFloat(),
			naviArrowStartY.toFloat(),
			boardCx.toFloat(),
			naviArrowStopY.toFloat(),
			linePaint
		)
		canvas.drawLine(0f, boardCy.toFloat(), boardWidth.toFloat(), boardCy.toFloat(), linePaint)
		canvas.drawPath(naviArrow!!, fillPaint)

		// Setup to draw from 0 degrees
		canvas.save()
		canvas.rotate(-animateDegrees, boardCx.toFloat(), boardCy.toFloat())

		val isHighlight = handlerColor == compassColor
		val paint = if (isHighlight) fillPaint else linePaint

		// Draw rotator
		if (isShowRotator && mode == MODE_ROTATE) {
			canvas.save()
			canvas.rotate((animateDegrees + rotatorRotatedDegrees).toFloat(), boardCx.toFloat(), boardCy.toFloat())
			canvas.drawCircle(boardCx.toFloat(), boardCy + handlerToCenter, handlerRadius, paint)
			canvas.drawCircle(boardCx.toFloat(), boardCy - handlerToCenter, handlerRadius, paint)
			canvas.restore()
		}
		else if (isShowPointer && mode == MODE_POINT) {
			val arrowDim = boardInnerRadius / 15f
			val startY = (boardCy + handlerToCenter - rotatorRadius).toFloat()
			val stopY = pointerStopY
			//			final float stopY = mPointerStopY * (1 - mCompassBitmapZoomLevel);
			val handlerArrow = newArrowAt(boardCx.toFloat(), stopY, arrowDim, arrowDim)
			canvas.save()
			canvas.rotate((animateDegrees + pointerDegrees), boardCx.toFloat(), boardCy.toFloat())
			canvas.drawLine(boardCx.toFloat(), startY, boardCx.toFloat(), stopY, paint)
			canvas.drawPath(handlerArrow, fillPaint)
			canvas.drawCircle(boardCx.toFloat(), boardCy + handlerToCenter, handlerRadius, paint)
			canvas.restore()
		}

		// Draw 24 pointer lines
		if (isShow24PointerLines) {
			val defaultStrokeWidth = linePaint.strokeWidth
			linePaint.strokeWidth = defaultStrokeWidth / 2
			canvas.save()
			canvas.rotate(7.5f, boardCx.toFloat(), boardCy.toFloat())
			val startY = (boardCy - Math.hypot(boardCx.toDouble(), boardCy.toDouble())).toFloat()
			val stopY = boardCy - boardCy / 35f
			for (i in 0..23) {
				canvas.drawLine(boardCx.toFloat(), startY, boardCx.toFloat(), stopY, linePaint)
				canvas.rotate(15f, boardCx.toFloat(), boardCy.toFloat())
			}
			canvas.restore()
			linePaint.strokeWidth = defaultStrokeWidth
		}
		canvas.restore()

		// Count down animation
		if (countdownAnimator.isRunning) {
			fillPaint.color = countdownAnimator.animatedValue as Int
			canvas.drawCircle(boardCx.toFloat(), boardCy.toFloat(), handlerRadius, fillPaint)
		}

		// Draw compass
		if (compass != null) {
			postRotateCompassMatrix(animateDegrees)
			canvas.drawBitmap(compass!!, compassBitmapMatrix, compassPaint)
		}
	}

	/**
	 * This method just call invalidate() since we need the view's dimension to build compass.
	 * Compass will be built as soon as possible when the view is laid out.
	 */
	fun buildCompass(rings: List<DkCompassRing>, locale: Locale) {
		buildCompassRings = rings
		buildCompassLocale = locale

		if (width > 0 && height > 0) {
			buildCompassActual(locale)
		}
		else {
			isRequestBuildCompass = true
			invalidate()
		}
	}

	fun calcPointerDegreesOnRotatedCompass(): Float {
		return calcDisplayAngle(pointerDegrees + compassDegreesInPointMode)
	}

	fun readCurInfo(): DkCompassInfo {
		return controller.readInfo(context, nextAnimateDegrees, buildCompassRings, poleLongNames)
	}

	/**
	 * Rotate the compass clockwisely an angle in degrees based North pole.
	 *
	 * @param degrees clockwise angle in degrees.
	 */
	fun rotate(degrees: Float) {
		when (mode) {
			MODE_NORMAL -> {
				compassDegreesInNormalMode = degrees
			}
			MODE_ROTATE -> {
				compassDegreesInRotateMode = degrees
			}
			MODE_POINT -> {
				compassDegreesInPointMode = degrees
			}
		}
		nextAnimateDegrees = degrees
		invalidate()
	}

	override fun onTouch(v: View, event: MotionEvent): Boolean {
		var isRequestNextEvent = false
		val x = event.x
		val y = event.y

		if (isAdjustCompass) {
			isRequestNextEvent = true
			gestureDetector.onTouchEvent(event)
		}

		kotlin.run { // for `return` since we cannot break in `when` expression
			when (event.actionMasked) {
				MotionEvent.ACTION_DOWN -> {
					lastStopTime = System.currentTimeMillis()
					touchStartDegrees = point2degrees(x, y, boardCenterX, boardCenterY)
					isTouchInsideRotator = isInsideHandlers(x.toDouble(), y.toDouble())
					handlerColor = if (isTouchInsideRotator) compassColor else compassSemiColor
					if (isTouchInsideRotator) {
						handler.sendMessageDelayed(handler.obtainMessage(MSG_TURN_OFF_HIGH_LIGHT), 500)
					}
					invalidate()
				}
				MotionEvent.ACTION_MOVE -> {
					if (!isTouchInsideRotator) {
						return@run
					}
					// do not move handler if time passed over the specific value
					val stopTimeElapsed = System.currentTimeMillis() - lastStopTime
					if (stopTimeElapsed >= rotatorDisabledCountDown) {
						lastStopTime = 0
						return@run
					}
					lastStopTime = System.currentTimeMillis()
					val touchEndDegrees = point2degrees(x, y, boardCenterX, boardCenterY)
					if (mode == MODE_ROTATE) {
						rotatorRotatedDegrees = touchEndDegrees.toDouble()
						val rotatedDegrees = DkMaths.reduceToDefaultRange(touchEndDegrees - touchStartDegrees)
						if (rotatedDegrees <= -1 || rotatedDegrees >= 1) {
							touchStartDegrees = touchEndDegrees

							listener?.onRotatorElapsed(rotatedDegrees * rotationFactor)
						}
					}
					else if (mode == MODE_POINT) {
						pointerDegrees = DkMaths.reduceToDefaultRange(touchEndDegrees - 180)

						listener?.onPointerChanged(touchEndDegrees)
					}
					invalidate()
				}
				MotionEvent.ACTION_OUTSIDE, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
					handlerColor = compassSemiColor
					if (countdownAnimator.isRunning) {
						moveCompassToCenterAndStopCountdown()
					}

					listener?.onReleased()
					invalidate()
				}
			}
		}
		return isRequestNextEvent || isTouchInsideRotator
	}

	//region GetSet
	fun setIsAdjustCompass(isAdjust: Boolean): DkCompassView {
		isAdjustCompass = isAdjust
		return this
	}

	fun setCompassColor(color: Int): DkCompassView {
		compassColor = color
		handlerColor = DkColors.toSemiColor(color)
		compassSemiColor = handlerColor
		compassPaint.colorFilter = PorterDuffColorFilter(compassColor, PorterDuff.Mode.SRC_ATOP)
		countdownAnimator.apply {
			this.setIntValues(color, compassSemiColor)
			this.setEvaluator(controller.argbEvaluator)
			this.removeAllUpdateListeners()
			this.addUpdateListener { invalidate() }
		}
		return this
	}

	/**
	 * @return degrees (Oy based rotation) of the compass
	 */
	val compassDegrees: Double
		get() {
			when (mode) {
				MODE_NORMAL -> {
					return compassDegreesInNormalMode.toDouble()
				}
				MODE_ROTATE -> {
					return compassDegreesInRotateMode.toDouble()
				}
				MODE_POINT -> {
					return compassDegreesInPointMode.toDouble()
				}
			}
			throw RuntimeException("Invalid compass mode: $mode")
		}

	//endregion GetSet
	private fun moveCompassToCenterAndStopCountdown() {
		isCompassMovable = false
		hasCmpCenterLeftCountDown = false
		countdownAnimator.cancel()
		val dx = (boardCenterX - compassCx).toDouble()
		val dy = (boardCenterY - compassCy).toDouble()
		compassCx = boardCenterX.toFloat()
		compassCy = boardCenterY.toFloat()
		compassBitmapMatrix.postTranslate(dx.toFloat(), dy.toFloat())
		handler.sendMessageDelayed(handler.obtainMessage(MSG_ALLOW_MOVE_COMPASS), 1000)
	}
	// when compass center leave countdown

	// when compass center come back
	private val isShouldStartCountDown: Boolean
		get() {
			val dx = (boardCenterX - compassCx).toDouble()
			val dy = (boardCenterY - compassCy).toDouble()
			val cmpToCenter = dx * dx + dy * dy
			val countdown = rotatorRadius * rotatorRadius
			var isShouldStart = false

			// when compass center leave countdown
			if (cmpToCenter > countdown && !hasCmpCenterLeftCountDown) {
				isShouldStart = true
				hasCmpCenterLeftCountDown = true
			}

			// when compass center come back
			if (cmpToCenter < countdown && hasCmpCenterLeftCountDown) {
				hasCmpCenterLeftCountDown = false
			}
			return isShouldStart
		}

	private fun isInsideHandlers(x: Double, y: Double): Boolean {
		val boardCx = boardCenterX
		val boardCy = boardCenterY
		val handlerToCenter = distFromRotatorCenterToBoardCenter
		if (mode == MODE_POINT && isShowPointer) {
			val radian = Math.toRadians(pointerDegrees.toDouble())
			val sin = Math.sin(radian)
			val cos = Math.cos(radian)
			val cx = boardCx - handlerToCenter * sin
			val cy = boardCy + handlerToCenter * cos
			return Math.hypot(x - cx, y - cy) <= rotatorRadius
		}
		if (mode == MODE_ROTATE && isShowRotator) {
			val radian = Math.toRadians(rotatorRotatedDegrees)
			val sin = Math.sin(radian)
			val cos = Math.cos(radian)
			var cx = boardCx + handlerToCenter * sin
			var cy = boardCy - handlerToCenter * cos
			if (Math.hypot(x - cx, y - cy) <= rotatorRadius) {
				return true
			}
			cx = boardCx - handlerToCenter * sin
			cy = boardCy + handlerToCenter * cos
			return Math.hypot(x - cx, y - cy) <= rotatorRadius
		}
		return false
	}

	private fun buildCompassActual(locale: Locale) {
		isBuildingCompass = true

		DkObservable
			.fromCallable {
				updateMaterialsOnBoardSizeChanged()
				buildCompassInternal(buildCompassRings, locale)
			}
			.scheduleInBackgroundAndObserveOnForeground()
			.doOnNext { bitmap ->
				compass = bitmap
				isFitCompassInsideBoard = true
				isBuildingCompass = false
				invalidate()
			}
			.doOnError { isBuildingCompass = false }
			.subscribe()
	}

	private fun updateMaterialsOnBoardSizeChanged() {
		// Board
		boardCenterX = boardWidth shr 1
		boardCenterY = boardHeight shr 1
		boardInnerRadius = Math.min(boardCenterX, boardCenterY)

		// Compass color
		val cmpColor = compassColor
		linePaint.color = cmpColor
		textPaint.color = cmpColor
		fillPaint.color = cmpColor

		// Rotator, pointer and navigation
		val boardInnerRadius = boardInnerRadius
		val arrowTall = (boardInnerRadius shr 4) + (boardInnerRadius shr 8)
		val boardPadding = boardCenterY - boardInnerRadius
		distFromRotatorCenterToBoardCenter = ((boardInnerRadius shr 2) + (boardInnerRadius shr 3)).toFloat()
		rotatorRadius = ((boardInnerRadius shr 3) + (boardInnerRadius shr 4)).toDouble()

		// Pointer
		pointerStopY = (boardCenterY - Math.max(boardCenterX, boardCenterY) + arrowTall).toFloat()

		// Navigation Ox, Oy
		naviArrowStartY = boardCenterY + boardInnerRadius + (boardPadding shr 1)
		naviArrowStopY = boardCenterY - boardInnerRadius - (boardPadding shr 1)
		naviArrow = newArrowAt(boardCenterX.toFloat(), naviArrowStopY.toFloat(), arrowTall.toFloat(), arrowTall.toFloat())
	}

	private fun fitCompassInsideBoard() {
		this.compass?.let { compass ->
			val boardCx = boardCenterX
			val boardCy = boardCenterY
			compassCx = boardCx.toFloat()
			compassCy = boardCy.toFloat()
			val cmpHalfWidth = (compass.width shr 1).toFloat()
			val cmpHalfHeight = (compass.height shr 1).toFloat()
			val scaleFactor = min(boardCx, boardCy) / Math.min(cmpHalfWidth, cmpHalfHeight)
			compassBitmapMatrix.reset()
			compassBitmapMatrix.postTranslate(boardCx - cmpHalfWidth, boardCy - cmpHalfHeight)
			compassBitmapMatrix.postScale(scaleFactor, scaleFactor, boardCx.toFloat(), boardCy.toFloat())
			compassBitmapZoomLevel = 1f
			lastAnimatedDegrees = 0f
		}
	}

	private fun buildCompassInternal(ringList: List<DkCompassRing>, locale: Locale): Bitmap {
		// Use to determine default values (padding, height...)
		val defaultText = "360"
		val textPaint = textPaint
		val tmpRect = tmpRect
		textPaint.textSize = DEFAULT_WORD_TEXT_SIZE
		textPaint.getTextBounds(defaultText, 0, defaultText.length, tmpRect)
		val defaultSpace = tmpRect.height()
		val rayPadding = max(4, defaultSpace shr 2)

		// Step 1. measure radius of compass should be. Also calculate preferred
		// word-fontsize for each ring to fit with compass bitmap size.
		val cmpMaxRadius = compassMaxRadius
		var cmpRadius: Float
		lateinit var wordTextSizes: FloatArray
		lateinit var maxWordDims: Array<FloatArray>
		lateinit var wordDims: Array<Array<FloatArray>>

		val ringCnt = ringList.size
		if (ringCnt > 0) {
			wordTextSizes = FloatArray(ringCnt)
			maxWordDims = Array(ringCnt) { FloatArray(2) }
			wordDims = Array(ringCnt) { arrayOf() }
		}

		var time = 0

		while (true) {
			var tmpCmpRadius = (boardInnerRadius shr 3).toFloat()
			var numberUnnecessaryMeasureRing = 0

			for (ringIndex in ringCnt - 1 downTo 0) {
				val ring = ringList[ringIndex]
				if (!ring.isVisible) {
					++numberUnnecessaryMeasureRing
					continue
				}

				// word font size should between [1, 100]
				val wordFontSize = max(1, min(100, ring.wordFontSize))
				var ringTextSize = wordFontSize * DkConfig.scaledDensity() - time
				if (ringTextSize < 1) {
					ringTextSize = 1f
					++numberUnnecessaryMeasureRing
				}

				wordTextSizes[ringIndex] = ringTextSize

				val words = ring.words
				val isHorizontal = ring.isHorizontalWord
				val wordCnt = words.size

				// Re-init for each elm
				wordDims[ringIndex] = Array(2) { FloatArray(wordCnt) }

				for (wordIndex in 0 until wordCnt) {
					var word = words[wordIndex]
					if (ring.isWordLowerCase) {
						word = word.lowercase(locale)
					}
					else if (ring.isWordUpperCase) {
						word = word.uppercase(locale)
					}
					var endLength = ring.shownCharCount
					if (endLength > word.length) {
						endLength = word.length
					}
					if (endLength < word.length) {
						word = word.substring(0, endLength)
					}
					textPaint.textSize = ringTextSize
					textPaint.getTextBounds(word, 0, endLength, tmpRect)
					wordDims[ringIndex][0][wordIndex] = if (isHorizontal) tmpRect.width().toFloat() else tmpRect.height().toFloat()
					wordDims[ringIndex][1][wordIndex] = if (isHorizontal) tmpRect.height().toFloat() else tmpRect.width().toFloat()
				}
				var maxWidth = 0f
				var maxHeight = 0f
				for (width in wordDims[ringIndex][0]) {
					if (maxWidth < width) {
						maxWidth = width
					}
				}
				for (height in wordDims[ringIndex][1]) {
					if (maxHeight < height) {
						maxHeight = height
					}
				}
				maxWordDims[ringIndex][0] = maxWidth
				maxWordDims[ringIndex][1] = maxHeight
				val sumLength = wordCnt * (maxWidth + (rayPadding shl 1))
				val nextRadius = (sumLength / 2 / Math.PI + maxHeight).toFloat()
				val minRadius = tmpCmpRadius + maxHeight
				tmpCmpRadius = Math.max(minRadius, nextRadius)
			}
			if (tmpCmpRadius <= cmpMaxRadius) {
				cmpRadius = tmpCmpRadius
				break
			}
			if (numberUnnecessaryMeasureRing == ringCnt) {
				cmpRadius = cmpMaxRadius
				break
			}
			++time
		}

		// calculate radius for 36 angles
		textPaint.textSize = DEFAULT_WORD_TEXT_SIZE
		textPaint.getTextBounds(defaultText, 0, defaultText.length, tmpRect)
		val sumLength = (36 * (tmpRect.width() + (rayPadding shl 1))).toFloat()
		val justNeedLength = (sumLength / 2 / Math.PI + tmpRect.width()).toFloat()
		val minLengthRequired = cmpRadius + tmpRect.height()
		cmpRadius = Math.max(minLengthRequired, justNeedLength)

		// calculate radius for 4 pole-indicators of N, E, S, W
		val indicatorHeight = (defaultSpace shr 1) + (defaultSpace shr 2)
		cmpRadius += indicatorHeight.toFloat()
		if (BuildConfig.DEBUG) {
			DkLogcats.info(this, "Compass radius: %f/%f, inner radius: %d", cmpRadius, cmpMaxRadius, boardInnerRadius)
		}
		if (cmpRadius < boardInnerRadius) {
			cmpRadius = boardInnerRadius.toFloat()
		}

		// Step 2. create, draw default compass
		val cmpDiameter = (cmpRadius * 2).toInt()
		val compass = Bitmap.createBitmap(cmpDiameter, cmpDiameter, Bitmap.Config.ALPHA_8)
		val canvas = Canvas(compass)
		DkLogcats.info(this, "Compass size: " + compass.sizeDk())

		// obtain compass radius and center coordinate
		val cmpSemiWidth = compass.width shr 1
		val cmpSemiHeight = compass.height shr 1
		cmpRadius = Math.min(cmpSemiWidth, cmpSemiHeight).toFloat()
		var belowRayRadius = cmpRadius - indicatorHeight

		// draw 4-arrow-indicator for north, east, south and west direction
		canvas.save()
		for (i in 0..3) {
			val arrow = newArrowAt(cmpSemiWidth.toFloat(), 0f, defaultSpace.toFloat(), indicatorHeight.toFloat())
			canvas.drawPath(arrow, fillPaint)
			canvas.rotate(90f, cmpSemiWidth.toFloat(), cmpSemiHeight.toFloat())
		}
		canvas.restore()

		// draw center lines for compass
		val halfHandlerRadius = rotatorRadius / 2
		val vsy = (cmpSemiHeight - halfHandlerRadius).toFloat()
		val vey = (cmpSemiHeight + halfHandlerRadius).toFloat()
		val hsx = (cmpSemiWidth - halfHandlerRadius).toFloat()
		val hex = (cmpSemiWidth + halfHandlerRadius).toFloat()
		canvas.drawLine(cmpSemiWidth.toFloat(), vsy, cmpSemiWidth.toFloat(), vey, linePaint)
		canvas.drawLine(hsx, cmpSemiHeight.toFloat(), hex, cmpSemiHeight.toFloat(), linePaint)

		// draw most-outer circle
		canvas.drawCircle(cmpSemiWidth.toFloat(), cmpSemiHeight.toFloat(), belowRayRadius, linePaint)

		// draw 360 dividers
		canvas.save()
		val divHeight = 2 * defaultSpace / 3
		for (i in 0..359) {
			val stopY = if (i % 10 == 0)
				cmpRadius - belowRayRadius + defaultSpace
			else
				cmpRadius - belowRayRadius + divHeight
			canvas.drawLine(cmpSemiWidth.toFloat(), indicatorHeight.toFloat(), cmpSemiWidth.toFloat(), stopY, linePaint)
			canvas.rotate(1f, cmpSemiWidth.toFloat(), cmpSemiHeight.toFloat())
		}
		canvas.restore()

		// draw 36 angles: 0, 10, 20,..., 350
		canvas.save()
		belowRayRadius -= (defaultSpace + rayPadding shl 1).toFloat()

		for (i in 0..35) {
			val number = when (i) {
				0 -> getPoleShortName(0)
				9 -> getPoleShortName(1)
				18 -> getPoleShortName(2)
				27 -> getPoleShortName(3)
				else -> (i * 10).toString() + ""
			}
			if (i % 9 == 0) {
				textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
			}
			else {
				textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
			}
			textPaint.getTextBounds(number, 0, number.length, tmpRect)
			val x = (cmpSemiWidth - (tmpRect.width() shr 1)).toFloat()
			val y = cmpSemiHeight - belowRayRadius - rayPadding
			val a = DkViews.getTextViewDrawPoint(tmpRect, x, y)
			canvas.drawText(number, a[0], a[1], textPaint)
			canvas.rotate(10f, cmpSemiWidth.toFloat(), cmpSemiHeight.toFloat())
		}
		textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
		canvas.restore()

		// draw circle below 36 angles
		canvas.drawCircle(cmpSemiWidth.toFloat(), cmpSemiHeight.toFloat(), belowRayRadius, linePaint)

		// Step 3. draw user customed compass
		for (ringInd in 0 until ringCnt) {
			val ring = ringList[ringInd]
			if (!ring.isVisible) {
				continue
			}
			textPaint.textSize = wordTextSizes[ringInd]
			textPaint.typeface = Typeface.create(Typeface.DEFAULT, ring.wordStyle)
			val defaultRotateDegrees = ring.rotatedDegrees
			val words = ring.words
			val wordCnt = words.size
			val prevBelowRayRadius = belowRayRadius
			belowRayRadius -= (rayPadding shl 1) + maxWordDims[ringInd][1]
			if (belowRayRadius < 0) {
				break
			}

			// now draw words on this ring
			canvas.save()
			canvas.rotate(defaultRotateDegrees, cmpSemiWidth.toFloat(), cmpSemiHeight.toFloat())
			val isHorizontal = ring.isHorizontalWord
			val isCurveText = ring.isCurvedWord
			val fixedWordRangeDegrees = 360f / wordCnt
			val path = Path()
			path.addCircle(cmpSemiWidth.toFloat(), cmpSemiHeight.toFloat(), belowRayRadius + rayPadding, Path.Direction.CW)
			for (wordInd in 0 until wordCnt) {
				canvas.save()
				var word = words[wordInd]
				if (ring.isWordLowerCase) {
					word = word.lowercase(locale)
				}
				else if (ring.isWordUpperCase) {
					word = word.uppercase(locale)
				}
				var endLength = ring.shownCharCount
				if (endLength > word.length) {
					endLength = word.length
				}
				if (endLength < word.length) {
					word = word.substring(0, endLength)
				}
				textPaint.getTextBounds(word, 0, endLength, tmpRect)

				if (isHorizontal) {
					if (isCurveText) {
						val wordRangeDegrees = (180 * wordDims[ringInd][0][wordInd] / Math.PI
							/ (belowRayRadius + rayPadding)).toFloat()
						canvas.rotate(-90 - wordRangeDegrees / 2, cmpSemiWidth.toFloat(), cmpSemiHeight.toFloat())
						canvas.drawTextOnPath(word, path, 0f, -tmpRect.bottom.toFloat(), textPaint)
					}
					else {
						val leftBottomX = cmpSemiWidth - wordDims[ringInd][0][wordInd] / 2
						val leftBottomY = cmpSemiHeight - belowRayRadius - rayPadding
						val a = DkViews.getTextViewDrawPoint(tmpRect, leftBottomX, leftBottomY)
						canvas.drawText(word, a[0], a[1], textPaint)
					}
				}
				else {
					var pad = prevBelowRayRadius - belowRayRadius
					pad = (pad - wordDims[ringInd][1][wordInd]) / 2
					val dy = cmpSemiHeight - belowRayRadius - pad
					canvas.translate(cmpSemiWidth.toFloat(), dy)
					canvas.rotate(-90f, 0f, 0f)
					val a = DkViews.getTextViewDrawPoint(
						tmpRect, 0f,
						wordDims[ringInd][0][wordInd] / 2
					)
					canvas.drawText(word, a[0], a[1], textPaint)
				}
				canvas.restore()

				// draw word-divider line
				canvas.save()
				canvas.rotate(fixedWordRangeDegrees / 2, cmpSemiWidth.toFloat(), cmpSemiHeight.toFloat())
				canvas.drawLine(
					cmpSemiWidth.toFloat(), cmpSemiHeight - belowRayRadius,
					cmpSemiWidth.toFloat(), cmpSemiHeight - prevBelowRayRadius, linePaint
				)
				canvas.restore()
				canvas.rotate(fixedWordRangeDegrees, cmpSemiWidth.toFloat(), cmpSemiHeight.toFloat())
			}
			canvas.restore()

			// draw under circle
			canvas.drawCircle(cmpSemiWidth.toFloat(), cmpSemiHeight.toFloat(), belowRayRadius, linePaint)
		}

		// Step 4. restore default settings
		textPaint.textSize = DEFAULT_WORD_TEXT_SIZE
		textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
		return compass
	}

	private fun postRotateCompassMatrix(degrees: Float) {
		val delta = degrees - lastAnimatedDegrees
		lastAnimatedDegrees = degrees
		compassBitmapMatrix.postRotate(-delta, compassCx, compassCy)
	}

	private fun postTranslateCompassMatrix(dx: Double, dy: Double) {
		compassCx += dx.toFloat()
		compassCy += dy.toFloat()
		compassBitmapMatrix.postTranslate(dx.toFloat(), dy.toFloat())
	}

	//todo take care max-memory
	private val compassMaxRadius: Float
		get() {
			//todo take care max-memory
			val maxMemory = Runtime.getRuntime().maxMemory()
			return 2000f
		}

	private fun getPoleShortName(index: Int): String {
		return poleShortNames[index]
	}

	private fun getPoleLongName(index: Int): String {
		return poleLongNames[index]
	}
}
