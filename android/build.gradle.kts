allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
val newBuildDir = rootProject.layout.buildDirectory.dir("../../build").get()
rootProject.layout.buildDirectory.set(newBuildDir)
subprojects {
    layout.buildDirectory.set(newBuildDir.dir(project.name))
    evaluationDependsOn(":app")
}
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
