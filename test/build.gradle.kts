plugins {
    id("gradleUp.variant-republisher")
}

republish {
    val jogampNatives = listOf("natives-android-aarch64",
                               "natives-linux-aarch64",
                               "natives-linux-amd64",
                               "natives-linux-armv6hf",
                               "natives-macosx-universal",
                               "natives-windows-amd64")
    library {
        from {
            gav = "org.jogamp.gluegen:gluegen-rt:2.5.0"
            natives = jogampNatives
            repo = "https://jogamp.org/deployment/maven"
        }
        into {
            group = "org.scijava"
            repo = layout.buildDirectory.dir("repo")
        }
    }
    library {
        from {
            gav = "org.jogamp.jogl:jogl-all:2.5.0"
            natives = jogampNatives
            repo = "https://jogamp.org/deployment/maven"
        }
        into {
            group = "org.scijava"
            repo = layout.buildDirectory.dir("repo")
        }
    }
}