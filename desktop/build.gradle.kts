plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

val javafxVersion: String by rootProject.extra { rootProject.property("javafxVersion") as String }
val jnaVersion = rootProject.property("jnaVersion") as String
val jacksonVersion = rootProject.property("jacksonVersion") as String
val junitVersion = rootProject.property("junitVersion") as String
val assertjVersion = rootProject.property("assertjVersion") as String
val slf4jVersion = rootProject.property("slf4jVersion") as String
val logbackVersion = rootProject.property("logbackVersion") as String

javafx {
    version = rootProject.property("javafxVersion") as String
    modules = listOf("javafx.controls", "javafx.graphics")
}

dependencies {
    // Native Windows integration (always-on-top, window geometry, monitor bounds).
    implementation("net.java.dev.jna:jna:$jnaVersion")
    implementation("net.java.dev.jna:jna-platform:$jnaVersion")

    // Local voice pipeline: sherpa-onnx JVM API + Windows x64 native libs (vendored;
    // not on Maven Central). Loads into the signed java.exe via JNI (ADR-006).
    implementation(files("../libs/sherpa-onnx-jvm-1.13.7.jar"))
    runtimeOnly(files("../libs/sherpa-onnx-native-lib-win-x64-1.13.7.jar"))

    // Live2D prototype (path A, superseded): LWJGL transparent GL window spike.
    implementation(platform("org.lwjgl:lwjgl-bom:3.3.3"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    runtimeOnly("org.lwjgl:lwjgl::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-windows")

    // Live2D (path B): JCEF (Chromium) hosts pixi-live2d-display in a transparent
    // OSR overlay. jcefmaven auto-downloads the CEF natives on first run.
    implementation("me.friwi:jcefmaven:135.0.20")

    // JSON: sprite frame atlases, persona/config, structured SLM responses.
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

    // Logging.
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    runtimeOnly("ch.qos.logback:logback-classic:$logbackVersion")

    // Tests.
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
}

application {
    mainClass.set("os.companion.app.CompanionApplication")
}

// Convenience: run the Milestone A overlay/animation spike directly.
tasks.register<JavaExec>("spike") {
    group = "companion"
    description = "Run the Milestone A walking-skeleton overlay spike."
    // Launch via the non-Application Launcher so JavaFX (on the classpath / unnamed
    // module here) does not reject a directly-launched Application subclass.
    mainClass.set("os.companion.app.CompanionApplication\$Launcher")
    classpath = sourceSets["main"].runtimeClasspath
}

// Spike 3: boot the local SLM (llama-server + Phi-4-mini) and print structured replies.
tasks.register<JavaExec>("slm") {
    group = "companion"
    description = "Run the local-SLM smoke test (no GUI)."
    mainClass.set("os.companion.ai.AiSmokeMain")
    classpath = sourceSets["main"].runtimeClasspath
}

// Spike 4 step 0: verify the sherpa-onnx native library loads (Defender check).
tasks.register<JavaExec>("voicecheck") {
    group = "companion"
    description = "Load the sherpa-onnx JNI and print versions."
    mainClass.set("os.companion.voice.VoiceSmokeMain")
    classpath = sourceSets["main"].runtimeClasspath
}

// Spike 4: synthesize + play an EN and RU line (no microphone needed).
tasks.register<JavaExec>("voicetts") {
    group = "companion"
    description = "Speak an English and Russian test line via local TTS."
    mainClass.set("os.companion.voice.VoiceTtsSmokeMain")
    classpath = sourceSets["main"].runtimeClasspath
}

// Live2D spike step 1: transparent always-on-top OpenGL window (LWJGL/GLFW).
tasks.register<JavaExec>("live2dspike") {
    group = "companion"
    description = "Open a transparent always-on-top GL window (Live2D overlay foundation)."
    mainClass.set("os.companion.character.live2d.Live2DWindowSpike")
    classpath = sourceSets["main"].runtimeClasspath
}

// Live2D path B: JCEF transparent overlay hosting pixi-live2d-display (Haru sample).
tasks.register<JavaExec>("live2djcef") {
    group = "companion"
    description = "Show the Live2D web model in a transparent JCEF overlay window."
    mainClass.set("os.companion.character.live2d.Live2DJcefSpike")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs = listOf("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
                     "--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")
}

// Dev: snapshot eyes-open + mid-blink frames for review.
tasks.register<JavaExec>("animsnap") {
    group = "companion"
    description = "Snapshot the blink (open/closed) to build/*.png."
    mainClass.set("os.companion.app.AnimSnapshotMain\$Launcher")
    classpath = sourceSets["main"].runtimeClasspath
}

// Dev: render the kawaii speech bubble to build/bubble_preview.png.
tasks.register<JavaExec>("bubblepreview") {
    group = "companion"
    description = "Render the speech bubble to a PNG for review."
    mainClass.set("os.companion.app.BubblePreviewMain\$Launcher")
    classpath = sourceSets["main"].runtimeClasspath
}
