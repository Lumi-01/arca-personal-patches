package dev.lumi.arca

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableAnnotation
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AnnotationVisibility
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import org.w3c.dom.Element

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
    name = "Morphe 설정·광고·스프링 전환",
    description = "앱 설정에 광고·본문 표시 제어를 추가합니다. 원본 이동에 스프링을 적용하고 반복 배치 계산과 본문 뷰 준비를 줄입니다.",
    default = true,
) {
    compatibleWith(arcaPlus)
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
            """
                invoke-static {p0}, Llocal/arca/MorphePrefs;->init(Landroid/content/Context;)V
                invoke-static {p0}, Llocal/arca/BodyWebViewCache;->init(Landroid/content/Context;)V
            """.trimIndent())

        // Reuse only an empty article WebView from this exact foreground
        // Context. The original factory still binds fresh listeners/settings.
        val bodyFactory = Fingerprint(definingClass = "LA9/g;", name = "i",
            parameters = listOf("I", "Lc8/l;", "Lc8/l;", "Ln4/c;", "Ljava/lang/String;",
                "Lu9/p0;", "LV/w0;", "Landroid/content/Context;"), returnType = "LDa/c;").method
        val factoryInsns = bodyFactory.implementation!!.instructions
        val createBody = factoryInsns.indexOfFirst {
            it.opcode == Opcode.NEW_INSTANCE &&
                (it as? ReferenceInstruction)?.reference.toString() == "LDa/c;"
        }
        val constructBody = factoryInsns.indexOfFirst {
            (it as? ReferenceInstruction)?.reference.toString() ==
                "LDa/c;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;IILd8/k;)V"
        }
        check(bodyFactory.implementation!!.registerCount == 17 && createBody >= 0 &&
            constructBody == createBody + 5 &&
            (factoryInsns[createBody - 2] as? TwoRegisterInstruction)?.let {
                it.registerA == 2 && it.registerB == 16
            } == true &&
            (factoryInsns[createBody] as? OneRegisterInstruction)?.registerA == 1 &&
            (factoryInsns[constructBody + 1] as? TwoRegisterInstruction)?.let {
                it.registerA == 0 && it.registerB == 1
            } == true && factoryInsns.any {
                (it as? ReferenceInstruction)?.reference.toString() == "LDa/c;->d(ILDa/c${'$'}a;)V"
            }) { "Article WebView factory changed" }
        bodyFactory.addInstructionsWithLabels(createBody, """
            invoke-static {v2}, Llocal/arca/BodyWebViewCache;->take(Landroid/content/Context;)Landroid/webkit/WebView;
            move-result-object v1
            if-eqz v1, :morphe_create_body
            check-cast v1, LDa/c;
            goto :morphe_bind_body
        """.trimIndent(),
            ExternalLabel("morphe_create_body", factoryInsns[createBody]),
            ExternalLabel("morphe_bind_body", factoryInsns[constructBody + 1]),
        )

        // The HTML cache and callback belong to the previous Article screen.
        // Clear them inside their declaring class before releasing the view.
        check(classDefBy("LDa/c;").fields.any { it.name == "D" && it.type == "LDa/c${'$'}a;" } &&
            classDefBy("LDa/c;").fields.any { it.name == "E" && it.type == "Ljava/lang/String;" }) {
            "Article WebView state changed"
        }
        val configureBody = Fingerprint(definingClass = "LDa/c;", name = "d",
            parameters = listOf("I", "LDa/c${'$'}a;"), returnType = "V").method
        val updateBody = Fingerprint(definingClass = "LDa/c;", name = "f",
            parameters = listOf("Ljava/lang/String;", "Z"), returnType = "V").method
        check(configureBody.implementation!!.instructions.any {
            (it as? ReferenceInstruction)?.reference.toString() == "webViewTunnel"
        } && updateBody.implementation!!.instructions.any {
            (it as? ReferenceInstruction)?.reference.toString() == "LDa/c;->E:Ljava/lang/String;"
        } && updateBody.implementation!!.instructions.any {
            (it as? ReferenceInstruction)?.reference.toString() ==
                "Landroid/webkit/WebView;->loadDataWithBaseURL(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"
        }) { "Article HTML cache or JavaScript binding changed" }
        // This flag is the stock observeResumeAsState result. Navigation keeps
        // an incoming entry below RESUMED while its transition runs. Avoid
        // loading a paused page under that moving layer; no fixed delay/timer.
        check(classDefBy("LEa/d;").methods.any { method ->
            method.implementation?.instructions?.any {
                (it as? ReferenceInstruction)?.reference.toString()
                    ?.contains("observeResumeAsState (LifecycleUtils.kt:") == true
            } == true
        } && classDefBy("Lu9/t;").methods.any { method ->
            method.implementation?.instructions?.any {
                (it as? ReferenceInstruction)?.reference.toString()
                    ?.startsWith("LEa/d;->c(Landroidx/lifecycle/m;") == true
            } == true
        }) { "Article resumed-state source changed" }
        val updateInsns = updateBody.implementation!!.instructions
        val htmlRead = updateInsns.indexOfFirst {
            it.opcode == Opcode.IGET_OBJECT && (it as? ReferenceInstruction)?.reference.toString() ==
                "LDa/c;->E:Ljava/lang/String;"
        }
        check(htmlRead >= 0 && updateBody.implementation!!.registerCount == 10) {
            "Article HTML update registers changed"
        }
        updateBody.addInstructionsWithLabels(htmlRead, """
            invoke-static {}, Llocal/arca/MorphePrefs;->reuseBodyView()Z
            move-result v0
            if-eqz v0, :morphe_load_html
            if-nez p2, :morphe_load_html
            sget-object v0, LDa/d;->a:LDa/d;
            invoke-virtual {v0, p0}, LDa/d;->b(Landroid/webkit/WebView;)V
            return-void
        """.trimIndent(), ExternalLabel("morphe_load_html", updateInsns[htmlRead]))
        val detachBody = Fingerprint(definingClass = "LDa/c;", name = "onDetachedFromWindow",
            parameters = emptyList(), returnType = "V").method
        val detachInsns = detachBody.implementation!!.instructions
        val destroyBody = detachInsns.indexOfFirst {
            (it as? ReferenceInstruction)?.reference.toString() == "LDa/d;->a(Landroid/webkit/WebView;)V"
        }
        check(detachBody.implementation!!.registerCount == 2 && destroyBody >= 0 &&
            (detachInsns[destroyBody] as? FiveRegisterInstruction)?.let {
                it.registerCount == 2 && it.registerC == 0 && it.registerD == 1
            } == true) { "Article WebView destruction changed" }
        detachBody.replaceInstruction(destroyBody,
            "invoke-virtual {v0, p0}, LDa/d;->b(Landroid/webkit/WebView;)V")
        detachBody.addInstructions(destroyBody + 1, """
            const/4 v0, 0x0
            iput-object v0, p0, LDa/c;->D:LDa/c${'$'}a;
            const-string v0, ""
            iput-object v0, p0, LDa/c;->E:Ljava/lang/String;
            invoke-static {p0}, Llocal/arca/BodyWebViewCache;->release(Landroid/webkit/WebView;)V
        """.trimIndent())

        // The existing set records which WebViews have already resumed. Keep
        // first resume, every pause and global-timer handling unchanged.
        val resume = Fingerprint(definingClass = "LDa/d;", name = "c",
            parameters = listOf("Landroid/webkit/WebView;"), returnType = "V").method
        val resumeInsns = resume.implementation!!.instructions
        val resumeCall = resumeInsns.indexOfFirst {
            (it as? ReferenceInstruction)?.reference.toString() == "Landroid/webkit/WebView;->onResume()V"
        }
        check(resume.implementation!!.registerCount == 4 && resumeCall >= 0 &&
            classDefBy("LDa/d;").fields.any { it.name == "b" && it.type == "Ljava/util/HashSet;" } &&
            resumeInsns.any { (it as? ReferenceInstruction)?.reference.toString() ==
                "Ljava/util/HashSet;->add(Ljava/lang/Object;)Z" } &&
            resumeInsns.any { (it as? ReferenceInstruction)?.reference.toString() ==
                "Landroid/webkit/WebView;->resumeTimers()V" }) { "WebView lifecycle tracking changed" }
        resume.addInstructionsWithLabels(resumeCall, """
            sget-object v0, LDa/d;->b:Ljava/util/HashSet;
            invoke-virtual {p1}, Ljava/lang/Object;->hashCode()I
            move-result v1
            invoke-static {v1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
            move-result-object v1
            invoke-virtual {v0, v1}, Ljava/util/HashSet;->contains(Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :morphe_first_resume
            return-void
        """.trimIndent(), ExternalLabel("morphe_first_resume", resumeInsns[resumeCall]))

        val settingsContent = Fingerprint(
            definingClass = "Lva/K${'$'}b;", name = "s",
            parameters = listOf("LA/e;", "LV/n;", "I"), returnType = "V",
        ).method
        val instructions = settingsContent.implementation!!.instructions
        val labelIndex = instructions.indexOfFirst {
            (it as? ReferenceInstruction)?.reference?.toString()
                ?.contains("settings.root.application.patch_note") == true
        }
        check(labelIndex >= 0) { "Settings patch-note row changed" }
        val rowIndex = (labelIndex until instructions.size).firstOrNull {
            (instructions[it] as? ReferenceInstruction)?.reference?.toString()
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

        // Preserve the stock slide directions and offsets. A 1 px visibility
        // threshold ends the spring once its remaining movement is invisible,
        // so outgoing and incoming screens are not drawn through a long tail.
        Fingerprint(definingClass = "Ls/k;", name = "k",
            parameters = listOf("F", "F", "Ljava/lang/Object;"),
            returnType = "Ls/m0;").method
        for (name in listOf("g", "i", "k", "m")) {
            val nav = Fingerprint(definingClass = "LKa/n;", name = name,
                parameters = emptyList(), returnType = if (name == "g" || name == "i")
                    "Landroidx/compose/animation/i;" else "Landroidx/compose/animation/k;").method
            val instructions = nav.implementation!!.instructions
            val easing = instructions.indexOfFirst {
                (it as? ReferenceInstruction)?.reference.toString() == "Ls/H;->e()Ls/F;"
            }
            val tween = instructions.indexOfFirst {
                (it as? ReferenceInstruction)?.reference.toString() ==
                    "Ls/k;->n(IILs/F;ILjava/lang/Object;)Ls/x0;"
            }
            check(easing >= 0 && tween == easing + 6 && tween + 1 < instructions.size &&
                instructions[easing + 1].opcode == Opcode.MOVE_RESULT_OBJECT &&
                (instructions[easing + 2] as? NarrowLiteralInstruction)?.narrowLiteral == 2 &&
                (instructions[easing + 3] as? NarrowLiteralInstruction)?.narrowLiteral == 0 &&
                (instructions[easing + 4] as? NarrowLiteralInstruction)?.narrowLiteral == 250 &&
                (instructions[easing + 5] as? NarrowLiteralInstruction)?.narrowLiteral == 0 &&
                instructions[tween + 1].opcode == Opcode.MOVE_RESULT_OBJECT &&
                nav.implementation!!.registerCount == 5) {
                "Navigation animation changed: $name"
            }
            nav.replaceInstruction(easing, "const v1, 0x3f800000")
            nav.replaceInstruction(easing + 1, "const v2, 0x44160000")
            nav.replaceInstruction(easing + 2, "const/4 v3, 0x1")
            nav.replaceInstruction(easing + 3,
                "invoke-static {v3, v3}, Lb1/q;->a(II)J")
            nav.replaceInstruction(easing + 4, "move-result-wide v3")
            nav.replaceInstruction(easing + 5,
                "invoke-static {v3, v4}, Lb1/p;->b(J)Lb1/p;")
            nav.addInstructions(tween, "move-result-object v3")
            nav.replaceInstruction(tween + 1,
                "invoke-static {v1, v2, v3}, Ls/k;->k(FFLjava/lang/Object;)Ls/m0;")
        }

        // Only defer the slide when it cannot affect the measured size or
        // alignment animation. The existing layer still handles fade/scale.
        // No timer, fixed refresh rate, extra layer, or per-frame reflection.
        val layerApi = classDefBy("Landroidx/compose/ui/graphics/c;")
        for ((name, parameters, result) in listOf(
            Triple("D", emptyList(), "F"), Triple("A", emptyList(), "F"),
            Triple("l", listOf("F"), "V"), Triple("h", listOf("F"), "V"),
        )) {
            check(layerApi.methods.any {
                it.name == name && it.parameterTypes.map(CharSequence::toString) == parameters &&
                    it.returnType == result
            }) { "Graphics layer API changed: $name" }
        }
        val layerImpl = classDefBy("Landroidx/compose/ui/graphics/d;")
        for ((setter, getter, field) in listOf(
            Triple("l", "D", "D"), Triple("h", "A", "E"),
        )) {
            val fieldRef = "Landroidx/compose/ui/graphics/d;->$field:F"
            for (name in listOf(setter, getter)) {
                check(layerImpl.methods.singleOrNull { it.name == name }?.implementation?.instructions?.any {
                    (it as? ReferenceInstruction)?.reference.toString() == fieldRef
                } == true) { "Layer translation mapping changed: $name" }
            }
        }
        check(classDefBy("Lc8/l;").methods.any {
            it.name == "d" && it.parameterTypes.toString() == "[Ljava/lang/Object;]" &&
                it.returnType == "Ljava/lang/Object;"
        } && classDefBy("LV/H1;").methods.any {
            it.name == "getValue" && it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/Object;"
        } && classDefBy("Lb1/p;").methods.any {
            it.name == "o" && it.parameterTypes.isEmpty() && it.returnType == "J"
        }) { "Slide layer compile-only API changed" }
        val measure = Fingerprint(
            definingClass = "Landroidx/compose/animation/h;", name = "b",
            parameters = listOf("LF0/M;", "LF0/G;", "J"), returnType = "LF0/K;",
        ).method
        val measureInsns = measure.implementation!!.instructions
        val slideField = measureInsns.indexOfFirst {
            (it as? ReferenceInstruction)?.reference.toString() ==
                "Landroidx/compose/animation/h;->P:Ls/t0\$a;"
        }
        val slideRead = (slideField + 1 until measureInsns.size).firstOrNull {
            (measureInsns[it] as? ReferenceInstruction)?.reference.toString() ==
                "LV/H1;->getValue()Ljava/lang/Object;"
        } ?: error("Slide state read changed")
        val layerCall = measureInsns.indexOfFirst {
            (it as? ReferenceInstruction)?.reference.toString() == "Lr/p;->a()Lc8/l;"
        }
        check(slideField >= 0 && measure.implementation!!.registerCount == 24 &&
            (measureInsns[slideField] as? TwoRegisterInstruction)?.let {
                it.registerA == 1 && it.registerB == 0
            } == true && layerCall >= 0 &&
            (measureInsns[layerCall + 1] as? OneRegisterInstruction)?.registerA == 12 &&
            (measureInsns[slideRead] as? FiveRegisterInstruction)?.let {
                it.registerCount == 1 && it.registerC == 1
            } == true &&
            measureInsns[slideRead + 1].opcode == Opcode.MOVE_RESULT_OBJECT &&
            (measureInsns[slideRead + 1] as? OneRegisterInstruction)?.registerA == 1 &&
            (measureInsns[slideRead + 2] as? ReferenceInstruction)?.reference.toString() == "Lb1/p;" &&
            (measureInsns[slideRead + 2] as? OneRegisterInstruction)?.registerA == 1 &&
            (measureInsns[slideRead + 3] as? ReferenceInstruction)?.reference.toString() == "Lb1/p;->o()J" &&
            (measureInsns[slideRead + 3] as? FiveRegisterInstruction)?.registerC == 1 &&
            measureInsns[slideRead + 4].opcode == Opcode.MOVE_RESULT_WIDE &&
            (measureInsns[slideRead + 4] as? OneRegisterInstruction)?.registerA == 1 &&
            measureInsns[slideRead + 5].opcode == Opcode.GOTO) {
            "Slide measurement layout changed"
        }
        measure.addInstructionsWithLabels(slideRead, """
            iget-object v3, v0, Landroidx/compose/animation/h;->N:Ls/t0${'$'}a;
            if-nez v3, :morphe_measure_slide
            iget-object v3, v0, Landroidx/compose/animation/h;->O:Ls/t0${'$'}a;
            if-nez v3, :morphe_measure_slide
            invoke-static {v1, v12}, Llocal/arca/SlideLayer;->wrap(LV/H1;Lc8/l;)Lc8/l;
            move-result-object v12
            const-wide/16 v1, 0x0
            goto :morphe_slide_ready
        """.trimIndent(),
            ExternalLabel("morphe_measure_slide", measureInsns[slideRead]),
            ExternalLabel("morphe_slide_ready", measureInsns[slideRead + 5]),
        )
    }
}

@Suppress("unused")
val trackerReductionPatch = bytecodePatch(
    name = "분석 수집·백그라운드 작업 축소",
    description = "분석·충돌·세션 수집과 측정 서비스·작업을 비활성화하고 광고 식별자 권한을 제거합니다. 푸시 알림은 유지합니다.",
    default = true,
) {
    compatibleWith(arcaPlus)
    dependsOn(trackerManifestPatch)
}

/** The Plus build uses a private MediaStore saver; its legacy saver already streams. */
@Suppress("unused")
val streamingDownloadPlusPatch = bytecodePatch(
    name = "미디어 저장 스트리밍 (플러스)",
    description = "플러스 앱의 이미지·GIF·동영상 저장을 작은 버퍼로 처리합니다. 재생·미리보기에는 적용되지 않습니다.",
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
