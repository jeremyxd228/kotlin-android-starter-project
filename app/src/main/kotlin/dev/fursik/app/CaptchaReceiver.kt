// CaptchaReceiver.kt
package dev.fursik.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CaptchaReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION = "dev.fursik.AUTO_ENTER"
        const val EXTRA_CODE = "captcha_code"
        private const val TAG = "CaptchaReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) {
            Log.w(TAG, "Получен неожиданный action: ${intent.action}")
            return
        }

        val code = intent.getStringExtra(EXTRA_CODE)
        if (code.isNullOrEmpty() || !code.matches(Regex("\\d{5}"))) {
            Log.e(TAG, "Невалидный код капчи: '$code'")
            return
        }

        Log.i(TAG, "Получен код капчи: $code → передаём в AccessibilityService")

        // Пересылаем код в AccessibilityService через локальный Intent.
        // Используем явный Intent на конкретный класс (Explicit),
        // т.к. прямой вызов методов сервиса небезопасен между компонентами.
        val serviceIntent = Intent(context, CaptchaAccessibilityService::class.java).apply {
            action = CaptchaAccessibilityService.ACTION_ENTER_CODE
            putExtra(CaptchaAccessibilityService.EXTRA_CODE, code)
        }
        context.startService(serviceIntent)
    }
}