package dev.fuelpilot

import android.content.Context
import android.content.Intent
import dev.fuelpilot.demo.DemoActivity

object VariantDemo {
    fun intent(context: Context): Intent? = Intent(context, DemoActivity::class.java)
}
