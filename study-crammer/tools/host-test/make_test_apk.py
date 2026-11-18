#!/usr/bin/env python3
"""Builds synthetic APKs (ZIP + APK Signing Block v2/v3) to exercise the native
integrity engine and the Gradle checksum task on a desktop machine.

usage: make_test_apk.py OUT_DIR
Writes good.apk, the cert DER (cert.der / evil_cert.der) and tampered variants.
"""
import os, struct, sys, zipfile, io

MAGIC = bytes([0x49,0x47,0x7F,0x48,0x41,0x53,0x48,0x42,0x4C,0x42,0x01,0x76,0x31,0xA5,0x5A,0xC3])
BLOB = 736

def lp(b): return struct.pack('<I', len(b)) + b

def signer_v2(cert):
    digests = lp(lp(struct.pack('<I', 0x0103) + lp(b'd' * 32)))
    signed_data = digests + lp(lp(cert)) + lp(b'')
    return lp(signed_data) + lp(b'sigs') + lp(b'pubkey')

def signer_v3(cert):
    digests = lp(lp(struct.pack('<I', 0x0103) + lp(b'd' * 32)))
    signed_data = digests + lp(lp(cert)) + struct.pack('<II', 28, 0x7fffffff) + lp(b'')
    return lp(signed_data) + struct.pack('<II', 28, 0x7fffffff) + lp(b'sigs') + lp(b'pubkey')

def sig_block(v2_cert, v3_cert, two_signers=False):
    v2 = lp(lp(signer_v2(v2_cert)) + (lp(signer_v2(v2_cert)) if two_signers else b''))
    v3 = lp(lp(signer_v3(v3_cert)))
    pairs = struct.pack('<QI', len(v2) + 4, 0x7109871a) + v2
    pairs += struct.pack('<QI', 8 + 4, 0x42726577) + b'\0' * 8   # padding/verity-style unknown pair
    pairs += struct.pack('<QI', len(v3) + 4, 0xf05368c0) + v3
    size = len(pairs) + 24
    return struct.pack('<Q', size) + pairs + struct.pack('<Q', size) + b'APK Sig Block 42'

def build(path, files, block):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w') as z:
        for name, data, method in files:
            z.writestr(zipfile.ZipInfo(name, (2024, 1, 1, 0, 0, 0)), data, compress_type=method)
    raw = buf.getvalue()
    eocd = raw.rfind(b'PK\x05\x06')
    cd_off = struct.unpack_from('<I', raw, eocd + 16)[0]
    if block:
        raw = raw[:cd_off] + block + raw[cd_off:]
        eocd += len(block)
        raw = raw[:eocd + 16] + struct.pack('<I', cd_off + len(block)) + raw[eocd + 20:]
    with open(path, 'wb') as f:
        f.write(raw)

def main(out):
    os.makedirs(out, exist_ok=True)
    rnd = os.urandom
    cert = b'\x30\x82' + rnd(700)
    evil = b'\x30\x82' + rnd(700)
    so = lambda: rnd(6000) + MAGIC + rnd(BLOB - 16) + rnd(4000)
    so_x86_64, so_arm64 = so(), so()
    dex1 = b'dex\n035\0' + b'compressible dex payload ' * 40000 + rnd(3000)
    dex2 = b'dex\n035\0' + rnd(50000) + b'zz' * 20000
    D, S = zipfile.ZIP_DEFLATED, zipfile.ZIP_STORED
    base = [('AndroidManifest.xml', b'<manifest/>' * 50, D),
            ('classes.dex', dex1, D), ('classes2.dex', dex2, D),
            ('lib/x86_64/libintegrity_guard.so', so_x86_64, S),
            ('lib/arm64-v8a/libintegrity_guard.so', so_arm64, S),
            ('res/raw/notclasses.dex.txt', b'x', D)]
    blk = sig_block(cert, cert)
    open(os.path.join(out, 'cert.der'), 'wb').write(cert)
    open(os.path.join(out, 'evil_cert.der'), 'wb').write(evil)
    build(os.path.join(out, 'good.apk'), base, blk)

    def variant(name, files=base, block=blk):
        build(os.path.join(out, name + '.apk'), files, block)
    swap = lambda n, d, m=None: [(a, d if a == n else b, m if (a == n and m is not None) else c) for a, b, c in base]
    variant('tamper_dex', swap('classes.dex', dex1[:-1] + b'!'))
    variant('extra_dex', base + [('classes3.dex', b'dex\n035\0evil', D)])
    variant('resigned', block=sig_block(evil, evil))
    variant('mixed_blocks', block=sig_block(cert, evil))       # stale v2 + attacker v3
    variant('no_sig_block', block=None)
    variant('two_signers', block=sig_block(cert, cert, two_signers=True))
    i = so_x86_64.index(MAGIC)
    variant('tamper_so_code', swap('lib/x86_64/libintegrity_guard.so', so_x86_64[:100] + b'\x90' + so_x86_64[101:]))
    variant('tamper_so_blob', swap('lib/x86_64/libintegrity_guard.so',
                                   so_x86_64[:i + 100] + b'\xAB' * 8 + so_x86_64[i + 108:]))  # inside blob => still OK
    variant('so_deflated', swap('lib/x86_64/libintegrity_guard.so', so_x86_64, D))    # inflate path => still OK

if __name__ == '__main__':
    main(sys.argv[1])
