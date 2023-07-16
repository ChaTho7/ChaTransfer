package com.chatho.chatransfer

class Utils {
    companion object {
        fun toDouble(doubleValue: Double, fractionDigits: Int = 1): String {

            return "%.${fractionDigits}f".format(doubleValue)
        }
    }
}