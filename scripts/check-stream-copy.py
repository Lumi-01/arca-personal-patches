"""Check byte preservation, short-read write coalescing and failure cleanup."""
import pathlib
import subprocess
import tempfile

root = pathlib.Path(__file__).resolve().parents[1]
java = root.parent / "tools/jdk-21.0.12.1+1/bin"
sources = [root / "extensions/extension/src/main/java/local/arca/StreamCopy.java",
           root / "tests/stream-copy/local/arca/StreamCopyCheck.java"]
with tempfile.TemporaryDirectory(prefix="arca-stream-copy-") as folder:
    subprocess.run([str(java / "javac"), "-d", folder, *map(str, sources)], check=True)
    subprocess.run([str(java / "java"), "-cp", folder, "local.arca.StreamCopyCheck"], check=True)
