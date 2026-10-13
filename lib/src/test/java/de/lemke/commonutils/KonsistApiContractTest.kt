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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import java.io.DataInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.relativeTo

/**
 * A Konsist descriptor change throws NoSuchMethodError on the consumers that use the other Konsist version. Every
 * Konsist method the fixtures reference is listed in `konsist-references.txt`; a new reference fails this test until
 * its descriptor is checked with `javap` against the oldest and the newest Konsist version the fixtures support.
 */
class KonsistApiContractTest : ShouldSpec() {
    init {
        should("reference only Konsist methods whose descriptors are verified") {
            fixtureClassFiles().flatMap { it.konsistMethodReferences() }.toSet() shouldContainExactlyInAnyOrder verifiedReferences()
        }
    }

    private fun verifiedReferences(): List<String> =
        checkNotNull(javaClass.getResourceAsStream("/konsist-references.txt")).bufferedReader().use { it.readLines() }

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

    private fun ByteArray.konsistMethodReferences(): List<String> {
        val input = DataInputStream(inputStream())
        input.skipBytes(CLASS_HEADER_BYTES)
        val count = input.readUnsignedShort()
        val utf8 = arrayOfNulls<String>(count)
        val classNameIndex = IntArray(count)
        val nameAndType = Array(count) { IntArray(2) }
        val methodRefs = mutableListOf<IntArray>()
        var index = 1
        while (index < count) {
            val tag = input.readUnsignedByte()
            when (tag) {
                TAG_UTF8 -> utf8[index] = input.readUTF()
                TAG_CLASS -> classNameIndex[index] = input.readUnsignedShort()
                TAG_NAME_AND_TYPE -> nameAndType[index] = intArrayOf(input.readUnsignedShort(), input.readUnsignedShort())
                TAG_METHOD_REF, TAG_INTERFACE_METHOD_REF -> {
                    methodRefs += intArrayOf(input.readUnsignedShort(), input.readUnsignedShort())
                }
                else -> input.skipBytes(constantSize(tag))
            }
            index += if (tag == TAG_LONG || tag == TAG_DOUBLE) 2 else 1
        }
        return methodRefs.mapNotNull { (classIndex, nameAndTypeIndex) ->
            val owner = utf8[classNameIndex[classIndex]]!!
            val (name, descriptor) = nameAndType[nameAndTypeIndex].map { utf8[it]!! }
            "$owner.$name$descriptor".takeIf { owner.startsWith("com/lemonappdev/konsist/") }
        }
    }

    private fun constantSize(tag: Int): Int =
        when (tag) {
            TAG_STRING, TAG_METHOD_TYPE, TAG_MODULE, TAG_PACKAGE -> 2
            TAG_METHOD_HANDLE -> 3
            TAG_INTEGER, TAG_FLOAT, TAG_FIELD_REF, TAG_DYNAMIC, TAG_INVOKE_DYNAMIC -> 4
            TAG_LONG, TAG_DOUBLE -> 8
            else -> error("Unknown constant pool tag $tag")
        }

    private companion object {
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
