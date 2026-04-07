// CaptchaAccessibilityService.kt
package dev.fursik.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.app.Service
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

class CaptchaAccessibilityService : AccessibilityService() {

    // ─────────────────────────────────────────────
    // Константы
    // ─────────────────────────────────────────────

    companion object {
        const val ACTION_ENTER_CODE = "dev.fursik.action.ENTER_CODE"
        const val EXTRA_CODE        = "captcha_code"
        private const val TAG       = "CaptchaA11y"

        // Координаты поля ввода капчи в игре (в пикселях, под твоё разрешение).
        // !! ЗАМЕНИ на реальные значения для своего устройства !!
        private const val FIELD_X = 540f
        private const val FIELD_Y = 1200f

        // Таймаут ожидания IME (мс). Если клавиатура не появилась — сброс.
        private const val IME_TIMEOUT_MS = 2000L

        // Пауза между тапами по цифрам (мс). Имитация человеческого ввода.
        private const val TAP_DELAY_MS   = 80L

        // Длительность одного тапа (мс)
        private const val TAP_DURATION_MS = 50L
    }

    // ─────────────────────────────────────────────
    // Состояния конечного автомата
    // ─────────────────────────────────────────────

    private enum class State {
        IDLE,       // Ожидание входящего кода
        TRIGGER,    // Тап по полю ввода → ждём появления IME
        VALIDATION, // Ожидание события TYPE_WINDOW_STATE_CHANGED с IME
        EXECUTION   // Последовательный ввод цифр
    }

    @Volatile private var currentState = State.IDLE
    @Volatile private var pendingCode: String = ""

    private val mainHandler = Handler(Looper.getMainLooper())

    // Runnable для таймаута ожидания IME
    private val imeTimeoutRunnable = Runnable {
        if (currentState == State.VALIDATION) {
            Log.w(TAG, "IME не появилась за ${IME_TIMEOUT_MS}мс → сброс в IDLE")
            resetToIdle()
        }
    }

    // ─────────────────────────────────────────────
    // Карта координат кнопок клавиатуры
    // Формат: символ → Pair(x, y) в пикселях
    // !! ЗАМЕНИ координаты на реальные для своего устройства !!
    // ─────────────────────────────────────────────
    private val keyboardMap: Map<Char, Pair<Float, Float>> = mapOf(
        '1' to Pair(120f, 1680f),
        '2' to Pair(270f, 1680f),
        '3' to Pair(420f, 1680f),
        '4' to Pair(120f, 1780f),
        '5' to Pair(270f, 1780f),
        '6' to Pair(420f, 1780f),
        '7' to Pair(120f, 1880f),
        '8' to Pair(270f, 1880f),
        '9' to Pair(420f, 1880f),
        '0' to Pair(270f, 1980f)
        // Добавь остальные цифры с реальными координатами
    )

