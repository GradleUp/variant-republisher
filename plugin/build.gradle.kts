plugins { // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`

    // Apply the Kotlin JVM plugin to add support for Kotlin.
    //    embeddedKotlin("jvm")

    `maven-publish`

    embeddedKotlin("jvm")
}

version = "0.0.8"

repositories { // Use Maven Central for resolving dependencies.
    mavenCentral()
    maven("https://jogamp.org/deployment/maven/")
}

val gluegenDep = configurations.dependencyScope("jogampDep")

val gluegenVersion = "2.4.0"
dependencies {
    val libraries = listOf(
        "gluegen:gluegen-rt",
        //                           "joal:joal",
        //                           "jocl:jocl",
        //                           "jogl:jogl-all",
        //                           "jogl:jogl-all-noawt"
                          )
    val variants = listOf("android-aarch64",
                          "linux-aarch64",
                          "linux-amd64",
                          "linux-armv6hf",
                          "macosx-universal",
                          "windows-amd64")
    for (lib in libraries)
        for (variant in variants)
            gluegenDep("org.jogamp.$lib:$gluegenVersion:natives-$variant")
    gluegenDep("org.jogamp.${libraries[0]}:$gluegenVersion-rt")

    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
}
val gluegenRes = configurations.resolvable("jogampRes") {
    extendsFrom(gluegenDep.get())
}

// remove antlr
var files = gluegenRes.get().resolve().filter { "gluegen" in it.name }
check(files.last().name == "gluegen-rt-$gluegenVersion.jar")
val gluegen = files.last()
println(gluegen)
for (file in files)
    println(file)
files = files.dropLast(1)

// Add a different runtime variant for each platform
val javaComponent = components.findByName("java") as AdhocComponentWithVariants

for (file in files) {
    val classifier = file.name.substringAfter("natives-").substringBefore('.')
    // Creation of the native jars
    val nativeJar = tasks.register<Jar>("${classifier}Jar") {
        archiveClassifier = classifier
        from(file)
        //            val md = MessageDigest.getInstance("SHA-1")
        //            md.update(variantDefinition.lib.readBytes())
        //            manifest.attributes["sha1"] = md.digest().joinToString("") { "%02x".format(it) }
    }

    fun String.normalized() = lowercase().replace("[^a-z0-9]+".toRegex(), "")
    fun archOf(value: String): Arch {
        val v = value.normalized()
        return when {
            v.matches(Regex("^(x8664|amd64|ia32e|em64t|x64)$")) -> Arch.x86_64
            v.matches(Regex("^(x8632|x86|i[3-6]86|ia32|x32)$")) -> Arch.x86_32
            v.matches(Regex("^(ia64w?|itanium64)$")) -> Arch.itanium_64
            v == "ia64n" -> Arch.itanium_32
            v.matches(Regex("^(sparc|sparc32)$")) -> Arch.sparc_32
            v.matches(Regex("^(sparcv9|sparc64)$")) -> Arch.sparc_64
            v.matches(Regex("^(arm|arm32)$")) || v == "armv6hf" -> Arch.arm_32
            v == "aarch64" -> Arch.aarch_64
            v.matches(Regex("^(mips|mips32)$")) -> Arch.mips_32
            v.matches(Regex("^(mipsel|mips32el)$")) -> Arch.mipsel_32
            v == "mips64" -> Arch.mips_64
            v == "mips64el" -> Arch.mipsel_64
            v.matches(Regex("^(ppc|ppc32)$")) -> Arch.ppc_32
            v.matches(Regex("^(ppcle|ppc32le)$")) -> Arch.ppcle_32
            v == "ppc64" -> Arch.ppc_64
            v == "ppc64le" -> Arch.ppcle_64
            v == "s390" -> Arch.s390_32
            v == "s390x" -> Arch.s390_64
            v.matches(Regex("^(riscv|riscv32)$")) -> Arch.riscv
            v == Arch.riscv64.name -> Arch.riscv64
            v == Arch.e2k.name -> Arch.e2k
            v == "loongarch64" -> Arch.loongarch_64
            v == Arch.universal.name -> Arch.universal
            else -> error("invalid Arch: $v ($value)")
        }
    }

    fun currentArch(): Arch = archOf(System.getProperty("os.arch"))
    fun osOf(value: String): OS {
        val v = value.normalized()
        return when {
            v == OS.android.name -> OS.android
            v.startsWith(OS.aix.name) -> OS.aix
            v.startsWith(OS.hpux.name) -> OS.hpux
            // Avoid the names such as os4000
            v.startsWith(OS.os400.name) && (v.length <= 5 || !v[5].isDigit()) -> OS.os400
            v.startsWith(OS.linux.name) -> OS.linux
            v.startsWith("mac") || v.startsWith(OS.osx.name) -> OS.osx
            v.startsWith(OS.freebsd.name) -> OS.freebsd
            v.startsWith(OS.openbsd.name) -> OS.openbsd
            v.startsWith(OS.netbsd.name) -> OS.netbsd
            v.startsWith("solaris") || v.startsWith(OS.sunos.name) -> OS.sunos
            v.startsWith(OS.windows.name) -> OS.windows
            v.startsWith(OS.zos.name) -> OS.zos
            else -> error("invalid OS: $v ($value)")
        }
    }

    fun currentOS(): OS = osOf(System.getProperty("os.name"))

    val nativeRuntimeElements = configurations.consumable("${classifier}RuntimeElements") {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME)) // this is also by default
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY)) // this is also by default
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR)) // this is also by default
            val (o, a) = classifier.split("-")
            println("$o, $a")
            os = osOf(o)
            arch = archOf(a)
        }
        outgoing {
            //            artifact(gluegen)
            artifact(nativeJar)
        }
        extendsFrom(configurations.runtimeElements.get())
    }
    javaComponent.addVariantsFromConfiguration(nativeRuntimeElements.get()) {}
}

