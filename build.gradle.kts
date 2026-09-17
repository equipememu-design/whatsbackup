// Root build.gradle.kts - Configuration for WhatsApp Backup Premium
// This file is now minimal as configuration is handled in settings.gradle.kts

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
