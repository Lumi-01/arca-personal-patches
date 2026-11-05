"""Host checks for scoped formatter cancellation; requires JAVA_HOME."""
import os
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[1]
java = Path(os.environ["JAVA_HOME"]) / "bin"
sources = [root / "extensions/extension/src/main/java/local/arca/BodyWork.java",
           root / "tests/body-work/local/arca/BodyWorkCheck.java"]
with tempfile.TemporaryDirectory(prefix="arca-body-check-") as folder:
    subprocess.run([str(java / "javac"), "-d", folder, *map(str, sources)], check=True)
    subprocess.run([str(java / "java"), "-cp", folder, "local.arca.BodyWorkCheck"], check=True)
