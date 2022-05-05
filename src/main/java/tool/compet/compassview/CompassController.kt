/*
 * Copyright (c) 2017-2020 DarkCompet. All rights reserved.
 */
package tool.compet.compassview

import android.animation.TypeEvaluator
import android.content.Context
import tool.compet.compassview.DkCompassHelper.calcDisplayAngle
import tool.compet.compassview.DkCompassHelper.calcOneDecimalDisplayAngle

internal class CompassController {
	private var mArgbEvaluator: TypeEvaluator<*>? = null
	fun readInfo(
		context: Context,
		degrees: Float,
		rings: List<DkCompassRing>,
		poleLongNames: Array<String>
	): DkCompassInfo {
		val root = DkCompassInfo()
		val northInfo = DkCompassInfo(poleLongNames[0])
		val eastInfo = DkCompassInfo(poleLongNames[1])
		val southInfo = DkCompassInfo(poleLongNames[2])
		val westInfo = DkCompassInfo(poleLongNames[3])
		root.addChild(northInfo).addChild(eastInfo).addChild(southInfo).addChild(westInfo)
		val east = calcDisplayAngle(degrees + 90)
		val south = calcDisplayAngle(degrees + 180)
		val west = calcDisplayAngle(degrees + 270)
		val northDegrees = calcOneDecimalDisplayAngle(degrees)
		val eastDegrees = calcOneDecimalDisplayAngle(east)
		val southDegrees = calcOneDecimalDisplayAngle(south)
		val westDegrees = calcOneDecimalDisplayAngle(west)
		val degreesKey = context.getString(R.string.degrees)
		northInfo.addChild(DkCompassInfo(degreesKey, northDegrees))
		eastInfo.addChild(DkCompassInfo(degreesKey, eastDegrees))
		southInfo.addChild(DkCompassInfo(degreesKey, southDegrees))
		westInfo.addChild(DkCompassInfo(degreesKey, westDegrees))

		// Calculate ring names
		for (ring in rings) {
			val rotateDegrees = ring.rotatedDegrees
			val words = ring.words
			val wordCnt = words.size
			val delta = 360f / wordCnt
			val offset = calcDisplayAngle(-delta / 2 + rotateDegrees)
			val ringName = ring.ringName
			for (i in 0 until wordCnt) {
				val word = words[i]
				val fromDegrees = calcDisplayAngle(offset + i * delta)
				val toDegrees = calcDisplayAngle(fromDegrees + delta)
				collectInfo(northInfo, ringName, degrees.toDouble(), fromDegrees.toDouble(), toDegrees.toDouble(), word)
				collectInfo(eastInfo, ringName, east.toDouble(), fromDegrees.toDouble(), toDegrees.toDouble(), word)
				collectInfo(southInfo, ringName, south.toDouble(), fromDegrees.toDouble(), toDegrees.toDouble(), word)
				collectInfo(westInfo, ringName, west.toDouble(), fromDegrees.toDouble(), toDegrees.toDouble(), word)
			}
		}
		return root
	}

	private fun collectInfo(
		info: DkCompassInfo,
		ringName: String?,
		angle: Double,
		from: Double,
		to: Double,
		word: String
	) {
		if ((angle in from..to) || (from > to && (from <= angle || angle <= to))) {
			info.addChild(DkCompassInfo(ringName, word))
		}
	}

	val argbEvaluator: TypeEvaluator<*>?
		get() {
			if (mArgbEvaluator == null) {
				mArgbEvaluator = MyArgbEvaluator()
			}
			return mArgbEvaluator
		}

	internal inner class MyArgbEvaluator : TypeEvaluator<Any?> {
		override fun evaluate(fraction: Float, startValue: Any?, endValue: Any?): Any? {
			val startInt = startValue as Int
			val startA = (startInt shr 24 and 0xff) / 255.0f
			var startR = (startInt shr 16 and 0xff) / 255.0f
			var startG = (startInt shr 8 and 0xff) / 255.0f
			var startB = (startInt and 0xff) / 255.0f
			val endInt = endValue as Int
			val endA = (endInt shr 24 and 0xff) / 255.0f
			var endR = (endInt shr 16 and 0xff) / 255.0f
			var endG = (endInt shr 8 and 0xff) / 255.0f
			var endB = (endInt and 0xff) / 255.0f

			// convert from sRGB to linear
			startR = Math.pow(startR.toDouble(), 2.2).toFloat()
			startG = Math.pow(startG.toDouble(), 2.2).toFloat()
			startB = Math.pow(startB.toDouble(), 2.2).toFloat()
			endR = Math.pow(endR.toDouble(), 2.2).toFloat()
			endG = Math.pow(endG.toDouble(), 2.2).toFloat()
			endB = Math.pow(endB.toDouble(), 2.2).toFloat()

			// compute the interpolated color in linear space
			var a = startA + fraction * (endA - startA)
			var r = startR + fraction * (endR - startR)
			var g = startG + fraction * (endG - startG)
			var b = startB + fraction * (endB - startB)

			// convert back to sRGB in the [0..255] range
			a *= 255.0f
			r = Math.pow(r.toDouble(), 1.0 / 2.2).toFloat() * 255.0f
			g = Math.pow(g.toDouble(), 1.0 / 2.2).toFloat() * 255.0f
			b = Math.pow(b.toDouble(), 1.0 / 2.2).toFloat() * 255.0f
			return Math.round(a) shl 24 or (Math.round(r) shl 16) or (Math.round(g) shl 8) or Math.round(b)
		}
	}
}
