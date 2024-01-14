package com.chatho.chatransfer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.coroutines.CoroutineContext

class Utils {
    companion object {
        fun toDouble(doubleValue: Double, fractionDigits: Int = 1): Double {
            return "%.${fractionDigits}f".format(Locale.US,doubleValue).toDouble()
        }

        fun runInCoroutineScope(
            context: CoroutineContext, runBlock: suspend CoroutineScope.() -> Unit
        ) {
            CoroutineScope(context).launch(
                context, CoroutineStart.DEFAULT, runBlock
            )
        }
    }
}