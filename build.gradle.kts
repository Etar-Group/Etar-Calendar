import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers

plugins {
    id("com.android.application") version "8.5.1" apply false
    id("com.android.library") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
    id("org.ec4j.editorconfig") version "0.1.0" apply false
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.8" apply true
}

// External project configuration start

// Configure and automatically generate AAR of the below mentioned external
// projects using a custom task  in order to let Android Studio work properly.
// MUST BE RUN MANUALLY ON CLI COMPILATION
val externalProjects = listOf(
    project(":external:calendar"),
    project(":external:chips"),
    project(":external:colorpicker"),
    project(":external:timezonepicker")
)

configure(externalProjects) {
    apply {
        from("../configuration/common.gradle")
    }
}

tasks.register("aarGen") {
    description = "Generates AAR from the external projects for Etar-Calendar"
    val aarTasks = arrayOf(
        ":external:calendar:copyAAR",
        ":external:chips:copyAAR",
        ":external:colorpicker:copyAAR",
        ":external:timezonepicker:copyAAR"
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
