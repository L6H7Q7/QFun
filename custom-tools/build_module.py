"""Build the Java extension over the pinned official QFun 1.3.4 APK.

Requires Python 3.11+, JDK 17+, Android SDK build-tools, and Xposed API 82.
The upstream Gradle build is separate; the tested release uses this overlay.
"""
import argparse
import hashlib
from pathlib import Path
import shutil
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASE_SHA256 = '252dbcc75e5ea59abbb7a99258d0f4d2e6f56b945f6f121eabd126b2ff04a63c'

def run(*args):
    subprocess.run([str(x) for x in args], check=True)

def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--xposed-api', type=Path, required=True)
    parser.add_argument('--build-tools', default='36.0.0')
    parser.add_argument('--platform', default='android-36')
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    assert hashlib.sha256(args.base.read_bytes()).hexdigest() == BASE_SHA256, 'Unexpected base APK'
    assert not args.out.exists(), 'Output already exists'
    args.out.parent.mkdir(parents=True, exist_ok=True)
    work = args.out.parent / 'overlay-build'
    work.mkdir(exist_ok=True)
    classes, dex = work / 'classes', work / 'dex'
    classes.mkdir(exist_ok=True)
    dex.mkdir(exist_ok=True)
    suffix = '.exe' if __import__('os').name == 'nt' else ''
    javac = args.java_home / 'bin' / ('javac' + suffix)
    java = args.java_home / 'bin' / ('java' + suffix)
    android = args.sdk / 'platforms' / args.platform / 'android.jar'
    sep = __import__('os').pathsep
    sources = sorted((ROOT / 'app/src/main/java/me/yxp/qfun/custom').glob('*.java'))
    run(javac, '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-cp', str(android) + sep + str(args.xposed_api), '-d', classes, *sources)
    jar = work / 'extension.jar'
    with zipfile.ZipFile(jar, 'w') as z:
        for p in classes.rglob('*.class'):
            z.write(p, p.relative_to(classes).as_posix())
    tools = args.sdk / 'build-tools' / args.build_tools
    run(java, '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '26', '--lib', android, '--classpath', args.xposed_api, '--output', dex, jar)
    additions = {'classes2.dex': (dex / 'classes.dex').read_bytes()}
    assets = ROOT / 'app/src/main/assets'
    additions['assets/xposed_init'] = (assets / 'xposed_init').read_bytes()
    for p in (assets / 'qfun-splash').glob('*.png'):
        additions['assets/qfun-splash/' + p.name] = p.read_bytes()
    additions['META-INF/xposed/module.prop'] = (ROOT / 'app/src/main/resources/META-INF/xposed/module.prop').read_bytes()
    with zipfile.ZipFile(args.base) as src:
        assert 'classes2.dex' not in src.namelist()
        cut = src.start_dir
        with args.base.open('rb') as f:
            f.seek(cut - 24)
            footer = f.read(24)
            if footer[8:] == b'APK Sig Block 42':
                cut -= struct.unpack('<Q', footer[:8])[0] + 8
        retained = [i for i in src.infolist() if i.filename not in additions and not (
            i.filename.upper().startswith('META-INF/') and (
                i.filename.upper().endswith(('.RSA', '.SF', '.DSA', '.EC'))
                or i.filename.upper() == 'META-INF/MANIFEST.MF'))]
        assert all(i.header_offset < cut for i in retained)
        with args.base.open('rb') as f, zipfile.ZipFile(args.out, 'w') as dst:
            left = cut
            while left:
                block = f.read(min(left, 8 * 1024 * 1024))
                assert block
                dst.fp.write(block)
                left -= len(block)
            dst.start_dir = cut
            dst.filelist = retained
            dst.NameToInfo = {i.filename: i for i in retained}
            dst._didModify = True
            for name, data in additions.items():
                dst.writestr(name, data, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(args.out) as built, zipfile.ZipFile(args.base) as base:
        assert built.read('classes.dex') == base.read('classes.dex')
        assert b'CardClickEntry' in built.read('assets/xposed_init')
    print('Unsigned module:', args.out)
    print('Sign with your own keystore using Android apksigner (minimum SDK 26).')

if __name__ == '__main__':
    main()
