/*
 * Copyright (c) 2017-2020 DarkCompet. All rights reserved.
 */
package tool.compet.compassview

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

@Keep // Tell R8 don't shrink (remove) this
class DkCompassInfo {
	@Expose
	@SerializedName("key")
	var key: String? = null // for eg,. degrees

	@Expose
	@SerializedName("value")
	var value: String? = null // for eg,. 180

	@Expose
	@SerializedName("list")
	var children = mutableListOf<DkCompassInfo>()

	constructor()
	constructor(key: String?) {
		this.key = key
	}

	constructor(key: String?, value: String?) {
		this.key = key
		this.value = value
	}

	fun addChild(child: DkCompassInfo?): DkCompassInfo {
		if (child != null) {
			children.add(child)
		}
		return this
	}
}