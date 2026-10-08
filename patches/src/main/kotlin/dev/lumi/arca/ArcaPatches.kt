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

private val arcaPlay = Compatibility(
    name = "Arca Live (Play Store)",
    packageName = "live.arca.android.playstore",
    apkFileType = ApkFileType.APK,
    appIconColor = 0x303C70,
    targets = listOf(
        AppTarget(version = "0.9.35185", isExperimental = true),
        AppTarget(version = null, isExperimental = true),
    ),
)

private val arcaPlus = Compatibility(
    name = "Arca Live Plus",
    packageName = "live.arca.android",
    apkFileType = ApkFileType.APK,
    appIconColor = 0x303C70,
    targets = listOf(
        AppTarget(version = "0.9.32768", isExperimental = true),
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
                    name == "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE" ||
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
            // Disable the analytics-only entry points, while preserving Firebase
            // Messaging and Remote Config for notifications and app settings.
            val measurementComponents = setOf(
                "com.google.android.gms.measurement.AppMeasurementReceiver",
                "com.google.android.gms.measurement.AppMeasurementService",
                "com.google.android.gms.measurement.AppMeasurementJobService",
                "com.google.firebase.sessions.SessionLifecycleService",
            )
            listOf("receiver", "service").forEach { tag ->
                val components = app.getElementsByTagName(tag)
                for (index in 0 until components.length) {
                    val component = components.item(index) as Element
                    if (component.getAttribute("android:name") in measurementComponents) {
                        component.setAttribute("android:enabled", "false")
                    }
                }
            }
        }
    }
}

private val morpheSettingsManifestPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { document ->
            val app = document.documentElement.getElementsByTagName("application").item(0) as Element
            val activity = document.createElement("activity")
            activity.setAttribute("android:name", "local.arca.MorpheSettingsActivity")
            activity.setAttribute("android:exported", "false")
            app.appendChild(activity)
        }
    }
}

/** The page image ad is distinct from user-submitted posts and the text-ad API. */
@Suppress("unused")
val appAdsPatch = bytecodePatch(
    name = "Morphe 설정·광고·화면 전환",
    description = "앱 설정에 Morphe 설정을 추가합니다. 앱 이미지 광고 제거와 가벼운 화면 전환을 앱에서 조절할 수 있습니다.",
    default = true,
) {
    compatibleWith(arcaPlay, arcaPlus)
    dependsOn(morpheSettingsManifestPatch)
    extendWith("extensions/extension.mpe")
    execute {
        Fingerprint(
            definingClass = "LVa/d;", name = "b",
            parameters = listOf("I", "LS7/e;"), returnType = "Ljava/lang/Object;",
        ).method.addInstructions(0, """
            invoke-static {}, Llocal/arca/MorphePrefs;->blockAds()Z
            move-result v0
            if-eqz v0, :morphe_ad_enabled
            const/4 v0, 0x0
            return-object v0
            :morphe_ad_enabled
        """.trimIndent())
        Fingerprint(
            definingClass = "LJ9/d;", name = "d",
            parameters = listOf("Llive/arca/android/model/api/Ad;", "Z", "J",
                "Lc8/a;", "Lc8/a;", "LV/n;", "I", "I"),
            returnType = "V",
        ).method.addInstructions(0, """
            invoke-static {}, Llocal/arca/MorphePrefs;->blockAds()Z
            move-result v0
            if-eqz v0, :morphe_ad_visible
            return-void
            :morphe_ad_visible
        """.trimIndent())

        val appOnCreate = Fingerprint(
            definingClass = "Llive/arca/android/global/App;", name = "onCreate",
            parameters = emptyList(), returnType = "V",
        ).method
        val superCall = appOnCreate.implementation!!.instructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_SUPER
        }
        check(superCall >= 0) { "Application initialization changed" }
        appOnCreate.addInstructions(superCall + 1,
            "invoke-static {p0}, Llocal/arca/MorphePrefs;->init(Landroid/content/Context;)V")

        val settingsContent = Fingerprint(
            definingClass = "Lva/K${'$'}b;", name = "s",
            parameters = listOf("LA/e;", "LV/n;", "I"), returnType = "V",
        ).method
        val instructions = settingsContent.implementation!!.instructions
        val labelIndex = instructions.indexOfFirst {
            (it as? ReferenceInstruction)?.reference.toString()
                ?.contains("settings.root.application.patch_note") == true
        }
        check(labelIndex >= 0) { "Settings patch-note row changed" }
        val rowIndex = (labelIndex until instructions.size).firstOrNull {
            (instructions[it] as? ReferenceInstruction)?.reference.toString()
                ?.contains("Lg9/h;->c(Ljava/lang/String;") == true
        } ?: error("Settings row renderer changed")
        settingsContent.addInstructions(rowIndex + 1, """
            const-string v1, "Morphe 설정"
            const/4 v2, 0x0
            new-instance v8, Lva/U;
            invoke-direct {v8, v2}, Lva/U;-><init>(Lc8/l;)V
            const/4 v3, 0x1
            const/4 v4, 0x0
            const/4 v5, 0x0
            const/4 v6, 0x0
            const/4 v7, 0x0
            const/16 v10, 0x180
            const/16 v11, 0x7a
            invoke-static/range {v1 .. v11}, Lg9/h;->c(Ljava/lang/String;Ljava/lang/String;ZLc8/p;ZLA/Q;Lc8/a;Lc8/a;LV/n;II)V
        """.trimIndent())
        Fingerprint(
            definingClass = "Lva/U;", name = "e",
            parameters = emptyList(), returnType = "Ljava/lang/Object;",
        ).method.addInstructions(0, """
            iget-object v0, p0, Lva/U;->z:Lc8/l;
            if-nez v0, :morphe_existing_row
            invoke-static {}, Llive/arca/android/global/a;->a()Landroid/content/Context;
            move-result-object v0
            invoke-static {v0}, Llocal/arca/MorpheSettingsActivity;->open(Landroid/content/Context;)V
            sget-object v0, LN7/M;->a:LN7/M;
            return-object v0
            :morphe_existing_row
        """.trimIndent())

        for (name in listOf("g", "i", "k", "m")) {
            val nav = Fingerprint(definingClass = "LKa/n;", name = name,
                parameters = emptyList(), returnType = if (name == "g" || name == "i")
                    "Landroidx/compose/animation/i;" else "Landroidx/compose/animation/k;").method
            val duration = nav.implementation!!.instructions.indexOfFirst { it.opcode == Opcode.CONST_16 }
            check(duration >= 0) { "Navigation duration changed: $name" }
            nav.replaceInstruction(duration,
                "invoke-static {}, Llocal/arca/MorphePrefs;->navigationDuration()I")
            nav.addInstructions(duration + 1, "move-result v3")
        }
        for (name in listOf("j", "n")) {
            val nav = Fingerprint(definingClass = "LKa/n;", name = name,
                parameters = listOf("Lb1/t;"), returnType = "Lb1/p;").method
            val width = nav.implementation!!.instructions.indexOfFirst {
                (it as? ReferenceInstruction)?.reference.toString()
                    ?.contains("Lb1/t;->g(J)I") == true
            }
            check(width >= 0) { "Navigation enter distance changed: $name" }
            nav.addInstructions(width + 2, """
                invoke-static {p0}, Llocal/arca/MorphePrefs;->enterDistance(I)I
                move-result p0
            """.trimIndent())
        }
        for (name in listOf("h", "l")) {
            val nav = Fingerprint(definingClass = "LKa/n;", name = name,
                parameters = listOf("Lb1/t;"), returnType = "Lb1/p;").method
            val distance = nav.implementation!!.instructions.indexOfFirst { it.opcode == Opcode.DIV_INT_LIT8 }
            check(distance >= 0) { "Navigation exit distance changed: $name" }
            nav.addInstructions(distance + 1, """
                invoke-static {p0}, Llocal/arca/MorphePrefs;->exitDistance(I)I
                move-result p0
            """.trimIndent())
        }
    }
}

