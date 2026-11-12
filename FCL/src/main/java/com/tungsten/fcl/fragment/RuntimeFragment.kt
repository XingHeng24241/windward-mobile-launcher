package com.tungsten.fcl.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import com.tungsten.fcl.R
import com.tungsten.fcl.activity.SplashActivity
import com.tungsten.fcl.databinding.FragmentRuntimeBinding
import com.tungsten.fcl.game.WindwardModpackBootstrap
import com.tungsten.fcl.setting.DownloadProviders
import com.tungsten.fcl.setting.Profile
import com.tungsten.fcl.setting.Profiles
import com.tungsten.fcl.util.RuntimeUtils
import com.tungsten.fclauncher.utils.Architecture
import com.tungsten.fclauncher.utils.FCLPath
import com.tungsten.fclcore.fakefx.beans.value.ChangeListener
import com.tungsten.fclcore.task.Schedulers
import com.tungsten.fclcore.task.Task
import com.tungsten.fclcore.task.TaskExecutor
import com.tungsten.fclcore.task.TaskListener
import com.tungsten.fcllibrary.component.FCLFragment
import com.tungsten.fcllibrary.component.dialog.FCLAlertDialog
import com.tungsten.fcllibrary.component.theme.ThemeEngine
import com.tungsten.fcllibrary.component.view.FCLImageView
import com.tungsten.fcllibrary.component.view.FCLProgressBar
import com.tungsten.fcllibrary.component.view.FCLTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuntimeFragment : FCLFragment(), View.OnClickListener {
    private lateinit var bind: FragmentRuntimeBinding
    var lwjgl = false
    var cacio = false
    var cacio17 = false
    var java8 = false
    var java25 = false
    var java17 = false
    var java21 = false
    var jna = false
    var windwardPack = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_runtime, container, false)
        bind = FragmentRuntimeBinding.bind(view)
        bind.install.setOnClickListener(this)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { initState() }
            refreshDrawables()
            check()
        }
        return view
    }

    private fun initState() {
        lwjgl = (activity as SplashActivity).lwjgl
        cacio = (activity as SplashActivity).cacio
        cacio17 = (activity as SplashActivity).cacio17
        java8 = (activity as SplashActivity).java8
        java17 = (activity as SplashActivity).java17
        java21 = (activity as SplashActivity).java21
        java25 = (activity as SplashActivity).java25
        jna = (activity as SplashActivity).jna
        windwardPack = (activity as SplashActivity).windwardPack
    }

    private fun refreshDrawables() {
        if (context != null) {
            val stateUpdate =
                AppCompatResources.getDrawable(requireContext(), R.drawable.ic_baseline_update_24)
            val stateDone =
                AppCompatResources.getDrawable(requireContext(), R.drawable.ic_baseline_done_24)

            stateUpdate?.setTint(ThemeEngine.getTheme().getColor2())
            stateDone?.setTint(ThemeEngine.getTheme().getColor2())

            bind.apply {
                lwjglState.setBackgroundDrawable(if (lwjgl) stateDone else stateUpdate)
                cacioState.setBackgroundDrawable(if (cacio) stateDone else stateUpdate)
                cacio17State.setBackgroundDrawable(if (cacio17) stateDone else stateUpdate)
                java8State.setBackgroundDrawable(if (java8) stateDone else stateUpdate)
                java17State.setBackgroundDrawable(if (java17) stateDone else stateUpdate)
                java21State.setBackgroundDrawable(if (java21) stateDone else stateUpdate)
                java25State.setBackgroundDrawable(if (java25) stateDone else stateUpdate)
                jnaState.setBackgroundDrawable(if (jna) stateDone else stateUpdate)
                modpackState.setBackgroundDrawable(if (windwardPack) stateDone else stateUpdate)
            }
        }
    }

    private val areRuntimesLatest: Boolean
        get() = lwjgl && cacio && cacio17 && java8 && java25 && java17 && java21 && jna

    private val isLatest: Boolean
        get() = areRuntimesLatest && windwardPack

    private fun check() {
        if (isLatest) {
            view?.visibility = View.GONE
            (activity as SplashActivity).enterLauncher()
        } else if (areRuntimesLatest && installing && !modpackInstalling) {
            launchModpackInstall()
        }
    }

    private var installing = false
    private var modpackInstalling = false
    private var modpackExecutor: TaskExecutor? = null
    private var displayedTask: Task<*>? = null
    private var displayedMessageListener: ChangeListener<String>? = null

    private fun install() {
        if (installing) return

        installing = true
        bind.apply {
            if (!lwjgl) {
                launchInstall(lwjglState, lwjglProgress, lwjglDetail, { lwjgl = true }) {
                    RuntimeUtils.install(context, FCLPath.LWJGL_DIR, "app_runtime/lwjgl", it)
                }
            }
            if (!cacio) {
                launchInstall(cacioState, cacioProgress, cacioDetail, { cacio = true }) {
                    RuntimeUtils.install(
                        context,
                        FCLPath.CACIOCAVALLO_8_DIR,
                        "app_runtime/caciocavallo",
                        it
                    )
                }
            }
            if (!cacio17) {
                launchInstall(cacio17State, cacio17Progress, cacio17Detail, { cacio17 = true }) {
                    RuntimeUtils.install(
                        context,
                        FCLPath.CACIOCAVALLO_17_DIR,
                        "app_runtime/caciocavallo17",
                        it
                    )
                }
            }
            if (!java8) {
                launchInstall(java8State, java8Progress, java8Detail, { java8 = true }) {
                    RuntimeUtils.installJava(context, FCLPath.JAVA_8_PATH, "app_runtime/java/jre8", it)
                }
            }
            if (!java17) {
                launchInstall(java17State, java17Progress, java17Detail, { java17 = true }) {
                    RuntimeUtils.installJava(
                        context,
                        FCLPath.JAVA_17_PATH,
                        "app_runtime/java/jre17",
                        it
                    )
                }
            }
            if (!java21) {
                launchInstall(java21State, java21Progress, java21Detail, { java21 = true }) {
                    RuntimeUtils.installJava(
                        context,
                        FCLPath.JAVA_21_PATH,
                        "app_runtime/java/jre21",
                        it
                    )
                }
            }
            if (!java25) {
                launchInstall(java25State, java25Progress, java25Detail, { java25 = true }) {
                    RuntimeUtils.installJava(
                        context,
                        FCLPath.JAVA_25_PATH,
                        "app_runtime/java/jre25",
                        it
                    )
                }
            }
            if (!jna) {
                launchInstall(jnaState, jnaProgress, jnaDetail, { jna = true }) {
                    RuntimeUtils.installJna(context, FCLPath.JNA_PATH, "app_runtime/jna", it)
                }
            }
        }
        check()
    }

    private fun launchModpackInstall() {
        if (modpackInstalling || windwardPack) return
        modpackInstalling = true
        bind.apply {
            modpackState.visibility = View.GONE
            modpackProgress.visibility = View.VISIBLE
            modpackDetail.setText(R.string.splash_runtime_modpack_preparing)
            modpackDetail.visibility = View.VISIBLE
        }

        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            try {
                (activity as SplashActivity).prepareLauncherCore()
                val alreadyInstalled = withContext(Dispatchers.IO) {
                    WindwardModpackBootstrap.isInstalled(appContext)
                }
                if (alreadyInstalled) {
                    markModpackReady()
                    return@launch
                }
                val profile = Profiles.getSelectedProfile()
                val task = withContext(Dispatchers.IO) {
                    WindwardModpackBootstrap.createInstallTask(appContext, profile)
                }
                startModpackTask(profile, task)
            } catch (error: Exception) {
                finishModpackFailure(error)
            }
        }
    }

    private fun startModpackTask(profile: Profile, task: Task<*>) {
        val executor = task.executor(object : TaskListener() {
            override fun onRunning(task: Task<*>) {
                displayTask(task)
            }

            override fun onStop(success: Boolean, executor: TaskExecutor) {
                Schedulers.androidUIThread().execute {
                    clearDisplayedTask()
                    if (success) {
                        finishModpackSuccess(profile)
                    } else {
                        finishModpackFailure(executor.exception)
                    }
                }
            }
        })
        modpackExecutor = executor
        executor.start()
    }

    private fun displayTask(task: Task<*>) {
        Schedulers.androidUIThread().execute {
            if (!isAdded || view == null) return@execute
            clearDisplayedTask()
            displayedTask = task
            bind.modpackProgress.percentProgressProperty().bind(task.progressProperty())
            bind.modpackDetail.text = describeTask(task)
            val listener = ChangeListener<String> { _, _, message ->
                if (!message.isNullOrBlank() && isAdded && view != null) {
                    bind.modpackDetail.text = message
                }
            }
            task.messageProperty().addListener(listener)
            displayedMessageListener = listener
        }
    }

    private fun describeTask(task: Task<*>): String {
        val stage = task.stage ?: task.inheritedStage
        return when {
            stage == "fcl.modpack.download" ->
                getString(R.string.splash_runtime_modpack_downloading)

            stage == "fcl.modpack" -> getString(R.string.splash_runtime_modpack_unpacking)
            stage?.startsWith("fcl.install.minecraft") == true ->
                getString(R.string.splash_runtime_modpack_minecraft)

            stage?.startsWith("fcl.install.fabric") == true ->
                getString(R.string.splash_runtime_modpack_fabric)

            else -> getString(R.string.splash_runtime_modpack_installing)
        }
    }

    private fun clearDisplayedTask() {
        displayedTask?.let { task ->
            displayedMessageListener?.let { task.messageProperty().removeListener(it) }
        }
        displayedTask = null
        displayedMessageListener = null
        if (::bind.isInitialized) {
            bind.modpackProgress.percentProgressProperty().unbind()
        }
    }

    private fun finishModpackSuccess(profile: Profile) {
        val appContext = context?.applicationContext ?: return
        lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    WindwardModpackBootstrap.completeInstallation(appContext, profile)
                }.exceptionOrNull()
            }
            if (error != null) {
                finishModpackFailure(error)
                return@launch
            }

            profile.selectedVersion = WindwardModpackBootstrap.VERSION_NAME
            markModpackReady()
        }
    }

    private fun markModpackReady() {
        windwardPack = true
        (activity as? SplashActivity)?.windwardPack = true
        modpackInstalling = false
        installing = false
        if (!isAdded || view == null) return
        bind.apply {
            modpackProgress.visibility = View.GONE
            modpackState.visibility = View.VISIBLE
            modpackDetail.visibility = View.GONE
        }
        refreshDrawables()
        check()
    }

    private fun finishModpackFailure(error: Throwable?) {
        modpackExecutor = null
        modpackInstalling = false
        installing = false
        if (!isAdded || view == null) return
        clearDisplayedTask()
        bind.apply {
            modpackProgress.visibility = View.GONE
            modpackState.visibility = View.VISIBLE
            modpackDetail.visibility = View.GONE
        }
        refreshDrawables()
        val message = when (error) {
            is WindwardModpackBootstrap.VersionConflictException ->
                getString(R.string.splash_runtime_modpack_conflict)

            is WindwardModpackBootstrap.InvalidBundledModpackException ->
                getString(R.string.splash_runtime_modpack_invalid)

            is WindwardModpackBootstrap.AlreadyInstalledException -> {
                markModpackReady()
                return
            }

            null -> getString(R.string.splash_runtime_modpack_failed)
            else -> DownloadProviders.localizeErrorMessage(requireContext(), error)
        }
        showErrorDialog(message)
    }

    private fun launchInstall(
        state: FCLImageView,
        progress: FCLProgressBar,
        detail: FCLTextView,
        markDone: () -> Unit,
        block: (RuntimeUtils.InstallListener) -> Unit
    ) {
        val listener = createListener(detail)
        state.visibility = View.GONE
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching { block(listener) }.exceptionOrNull()
            }
            state.visibility = View.VISIBLE
            progress.visibility = View.GONE
            detail.visibility = View.GONE
            if (error != null) {
                showErrorDialog(error.toString())
            } else {
                markDone()
            }
            refreshDrawables()
            check()
        }
    }

    private fun createListener(detail: FCLTextView): RuntimeUtils.InstallListener {
        val mainHandler = Handler(Looper.getMainLooper())
        var lastUpdateTime = 0L
        fun post(text: String, force: Boolean = false) {
            val now = SystemClock.elapsedRealtime()
            // 节流，避免解压大量小文件时刷爆主线程消息队列；阶段文案不节流
            if (!force && now - lastUpdateTime < DETAIL_UPDATE_INTERVAL_MS) return
            lastUpdateTime = now
            mainHandler.post {
                if (isAdded) {
                    detail.text = text
                    detail.visibility = View.VISIBLE
                }
            }
        }
        return object : RuntimeUtils.InstallListener {
            override fun onUpdate(detailText: String) {
                post(detailText)
            }

            override fun onStage(resId: Int) {
                post(getString(resId), force = true)
            }
        }
    }

    override fun onClick(view: View) {
        if (view === bind.install) {
            val deviceArch = Architecture.archAsString(Architecture.getDeviceArchitecture())
            if (!isJavaArchSupported(deviceArch)) {
                showErrorDialog(
                    getString(
                        R.string.missing_runtime_arch_files,
                        deviceArch,
                        "FCL-release-x.x.x.x-$deviceArch.apk",
                        "FCL-release-x.x.x.x-all.apk"
                    )
                )
                return
            }
            install()
        }
    }

    private fun isJavaArchSupported(arch: String): Boolean {
        try {
            val javaDirs = listOf("jre8", "jre17", "jre21", "jre25")
            val assetManager = requireContext().assets
            var supportedCount = 0
            for (javaDir in javaDirs) {
                val dirPath = "app_runtime/java/$javaDir"
                val files = assetManager.list(dirPath)
                if (files != null) {
                    val expectedFile = "bin-$arch.tar.xz"
                    if (files.contains(expectedFile)) {
                        supportedCount++
                    }
                }
            }
            return supportedCount > 0
        } catch (e: Exception) {
            showErrorDialog(e.toString())
            return false
        }
    }

    private fun showErrorDialog(message: String) {
        installing = false
        modpackInstalling = false
        lifecycleScope.launch(Dispatchers.Main) {
            FCLAlertDialog.Builder(requireContext())
                .setMessage(message)
                .setPositiveButton {
                }
                .create()
                .show()
        }
    }

    override fun onDestroyView() {
        clearDisplayedTask()
        super.onDestroyView()
    }

    companion object {
        private const val DETAIL_UPDATE_INTERVAL_MS = 50L
    }
}
