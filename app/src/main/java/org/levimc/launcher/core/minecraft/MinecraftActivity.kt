package org.levimc.launcher.core.minecraft

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Color
import android.graphics.PointF
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.InputType
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import com.mojang.minecraftpe.MainActivity
import org.levimc.launcher.core.crash.CrashReporter
import org.levimc.launcher.core.mods.ModManager
import org.levimc.launcher.core.mods.inbuilt.nativemod.AimAssistMod
import org.levimc.launcher.core.mods.inbuilt.nativemod.PojavControlsMod
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager
import org.levimc.launcher.core.mods.inbuilt.overlay.InbuiltOverlayManager
import org.levimc.launcher.preloader.PreloaderInput
import org.levimc.launcher.settings.FeatureSettings
import org.levimc.launcher.util.LauncherBackgroundController
import org.levimc.pojavcontrols.PojavControls
import org.levimc.pojavcontrols.PojavControlsHost
import java.io.File
import java.io.FileOutputStream

class MinecraftActivity : MainActivity(), PojavControlsHost {

    companion object {
        private const val PRELOADER_DOCUMENT_REQUEST = 0x4C50

        // Максимальный pointerId в Android (InputDispatcher::MAX_POINTER_ID).
        private const val MAX_POINTER_ID = 31

        private const val TAG_ATTACK = "TriggerBotAttack"

        // Дефолт Bedrock для кнопки attack/destroy (options.txt gfx_touchButton2).
        private const val DEFAULT_ATTACK_BUTTON_X = 0.825f
        private const val DEFAULT_ATTACK_BUTTON_Y = 0.5f

        // D-pad & tap to interact: отдельной кнопки атаки нет, бьём тапом по экрану.
        private const val INTERACTION_MODEL_CLASSIC = 2
    }

    private lateinit var gameManager: GamePackageManager
    private lateinit var trace: LaunchTrace
    private var overlayManager: InbuiltOverlayManager? = null
    private var normalExitPrepared = false
    private var normalExitRestartScheduled = false
    private var gameRuntimeStarted = false
    private var preloaderTextInput: PreloaderTextInput? = null
    private var previousInputFocus: View? = null
    private var inbuiltKeyDispatchDepth = 0

    /**
     * Реальные пальцы на экране: pointerId → последние координаты.
     * Обновляется только из dispatchTouchEvent() — синтетические события
     * идут через super.dispatchTouchEvent() и сюда не попадают.
     * Нужно, чтобы синтетический тап (TriggerBot) шёл отдельным pointer'ом
     * и не ломал жест настоящего пальца.
     */
    private val activeTouchPointers = LinkedHashMap<Int, PointF>()
    private var realGestureDownTime = 0L

    /** true, пока синтетический тап между DOWN и UP — тапы не наслаиваются. */
    @Volatile
    private var syntheticTapInFlight = false

    /**
     * Нормализованная (0..1) позиция центра экранной кнопки attack/destroy
     * из options.txt игры (gfx_touchButton2X/Y). Геймпад/клавиатура её не
     * используют, поэтому по умолчанию — дефолт Bedrock (0.825, 0.5).
     */
    @Volatile
    private var attackButtonNormX = DEFAULT_ATTACK_BUTTON_X

    @Volatile
    private var attackButtonNormY = DEFAULT_ATTACK_BUTTON_Y

    /** Модель управления: 0/1 — джойстик (кнопки), 2 — D-pad (тап по экрану). */
    @Volatile
    private var touchInteractionModel = -1

    /** id реального тачскрина: с deviceId 0 игра может отбросить событие. */
    private val touchDeviceId: Int by lazy {
        try {
            InputDevice.getDeviceIds().firstOrNull { id ->
                val device = InputDevice.getDevice(id)
                device != null &&
                    (device.sources and InputDevice.SOURCE_TOUCHSCREEN) != 0
            } ?: 0
        } catch (throwable: Throwable) {
            0
        }
    }

    private class PreloaderTextInput(context: Context) : AppCompatEditText(context) {
        override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
            val target = super.onCreateInputConnection(outAttrs) ?: return null
            return object : InputConnectionWrapper(target, true) {
                override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                    if (!text.isNullOrEmpty() && PreloaderInput.onTextInput(text)) {
                        super.commitText(text, newCursorPosition)
                        return true
                    }
                    return super.commitText(text, newCursorPosition)
                }