@Suppress("unused")
val trackerReductionPatch = bytecodePatch(
    name = "분석 수집·백그라운드 작업 축소",
    description = "분석·충돌·세션 수집과 측정 서비스·작업을 비활성화하고 광고 식별자 권한을 제거합니다. 푸시 알림은 유지합니다.",
    default = true,
) {
    compatibleWith(arcaPlay, arcaPlus)
    dependsOn(trackerManifestPatch)
}

/** Only the image download endpoint is switched to streaming. */
@Suppress("unused")
val streamingDownloadPatch = bytecodePatch(
    name = "이미지 저장 스트리밍 (플레이스토어)",
    description = "이미지를 통째로 메모리에 읽지 않고 작은 버퍼로 저장합니다. 저장 속도 개선량은 네트워크 상태에 따라 다릅니다.",
    default = true,
) {
    compatibleWith(arcaPlay)
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

/** The Plus build uses a private MediaStore saver; its legacy saver already streams. */
@Suppress("unused")
val streamingDownloadPlusPatch = bytecodePatch(
    name = "이미지 저장 스트리밍 (플러스)",
    description = "플러스 앱의 MediaStore 이미지 저장을 작은 버퍼로 처리합니다. 저장 속도 개선량은 네트워크 상태에 따라 다릅니다.",
    default = true,
) {
    compatibleWith(arcaPlus)
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
            definingClass = "LYa/i;", name = "b",
            parameters = listOf("Landroid/content/ContentResolver;", "Ljava/lang/String;",
                "Ljava/lang/String;", "Lbb/E;"), returnType = "Landroid/net/Uri;",
        ).method
        val insns = saver.implementation!!.instructions
        val index = insns.indexOfFirst {
            (it as? ReferenceInstruction)?.reference.toString() == "Lbb/E;->a()[B"
        }
        check(index >= 0 && insns[index + 1].opcode == Opcode.MOVE_RESULT_OBJECT &&
            (insns[index + 2] as? ReferenceInstruction)?.reference.toString() ==
            "Ljava/io/FileOutputStream;->write([B)V") { "Plus image saver changed" }
        saver.replaceInstruction(index, "invoke-virtual {p4}, Lbb/E;->O0()Lpb/g;")
        saver.replaceInstruction(index + 1, "move-result-object p4")
        saver.replaceInstruction(index + 2, "invoke-interface {p4}, Lpb/g;->X0()Ljava/io/InputStream;")
        saver.addInstructions(index + 3, """
            move-result-object p4
            invoke-static {p4, v0}, Llocal/arca/StreamCopy;->copy(Ljava/io/InputStream;Ljava/io/OutputStream;)V
        """.trimIndent())
    }
}