    // ─────────────────────────────────────────────
    // Lifecycle сервиса
    // ─────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AccessibilityService подключён. Состояние: IDLE")
        currentState = State.IDLE
    }

    /**
     * Точка входа для получения кода от CaptchaReceiver.
     * AccessibilityService наследует Service, поэтому onStartCommand работает.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ENTER_CODE) {
            val code = intent.getStringExtra(EXTRA_CODE) ?: return START_NOT_STICKY

            if (currentState != State.IDLE) {
                Log.w(TAG, "Получен новый код, но автомат занят (${currentState}). Игнорируем.")
                return START_NOT_STICKY
            }

            Log.i(TAG, "Получен код '$code' → переход в TRIGGER")
            pendingCode = code
            transitionToTrigger()
        }
        return START_NOT_STICKY
    }

    override fun onInterrupt() {
        Log.w(TAG, "Сервис прерван. Сброс состояния.")
        resetToIdle()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        resetToIdle()
        return super.onUnbind(intent)
    }

    // ─────────────────────────────────────────────
    // Обработка событий доступности
    // ─────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Нас интересуют события изменения состояния окон ТОЛЬКО в состоянии VALIDATION
        if (currentState != State.VALIDATION) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        Log.d(TAG, "VALIDATION: получено TYPE_WINDOW_STATE_CHANGED, проверяем IME...")

        if (isImeVisible()) {
            Log.i(TAG, "IME подтверждена → переход в EXECUTION")
            // Отменяем таймаут, т.к. IME появилась вовремя
            mainHandler.removeCallbacks(imeTimeoutRunnable)
            transitionToExecution()
        }
    }

    // ─────────────────────────────────────────────
    // Переходы между состояниями
    // ─────────────────────────────────────────────

    /** IDLE → TRIGGER: тап по полю ввода */
    private fun transitionToTrigger() {
        currentState = State.TRIGGER
        Log.d(TAG, "TRIGGER: тап по полю ввода ($FIELD_X, $FIELD_Y)")

        tap(FIELD_X, FIELD_Y) {
            // Callback после завершения жеста
            Log.d(TAG, "TRIGGER: жест выполнен → переход в VALIDATION")
            currentState = State.VALIDATION
            // Запускаем таймаут на случай, если IME не появится
            mainHandler.postDelayed(imeTimeoutRunnable, IME_TIMEOUT_MS)
        }
    }

    /** VALIDATION → EXECUTION: запуск ввода цифр */
    private fun transitionToExecution() {
        currentState = State.EXECUTION
        executeCodeInput(pendingCode)
    }

    /** Любое состояние → IDLE: сброс */
    private fun resetToIdle() {
        mainHandler.removeCallbacks(imeTimeoutRunnable)
        pendingCode = ""
        currentState = State.IDLE
        Log.i(TAG, "→ Состояние сброшено в IDLE")
    }

    // ─────────────────────────────────────────────
    // Ввод кода через последовательные тапы
    // ─────────────────────────────────────────────

    /**
     * Последовательно вводит каждую цифру кода с задержкой TAP_DELAY_MS.
     * Использует рекурсивный Handler вместо корутины,
     * чтобы не тащить зависимость от kotlinx.coroutines.
     */
    private fun executeCodeInput(code: String, index: Int = 0) {
        if (index >= code.length) {
            Log.i(TAG, "EXECUTION: все ${code.length} цифр введены → IDLE")
            resetToIdle()
            return
        }

        val digit = code[index]
        val coords = keyboardMap[digit]

        if (coords == null) {
            Log.e(TAG, "EXECUTION: нет координат для символа '$digit' → сброс")
            resetToIdle()
            return
        }

        Log.d(TAG, "EXECUTION: ввод '$digit' (${index + 1}/${code.length}) → (${coords.first}, ${coords.second})")

        tap(coords.first, coords.second) {
            // После каждого тапа — пауза перед следующим символом
            mainHandler.postDelayed(
                { executeCodeInput(code, index + 1) },
                TAP_DELAY_MS
            )
        }
    }

    // ─────────────────────────────────────────────
    // Утилиты
    // ─────────────────────────────────────────────

    /**
     * Проверяет, видима ли IME (экранная клавиатура) среди активных окон.
     * Требует флага flagRetrieveInteractiveWindows в конфиге сервиса.
     */
    private fun isImeVisible(): Boolean {
        val windows = windows ?: return false
        return windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    }

    /**
     * Обёртка над dispatchGesture для одиночного тапа.
     * @param x, y  - координаты тапа в пикселях
     * @param onComplete - callback, вызываемый после завершения жеста
     */
    private fun tap(x: Float, y: Float, onComplete: (() -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }

        val stroke = StrokeDescription(
            path,
            /* startTime = */ 0L,
            /* duration  = */ TAP_DURATION_MS
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                super.onCompleted(gestureDescription)
                onComplete?.invoke()
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Жест ($x, $y) отменён системой")
                // При отмене жеста также вызываем callback,
                // чтобы автомат не завис. Можно добавить retry-логику.
                onComplete?.invoke()
            }
        }, mainHandler)
    }
}