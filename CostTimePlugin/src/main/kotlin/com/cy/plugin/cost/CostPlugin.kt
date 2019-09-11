package com.cy.plugin.cost

import com.android.build.api.transform.*
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.pipeline.TransformManager
import com.cy.plugin.cost.visitor.CostClassVisitor
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

class CostTimePlugin : Transform(), Plugin<Project> {
    /**
     * Returns the unique name of the transform.
     *
     *
     * This is associated with the type of work that the transform does. It does not have to be
     * unique per variant.
     */
    override fun getName() = "CostTimePlugin"

    /**
     * Returns the type(s) of data that is consumed by the Transform. This may be more than
     * one type.
     *
     * **This must be of type [QualifiedContent.DefaultContentType]**
     */
    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return  TransformManager.CONTENT_CLASS
    }

    /**
     * Returns whether the Transform can perform incremental work.
     *
     *
     * If it does, then the TransformInput may contain a list of changed/removed/added files, unless
     * something else triggers a non incremental run.
     */
    override fun isIncremental(): Boolean {
        return false
    }

    /**
     * Returns the scope(s) of the Transform. This indicates which scopes the transform consumes.
     */
    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    override fun apply(project: Project) {

        println("========================")
        println("Hello gradle plugin with Kotlin")
        println("========================")

        val android = project.extensions.getByType(AppExtension::class.java)
        android.registerTransform(this)
    }

    override fun transform(transformInvocation: TransformInvocation?) {
        println("-----------cost plugin visit start------------")
        val startTime = System.currentTimeMillis()

        val inputs = transformInvocation?.inputs
        val outputProvider = transformInvocation?.outputProvider

        // 删除之前的输出
        outputProvider?.deleteAll()

        // 遍历 inputs
        inputs?.forEach { input ->
            input.directoryInputs.forEach {
                handleDirectoryInputs(it)

                // 处理完输入文件之后，要把输出给下一个任务
                val dest = outputProvider?.getContentLocation(it.name, it.contentTypes, it.scopes, Format.DIRECTORY)
                FileUtils.copyDirectory(it.file, dest)
            }

            input.jarInputs.forEach {
                handleJarInputs(it, outputProvider)
            }
        }

        val cost = (System.currentTimeMillis() - startTime) / 1000
        println("-----------cost plugin visit end------------")
        println("CostPlugin cost: $cost s")
    }

    /**
     * 遍历 DirectoryInputs
     */
    private fun handleDirectoryInputs(directoryInput: DirectoryInput?) {
        if (directoryInput?.file?.isDirectory == true) {
            directoryInput.file?.listFiles { file ->
                val name = file.name
                if (checkClassFile(name)) {
                    println("------deal with 'class' file <$name> ----------")

                    val classReader = ClassReader(file.readBytes())
                    val classWriter = ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                    val classVisitor = CostClassVisitor(classWriter)

                    classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)

                    val code = classWriter.toByteArray()
                    val fos = FileOutputStream(file.absolutePath)
                    fos.write(code)
                    fos.close()
                }
                true
            }
        }
    }

    /**
     * 遍历 jar 中的 class 文件
     */
    private fun handleJarInputs(jarInput: JarInput?, outputProvider: TransformOutputProvider?) {
        if (jarInput?.file?.absolutePath?.endsWith(".jar") != true) {
            return
        }

        var jarName = jarInput.name
        val md5Name = DigestUtils.md5Hex(jarInput.file.absolutePath)
        if (jarName.endsWith(".jar")) {
            jarName = jarName.substring(0, jarName.length - 4)
        }

        val jarFile = JarFile(jarInput.file)
        val enumeration = jarFile.entries()
        val tempFile = File(jarInput.file.parent + File.separator + "class_temp.jar")
        // 避免上次的缓存被重复插入
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val jarOutputStream = JarOutputStream(FileOutputStream(tempFile))
        // 保存
        while (enumeration.hasMoreElements()) {
            val jarEntry = enumeration.nextElement()
            val entryName = jarEntry.name
            val zipEntry = ZipEntry(entryName)
            val inputStream = jarFile.getInputStream(jarEntry)

            //插桩class
            if (checkClassFile(entryName)) {
                println("----------- deal with 'jar' class file <$entryName> -----------")
                jarOutputStream.putNextEntry(zipEntry)

                val classReader = ClassReader(IOUtils.toByteArray(inputStream))
                val classWriter = ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                val classVisitor = CostClassVisitor(classWriter)

                classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)

                val code = classWriter.toByteArray()
                jarOutputStream.write(code)
            } else {
                jarOutputStream.putNextEntry(zipEntry)
                jarOutputStream.write(IOUtils.toByteArray(inputStream))
            }
            jarOutputStream.closeEntry()
        }

        // 遍历结束
        jarOutputStream.close()
        jarFile.close()

        val dest = outputProvider?.getContentLocation(jarName + md5Name, jarInput.contentTypes, jarInput.scopes, Format.JAR)
        FileUtils.copyFile(tempFile, dest)
        tempFile.delete()
    }

    private fun checkClassFile(name: String) = name.endsWith(".class") && !name.startsWith("R\$") &&
            "R.class" != name && "BuildConfig.class" != name
}