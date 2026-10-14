/*
 * Copyright 2024-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.lemke.commonutils

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.io.DataInputStream
import java.io.InputStream
import java.lang.invoke.MethodType
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.relativeTo

/**
 * A Konsist member or class that one supported version lacks throws NoSuchMethodError, NoSuchFieldError or
 * NoClassDefFoundError on the consumers that use that version. The test resolves every Konsist reference in the
 * fixtures' bytecode, including inherited members, against the jar of each supported version.
 */
class KonsistApiContractTest : ShouldSpec() {
    private val getName = MethodReference("com/lemonappdev/konsist/api/declaration/KoClassDeclaration", "getName", "()Ljava/lang/String;")
    private val privateModifier =
        FieldReference("com/lemonappdev/konsist/api/KoModifier", "PRIVATE", "Lcom/lemonappdev/konsist/api/KoModifier;")
    private val modifierClass = ClassReference("com/lemonappdev/konsist/api/KoModifier")

    init {
        should("resolve every Konsist reference of the fixtures in each supported Konsist version") {
            val references = fixtureClassFiles().flatMap { it.konsistReferences() }.toSet()
            val jars = supportedJars()

            jars.size shouldBe 2
            jars.forEach { (version, jar) ->
                KonsistJar(jar).use { konsist -> references.filterNot(konsist::resolves).map { "$version: $it" }.shouldBeEmpty() }
            }
        }
        should("collect method, field and class references from the constant pool") {
            val references = fixtureClassFiles().flatMap { it.konsistReferences() }.toSet()

            references shouldContain getName
            references shouldContain privateModifier
            references shouldContain modifierClass
        }
        should("report the references a Konsist version lacks") {
            val wrongReturnType = getName.copy(descriptor = "()I")
            val unknownMethod = getName.copy(name = "getNothing")
            val unknownField = privateModifier.copy(name = "NOTHING")
            val wrongFieldType = privateModifier.copy(descriptor = "I")
            val unknownClass = ClassReference("com/lemonappdev/konsist/api/Nothing")
            val missing = listOf(wrongReturnType, unknownMethod, unknownField, wrongFieldType, unknownClass)

            KonsistJar(supportedJars().values.first()).use { konsist ->
                (listOf(getName, privateModifier, modifierClass) + missing).filterNot(konsist::resolves) shouldContainExactlyInAnyOrder
                    missing
            }
        }
    }

    private fun supportedJars(): Map<String, Path> =
        System
            .getProperties()
            .stringPropertyNames()
            .filter { it.startsWith(JAR_PROPERTY_PREFIX) }
            .associate { it.removePrefix(JAR_PROPERTY_PREFIX) to Path.of(System.getProperty(it)) }

    private fun fixtureClassFiles(): List<ByteArray> {
        val codeSource = checkNotNull(Class.forName("de.lemke.commonutils.ArchitectureKt").protectionDomain?.codeSource)
        val location = Path.of(codeSource.location.toURI())
        return if (location.isDirectory()) {
            Files.walk(location).use { paths ->
                paths
                    .filter { it.extension == "class" && it.relativeTo(location).startsWith("de") }
                    .map { Files.readAllBytes(it) }
                    .toList()
            }
        } else {
            ZipFile(location.toFile()).use { zip ->
                zip
                    .entries()
                    .asSequence()
                    .filter { it.name.endsWith(".class") && it.name.startsWith("de/lemke/commonutils/") }
                    .map { entry -> zip.getInputStream(entry).use(InputStream::readBytes) }
                    .toList()
            }
        }
    }

    private fun ByteArray.konsistReferences(): List<KonsistReference> {
        val input = DataInputStream(inputStream())
        input.skipBytes(CLASS_HEADER_BYTES)
        val count = input.readUnsignedShort()
        val utf8 = arrayOfNulls<String>(count)
        val classNameIndex = IntArray(count)
        val nameAndType = Array(count) { IntArray(2) }
        val memberRefs = mutableListOf<Triple<Int, Int, Int>>()
        var index = 1
        while (index < count) {
            val tag = input.readUnsignedByte()
            when (tag) {
                TAG_UTF8 -> utf8[index] = input.readUTF()
                TAG_CLASS -> classNameIndex[index] = input.readUnsignedShort()
                TAG_NAME_AND_TYPE -> nameAndType[index] = intArrayOf(input.readUnsignedShort(), input.readUnsignedShort())
                TAG_FIELD_REF, TAG_METHOD_REF, TAG_INTERFACE_METHOD_REF ->
                    memberRefs += Triple(tag, input.readUnsignedShort(), input.readUnsignedShort())
                else -> input.skipBytes(constantSize(tag))
            }
            index += if (tag == TAG_LONG || tag == TAG_DOUBLE) 2 else 1
        }
        val classes = classNameIndex.filter { it != 0 }.map { ClassReference(utf8[it]!!) }
        val members =
            memberRefs.map { (tag, classIndex, nameAndTypeIndex) ->
                val owner = utf8[classNameIndex[classIndex]]!!
                val (name, descriptor) = nameAndType[nameAndTypeIndex].map { utf8[it]!! }
                if (tag == TAG_FIELD_REF) FieldReference(owner, name, descriptor) else MethodReference(owner, name, descriptor)
            }
        return (classes + members).filter { it.owner.startsWith(KONSIST_PACKAGE) }
    }

