group = "dev.lumi.arca"

patches {
    about {
        name = "Lumi Arca Live patches"
        description = "Personal Arca Live APK clean-up patches"
        source = "https://github.com/Lumi-01/arca-personal-patches"
        author = "Lumi-01"
        contact = "https://github.com/Lumi-01/arca-personal-patches/issues"
        website = "https://github.com/Lumi-01/arca-personal-patches"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
