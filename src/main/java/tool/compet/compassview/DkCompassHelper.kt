/*
 * Copyright (c) 2017-2020 DarkCompet. All rights reserved.
 */
package tool.compet.compassview

import android.graphics.Path
import tool.compet.core.DkMaths
import tool.compet.core.DkStrings
import tool.compet.core.parseFloatDk
import java.util.*

object DkCompassHelper {
	/**
	 * Use this for expression. It returns angle in-range [0, 360) in degrees based on Oy-axis.
	 */
	@JvmStatic
	fun calcDisplayAngle(_degrees: Float): Float {
		var degrees = _degrees
		if (degrees >= 360 || degrees <= -360) {
			degrees %= 360f
		}
		if (degrees >= 360) {
			degrees -= 360f
		}
		if (degrees < 0) {
			degrees += 360f
		}
		return degrees
	}

	fun calcOneDecimalDisplayAngle(degrees: String): String {
		return calcOneDecimalDisplayAngle(degrees.parseFloatDk())
	}

	@JvmStatic
	fun calcOneDecimalDisplayAngle(_degrees: Float): String {
		var degrees = _degrees
		degrees = calcDisplayAngle(degrees)
		var angle = DkStrings.format("%.1f", degrees)
		if (angle.startsWith("360")) {
			angle = angle.replace("360", "0")
		}
		return angle
	}

	@JvmStatic
	fun point2degrees(px: Float, py: Float, boardCx: Int, boardCy: Int): Float {
		val dx = px - boardCx
		val dy = -py + boardCy
		var res: Float =
			if (dx == 0f) if (dy < 0) 180f else 0f else (90 - Math.toDegrees(Math.atan((dy / dx).toDouble()))).toFloat()
		if (dx < 0) {
			res += 180f
		}
		return DkMaths.reduceToDefaultRange(res)
	}

	fun applyWordCase(ring: DkCompassRing, locale: Locale?) {
		if (!ring.isWordUpperCase && !ring.isWordLowerCase) {
			return
		}
		ring.words.let { words ->
			if (ring.isWordLowerCase) {
				for (i in words.indices.reversed()) {
					words[i] = words[i].lowercase(locale!!)
				}
			}
			else if (ring.isWordUpperCase) {
				for (i in words.indices.reversed()) {
					words[i] = words[i].uppercase(locale!!)
				}
			}
		}
	}

	@JvmStatic
	fun newArrowAt(headX: Float, headY: Float, width: Float, height: Float): Path {
		val dw = width / 2
		val dh = height / 2
		val path = Path()
		path.moveTo(headX, headY)
		path.lineTo(headX - dw, headY + dh)
		path.lineTo(headX, headY + dh * 2 / 3)
		path.lineTo(headX + dw, headY + dh)
		path.lineTo(headX, headY)
		path.close()
		return path
	}
}
