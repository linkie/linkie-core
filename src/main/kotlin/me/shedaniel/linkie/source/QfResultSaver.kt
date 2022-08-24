package me.shedaniel.linkie.source

import net.fabricmc.fernflower.api.IFabricResultSaver
import org.jetbrains.java.decompiler.main.DecompilerContext
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.jar.Manifest

class QfResultSaver(private val output: File) : IFabricResultSaver {
    var saveExecutors: MutableMap<String, ExecutorService> = HashMap()

    override fun createArchive(path: String, archiveName: String, manifest: Manifest?) {
        val key = "$path/$archiveName"
        saveExecutors[key] = Executors.newSingleThreadExecutor()
    }

    override fun saveClassEntry(path: String, archiveName: String, qualifiedName: String, entryName: String, content: String?) {
        saveClassEntry(path, archiveName, qualifiedName, entryName, content, null)
    }

    override fun saveClassEntry(path: String, archiveName: String, qualifiedName: String, entryName: String, content: String?, mapping: IntArray?) {
        val key = "$path/$archiveName"
        val executor: ExecutorService = saveExecutors[key]!!
        executor.submit {
            try {
                if (content != null) {
                    val file = output.resolve(entryName)
                    file.parentFile.mkdirs()
                    file.writeBytes(content.toByteArray(StandardCharsets.UTF_8))
                }
            } catch (e: IOException) {
                DecompilerContext.getLogger().writeMessage("Cannot write entry $entryName", e)
            }
        }
    }

    override fun closeArchive(path: String, archiveName: String) {
        val key = "$path/$archiveName"
        val executor: ExecutorService = saveExecutors[key]!!
        executor.shutdown()
        saveExecutors.remove(key)
    }

    override fun saveFolder(path: String?) {}
    override fun copyFile(source: String?, path: String?, entryName: String?) {}
    override fun saveClassFile(path: String?, qualifiedName: String?, entryName: String?, content: String?, mapping: IntArray?) {}
    override fun saveDirEntry(path: String?, archiveName: String?, entryName: String?) {}
    override fun copyEntry(source: String?, path: String?, archiveName: String?, entry: String?) {}
}