                override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                    if (beforeLength > 0 && dispatchBackspace()) {
                        return true
                    }
                    return super.deleteSurroundingText(beforeLength, afterLength)
                }

                override fun deleteSurroundingTextInCodePoints(
                    beforeLength: Int,
                    afterLength: Int
                ): Boolean {
                    if (beforeLength > 0 && dispatchBackspace()) {
                        return true
                    }
                    return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
                }

                private fun dispatchBackspace(): Boolean {
                    val downConsumed = PreloaderInput.onKeyEvent(
                        KeyEvent.KEYCODE_DEL,
                        0,
                        true
                    )
                    val upConsumed = PreloaderInput.onKeyEvent(
                        KeyEvent.KEYCODE_DEL,
                        0,
                        false
                    )
                    return downConsumed || upConsumed
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LauncherBackgroundController.suspendForMinecraft()
        LauncherBackgroundController.awaitShutdown()
        trace = LaunchTrace.ensure(intent)
        trace.mark("MinecraftActivity onCreate entered")
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(resolveLaunchBackgroundColor()))

        if (savedInstanceState != null) {
            trace.mark("MinecraftActivity restored instance, continuing launch")
        }

        try {
            val preparedRuntime = MinecraftLaunchSession.getPreparedRuntime()
                ?: MinecraftRuntimePreparer.prepare(applicationContext, intent)
            gameManager = preparedRuntime.gameManager
            configureMinecraftFirebase()
            trace.mark("Prepared runtime consumed")
        } catch (throwable: Throwable) {
            trace.error("MinecraftActivity prepare failed", formatLaunchFailure(throwable))
            returnToLauncherAfterLaunchFailure()
            return
        }
        trace.mark("Native mod enable started")
        ModManager.enableLoadedMods()
        trace.mark("Native mod enable finished")
        setLeviKeepRunningInBackground(FeatureSettings.getInstance().isForegroundServiceEnabled())
        trace.mark("Mojang MainActivity super.onCreate starting")
        try {
            gameRuntimeStarted = true
            super.onCreate(savedInstanceState)
        } catch (throwable: Throwable) {
            trace.error("Mojang MainActivity super.onCreate failed", formatLaunchFailure(throwable))
            returnToLauncherAfterLaunchFailure()
            return
        }
        trace.mark("Mojang MainActivity super.onCreate finished")

        MinecraftForegroundService.startIfEnabled(this)

