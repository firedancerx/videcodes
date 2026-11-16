// ============================================================================
// expected_hashes.h — release integrity expectations for integrity_guard.cpp
// ----------------------------------------------------------------------------
// PLACEHOLDER (UNPROVISIONED). Every value is zero, which integrity_guard.cpp
// treats as "not provisioned" => a release build (IG_ENFORCE=1) FAILS CLOSED
// with Error 409 until you run:
//
//     ./gradlew :app:assembleRelease :app:printReleaseChecksums
//     ./gradlew :app:assembleRelease :app:verifyReleaseChecksums
//
// printReleaseChecksums overwrites this file with XOR-split shares
// (expected = SHARE_A ^ SHARE_B) of the real hashes. Debug builds ignore it.
// See docs/BUILD_AND_RELEASE.md.
// ============================================================================
#pragma once

#define IG_DEX_COUNT 0

#define IG_CERT_A { 0 }
#define IG_CERT_B { 0 }

#define IG_DEX_A { { 0 } }
#define IG_DEX_B { { 0 } }

// ABI slot order: arm64-v8a, armeabi-v7a, x86_64, x86
#define IG_SO_A { { 0 } }
#define IG_SO_B { { 0 } }
