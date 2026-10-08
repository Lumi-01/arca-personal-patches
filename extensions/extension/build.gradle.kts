extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "app.template.extension"
}

// The injected extension is Java-only. The patching plugin otherwise packages
// Kotlin's full standard library and JetBrains compile-time annotations into
// the target APK, even though none of our injected classes use them.
configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains", module = "annotations")
}