        val launchVertically = intent.getBooleanExtra("LAUNCH_VERTICALLY", false)
        if (launchVertically) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        
        initializePreloaderTextInput()
        PreloaderInput.setActivity(this)
        MinecraftActivityState.onCreated(this)
        trace.mark("MinecraftActivity onCreate finished")
    }


    private fun configureMinecraftFirebase() {
        val appId = gameManager.getGameStringResource("google_app_id")
            ?: "1:486187589451:android:b2331110821fe2304bd2ce"
        val apiKey = gameManager.getGameStringResource("google_api_key")
            ?: gameManager.getGameStringResource("google_crash_reporting_api_key")
            ?: ""
        val projectId = gameManager.getGameStringResource("project_id")
            ?: "minecraft-bedrock-57580"
        val senderId = gameManager.getGameStringResource("gcm_defaultSenderId")
            ?: "486187589451"
        intent.putExtra("MINECRAFT_FIREBASE_APP_ID", appId)
        intent.putExtra("MINECRAFT_FIREBASE_API_KEY", apiKey)
        intent.putExtra("MINECRAFT_FIREBASE_PROJECT_ID", projectId)
        intent.putExtra("MINECRAFT_FIREBASE_SENDER_ID", senderId)
    }

    private fun returnToLauncherAfterLaunchFailure() {
        gameRuntimeStarted = false
        MinecraftLaunchSession.clear()
        MinecraftProcessRestarter.restartLauncherAfterMinecraftExit(this)
        finish()
    }

    private fun formatLaunchFailure(throwable: Throwable): String {
        return throwable.message ?: throwable.javaClass.simpleName
    }

    private fun resolveLaunchBackgroundColor(): Int {
        val typedValue = android.util.TypedValue()
        return if (theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)) {
            typedValue.data
        } else {
            Color.BLACK
        }
    }

    private fun startInbuiltModServices() {
        InbuiltModManager.getInstance(this).resetAllToDefaults()
        overlayManager = InbuiltOverlayManager(this)
        overlayManager?.showEnabledOverlays()
        org.levimc.launcher.core.mods.inbuilt.nativemod.AimAssistMod.initializeAsync(null)
        org.levimc.launcher.core.mods.inbuilt.nativemod.EspMod.initializeAsync(null)
    }

    private fun stopInbuiltModServices() {
        overlayManager?.hideAllOverlays()
        overlayManager = null
    }

    override fun onNewIntent(intent: Intent) {
        setIntent(intent)
        super.onNewIntent(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PRELOADER_DOCUMENT_REQUEST) {
            handlePreloaderDocumentResult(resultCode, data)
            return
        }
        if (org.levimc.launcher.core.mods.inbuilt.overlay.MoreButtonsEditor.onActivityResult(requestCode, resultCode, data)) return
        if (PojavControls.onActivityResult(requestCode, resultCode, data)) return
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun requestOpenModMenu() {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            window.decorView.post {
                if (!isFinishing && !isDestroyed) {
                    overlayManager?.showModMenu()
                }
            }
        }
    }

    fun requestCloseModMenu() {
        runOnUiThread {
            overlayManager?.hideModMenu()
        }
    }

    fun openPreloaderDocumentPicker(mimeType: String) {
        runOnUiThread {
            try {
                val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mimeType.ifBlank { "*/*" }
                }
                startActivityForResult(picker, PRELOADER_DOCUMENT_REQUEST)
            } catch (throwable: Throwable) {
                PreloaderInput.onDocumentResult(false, "", "", throwable.message ?: "Unable to open document picker")
            }
        }
    }

    private fun handlePreloaderDocumentResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data?.data == null) {
            PreloaderInput.onDocumentResult(false, "", "", "Import cancelled")
            return
        }
        val uri = data.data ?: return
        var temporaryFile: File? = null
        try {
            var displayName = "selected_file"
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) displayName = cursor.getString(index) ?: displayName
                }
            }
            val safeName = displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(120).ifBlank { "selected_file" }
            val directory = File(cacheDir, "preloader_documents").also { it.mkdirs() }
            val importedFile = File(directory, "${System.nanoTime()}_$safeName")
            temporaryFile = importedFile
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(importedFile).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Unable to read selected document")
            PreloaderInput.onDocumentResult(true, importedFile.absolutePath, displayName, "")
        } catch (throwable: Throwable) {
            PreloaderInput.onDocumentResult(false, "", "", throwable.message ?: "Unable to import selected document")
        } finally {
            temporaryFile?.delete()
        }
    }

    override fun onBackPressed() {
        if (org.levimc.launcher.core.mods.inbuilt.overlay.MoreButtonsEditor.closeEditor()) return
        if (PojavControls.closeEditor()) return
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing) {
            normalExitPrepared = false
            normalExitRestartScheduled = false
        }
        MinecraftActivityState.onResumed(this)
        org.levimc.launcher.core.mods.inbuilt.overlay.MoreButtonsEditor.onResume()

        if (overlayManager == null) {
            startInbuiltModServices()
        }
    }

    private fun isMouseSource(source: Int): Boolean {
        return (source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
            (source and InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE
    }

    private fun getMouseButton(event: KeyEvent): Int {
        if (!isMouseSource(event.source)) {
            return 0
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_1 -> MotionEvent.BUTTON_PRIMARY
            KeyEvent.KEYCODE_BUTTON_2 -> MotionEvent.BUTTON_SECONDARY
            KeyEvent.KEYCODE_BUTTON_3 -> MotionEvent.BUTTON_TERTIARY
            KeyEvent.KEYCODE_BUTTON_4 -> MotionEvent.BUTTON_BACK
            KeyEvent.KEYCODE_BUTTON_5 -> MotionEvent.BUTTON_FORWARD
            else -> 0
        }
    }

    private fun shouldConsumeMouseMotion(event: MotionEvent): Boolean {
        return isMouseSource(event.source) &&
            PreloaderInput.onMouseMotion(
                event.actionMasked,
                event.actionButton,
                event.buttonState
            )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val textInputActive = isTextWidgetActive() || preloaderTextInput?.hasFocus() == true
        if (textInputActive) {
            overlayManager?.releaseKeybinds()
        }
        if (inbuiltKeyDispatchDepth == 0 && !textInputActive &&
            overlayManager?.handleModMenuShortcut(event) == true) {
            return true
        }
        val inbuiltKeyReleased = inbuiltKeyDispatchDepth == 0 &&
            event.action == KeyEvent.ACTION_UP && overlayManager?.handleKeyEvent(event) == true
        if (isTextWidgetActive() &&
            (event.keyCode == KeyEvent.KEYCODE_ESCAPE || event.keyCode == KeyEvent.KEYCODE_BACK)) {
            return super.dispatchKeyEvent(event)
        }

        val mouseButton = getMouseButton(event)
        if (mouseButton != 0 &&
            (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) &&
            PreloaderInput.onMouse(mouseButton, event.action == KeyEvent.ACTION_DOWN)) {
            return true
        }

        val unicodeChar = event.unicodeChar
        if (event.action == KeyEvent.ACTION_UP) {
            if (org.levimc.launcher.preloader.PreloaderInput.onKeyEvent(event.keyCode, unicodeChar, false)) {
                return true
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (org.levimc.launcher.preloader.PreloaderInput.onKeyEvent(event.keyCode, unicodeChar, true)) {
                return true
            }
        }

        if (inbuiltKeyReleased) return true
        if (inbuiltKeyDispatchDepth == 0 && !textInputActive) {
            if (overlayManager?.handleKeyEvent(event) == true) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    fun dispatchInbuiltKeyEvent(event: KeyEvent): Boolean {
        inbuiltKeyDispatchDepth++
        return try {
            dispatchKeyEvent(event)
        } finally {
            inbuiltKeyDispatchDepth--
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        trackRealTouch(event)
        if (shouldConsumeMouseMotion(event)) {
            return true
        }

        if (org.levimc.launcher.core.mods.inbuilt.overlay.VirtualCursorMod.isActive()) {
            org.levimc.launcher.core.mods.inbuilt.overlay.VirtualCursorMod.processTouchEvent(event, this)
            return true
        }

        if (PojavControls.ownsTouchInput()) {
            return super.dispatchTouchEvent(event)
        }

        val action = event.actionMasked
        val consumed = if (action == MotionEvent.ACTION_MOVE ||
            action == MotionEvent.ACTION_CANCEL) {
            var anyConsumed = false
            for (index in 0 until event.pointerCount) {
                if (PreloaderInput.onTouch(action, event.getPointerId(index),
                        event.getX(index), event.getY(index))) {
                    anyConsumed = true
                }
            }
            anyConsumed
        } else {
            val index = event.actionIndex
            PreloaderInput.onTouch(action, event.getPointerId(index),
                event.getX(index), event.getY(index))
        }
        if (consumed) {
            return true
        }

        overlayManager?.handleTouchEvent(event)

        return super.dispatchTouchEvent(event)
    }

    fun dispatchGenericMotionEventToGame(event: MotionEvent): Boolean {
        return super.dispatchGenericMotionEvent(event)
    }

    fun dispatchTouchEventToGame(event: MotionEvent): Boolean {
        return super.dispatchTouchEvent(event)
    }

    /** Трекинг настоящих пальцев: нужен, чтобы синтетический тап не
     *  переиспользовал pointerId живого пальца (это и давало «залипание»). */
    private fun trackRealTouch(event: MotionEvent) {
        if ((event.source and InputDevice.SOURCE_TOUCHSCREEN) != InputDevice.SOURCE_TOUCHSCREEN) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeTouchPointers.clear()
                realGestureDownTime = event.downTime
                val index = event.actionIndex
                activeTouchPointers[event.getPointerId(index)] =
                    PointF(event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                activeTouchPointers[event.getPointerId(index)] =
                    PointF(event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    val id = event.getPointerId(index)
                    activeTouchPointers[id]?.set(event.getX(index), event.getY(index))
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                activeTouchPointers.remove(event.getPointerId(event.actionIndex))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeTouchPointers.clear()
                realGestureDownTime = 0L
            }
        }
    }

    private fun snapshotRealPointers(): List<Triple<Int, Float, Float>> {
        val result = ArrayList<Triple<Int, Float, Float>>(activeTouchPointers.size)
        for ((id, pos) in activeTouchPointers) {
            result.add(Triple(id, pos.x, pos.y))
        }
        return result
    }

    /** Текущие реальные пальцы + синтетический pointer последним в списке. */
    private fun pointersWithSynthetic(
        syntheticId: Int,
        syntheticX: Float,
        syntheticY: Float
    ): List<Triple<Int, Float, Float>> {
        val result = ArrayList(snapshotRealPointers())
        result.add(Triple(syntheticId, syntheticX, syntheticY))
        return result
    }

    fun dispatchKeyEventToGame(event: KeyEvent): Boolean {
        return super.dispatchKeyEvent(event)
    }

    /**
     * Удар для TriggerBot. Сначала нажимаем экранную кнопку атаки из
     * Pojav Controls — ровно её, без касаний игровой области, поэтому
     * камера и остальные контролы не задеваются. Если кнопки нет, но
     * включён нативный ввод Pojav — инжектим кнопку мыши. И только если
     * недоступно ни то, ни другое — старый синтетический тап.
     */
    fun performTriggerBotAttack(): Boolean {
        if (isFinishing || isDestroyed) return false
        if (PojavControls.isEnabled() && PojavControls.pressAttackButton()) {
            return true
        }
        if (PojavControlsMod.isEnabled()) {
            return performNativeMouseAttack()
        }
        return performInbuiltAttack()
    }

    /**
     * Ищет кнопку атаки в options.txt игры и запоминает её позицию.
     * Bedrock пишет туда нормализованные (0..1) координаты центров кнопок:
     * gfx_touchButton2 — это ровно кнопка attack/destroy (та самая, с мечом).
     * Позиции меняются, когда игрок двигает кнопки в «Настроить управление»,
     * поэтому перечитываем файл при каждом включении модуля.
     */
    fun refreshAttackButtonPosition(): Boolean {
        val relative = "games/com.mojang/minecraftpe/options.txt"
        val candidates = ArrayList<File>(3)
        candidates.add(File(filesDir, relative))
        getExternalFilesDir(null)?.let { candidates.add(File(it, relative)) }
        try {
            candidates.add(File(getDir("games/com.mojang/minecraftpe", MODE_PRIVATE), "options.txt"))
        } catch (ignored: Throwable) {
        }

        for (file in candidates) {
            if (!file.isFile) continue
            try {
                var x: Float? = null
                var y: Float? = null
                var model = -1
                file.bufferedReader().useLines { lines ->
                    lines.forEach { raw ->
                        val line = raw.trim()
                        val sep = line.indexOf(':')
                        if (sep <= 0) return@forEach
                        val key = line.substring(0, sep)
                        val value = line.substring(sep + 1).trim()
                        when (key) {
                            "gfx_touchButton2X" -> value.toFloatOrNull()?.let { x = it }
                            "gfx_touchButton2Y" -> value.toFloatOrNull()?.let { y = it }
                            "ctrl_interactionModel" -> value.toIntOrNull()?.let { model = it }
                        }
                    }
                }
                if (x != null && y != null) {
                    attackButtonNormX = x!!.coerceIn(0f, 1f)
                    attackButtonNormY = y!!.coerceIn(0f, 1f)
                    touchInteractionModel = model
                    Log.i(TAG_ATTACK, "attack button x=$attackButtonNormX y=$attackButtonNormY model=$model from ${file.absolutePath}")
                    return true
                }
            } catch (t: Throwable) {
                Log.w(TAG_ATTACK, "read ${file.absolutePath} failed: ${t.message}")
            }
        }
        Log.w(TAG_ATTACK, "options.txt not found, default attack button x=$attackButtonNormX y=$attackButtonNormY")
        return false
    }

    /**
     * Тап по экранной кнопке attack/destroy, отправленный в штатный
     * onTouchEvent Minecraft. Урон, анимация руки и пакет атаки формируются
     * самим клиентом — для сервера это обычное нажатие, а не синтезированный
     * пакет. Позиция кнопки берётся из options.txt (см.
     * [refreshAttackButtonPosition]), поэтому тап попадает ровно в кнопку,
     * а не в центр экрана (центр в режиме прицела ничего не атакует).
     *
     * Если игрок держит палец на экране, тап идёт как ДОПОЛНИТЕЛЬНЫЙ
     * pointer (ACTION_POINTER_DOWN/UP) со свободным pointerId. Раньше тап
     * всегда слал ACTION_DOWN с id 0: при зажатом настоящем пальце это
     * ломало жест, и касания «залипали» до следующего чистого DOWN.
     */
    fun performInbuiltAttack(): Boolean {
        if (!::gameManager.isInitialized) return false
        if (isFinishing || isDestroyed) return false

        // When Pojav Controls is enabled its full-screen RuntimeSurface consumes
        // every touch event, so a synthetic tap never reaches the game and the
        // TriggerBot cannot hit. Inject a native mouse button instead; it goes
        // through the Pojav input pipeline straight into the game.
        if (PojavControlsMod.isEnabled()) {
            return performNativeMouseAttack()
        }

        val decor = window.decorView
        val width = if (decor.width > 0) decor.width else resources.displayMetrics.widthPixels
        val height = if (decor.height > 0) decor.height else resources.displayMetrics.heightPixels
        if (width <= 0 || height <= 0) return false

        // D-pad & tap to interact: отдельной кнопки атаки нет, атакует тап
        // по миру — бьём по центру. В режимах с джойстиком жмём кнопку.
        val x: Float
        val y: Float
        if (touchInteractionModel == INTERACTION_MODEL_CLASSIC) {
            x = width / 2f
            y = height / 2f
        } else {
            x = (attackButtonNormX * width).coerceIn(1f, width - 1f)
            y = (attackButtonNormY * height).coerceIn(1f, height - 1f)
        }

        // Не наслаиваем синтетические тапы: предыдущий ещё между DOWN и UP.
        if (syntheticTapInFlight) return false

        val realAtDown = snapshotRealPointers()
        val usedIds = HashSet<Int>(realAtDown.size)
        for (pointer in realAtDown) usedIds.add(pointer.first)
        // Берём заведомо высокий свободный pointerId: система выдаёт
        // реальным пальцам id по возрастанию от 0, поэтому 31 с ними
        // не пересечётся (MAX_POINTER_ID = 31).
        var syntheticId = MAX_POINTER_ID
        while (syntheticId in usedIds && syntheticId > 0) syntheticId--

        val multi = realAtDown.isNotEmpty()
        // При добавлении pointer'а к живому жесту Android ждёт downTime
        // исходного жеста, а не текущее время.
        val downTime = if (multi && realGestureDownTime > 0) {
            realGestureDownTime
        } else {
            SystemClock.uptimeMillis()
        }

        val downPointers = ArrayList(realAtDown)
        downPointers.add(Triple(syntheticId, x, y))
        val downAction = if (multi) MotionEvent.ACTION_POINTER_DOWN else MotionEvent.ACTION_DOWN
        val downIndex = if (multi) downPointers.size - 1 else 0
        val down = createGameTouchEvent(
            downTime, SystemClock.uptimeMillis(), downAction, downPointers, downIndex
        )
        val handled = try {
            dispatchTouchEventToGame(down)
        } catch (throwable: Throwable) {
            false
        } finally {
            down.recycle()
        }
        if (!handled) return false
        syntheticTapInFlight = true

        // Небольшой MOVE между DOWN и UP: некоторые gesture-детекторы
        // игнорируют идеально статичный тап, а с ним жест точно склеится.
        decor.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            val move = createGameTouchEvent(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE,
                pointersWithSynthetic(syntheticId, x + 0.5f, y + 0.5f), 0
            )
            try {
                dispatchTouchEventToGame(move)
            } catch (ignored: Throwable) {
            } finally {
                move.recycle()
            }
        }, 18L)

        // Длительность нажатия в человеческом диапазоне: 45-90 мс.
        val holdMs = 45L + kotlin.random.Random.nextInt(46).toLong()
        decor.postDelayed({
            // Флаг снимаем только после отправки UP: иначе следующий тап
            // мог начаться раньше, чем игра получила отпускание кнопки,
            // и кнопка «залипала».
            try {
                if (isFinishing || isDestroyed) return@postDelayed
                // Список пересобираем заново: за время удержания настоящие
                // пальцы могли подняться — тогда синтетический остаётся один
                // и жест корректно завершается обычным ACTION_UP.
                val upPointers = pointersWithSynthetic(syntheticId, x, y)
                val stillMulti = upPointers.size > 1
                val upAction = if (stillMulti) MotionEvent.ACTION_POINTER_UP else MotionEvent.ACTION_UP
                val upIndex = if (stillMulti) upPointers.size - 1 else 0
                val up = createGameTouchEvent(
                    downTime, SystemClock.uptimeMillis(), upAction, upPointers, upIndex
                )
                try {
                    dispatchTouchEventToGame(up)
                } finally {
                    up.recycle()
                }
            } catch (ignored: Throwable) {
            } finally {
                syntheticTapInFlight = false
            }
        }, holdMs)
        return true
    }

    /**
     * Нажатие ЛКМ через нативный инжектор Pojav Controls (libinbuiltmods).
     * Работает даже когда поверх игры висит полноэкранный RuntimeSurface.
     */
    private fun performNativeMouseAttack(): Boolean {
        val holdMs = 45L + kotlin.random.Random.nextInt(36).toLong()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val release = Runnable {
            try {
                PojavControlsMod.nativeSendMouseButton(MotionEvent.BUTTON_PRIMARY, false)
            } catch (ignored: Throwable) {
            }
        }
        return try {
            PojavControlsMod.nativeSendMouseButton(MotionEvent.BUTTON_PRIMARY, true)
            handler.postDelayed(release, holdMs)
            true
        } catch (ignored: Throwable) {
            false
        }
    }

    /**
     * Событие касания с произвольным числом pointer'ов.
     * [pointers] — тройки (pointerId, x, y); для ACTION_POINTER_DOWN/UP
     * [actionIndex] — индекс добавляемого/удаляемого pointer'а в списке.
     */
    private fun createGameTouchEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        pointers: List<Triple<Int, Float, Float>>,
        actionIndex: Int
    ): MotionEvent {
        val properties = Array(pointers.size) { MotionEvent.PointerProperties() }
        val coords = Array(pointers.size) { MotionEvent.PointerCoords() }
        for (index in pointers.indices) {
            val (id, px, py) = pointers[index]
            properties[index].id = id
            properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER
            coords[index].x = px
            coords[index].y = py
            coords[index].pressure = 1.0f
            coords[index].size = 1.0f
        }

        val actionWithIndex = if (
            action == MotionEvent.ACTION_POINTER_DOWN ||
            action == MotionEvent.ACTION_POINTER_UP
        ) {
            action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        } else {
            action
        }

        return MotionEvent.obtain(
            downTime, eventTime, actionWithIndex, pointers.size,
            properties, coords,
            0, 0, 1.0f, 1.0f, touchDeviceId, 0,
            InputDevice.SOURCE_TOUCHSCREEN, 0
        )
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (shouldConsumeMouseMotion(event)) {
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS ||
            event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE) {
            overlayManager?.handleMouseEvent(event)
        }

        if (event.action == MotionEvent.ACTION_SCROLL) {
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (vScroll != 0f) {
                overlayManager?.let { manager ->
                    if (manager.handleScrollEvent(vScroll)) {
                        return true
                    }
                }
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun pojavSendKey(bedrockKeyCode: Int, down: Boolean) {
        PojavControlsMod.nativeSendKey(bedrockKeyCode, down)
    }

    override fun pojavSendMouseButton(androidButton: Int, down: Boolean) {
        PojavControlsMod.nativeSendMouseButton(androidButton, down)
    }

    override fun pojavSendScroll(vertical: Float) {
        PojavControlsMod.nativeSendScroll(vertical)
    }

    override fun pojavSendLookDelta(deltaX: Float, deltaY: Float) {
        PojavControlsMod.nativeSendLookDelta(deltaX, deltaY)
        AimAssistMod.notifyCameraLook()
    }

    override fun pojavSendPointer(x: Float, y: Float) {
        PojavControlsMod.nativeSendPointer(x, y)
    }

    override fun pojavSendTouch(event: MotionEvent): Boolean {
        return super.onTouchEvent(event)
    }

    override fun pojavShowKeyboard() {
        showSoftKeyboard()
    }

    override fun pojavIsMenuOpen(): Boolean {
        if (PreloaderInput.isShowingMenu() || PreloaderInput.isPauseMenuOpen()) return true
        return !PreloaderInput.shouldForceGlobalModMenu() && !PreloaderInput.isHudScreenOpen()
    }

    override fun onPause() {
        overlayManager?.releaseModMenuShortcut()
        overlayManager?.releaseKeybinds()
        val shouldRestartAfterNormalExit = shouldRestartAfterNormalExit()
        if (shouldRestartAfterNormalExit) {
            PreloaderInput.cancelDocumentRequest("Minecraft closed")
            ModManager.disableAndUnloadLoadedMods()
            prepareNormalExitCleanup()
            scheduleNormalExitProcessRestart()
        }
        MinecraftActivityState.onPaused(this)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) {
            overlayManager?.releaseModMenuShortcut()
            overlayManager?.releaseKeybinds()
        }
        super.onWindowFocusChanged(hasFocus)
    }

    override fun onDestroy() {
        PreloaderInput.cancelDocumentRequest("Minecraft closed")
        ModManager.disableAndUnloadLoadedMods()
        val shouldPrepareNormalExit = shouldRestartAfterNormalExit()
        if (shouldPrepareNormalExit) {
            prepareNormalExitCleanup()
        }

        preloaderTextInput = null
        previousInputFocus = null
        PreloaderInput.clearActivity()
        MinecraftActivityState.onDestroyed(this)
        MinecraftLaunchSession.clear()
        stopInbuiltModServices()
        setLeviKeepRunningInBackground(false)
        MinecraftForegroundService.stop(this)

        try {
            super.onDestroy()
        } finally {
            if (shouldPrepareNormalExit) {
                scheduleNormalExitProcessRestart()
            }
        }
    }

    private fun shouldRestartAfterNormalExit(): Boolean {
        return gameRuntimeStarted && isFinishing && !CrashReporter.isHandlingCrash()
    }

    private fun prepareNormalExitCleanup() {
        if (normalExitPrepared) return
        normalExitPrepared = true
    }

    private fun scheduleNormalExitProcessRestart() {
        if (normalExitRestartScheduled) return
        normalExitRestartScheduled = true

        MinecraftProcessRestarter.restartLauncherAfterMinecraftExit(this)
    }

    override fun getAssets(): AssetManager {
        return if (::gameManager.isInitialized) {
            gameManager.getAssets()
        } else {
            super.getAssets()
        }
    }

    override fun getFilesDir(): File {
        return resolveStorageDir(MinecraftLauncher.EXTRA_STORAGE_FILES_DIR, super.getFilesDir())
    }

    override fun tick() {
        super.tick()
        try {
            overlayManager?.tick()
        } catch (_: RuntimeException) {
        }
    }

    override fun getDataDir(): File {
        return resolveStorageDir(MinecraftLauncher.EXTRA_STORAGE_DATA_DIR, super.getDataDir())
    }

    override fun getExternalFilesDir(type: String?): File? {
        val baseDir = resolveStorageDir(
            MinecraftLauncher.EXTRA_STORAGE_EXTERNAL_FILES_DIR,
            super.getExternalFilesDir(null)
        )
        return if (type.isNullOrEmpty()) {
            baseDir
        } else {
            File(baseDir, type).also { it.mkdirs() }
        }
    }

    override fun getInternalStoragePath(): String {
        return getFilesDir().absolutePath
    }

    override fun getExternalStoragePath(): String {
        return (getExternalFilesDir(null) ?: getFilesDir()).absolutePath
    }

    private fun resolveStorageDir(extraName: String, fallback: File?): File {
        val path = intent?.getStringExtra(extraName)
        val dir = if (!path.isNullOrEmpty()) File(path) else fallback ?: super.getFilesDir()
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    override fun getDatabasePath(name: String): File {
        val dbDir = File(getDataDir(), "databases")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        return File(dbDir, name)
    }

    override fun getCacheDir(): File {
        return resolveStorageDir(MinecraftLauncher.EXTRA_STORAGE_CACHE_DIR, super.getCacheDir())
    }

    private fun initializePreloaderTextInput() {
        val input = PreloaderTextInput(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            isEmojiCompatEnabled = false
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN or
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.TRANSPARENT)
            isCursorVisible = false
            alpha = 0f
            visibility = View.GONE
        }
        findViewById<ViewGroup>(android.R.id.content).addView(
            input,
            ViewGroup.LayoutParams(1, 1)
        )
        preloaderTextInput = input
    }

    fun showSoftKeyboard() {
        runOnUiThread {
            val input = preloaderTextInput ?: return@runOnUiThread
            previousInputFocus = currentFocus?.takeUnless { it === input }
            input.visibility = View.VISIBLE
            input.setText("")
            input.requestFocus()
            input.setSelection(0)

            val inputMethodManager =
                getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.restartInput(input)
            if (!inputMethodManager.showSoftInput(
                    input,
                    InputMethodManager.SHOW_IMPLICIT
                )
            ) {
                inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
            }
        }
    }

    fun hideSoftKeyboard() {
        runOnUiThread {
            val input = preloaderTextInput ?: return@runOnUiThread
            val inputMethodManager =
                getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(input.windowToken, 0)
            input.clearFocus()
            input.visibility = View.GONE

            previousInputFocus
                ?.takeIf { it.isAttachedToWindow && it.visibility == View.VISIBLE }
                ?.requestFocus()
            previousInputFocus = null
        }
    }
}
