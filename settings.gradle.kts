rootProject.name = "CompanionOS"

// Java desktop application (JavaFX overlay, local voice + SLM client, updater).
include(":desktop")

// Spring Boot control/distribution plane (auth, manifests, signed downloads).
// Enabled from Milestone E onwards. Kept out of the build until then so the
// desktop app can be built and run on its own.
// include(":backend")
