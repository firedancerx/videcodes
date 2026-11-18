// Host (desktop) test driver for the pure-C++ core of integrity_guard.cpp.
// Build: see run_host_tests.sh. Exit code 0 == all assertions passed.
#define IG_HOST_TEST 1
#include "../../app/src/main/cpp/integrity_guard.cpp"

#include <fstream>
#include <iostream>
#include <iterator>

static int g_failures = 0;
#define EXPECT(cond, msg) do { if (!(cond)) { ++g_failures; std::cerr << "FAIL: " << msg << "\n"; } \
                               else { std::cout << "ok   " << msg << "\n"; } } while (0)

static std::string hexs(const uint8_t* d) {
    char b[65];
    for (int i = 0; i < 32; ++i) snprintf(b + 2 * i, 3, "%02x", d[i]);
    return b;
}

static std::vector<uint8_t> slurp(const std::string& p) {
    std::ifstream f(p, std::ios::binary);
    return std::vector<uint8_t>((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
}

int main(int argc, char** argv) {
    if (argc < 2) { std::cerr << "usage: host_test DIR\n"; return 2; }
    const std::string dir = argv[1];

    // --- SHA-256 known-answer tests (FIPS 180-2 vectors) ---
    uint8_t h[32];
    ig::Sha256::digest(reinterpret_cast<const uint8_t*>(""), 0, h);
    EXPECT(hexs(h) == "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "sha256(\"\")");
    ig::Sha256::digest(reinterpret_cast<const uint8_t*>("abc"), 3, h);
    EXPECT(hexs(h) == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "sha256(abc)");
    {
        ig::Sha256 s;
        std::string chunk(1000, 'a');
        for (int i = 0; i < 1000; ++i) s.update(reinterpret_cast<const uint8_t*>(chunk.data()), chunk.size());
        s.finish(h);
        EXPECT(hexs(h) == "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", "sha256(1M x 'a', streamed)");
    }
    EXPECT(ig::dex_index("classes.dex") == 1 && ig::dex_index("classes2.dex") == 2 &&
           ig::dex_index("classes10.dex") == 10 && ig::dex_index("classes1.dex") == 0 &&
           ig::dex_index("classes02.dex") == 0 && ig::dex_index("lib/classes.dex") == 0, "dex_index naming rules");

    auto cert = slurp(dir + "/cert.der");
    auto evil = slurp(dir + "/evil_cert.der");
    EXPECT(ig::expected_provisioned(), "provisioned header injected");
    EXPECT(ig::check_cert_bytes({cert}, nullptr) == 0, "PackageManager cert: genuine");
    EXPECT(ig::check_cert_bytes({evil}, nullptr) == IG_FAIL_CERT_PM, "PackageManager cert: re-signed");
    EXPECT(ig::check_cert_bytes({cert, cert}, nullptr) == IG_FAIL_CERT_PM, "PackageManager cert: multiple signers");
    EXPECT(ig::check_cert_bytes({}, nullptr) == IG_FAIL_CERT_PM, "PackageManager cert: none");

    struct Case { const char* apk; int expect; };
    const Case cases[] = {
        {"good", 0},
        {"so_deflated", 0},
        {"tamper_so_blob", 0},
        {"tamper_dex", IG_FAIL_DEX},
        {"extra_dex", IG_FAIL_DEX},
        {"resigned", IG_FAIL_CERT_APK},
        {"mixed_blocks", IG_FAIL_CERT_APK},
        {"no_sig_block", IG_FAIL_CERT_APK},
        {"two_signers", IG_FAIL_CERT_APK},
        {"tamper_so_code", IG_FAIL_SO},
        {"does_not_exist", IG_FAIL_APK_OPEN | IG_FAIL_DEX | IG_FAIL_SO | IG_FAIL_CERT_APK},
    };
    for (const auto& c : cases) {
        std::string report;
        int got = ig::file_checks({dir + "/" + c.apk + ".apk"}, &report);
        EXPECT(got == c.expect, std::string("file_checks(") + c.apk + ") = 0x" + [&] {
            char b[16]; snprintf(b, sizeof b, "%x", got); return std::string(b); }() + "\n" + report);
    }
    EXPECT(ig::tracer_pid() == 0, "TracerPid == 0 (not being ptraced)");
    EXPECT(!ig::hook_artifacts_present(), "no frida/xposed artefacts in this process");

    std::cout << (g_failures ? "\nFAILED: " : "\nALL PASSED (") << g_failures << (g_failures ? "\n" : " failures)\n");
    return g_failures ? 1 : 0;
}
