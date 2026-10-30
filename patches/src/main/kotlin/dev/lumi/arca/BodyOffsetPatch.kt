package dev.lumi.arca

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

/** Keep the stock sticky WebView, but stop collecting asynchronous scroll-offset copies. */
internal val bodyOffsetPatch = bytecodePatch {
    execute {
        val method = Fingerprint(definingClass = "Lu9/t;", name = "l",
            returnType = "V", strings = listOf("live.arca.android.compose.feature.article.ArticleScreenLayout (ArticleScreenLayout.kt:157)")).method
        val insns = method.implementation!!.instructions
        fun ref(index: Int) = (insns[index] as? ReferenceInstruction)?.reference.toString()
        val collect = insns.indices.first { ref(it) == "LV/w1;->b(Lr8/P;LS7/i;LV/n;II)LV/H1;" }
        val call = insns[collect] as FiveRegisterInstruction
        check(listOf(call.registerC, call.registerD, call.registerE, call.registerF, call.registerG) ==
            listOf(8, 3, 2, 7, 10) && insns[collect + 1].opcode == Opcode.MOVE_RESULT_OBJECT &&
            (insns[collect + 1] as OneRegisterInstruction).registerA == 29 &&
            ref(collect + 2) == "Landroidx/compose/ui/platform/AndroidCompositionLocals_androidKt;->getLocalLifecycleOwner()LV/O0;") {
            "Article offset collector changed"
        }
        val listFactory = insns.indices.single { ref(it) == "LB/F;->c(IILV/n;II)LB/E;" }
        check((insns[listFactory + 1] as OneRegisterInstruction).registerA == 5 &&
            insns.subList(listFactory, collect).any {
                it.opcode == Opcode.MOVE_OBJECT_FROM16 && (it as TwoRegisterInstruction).registerA == 32 && it.registerB == 3
            } && insns.subList(listFactory, collect).any {
                (it as? ReferenceInstruction)?.reference.toString() == "Lu9/i;-><init>(LV/s0;)V"
            }) { "Article list/height state registers changed" }
        // v6 is dead here: the original next instruction overwrites it with the lifecycle local.
        // Leave both invoke/move-result pairs intact and jump AFTER the original pair.
        method.addInstructionsWithLabels(collect, """
            invoke-static {}, Llocal/arca/MorphePrefs;->syncBodyOffset()Z
            move-result v6
            if-eqz v6, :morphe_original_offset
            move-object/from16 v6, v32
            invoke-static {v5, v6, v2}, Llocal/arca/BodyOffset;->state(LB/E;LV/s0;LV/n;)LV/H1;
            move-result-object v29
            goto :morphe_offset_ready
        """.trimIndent(), ExternalLabel("morphe_original_offset", insns[collect]),
            ExternalLabel("morphe_offset_ready", insns[collect + 2]))
    }
}
