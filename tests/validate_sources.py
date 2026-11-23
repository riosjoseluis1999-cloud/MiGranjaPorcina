"""Comprobaciones ESTÁTICAS del proyecto. NO equivalen a compilación Android."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
main=(r/'app/src/main/java/pe/granja/porcina/MainActivity.kt').read_text()
repo=(r/'app/src/main/java/pe/granja/porcina/data/AgroRepository.kt').read_text()
db=(r/'app/src/main/java/pe/granja/porcina/data/Database.kt').read_text()
finance=(r/'app/src/main/java/pe/granja/porcina/ui/Finance.kt').read_text()
assert 'TIRIOS AGRO' in main
assert 'onboardingCompleted' in db and 'finishTour' in repo
assert 'class Partner' in db and 'class Outlay' in db
assert 'class FoodBatch' in db and 'class Notice' in db
assert 'tag' in db and 'd.tags().none' in repo
assert 'fun newBirth' in repo and 'fun death' in repo and 'fun sell' in repo
assert 'onDetail("ANIMAL"' in (r/'app/src/main/java/pe/granja/porcina/ui/Inventory.kt').read_text()
assert '¿Quieres actualizar algún alimento anterior como agotado?' in finance
assert 'if(!waste) assign("ALIMENTO"' in repo
assert 'val oldUsed=d.usages()' in repo
assert 'AGOTADO' in repo
assert 'TIRIOS-AGRO-v0-2-2-APK' in (r/'.github/workflows/android-apk.yml').read_text()
assert 'android-actions/setup-android@v4' in (r/'.github/workflows/android-apk.yml').read_text()
assert '2025.10.00' in (r/'app/build.gradle.kts').read_text()
assert (r/'app/src/main/res/drawable-nodpi/logo_tirios.png').is_file()
ET.parse(r/'app/src/main/AndroidManifest.xml')
ET.parse(r/'app/src/main/res/xml/file_paths.xml')
print('Comprobaciones estáticas completadas; falta compilar con Android SDK.')

# Los tres archivos del prototipo antiguo no deben formar parte de la versión limpia.
for legacy in ["data/Repository.kt","Operations.kt","SecurityAndReports.kt"]:
    assert not (r/"app/src/main/java/pe/granja/porcina"/legacy).exists(), f"Fuente obsoleta: {legacy}"
assert 'rm -f app/src/main/java/pe/granja/porcina/data/Repository.kt' in (r/'.github/workflows/android-apk.yml').read_text()
