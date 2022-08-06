package me.shedaniel.linkie.source

import net.fabricmc.fernflower.api.IFabricResultSaver
import org.jetbrains.java.decompiler.main.DecompilerContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.function.Supplier
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class QfResultSaver(private val output: File, private val lineMapFile: Supplier<File?>) : IFabricResultSaver {
    var outputStreams: MutableMap<String, ZipOutputStream> = HashMap()
    var saveExecutors: MutableMap<String, ExecutorService> = HashMap()
    var lineMapWriter: PrintWriter? = null

    override fun createArchive(path: String, archiveName: String, manifest: Manifest?) {
        val key = "$path/$archiveName"
        try {
            val fos = FileOutputStream(output)
            val zos: ZipOutputStream = if (manifest == null) ZipOutputStream(fos) else JarOutputStream(fos, manifest)
            outputStreams[key] = zos
            saveExecutors[key] = Executors.newSingleThreadExecutor()
        } catch (e: IOException) {
            throw RuntimeException("Unable to create archive: $output", e)
        }
        if (lineMapFile.get() != null) {
            try {
                lineMapWriter = PrintWriter(FileWriter(lineMapFile.get()))
            } catch (e: IOException) {
                throw RuntimeException("Unable to create line mapping file: " + lineMapFile.get(), e)
            }
        }
    }

    override fun saveClassEntry(path: String, archiveName: String, qualifiedName: String, entryName: String, content: String?) {
        saveClassEntry(path, archiveName, qualifiedName, entryName, content, null)
    }

    override fun saveClassEntry(path: String, archiveName: String, qualifiedName: String, entryName: String, content: String?, mapping: IntArray?) {
        val key = "$path/$archiveName"
        val executor: ExecutorService = saveExecutors[key]!!
        executor.submit {
            val zos: ZipOutputStream = outputStreams[key]!!
            try {
                zos.putNextEntry(ZipEntry(entryName))
                if (content != null) {
                    zos.write(content.toByteArray(StandardCharsets.UTF_8))
                }
            } catch (e: IOException) {
                DecompilerContext.getLogger().writeMessage("Cannot write entry $entryName", e)
            }
            if (mapping != null && lineMapWriter != null) {
                var maxLine = 0
                var maxLineDest = 0
                val builder = StringBuilder()
                var i = 0
                while (i < mapping.size) {
                    maxLine = Math.max(maxLine, mapping[i])
                    maxLineDest = Math.max(maxLineDest, mapping[i + 1])
                    builder.append("\t").append(mapping[i]).append("\t").append(mapping[i + 1]).append("\n")
                    i += 2
                }
                lineMapWriter!!.println(qualifiedName + "\t" + maxLine + "\t" + maxLineDest)
                lineMapWriter!!.println(builder.toString())
            }
        }
    }

    override fun closeArchive(path: String, archiveName: String) {
        val key = "$path/$archiveName"
        val executor: ExecutorService = saveExecutors[key]!!
        val closeFuture: Future<*> = executor.submit {
            val zos: ZipOutputStream = outputStreams[key]!!
            try {
                zos.close()
            } catch (e: IOException) {
                throw RuntimeException("Unable to close zip. $key", e)
            }
        }
        executor.shutdown()
        try {
            closeFuture.get()
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        } catch (e: ExecutionException) {
            throw RuntimeException(e)
        }
        outputStreams.remove(key)
        saveExecutors.remove(key)
        if (lineMapWriter != null) {
            lineMapWriter!!.flush()
            lineMapWriter!!.close()
        }
    }

    override fun saveFolder(path: String?) {}
    override fun copyFile(source: String?, path: String?, entryName: String?) {}
    override fun saveClassFile(path: String?, qualifiedName: String?, entryName: String?, content: String?, mapping: IntArray?) {}
    override fun saveDirEntry(path: String?, archiveName: String?, entryName: String?) {}
    override fun copyEntry(source: String?, path: String?, archiveName: String?, entry: String?) {}
}