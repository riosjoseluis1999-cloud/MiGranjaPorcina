# Cómo limpiar las fuentes antiguas desde la web de GitHub

1. Abre tu repositorio `MiGranjaPorcina` en la rama `main`.
2. Abre `app/src/main/java/pe/granja/porcina/data/Repository.kt`. Usa el menú del archivo `...` → `Delete file` y confirma `Commit changes`.
3. Repite para `app/src/main/java/pe/granja/porcina/Operations.kt`.
4. Repite para `app/src/main/java/pe/granja/porcina/SecurityAndReports.kt`.
5. Conserva `app/src/main/java/pe/granja/porcina/data/AgroRepository.kt` y `Database.kt`.
6. Reemplaza `.github/workflows/android-apk.yml` con el que se incluye en este ZIP (la limpieza se ejecuta durante la compilación).
7. Actualiza los demás archivos de la nueva versión si todavía no están completos.
8. En Actions, ejecuta el workflow **TIRIOS AGRO - APK local**; si falla, descarga el artifact `TIRIOS-AGRO-LOG-COMPILACION`.

Nota: Android Studio puede seguir viendo errores hasta que los tres archivos antiguos se eliminen físicamente del repositorio.
