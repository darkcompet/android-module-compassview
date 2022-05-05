/*
 * Copyright (c) 2017-2020 DarkCompet. All rights reserved.
 */
package tool.compet.compassview

class DkCompassRing {
	/**
	 * Value true means this ring is visible, otherwise means this ring should be gone
	 */
	var isVisible = true

	/**
	 * Clockwise rotated angle in degrees.
	 */
	@JvmField
	var rotatedDegrees = 0f

	/**
	 * Name of ring, anything you want to tag.
	 */
	@JvmField
	var ringName: String? = null

	/**
	 * Words of this ring.
	 */
	lateinit var words: MutableList<String>

	/**
	 * Indicate each word is horizontal or vertical.
	 */
	var isHorizontalWord = false

	/**
	 * Indicate each word is curved or straight when drawing.
	 */
	var isCurvedWord = false

	/**
	 * Indicate each word is normal, uppercase or lowercase. Value is one of
	 * {Character.UNASSIGNED, Character.UPPERCASE_LETTER or Character.LOWERCASE_LETTER}.
	 */
	var wordCase = 0

	/**
	 * Indicate each word is normal, bold or italic.
	 */
	var wordStyle = 0

	/**
	 * Font size of each word.
	 */
	var wordFontSize = 0

	/**
	 * Number of characters will be shown from left of each word when drawing.
	 */
	var shownCharCount = 0

	val isWordUpperCase: Boolean
		get() = wordCase == Character.UPPERCASE_LETTER.toInt()

	val isWordLowerCase: Boolean
		get() = wordCase == Character.UPPERCASE_LETTER.toInt()
}