// Copyright 2026 Minsoo Cheong
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package io.github.mscheong01.krotodc

import com.google.protobuf.Descriptors
import com.google.protobuf.GeneratedMessage

/**
 * A custom option (a scalar or enum `extend` field), so callers can read and write options such as
 * `MethodOptions` without importing the protoc-generated outer class that declares the extension. Generated in member
 * mode as a property of the `<OuterClassName>Kroto` object of the `.proto` file that declares the extension.
 *
 * Today it delegates to protoc's Java extension.
 */
class KrotoDCExtension<O : GeneratedMessage.ExtendableMessage<O>, T>(
    private val extension: GeneratedMessage.GeneratedExtension<O, T>
) {
    /** The extension field's descriptor. */
    val descriptor: Descriptors.FieldDescriptor
        get() = extension.descriptor

    /** The option's value in [options], or its default when unset. */
    fun get(options: O): T = options.getExtension(extension)

    /** Whether [options] sets the option. */
    fun has(options: O): Boolean = options.hasExtension(extension)

    /** Sets the option on [builder] and returns it. */
    fun <B : GeneratedMessage.ExtendableBuilder<O, B>> set(builder: B, value: T): B = builder.setExtension(extension, value)
}
