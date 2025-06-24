import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.ec4j.editorconfig) apply false
    alias(libs.plugins.jetbrains.idea.ext) apply true
}

// External project configuration start

// Configure and automatically generate AAR of the below mentioned external
// projects using a custom task  in order to let Android Studio work properly.
// MUST BE RUN MANUALLY ON CLI COMPILATION
val externalProjects = listOf(
    project(":external:chips"),
)

configure(externalProjects) {
    apply {
        from("../configuration/common.gradle")
    }
}

tasks.register("aarGen") {
    description = "Generates AAR from the external projects for Etar-Calendar"
    val aarTasks = arrayOf(
        ":external:chips:copyAAR",
    )
    dependsOn(*aarTasks)
}

idea.project.settings {
    taskTriggers {
        afterSync(tasks.named("aarGen"))
    }
}

// External project configuration end
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
    delete("app/libs")
}
