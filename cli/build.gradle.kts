dependencies {
    implementation(project(":api"))
    implementation(project(":core"))
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "com.botwithus.bot.cli.gui.JBotGui")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
