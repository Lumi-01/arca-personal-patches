package dev.lumi.arca

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

/** Replace the sticky overlay with a retained normal item and zero extra translation. */
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
            invoke-static {}, Llocal/arca/BodyPin;->offset()LV/H1;
            move-result-object v29
            goto :morphe_offset_ready
        """.trimIndent(), ExternalLabel("morphe_original_offset", insns[collect]),
            ExternalLabel("morphe_offset_ready", insns[collect + 2]))

        val scope = Fingerprint(definingClass = "Lu9/t${'$'}b${'$'}e${'$'}a${'$'}a;", name = "h", returnType = "LN7/M;").method
        val scopeInsns = scope.implementation!!.instructions
        val sticky = scopeInsns.indices.single {
            (scopeInsns[it] as? ReferenceInstruction)?.reference.toString() ==
                "LB/A;->c(LB/B;Ljava/lang/Object;Ljava/lang/Object;Lc8/q;ILjava/lang/Object;)V"
        }
        val range = scopeInsns[sticky] as RegisterRangeInstruction
        check(range.registerCount == 6 && scopeInsns.take(sticky).count {
            (it as? ReferenceInstruction)?.reference.toString() ==
                "LB/A;->a(LB/B;Ljava/lang/Object;Ljava/lang/Object;Lc8/q;ILjava/lang/Object;)V"
        } == 2) { "Body item registration changed" }
        scope.replaceInstruction(sticky, "invoke-static/range {v${range.startRegister} .. v${range.startRegister + 5}}, " +
            "Llocal/arca/BodyPin;->item(LB/B;Ljava/lang/Object;Ljava/lang/Object;Lc8/q;ILjava/lang/Object;)V")

        val content = Fingerprint(definingClass = "Lu9/t${'$'}b${'$'}e${'$'}a${'$'}a${'$'}c;", name = "f",
            parameters = listOf("LB/d;", "LV/n;", "I"), returnType = "V").method
        check(content.implementation!!.instructions.any {
            (it as? ReferenceInstruction)?.reference.toString()?.startsWith("LA9/g;->g(") == true
        }) { "Body composition changed" }
        content.addInstructions(0, "invoke-static/range {p2 .. p2}, Llocal/arca/BodyPin;->retain(LV/n;)V")
    }
}
