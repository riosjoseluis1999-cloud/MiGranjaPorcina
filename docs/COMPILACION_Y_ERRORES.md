# Compilación TIRIOS AGRO v0.2.1

## Proyecto
Sube **el contenido** de esta carpeta a la raíz del repositorio GitHub. No coloques otra carpeta `android/` encima de `app/`.

## Desde el celular
1. Abre tu repositorio en GitHub y actualiza los archivos modificados, especialmente el workflow `.github/workflows/android-apk.yml`.
2. Ve a **Actions** y abre **TIRIOS AGRO - APK local**.
3. Pulsa **Run workflow** o usa el commit para ejecutar automáticamente.
4. Si termina verde, descarga el artefacto **TIRIOS-AGRO-v2-1-APK**.
5. Si termina rojo, abre el trabajo **apk-debug** y descarga el artefacto **TIRIOS-AGRO-LOG-COMPILACION**. Comparte el archivo `build.log` para revisar las líneas `e:`, `error:` y `FAILURE:`.

## Verificaciones realizadas aquí
- Compilación de todos los archivos Kotlin con un **conjunto local de sustitutos mínimos** de Android/Compose/Room (sirve para comprobar sintaxis y tipos básicos, no equivale al compilador Android real).
- XML y estructura del proyecto.
- Compatibilidad declarada: AGP 8.13.2, Gradle 8.14.3, JDK 17, Compose BOM 2025.10.00, Room 2.8.5.

No hay SDK Android ni Gradle disponibles en este entorno; la compilación auténtica y la generación de APK siguen siendo responsabilidad de GitHub Actions.

## Importante
Se mantiene el nombre de base de datos `tirios_agro_local_v2.db` para no borrar por accidente los datos guardados de la versión 0.2.0 al actualizar. No desinstales la aplicación si ya registraste información real.
