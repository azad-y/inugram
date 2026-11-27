package desu.inugram.helpers.chat

import desu.inugram.InuConfig
import org.telegram.messenger.SharedConfig
import kotlin.math.min

/** Corner geometry, in dp, for the iOS-style message bubble option. */
object BubbleStyleHelper {
    // the cached bubble nine-patch is 40dp tall with 2dp padding, so it cannot draw a larger corner
    private const val MAX_RADIUS = 19

    /** changes whenever cached bubble drawables and paths have to be rebuilt */
    @JvmStatic
    fun styleKey(): Int = if (InuConfig.IOS_MESSAGE_BUBBLES.value) 1 else 0

    @JvmStatic
    fun isIos(): Boolean = InuConfig.IOS_MESSAGE_BUBBLES.value

    @JvmStatic
    fun radius(): Int =
        min(if (InuConfig.IOS_MESSAGE_BUBBLES.value) 18 else SharedConfig.bubbleRadius, MAX_RADIUS)

    /** corner joining grouped messages: stays round in the iOS style, except above bot buttons */
    @JvmStatic
    fun nearRadius(aboveBotButtons: Boolean): Int =
        if (isIos() && !aboveBotButtons) radius() else min(tailRadius(), radius())

    @JvmStatic
    fun tailRadius(): Int = if (InuConfig.IOS_MESSAGE_BUBBLES.value) 7 else 6
}
