// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
    }
    dependencies {
        // Удалили classpath Spotless, чтобы он больше не мешал
    }
}

plugins {
    id("com.android.application") version "8.0.2" apply false
    id("com.android.library") version "8.0.2" apply false
    id("org.jetbrains.kotlin.android") version "1.8.20" apply false
}

// Блок subprojects полностью удален

task("addPreCommitGitHookOnBuild") {
    println("⚈ ⚈ ⚈ Running Add Pre Commit Git Hook Script on Build ⚈ ⚈ ⚈")
    exec {
        commandLine("cp", "./.scripts/pre-commit", "./.git/hooks")
    }
    println("✅ Added Pre Commit Git Hook Script.")
}
