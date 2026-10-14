"""Exercise the actual result-admission helper without bootstrapping Android or running IPC."""
from pathlib import Path
import os,subprocess,tempfile
root=Path(__file__).resolve().parents[1]
platform=Path(os.environ['ANDROID_HOME'])/'platforms/android-36/android.jar'
java=Path(os.environ['JAVA_HOME'])/'bin'
with tempfile.TemporaryDirectory() as temp:
 classes=Path(temp)/'classes'
 sources=[str(p) for p in (root/'app/src/main/java').rglob('*.java')]+[str(root/'tests/ReadFenceTest.java')]
 subprocess.run([str(java/'javac'),'-classpath',str(platform),'-d',str(classes),*sources],check=True)
 subprocess.run([str(java/'java'),'-cp',str(classes)+os.pathsep+str(platform),'in.scrollcount.probe.ReadFenceTest'],check=True)