configurations {
    apiElements {
        outgoing {
            artifacts.clear()
            artifact(gluegen)
        }
    }
    runtimeElements {
        outgoing.artifacts.clear()
    }
}

// don't publish the default runtime without native jar
//javaComponent.withVariantsFromConfiguration(configurations.runtimeElements.get()) { skip() }
//javaComponent.withVariantsFromConfiguration(configurations.apiElements.get()) { skip() }

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
        groupId = "org.jogamp.gluegen"
        artifactId = "gluegen-rt"
        for (a in artifacts) {
            println(a.file)
            println(a.extension + ", " + a.classifier)
        }
    }
    repositories.maven {
        name = "repo"
        url = uri("$projectDir/$name")
    }
}


testing {
    suites { // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) { // Use Kotlin Test test framework
            useKotlinTest(embeddedKotlinVersion)
        }

        // Create a new test suite
        val functionalTest by registering(JvmTestSuite::class) { // Use Kotlin Test test framework
            useKotlinTest(embeddedKotlinVersion)

            dependencies { // functionalTest test suite depends on the production code in tests
                implementation(project())
            }

            targets {
                all { // This test suite should run after the built-in test suite has run its tests
                    testTask.configure { shouldRunAfter(test) }
                }
            }
        }
    }
}

gradlePlugin { // Define the plugin
    val greeting by plugins.creating {
        id = "org.example.greeting"
        implementationClass = "org.example.JoglVariantPublisherPlugin"
    }
}

gradlePlugin.testSourceSets.add(sourceSets["functionalTest"])

tasks.named<Task>("check") { // Include functionalTest as part of the check lifecycle
    dependsOn(testing.suites.named("functionalTest"))
}

val AttributeContainer.category: Category?
    get() = getAttribute(Category.CATEGORY_ATTRIBUTE)
val AttributeContainer.usage: Usage?
    get() = getAttribute(Usage.USAGE_ATTRIBUTE)
var AttributeContainer.os: OS?
    get() = getAttribute(Attribute.of(OS::class.java))
    set(value) {; attribute(Attribute.of(OS::class.java), value!!); }
var AttributeContainer.arch: Arch?
    get() = getAttribute(Attribute.of(Arch::class.java))
    set(value) {; attribute(Attribute.of(Arch::class.java), value!!); }

// based on https://github.com/trustin/os-maven-plugin


enum class OS {
    aix,
    android,
    hpux,
    os400,
    linux,
    osx,
    freebsd,
    openbsd,
    netbsd,
    sunos,
    windows,
    zos
}

enum class Arch {
    x86_64,
    x86_32,
    itanium_64,
    itanium_32,
    sparc_32,
    sparc_64,
    arm_32,
    aarch_64,
    mips_32,
    mipsel_32,
    mips_64,
    mipsel_64,
    ppc_32,
    ppcle_32,
    ppc_64,
    ppcle_64,
    s390_32,
    s390_64,
    riscv,
    riscv64,
    e2k,
    loongarch_64,
    universal
}