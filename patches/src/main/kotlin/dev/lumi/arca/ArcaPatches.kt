package dev.lumi.arca

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableAnnotation
import com.android.tools.smali.dexlib2.AnnotationVisibility
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import org.w3c.dom.Element

private val arca = Compatibility(
    name = "Arca Live",
    packageName = "live.arca.android.playstore",
    apkFileType = ApkFileType.APK,
    appIconColor = 0x303C70,
    targets = listOf(
        AppTarget(version = "0.9.35185", isExperimental = true),
        AppTarget(version = null, isExperimental = true),
    ),
)

private val trackerManifestPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { document ->
            val root = document.documentElement
            val app = root.getElementsByTagName("application").item(0) as Element
            val permissions = root.getElementsByTagName("uses-permission")
            for (index in permissions.length - 1 downTo 0) {
                val permission = permissions.item(index) as Element
                val name = permission.getAttribute("android:name")
                if (name == "com.google.android.gms.permission.AD_ID" ||
                    name.startsWith("android.permission.ACCESS_ADSERVICES_")) {
                    root.removeChild(permission)
                }
            }
            val flags = mapOf(
                "firebase_analytics_collection_deactivated" to "true",
                "firebase_analytics_collection_enabled" to "false",
                "google_analytics_adid_collection_enabled" to "false",
                "google_analytics_default_allow_ad_personalization_signals" to "false",
                "firebase_crashlytics_collection_enabled" to "false",
                "firebase_sessions_enabled" to "false",
            )
            val metadata = app.getElementsByTagName("meta-data")
            flags.forEach { (name, value) ->
                val existing = (0 until metadata.length).map { metadata.item(it) as Element }
                    .firstOrNull { it.getAttribute("android:name") == name }
                val element = existing ?: document.createElement("meta-data").also(app::appendChild)
                element.setAttribute("android:name", name)
                element.setAttribute("android:value", value)
            }
        }
    }
}

/** The page image ad is distinct from user-submitted posts and the text-ad API. */
@Suppress("unused")
val appAdsPatch = bytecodePatch(
    name = "앱 광고 요청 제거",
    description = "앱의 /api/v1/pagead 요청을 시작하지 않습니다. 이용자 게시물과 텍스트 광고는 유지합니다.",
    default = true,
) {
    compatibleWith(arca)
    execute {
        Fingerprint(
            definingClass = "LVa/d;", name = "b",
            parameters = listOf("I", "LS7/e;"), returnType = "Ljava/lang/Object;",
        ).method.addInstructions(0, "const/4 v0, 0x0\nreturn-object v0")
    }
}

@Suppress("unused")
val trackerReductionPatch = bytecodePatch(
    name = "분석 수집 축소",
    description = "Firebase 분석·충돌·세션 수집을 비활성화하고 광고 식별자 권한을 제거합니다. 푸시 알림은 유지합니다.",
    default = true,
) {
    compatibleWith(arca)
    dependsOn(trackerManifestPatch)
}

/** Only the image download endpoint is switched to streaming. */
@Suppress("unused")
val streamingDownloadPatch = bytecodePatch(
    name = "이미지 저장 스트리밍",
    description = "이미지를 통째로 메모리에 읽지 않고 작은 버퍼로 저장합니다. 저장 속도 개선량은 네트워크 상태에 따라 다릅니다.",
    default = true,
) {
    compatibleWith(arca)
    extendWith("extensions/extension.mpe")
    execute {
        val endpoint = Fingerprint(
            definingClass = "LN8/a;", name = "c",
            parameters = listOf("Ljava/lang/String;", "LS7/e;"),
            returnType = "Ljava/lang/Object;",
        ).method
        check(endpoint.annotations.none { it.type == "LXb/w;" }) { "Download endpoint already streams" }
        endpoint.annotations.add(MutableAnnotation(
            ImmutableAnnotation(AnnotationVisibility.RUNTIME, "LXb/w;", emptyList())
        ))

        val saver = Fingerprint(
            definingClass = "LYa/i;", name = "a",
            parameters = listOf("Landroid/content/ContentResolver;", "Ljava/lang/String;",
                "Ljava/lang/String;", "Lbb/E;"), returnType = "Landroid/net/Uri;",
        ).method
        val insns = saver.implementation!!.instructions
        val index = insns.indexOfFirst {
            (it as? ReferenceInstruction)?.reference.toString() == "Lbb/E;->a()[B"
        }
        check(index >= 0 && insns[index + 1].opcode == Opcode.MOVE_RESULT_OBJECT &&
            (insns[index + 2] as? ReferenceInstruction)?.reference.toString() ==
            "Ljava/io/FileOutputStream;->write([B)V") { "Image saver changed" }
        saver.replaceInstruction(index, "invoke-virtual {p4}, Lbb/E;->O0()Lpb/g;")
        saver.replaceInstruction(index + 1, "move-result-object p4")
        saver.replaceInstruction(index + 2, "invoke-interface {p4}, Lpb/g;->X0()Ljava/io/InputStream;")
        saver.addInstructions(index + 3, """
            move-result-object p4
            invoke-static {p4, v0}, Llocal/arca/StreamCopy;->copy(Ljava/io/InputStream;Ljava/io/OutputStream;)V
        """.trimIndent())
    }
}
