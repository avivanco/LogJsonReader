import java.util.Properties

plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.logviewer"
version = "0.1.0-SNAPSHOT"
description = "Friendly viewer for JSON-structured log files"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

// Optional, untracked, machine-local settings (see local.properties.example). `buildDir` lets a checkout that lives in
// a synced folder (OneDrive, Dropbox) keep its build output elsewhere: sync clients lock files in build/ and break
// Gradle's cleanup.
val localProperties = Properties().apply {
	val file = rootProject.file("local.properties")
	if (file.isFile) {
		file.inputStream().use { load(it) }
	}
}
localProperties.getProperty("buildDir")?.let { layout.buildDirectory.set(file(it)) }

val duckdbVersion = "1.5.6.0"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.duckdb:duckdb_jdbc:$duckdbVersion")

	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	// DuckDB loads a native library; opt in explicitly so the JDK does not warn.
	jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.bootJar {
	archiveFileName = "logviewer.jar"
	manifest {
		attributes("Enable-Native-Access" to "ALL-UNNAMED")
	}
}

// --- Frontend bundling ------------------------------------------------------------------------------------------
// The React app in ../frontend is built and copied into the jar under /static, so one artifact serves UI and API.
// Pass -PskipFrontend to leave it out (the Docker build supplies the compiled UI itself; backend-only work is faster).
if (!project.hasProperty("skipFrontend")) {
	val frontendDir = rootProject.file("../frontend")
	val npm = if (System.getProperty("os.name").lowercase().contains("win")) "npm.cmd" else "npm"

	val installFrontend by tasks.registering(Exec::class) {
		workingDir = frontendDir
		commandLine(npm, "ci")
		inputs.files(frontendDir.resolve("package.json"), frontendDir.resolve("package-lock.json"))
		outputs.dir(frontendDir.resolve("node_modules"))
	}

	val buildFrontend by tasks.registering(Exec::class) {
		dependsOn(installFrontend)
		workingDir = frontendDir
		commandLine(npm, "run", "build")
		inputs.dir(frontendDir.resolve("src"))
		inputs.dir(frontendDir.resolve("public"))
		inputs.files(
			frontendDir.resolve("index.html"), frontendDir.resolve("package.json"),
			frontendDir.resolve("vite.config.ts"), frontendDir.resolve("tsconfig.json"),
			frontendDir.resolve("tsconfig.app.json"), frontendDir.resolve("tsconfig.node.json"),
		)
		outputs.dir(frontendDir.resolve("dist"))
	}

	tasks.processResources {
		from(buildFrontend) {
			into("static")
		}
	}
}
