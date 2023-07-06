import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.0"
    application
    idea
    id("com.google.protobuf") version "0.9.3"
}

group = "com.fengsheng"
version = "1.0-SNAPSHOT"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}

dependencies {
    implementation("io.netty:netty-all:4.1.94.Final")
    implementation("com.google.protobuf:protobuf-java:3.23.3")
    implementation("log4j:log4j:1.2.17")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.23.2"
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

application {
    mainClass.set("com.fengsheng.Game")
}

tasks.withType<Jar> {
    // Otherwise you'll get a "No main manifest attribute" error
    manifest {
        attributes["Main-Class"] = "com.fengsheng.Game"
    }

    // To avoid the duplicate handling strategy error
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // To add all of the dependencies otherwise a "NoClassDefFoundError" error
    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}
//
//tasks.register("stat") {
//    Class.forName("com.fengsheng.Statistics").getMethod("main", Array<String>::class.java).invoke(arrayOf<String>())
//}
