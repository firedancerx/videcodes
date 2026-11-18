# ============================================================================
# libintegrity_guard.so — native self-checksum & anti-tamper engine
# ============================================================================
cmake_minimum_required(VERSION 3.22.1)
project(integrity_guard LANGUAGES CXX)

option(IG_ENFORCE "Fail-closed verification (release builds)" OFF)

add_library(integrity_guard SHARED integrity_guard.cpp)

target_compile_features(integrity_guard PRIVATE cxx_std_17)
target_compile_options(integrity_guard PRIVATE
    -O2
    -Wall -Wextra
    -fvisibility=hidden            # only JNI_OnLoad is exported (natives bound via RegisterNatives)
    -fvisibility-inlines-hidden
    -fno-rtti
    -fstack-protector-strong
    -ffunction-sections -fdata-sections
)

if(IG_ENFORCE)
    target_compile_definitions(integrity_guard PRIVATE IG_ENFORCE=1)
endif()

# The .so hashes ITSELF (minus the IgBlob region). A GNU build-id is a hash of the
# whole output, so it would change when expected_hashes.h changes and break the
# two-pass release flow. Remove the NDK default and disable it explicitly.
string(REPLACE "-Wl,--build-id=sha1" "" CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS}")
string(REPLACE "-Wl,--build-id" "" CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS}")

target_link_options(integrity_guard PRIVATE
    -Wl,--build-id=none
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
    -Wl,-z,max-page-size=16384    # Android 15+ 16 KB page-size devices (Play requirement)
    -Wl,-z,relro -Wl,-z,now
)

# liblog not needed (no native logging); zlib comes from the NDK sysroot.
target_link_libraries(integrity_guard PRIVATE z)