    private fun constantSize(tag: Int): Int =
        when (tag) {
            TAG_STRING, TAG_METHOD_TYPE, TAG_MODULE, TAG_PACKAGE -> 2
            TAG_METHOD_HANDLE -> 3
            TAG_INTEGER, TAG_FLOAT, TAG_DYNAMIC, TAG_INVOKE_DYNAMIC -> 4
            TAG_LONG, TAG_DOUBLE -> 8
            else -> error("Unknown constant pool tag $tag")
        }

    private sealed interface KonsistReference {
        val owner: String
    }

    private data class ClassReference(
        override val owner: String,
    ) : KonsistReference

    private data class MethodReference(
        override val owner: String,
        val name: String,
        val descriptor: String,
    ) : KonsistReference

    private data class FieldReference(
        override val owner: String,
        val name: String,
        val descriptor: String,
    ) : KonsistReference

    private class KonsistJar(
        jar: Path,
    ) : AutoCloseable {
        private val loader = KonsistClassLoader(jar.toUri().toURL(), KonsistJar::class.java.classLoader)

        fun resolves(reference: KonsistReference): Boolean {
            val type = loader.findKonsistClass(reference.owner.replace('/', '.')) ?: return false
            return when (reference) {
                is ClassReference -> true
                is MethodReference ->
                    type.hierarchy().any { candidate ->
                        candidate.declaredMethods.any { it.name == reference.name && it.descriptor() == reference.descriptor }
                    }
                is FieldReference ->
                    type.hierarchy().any { candidate ->
                        candidate.declaredFields.any { it.name == reference.name && it.type.descriptor() == reference.descriptor }
                    }
            }
        }

        override fun close() = loader.close()

        private fun Class<*>.hierarchy(): Sequence<Class<*>> =
            sequenceOf(this) + (listOfNotNull(superclass) + interfaces).asSequence().flatMap { it.hierarchy() }

        private fun java.lang.reflect.Method.descriptor(): String =
            MethodType.methodType(returnType, parameterTypes).toMethodDescriptorString()

        private fun Class<*>.descriptor(): String = MethodType.methodType(this).toMethodDescriptorString().substringAfter(')')
    }

    private class KonsistClassLoader(
        jar: URL,
        parent: ClassLoader,
    ) : URLClassLoader(arrayOf(jar), parent) {
        fun findKonsistClass(name: String): Class<*>? = runCatching { loadClass(name) }.getOrNull()

        override fun loadClass(
            name: String,
            resolve: Boolean,
        ): Class<*> =
            synchronized(this) {
                if (name.startsWith(KONSIST_PACKAGE.replace('/', '.'))) {
                    (findLoadedClass(name) ?: findClass(name)).also { if (resolve) resolveClass(it) }
                } else {
                    super.loadClass(name, resolve)
                }
            }
    }

    private companion object {
        const val JAR_PROPERTY_PREFIX = "konsist.contract.jar."
        const val KONSIST_PACKAGE = "com/lemonappdev/konsist/"
        const val CLASS_HEADER_BYTES = 8
        const val TAG_UTF8 = 1
        const val TAG_INTEGER = 3
        const val TAG_FLOAT = 4
        const val TAG_LONG = 5
        const val TAG_DOUBLE = 6
        const val TAG_CLASS = 7
        const val TAG_STRING = 8
        const val TAG_FIELD_REF = 9
        const val TAG_METHOD_REF = 10
        const val TAG_INTERFACE_METHOD_REF = 11
        const val TAG_NAME_AND_TYPE = 12
        const val TAG_METHOD_HANDLE = 15
        const val TAG_METHOD_TYPE = 16
        const val TAG_DYNAMIC = 17
        const val TAG_INVOKE_DYNAMIC = 18
        const val TAG_MODULE = 19
        const val TAG_PACKAGE = 20
    }
}
