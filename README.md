# TIRIOS AGRO — Gestión Porcina (Android local v0.2)

Proyecto Android nativo en **Kotlin + Jetpack Compose + Room**, preparado para probar el negocio desde cero **sin conexión a Google Sheets**. Incluye el logo verde y naranja seleccionado. El paquete Android se mantiene en `pe.granja.porcina` para actualizar la app ya instalada.

## IMPORTANTE ANTES DE USAR

- **Código fuente, no APK compilado.** Este entorno no cuenta con Android SDK ni Gradle descargables, por lo que no es posible comprobar aquí una compilación final. El workflow de GitHub Actions genera `app-debug.apk` si el código compila correctamente; si falla, consulta los logs.
- La versión anterior guardaba datos ficticios en `granja_local.db`. Esta versión usa otra BD, **`tirios_agro_local_v2.db`**, que empieza sin socios, animales, lotes, aportes, compras ni ventas. El código no migra los registros anteriores.
- Se precargan **solo los tres usuarios de prueba**. Son credenciales públicas para validación local, **no aptas para producción**. La autorización y los datos viven en este celular y no sincronizan entre dispositivos.
- Los gastos se registran una sola vez; el costo de animales comprados se genera al crear el lote y el costo de alimento se asigna al consumirlo. Nunca vuelvas a ingresar la misma compra como gasto independiente.
- **Costeo de corrales: estimación operativa**, no depreciación fiscal certificada. Vida útil configurable (predeterminada 36 meses); reparto simple por animales históricos del lote. Validar con contador para contabilidad formal.
- El indicador de «pendiente de cubrir desembolsos» compara ingresos brutos por ventas con desembolsos acumulados. No equivale a retorno de inversión contable ni a capital devuelto a socios.
- Los comprobantes adjuntos se copian al almacenamiento privado del dispositivo. Los PDF se generan localmente mediante el selector Guardar como... del Android.

## Datos para iniciar sesión

| Rol | Correo | Clave |
| --- | --- | --- |
| Administrador principal | `admin@granja.local` | `Admin1234!` |
| Administrador operativo | `socio@granja.local` | `Socio1234!` |
| Visualizador | `visor@granja.local` | `Visor1234!` |

Es obligatorio reemplazar estas cuentas por credenciales únicas y un mecanismo de identidad fiable antes de usar la app con información compartida o financiera real.

## Funciones implementadas en esta revisión

1. **Login y ayuda**: logo, tres roles, permisos por módulo/operación, recorrido breve por popups solo al primer inicio de cada usuario. Bandera `onboardingCompleted` guardada en la tabla `users` de Room. Botón de ayuda para repetir recorrido.
2. **Inicio**: cantidades reales, ventas, capital aportado, desembolsos, balance de ventas, costos de animales vendidos, ganancia y margen. No hay datos de ejemplo en la base.
3. **Inventario / chanchos**: registro masivo de animales comprados por lote; origen comprado o nacido; fecha de nacimiento, edad calculada, sexo editable, etapa, peso, observación, arete único histórico, ficha detallada, venta rápida, fallecimiento mediante popup, filtros por sexo/etapa/edad/peso.
4. **Partos y crías**: identificación de madre y padre opcional; creación individual automática de crías vivas, con vínculo a madre y lote. Las etapas no modifican el origen histórico.
5. **Gastos y compras**: destino del desembolso (infraestructura, alimento, sanidad, operativo, otro); concepto, proyecto, importe, pagador (socio/caja), actor autenticado, soporte de comprobantes privados, historial y anulación supervisada.
6. **Alimentos**: compra con cantidad en kg y lote de inventario; consumo y asignación por lote; mermas; acción rápida tras la compra para preguntar si deseas marcar otro alimento anterior como agotado; duración desde primer uso.
7. **Socios y capital**: crear máximo dos socios, asociar cada uno con usuario y registrar aportes/retiros, ver desembolsos personales acumulados por socio y diferencia entre ambos.
8. **Ventas**: botón dentro de la ficha del animal; al confirmar guarda ingreso, estado vendido, peso y costo estimado de corrales, y emite aviso. Historial en Reportes.
9. **Reportes**: ventas, márgenes, costos, inversión y PDF local (balance, socios, animales, ventas, gastos, almacén), con varias páginas cuando corresponda.
10. **Administración**: crear usuarios (operativo y visualizador), activar/bloquear cuenta, matriz de permisos, flags de login/mantenimiento, auditoría, vida útil de corrales.
11. **Notificaciones**: campana con cantidad no leída y lista de movimientos locales. Venta y gasto permiten abrir su detalle. No incluye push en dispositivos de otros socios.

## Límites actuales que requieren feedback o mejoras

- No hay Google Sheets ni envío de notificaciones a otros celulares. **Cada dispositivo tiene su propia Room.**
- La venta múltiple, proyección de engorde, cronograma sanitario, reembolsos explícitos y migración de datos v1 a v2 quedan pendientes.
- No hay edición de costo directo ya registrado; puede anularse un gasto solo cuando no existe consumo de su alimento. En esta primera versión se prioriza auditoría y prevención de doble asignación.
- Un usuario con control físico o técnico del teléfono puede inspeccionar/modificar una base local no cifrada; el RBAC de interfaz no equivale a seguridad de un servidor.
- La exportación PDF es básica, orientada a auditoría interna; no reemplaza un informe tributario.
- Si registras costos después de la venta, el costo reconocido puede variar; el uso estimado de infraestructura se fija al momento de vender.

## Requisitos técnicos

- Android Gradle Plugin `8.13.2`, Kotlin `2.2.21`, KSP `2.2.21-2.0.4`, Gradle `8.14.3`, Java 17.
- `compileSdk 36`, `minSdk 26`, Jetpack Compose BOM `2025.10.00`, Room `2.8.5`.
- Abre la **raíz** que contiene `settings.gradle.kts` como proyecto Android Studio. Si usas GitHub, el workflow compila desde la raíz, no desde una carpeta `android/`.

Consulta `docs/FLUJO_REAL.md` para el recorrido de uso y `docs/ACTUALIZAR_DESDE_CELULAR.md` si subes el código con Android.


## Actualización 0.2.1 de revisión de compilación

- Ajuste de nombres en verificación de hash del inicio de sesión para evitar ambigüedad de identificadores.
- Workflow GitHub Actions que conserva `build.log` y muestra errores específicos cuando falla la compilación.
- VersionCode 3 sin borrar la base Room de la v2.
- Consulta `docs/COMPILACION_Y_ERRORES.md` antes de actualizar GitHub.
