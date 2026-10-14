import com.google.protobuf.gradle.id

plugins {
    application
}

application {
    mainClass.set("io.github.mscheong01.krotodc.MainExecutor")
}

dependencies {
//    implementation(kotlin("stdlib"))
//    implementation("io.grpc:grpc-protobuf:${rootProject.ext["grpcJavaVersion"]}")
    // https://mvnrepository.com/artifact/com.google.protobuf/protobuf-java
    implementation("com.google.protobuf:protobuf-java:${rootProject.ext["protobufVersion"]}")
    implementation("com.google.protobuf:protobuf-java-util:${rootProject.ext["protobufVersion"]}")

    implementation(kotlin("reflect"))
    implementation("com.squareup:kotlinpoet:${rootProject.ext["kotlinPoetVersion"]}")
    // https://mvnrepository.com/artifact/com.squareup.okhttp/okhttp
    implementation("com.squareup.okhttp:okhttp:2.7.5")
    // https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind
    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.2")
    // https://mvnrepository.com/artifact/com.fasterxml.jackson.module/jackson-module-kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")

    implementation("io.grpc:grpc-stub:${rootProject.ext["grpcJavaVersion"]}")
    implementation("io.grpc:grpc-kotlin-stub:${rootProject.ext["grpcKotlinVersion"]}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.ext["coroutinesVersion"]}")
    implementation(project(":core"))
    testImplementation("io.grpc:grpc-protobuf:${rootProject.ext["grpcJavaVersion"]}")

    testImplementation("javax.annotation:javax.annotation-api:1.3.2")
    // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter-api
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // https://mvnrepository.com/artifact/org.assertj/assertj-core
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("io.grpc:grpc-inprocess:${rootProject.ext["grpcJavaVersion"]}")
    testImplementation("io.grpc:grpc-testing:${rootProject.ext["grpcJavaVersion"]}")
}

configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "protoc-gen-krotoDC",
        version = project.version.toString()
    )

    pom {
        name.set("krotoDC")
        description.set(
            "protoc-gen-krotoDC is a protoc plugin for generating kotlin data classes and " +
                "grpc service/stub from a .proto input."
        )
    }
}

tasks.jar {
    println(application.mainClass.get())
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    from(sourceSets.main.get().output)

    val runtimeClasspathJars = configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }
    from(runtimeClasspathJars.map { zipTree(it) })

    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    archiveClassifier = "jdk8"
}

protobuf {

    protoc {
        artifact = "com.google.protobuf:protoc:${rootProject.ext["protobufVersion"]}"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${rootProject.ext["grpcJavaVersion"]}"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${rootProject.ext["grpcKotlinVersion"]}:jdk8@jar"
        }
        id("krotoDC") {
            path = tasks.jar.get().archiveFile.get().asFile.absolutePath
        }
    }
    generateProtoTasks {
        all().forEach {
            if (it.name != "generateProto") {
                it.dependsOn("jar")
            }

            it.plugins {
                id("grpc")
                id("grpckt")
                id("krotoDC") {
                    if (it.sourceSet.name == "memberTest" || it.sourceSet.name == "explicitMemberTest") {
                        option("converterLocation=member")
                        option("converterAnnotations=false")
                        option("allFieldDefaults=true")
                        option("grpcDescriptors=true")
                    }
                    if (it.sourceSet.name == "explicitMemberTest") {
                        option("converterStyle=explicit")
                    }
                }
            }
        }
    }
}

// Protos generated with member converters, compiled and exercised end to end.
val memberTest: SourceSet by sourceSets.creating
configurations[memberTest.implementationConfigurationName].extendsFrom(
    configurations.implementation.get(),
    configurations.testImplementation.get()
)
configurations[memberTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

val memberTestTask = tasks.register<Test>("memberTest") {
    description = "Runs tests against code generated with converterLocation=member."
    group = "verification"
    testClassesDirs = memberTest.output.classesDirs
    classpath = memberTest.runtimeClasspath
    useJUnitPlatform()
}

// The same protos and tests as memberTest, generated with converterStyle=explicit.
val explicitMemberTest: SourceSet by sourceSets.creating {
    (extensions.getByName("proto") as SourceDirectorySet).srcDir("src/memberTest/proto")
}
extensions.getByType<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>()
    .sourceSets.getByName(explicitMemberTest.name).kotlin.srcDir("src/memberTest/kotlin")
configurations[explicitMemberTest.implementationConfigurationName].extendsFrom(
    configurations.implementation.get(),
    configurations.testImplementation.get()
)
configurations[explicitMemberTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

val explicitMemberTestTask = tasks.register<Test>("explicitMemberTest") {
    description = "Runs the memberTest tests against code generated with converterStyle=explicit."
    group = "verification"
    testClassesDirs = explicitMemberTest.output.classesDirs
    classpath = explicitMemberTest.runtimeClasspath
    useJUnitPlatform()
}

tasks.check {
    dependsOn(memberTestTask, explicitMemberTestTask)
}
