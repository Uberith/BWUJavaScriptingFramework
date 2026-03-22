plugins {
    application
    id("org.beryx.jlink")
    id("org.gradlex.extra-java-module-info") version "1.11"
}

val lwjglVersion = "3.3.6"
val imguiVersion = "1.90.0"
val lwjglNatives = "natives-windows"
val extractedNativesDir = layout.buildDirectory.dir("natives")

dependencies {
    implementation(project(":api"))
    implementation(project(":core"))
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("io.github.spair:imgui-java-app:$imguiVersion")
    runtimeOnly("io.github.spair:imgui-java-natives-windows:$imguiVersion")
    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:$lwjglNatives")
    implementation("ch.qos.logback:logback-classic:1.5.16")
}

extraJavaModuleInfo {
    automaticModule("org.msgpack:msgpack-core", "msgpack.core")
}

application {
    mainClass = "com.botwithus.bot.cli.gui.ImGuiApp"
    mainModule = "com.botwithus.bot.cli"
}

val extractNatives by tasks.registering(Sync::class) {
    val nativeJars = configurations.runtimeClasspath.map { runtimeClasspath ->
        runtimeClasspath.filter { it.name.contains("natives") }
    }
    from(nativeJars.map { jars -> jars.map(::zipTree) })
    into(extractedNativesDir)
    include("**/*.dll", "**/*.so", "**/*.dylib")
}

tasks.named<JavaExec>("run") {
    dependsOn(extractNatives)
    workingDir = rootProject.projectDir
    doFirst {
        jvmArgs("-Dorg.lwjgl.librarypath=${extractedNativesDir.get().asFile.absolutePath}")
    }
}

jlink {
    val jlinkHome = providers.gradleProperty("jlink.javaHome")
        .orElse(providers.environmentVariable("JLINK_JAVA_HOME"))
    if (jlinkHome.isPresent) {
        javaHome.set(file(jlinkHome.get()))
    }
    options.set(listOf("--strip-debug", "--compress", "zip-6", "--no-header-files", "--no-man-pages"))
    launcher {
        name = "jbot"
    }
    forceMerge("lwjgl")
    mergedModule {
        additive = true
    }
}
