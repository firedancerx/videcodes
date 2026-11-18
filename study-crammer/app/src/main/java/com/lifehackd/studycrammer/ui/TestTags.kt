package com.lifehackd.studycrammer.ui

/**
 * Stable IDs for automated UI testing. Exposed as Android resource IDs (testTagsAsResourceId),
 * so Firebase Test Lab Robo directives (text:/click:) can type into and tap these elements.
 * Renaming one requires updating .github/workflows/study-crammer-ci.yml.
 */
object TestTags {
    const val API_KEY_FIELD = "api_key_field"
    const val TEST_SAVE_BUTTON = "test_save_button"
    const val NOTES_FIELD = "notes_field"
}
