# Subir la versión nueva desde un celular Android

El ZIP contiene los archivos **en la raíz del proyecto**, es decir `app/`, `build.gradle.kts`, `settings.gradle.kts`, `.github/workflows/`, etc. **No pongas una carpeta `android` delante** en tu repositorio `MiGranjaPorcina`.

## Alternativa más cómoda cuando GitHub web no permite subir carpetas

Desde Android, utiliza Termux de F-Droid (no copies contraseñas en comandos). Requiere GitHub y permiso de acceso a tu repositorio:

```sh
pkg update
pkg install git unzip
termux-setup-storage
cd ~/storage/downloads
unzip TIRIOS_AGRO_Android_v2.zip -d tirios-nuevo
cd ~
git clone https://github.com/riosjoseluis1999-cloud/MiGranjaPorcina.git
cd MiGranjaPorcina
cp -r ~/storage/downloads/tirios-nuevo/TIRIOS_AGRO_Android_v2/. .
git add -A
git commit -m 'TIRIOS AGRO v0.2 local'
git push origin main
```

Si GitHub requiere autenticación, utiliza su proceso de token personal o una app Git autenticada; **no uses tu contraseña en la terminal**. Revisa `git status` antes de `git add -A` para evitar subir archivos privados.

## Compilación

1. En GitHub abre `Actions`.
2. Busca `TIRIOS AGRO - APK local` y ejecuta `Run workflow`; también arranca con `push`.
3. Si termina en verde, abre la ejecución → Artifacts → `TIRIOS-AGRO-v2-APK`.
4. Descarga el ZIP de Artifact, extráelo y abre `app-debug.apk` desde tu Android.
5. Si falla en rojo, entra al paso rojo y comparte el fragmento que contiene `FAILURE:` o `e:`.

**Nota:** Android puede pedir autorización para instalar APKs de fuentes externas. Instala solo tu compilación del repositorio que controlas. El proyecto utiliza el mismo `applicationId` y la clave debug de GitHub puede ser diferente de una compilación anterior; si aparece conflicto de firma al instalar, haz copia de los datos que necesites antes de desinstalar la versión previa.
