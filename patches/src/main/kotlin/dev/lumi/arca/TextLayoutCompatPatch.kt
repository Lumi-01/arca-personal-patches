package dev.lumi.arca

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableField

/**
 * Invalidate stale font metrics and match layout reuse to its exact intrinsics object.
 * Based on AndroidX commit 9a10a4c5bf8507c1f4128fdb2b0b3953a4ab535b
 * (Android Open Source Project, Apache-2.0). No layout-result height is forced.
 */
internal val textLayoutCompatPatch = bytecodePatch {
    execute {
        // The two caches implement the same policy with different obfuscated APIs.
        data class Cache(
            val type: String, val intrinsicsField: String, val intrinsicsType: String,
            val direction: String, val width: String, val height: String,
            val minLines: String, val refresh: String, val intrinsicHeight: String,
            val needsLayout: String, val reset: String, val measure: String,
            val layoutField: String, val layoutType: String,
            val refreshRegisters: Int, val needsRegisters: Int, val resetRegisters: Int,
        )
        val caches = listOf(
            Cache("LN/e;", "l", "LO0/l;", "m", "o", "p", "i", "l", "d",
                "j", "g", "f", "n", "LO0/N;", 10, 9, 2),
            Cache("LN/f;", "n", "LO0/t;", "o", "q", "r", "m", "n", "f",
                "l", "i", "h", "j", "LO0/q;", 12, 9, 5),
        )
        val setter = Fingerprint(definingClass = "LN/c;", name = "b",
            parameters = listOf("LN/c;"), returnType = "V").method
        check(setter.implementation!!.instructions.any {
            it.opcode == Opcode.SPUT_OBJECT && (it as? ReferenceInstruction)?.reference.toString() ==
                "LN/c;->j:LN/c;"
        }) { "Shared min-lines cache setter changed" }

        // The vendor's min-lines cache compares density but omits fontScale.
        // Check BOTH its per-node and shared-cache return paths before reuse.
        val minLinesFactory = Fingerprint(definingClass = "LN/c${'$'}a;", name = "a",
            parameters = listOf("LN/c;", "Lb1/v;", "LO0/U;", "Lb1/e;", "LT0/h${'$'}b;"),
            returnType = "LN/c;").method
        val factoryInsns = minLinesFactory.implementation!!.instructions
        val cachedReturns = factoryInsns.indices.filter {
            factoryInsns[it].opcode == Opcode.RETURN_OBJECT &&
                (factoryInsns[it] as? com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction)?.registerA == 8
        }
        check(minLinesFactory.implementation!!.registerCount == 13 && cachedReturns.size == 2 &&
            cachedReturns.all { it + 1 < factoryInsns.size }) { "Min-lines cache reuse changed" }
        for (index in cachedReturns.reversed()) {
            minLinesFactory.addInstructionsWithLabels(index, """
                invoke-static {}, Llocal/arca/MorphePrefs;->textLayoutCompat()Z
                move-result v0
                if-eqz v0, :morphe_keep_min_lines
                invoke-interface {p4}, Lb1/e;->L0()F
                move-result v0
                invoke-virtual {p1}, LN/c;->d()Lb1/e;
                move-result-object v1
                invoke-interface {v1}, Lb1/e;->L0()F
                move-result v1
                cmpg-float v0, v0, v1
                if-nez v0, :morphe_next_min_lines
            """.trimIndent(),
                ExternalLabel("morphe_keep_min_lines", factoryInsns[index]),
                ExternalLabel("morphe_next_min_lines", factoryInsns[index + 1]))
        }

        for (cache in caches) {
            val type = cache.type
            val definition = classDefBy(type)
            val fields = mapOf(cache.intrinsicsField to cache.intrinsicsType,
                cache.direction to "Lb1/v;", cache.width to "I", cache.height to "I",
                cache.minLines to "LN/c;", cache.layoutField to cache.layoutType)
            check(fields.all { (name, expected) -> definition.fields.any {
                it.name == name && it.type == expected
            } }) { "Text cache fields changed: $type" }
            check(definition.fields.none { it.name == "morpheLayoutIntrinsics" }) {
                "Text cache already patched: $type"
            }
            val captured = "$type->morpheLayoutIntrinsics:${cache.intrinsicsType}"
            val direction = "$type->${cache.direction}:Lb1/v;"
            val intrinsics = "$type->${cache.intrinsicsField}:${cache.intrinsicsType}"
            val width = "$type->${cache.width}:I"
            val height = "$type->${cache.height}:I"
            val minLines = "$type->${cache.minLines}:LN/c;"
            val layout = "$type->${cache.layoutField}:${cache.layoutType}"
            val fontCheck = "${cache.intrinsicsType}->b()Z"
            check(classDefBy(cache.intrinsicsType).methods.any {
                it.name == "b" && it.parameterTypes.isEmpty() && it.returnType == "Z"
            }) { "Stale-font API changed: $type" }

            val intrinsic = Fingerprint(definingClass = type, name = cache.intrinsicHeight,
                parameters = listOf("I", "Lb1/v;"), returnType = "I").method
            val intrinsicInsns = intrinsic.implementation!!.instructions
            check(intrinsic.implementation!!.registerCount == 6 &&
                (intrinsicInsns[0] as? ReferenceInstruction)?.reference.toString() == width &&
                (intrinsicInsns[1] as? ReferenceInstruction)?.reference.toString() == height &&
                intrinsicInsns.take(6).any { it.opcode == Opcode.RETURN }) {
                "Intrinsic-height cache changed: $type"
            }
            val refresh = Fingerprint(definingClass = type, name = cache.refresh,
                parameters = listOf("Lb1/v;"), returnType = cache.intrinsicsType).method
            val refreshInsns = refresh.implementation!!.instructions
            val rebuild = refreshInsns.indexOfFirst {
                it.opcode == Opcode.IPUT_OBJECT &&
                    (it as? ReferenceInstruction)?.reference.toString() == direction
            }
            check(refresh.implementation!!.registerCount == cache.refreshRegisters && rebuild >= 0 &&
                (refreshInsns[0] as? ReferenceInstruction)?.reference.toString() == intrinsics &&
                (refreshInsns[0] as? TwoRegisterInstruction)?.registerA == 0 &&
                refreshInsns.take(rebuild).any {
                    (it as? ReferenceInstruction)?.reference.toString() == fontCheck
                }) { "Font intrinsics rebuild changed: $type" }
            val needsParameters = if (type == "LN/e;") listOf("LO0/N;", "J", "Lb1/v;")
                else listOf("J", "Lb1/v;")
            val needs = Fingerprint(definingClass = type, name = cache.needsLayout,
                parameters = needsParameters, returnType = "Z").method
            check(needs.implementation!!.registerCount == cache.needsRegisters &&
                needs.implementation!!.instructions.any {
                    (it as? ReferenceInstruction)?.reference.toString() == fontCheck
                }) { "Text layout reuse guard changed: $type" }
            val reset = Fingerprint(definingClass = type, name = cache.reset,
                parameters = emptyList(), returnType = "V").method
            check(reset.implementation!!.registerCount == cache.resetRegisters &&
                reset.implementation!!.instructions.any {
                    it.opcode == Opcode.IPUT_OBJECT &&
                        (it as? ReferenceInstruction)?.reference.toString() == layout
                }) { "Text cache reset changed: $type" }
            val measure = Fingerprint(definingClass = type, name = cache.measure,
                parameters = listOf("J", "Lb1/v;"), returnType = "Z").method
            val measureInsns = measure.implementation!!.instructions
            val stores = measureInsns.indices.filter {
                measureInsns[it].opcode == Opcode.IPUT_OBJECT &&
                    (measureInsns[it] as? ReferenceInstruction)?.reference.toString() == layout
            }
            check(measure.implementation!!.registerCount == 12 &&
                stores.size == (if (type == "LN/e;") 2 else 1) && stores.all {
                    measureInsns[it + 1].opcode == Opcode.RETURN
                }) { "Text layout completion changed: $type" }
            mutableClassDefBy(type).fields.add(ImmutableField(type, "morpheLayoutIntrinsics", cache.intrinsicsType,
                AccessFlags.PRIVATE.value, null, null, null).toMutable())

            // Keep valid intrinsic measurements; invalidate only changed fonts/direction.
            // Unconditional recomputation in 0.3.14 did not resolve the reported overlap.
            val invoke = if (type == "LN/e;") "invoke-virtual" else "invoke-interface"
            intrinsic.addInstructionsWithLabels(0, """
                invoke-static {}, Llocal/arca/MorphePrefs;->textLayoutCompat()Z
                move-result v0
                if-eqz v0, :morphe_original_height
                iget-object v0, p0, $intrinsics
                if-eqz v0, :morphe_invalidate_height
                iget-object v1, p0, $direction
                if-ne p2, v1, :morphe_invalidate_height
                $invoke {v0}, $fontCheck
                move-result v0
                if-eqz v0, :morphe_original_height
                :morphe_invalidate_height
                const/4 v0, -0x1
                iput v0, p0, $width
                iput v0, p0, $height
            """.trimIndent(), ExternalLabel("morphe_original_height", intrinsicInsns[0]))

            // Preserve the branch-target instruction so every rebuild invalidates metrics.
            // Keep the existing layout object alive until remeasurement; drawing needs it.
            refresh.replaceInstruction(rebuild,
                "invoke-static {}, Llocal/arca/MorphePrefs;->textLayoutCompat()Z")
            refresh.addInstructionsWithLabels(rebuild + 1, """
                move-result v1
                if-eqz v1, :morphe_original_intrinsics
                const/4 v1, -0x1
                iput v1, p0, $width
                iput v1, p0, $height
                const/4 v1, 0x0
                iput-object v1, p0, $minLines
                if-eqz v0, :morphe_original_intrinsics
                const/4 v1, 0x0
                invoke-static {v1}, LN/c;->b(LN/c;)V
                :morphe_original_intrinsics
                iput-object p1, p0, $direction
            """.trimIndent())
            // Compare the intrinsics used by the completed layout, rather than a
            // sticky dirty bit that can request another measurement indefinitely.
            needs.addInstructionsWithLabels(0, """
                invoke-static {}, Llocal/arca/MorphePrefs;->textLayoutCompat()Z
                move-result v0
                if-eqz v0, :morphe_original_reuse
                iget-object v0, p0, $intrinsics
                iget-object v1, p0, $captured
                if-eq v0, v1, :morphe_original_reuse
                const/4 v0, 0x1
                return v0
            """.trimIndent(), ExternalLabel("morphe_original_reuse", needs.implementation!!.instructions[0]))
            for (store in stores.reversed()) {
                check((measureInsns[store + 1] as? com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction)?.registerA != 0) {
                    "Text layout return uses scratch register: $type"
                }
                measure.addInstructions(store + 1, "iget-object v0, p0, $intrinsics\niput-object v0, p0, $captured")
            }
        }
    }
}

