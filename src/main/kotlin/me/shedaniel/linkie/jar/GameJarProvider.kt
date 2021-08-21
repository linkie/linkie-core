package me.shedaniel.linkie.jar

import com.soywiz.korio.file.VfsFile

interface GameJarProvider {
    suspend fun canProvideVersion(version: String): Boolean
    suspend fun provide(version: String): Result
    
    data class Result(
        val minecraftFile: VfsFile,
        val libraries: List<VfsFile>,
    )
}
