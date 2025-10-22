plugins {
  `maven-publish`
  signing
}

publishing {
  // Configure all publications
  publications.withType<MavenPublication> {
    // Provide artifacts information required by Maven Central
    pom {
      name.set("Regional")
      description.set("Kotlin compiler plugin that supports region safety by creating local classes that represent regions")
      url.set("https://github.com/kyay10/regional")

      licenses {
        license {
          name.set("Apache-2.0")
          url.set("https://opensource.org/license/apache-2-0")
        }
      }
      developers {
        developer {
          id.set("kyay10")
          name.set("Youssef Shoaib")
        }
      }
      scm {
        url.set("https://github.com/kyay10/regional")
      }
    }
  }
}

signing {
  if (project.hasProperty("signing.gnupg.keyName")) {
    useGpgCmd()
    sign(publishing.publications)
  }
}
