package com.ziroh.zunucamera

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.WatchKey
import java.nio.file.WatchService

class AppFileObserver(private val directoryPath: Path, private val eventListener: FileEventListener) {
    private var watchService: WatchService? = null
    private var watchKey: WatchKey? = null

    interface FileEventListener {
        fun onFileCreated(file: Path)
        fun onFileDeleted(file: Path)
    }

    fun startWatching() {
        watchService = FileSystems.getDefault().newWatchService()
        watchKey = directoryPath.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)

        while (true) {
            val key: WatchKey = watchService!!.take()

            for (event in key.pollEvents()) {
                val kind = event.kind()
                val context = event.context() as Path
                val file = directoryPath.resolve(context)

                when (kind) {
                    ENTRY_CREATE -> eventListener.onFileCreated(file)
                    ENTRY_DELETE -> eventListener.onFileDeleted(file)
                    else -> Unit
                }
            }

            key.reset()
        }
    }

    fun stopWatching() {
        watchKey?.cancel()
        watchService?.close()
    }
}



