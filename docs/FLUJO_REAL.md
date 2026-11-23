# Flujo de prueba real — TIRIOS AGRO v0.2

## 1. Primer ingreso como administrador

1. Abre TIRIOS AGRO y autentícate con la cuenta `admin@granja.local`.
2. Aparecerán **popups breves**: Inicio, Inventario, Gastos, Socios, Reportes y Administración. Puedes continuar u omitir. Room guarda el flag **por usuario**. Repetir desde el ícono Ayuda.
3. El Dashboard empieza con **cero animales, cero ventas y cero aportes**. No hay socios ni gastos precargados.

## 2. Crear los dos socios

1. Entra a `Socios y capital → Registrar socios`.
2. Registra a José, participación 50%; vincula `admin@granja.local`.
3. Registra el segundo socio, 50%; vincula `socio@granja.local`.
4. Revisa `Socios y capital → Desembolsos por socio`. Ambos empiezan en S/0.
5. Si cada socio deposita dinero en caja, regístralo en `Aportes → Aporte a caja`. No vuelvas a registrar esa misma suma como compra. Si un socio paga directamente con dinero personal, queda identificado en la compra, no como depósito a la caja.

## 3. Materiales para corrales

1. Entra a `Gastos y compras`.
2. Registra `Ladrillos`, S/1500, destino `Construcción de corrales`, proyecto `Corrales`, fondos `Pago personal de socio`, pagado por José. Adjunta boleta si la tienes.
3. Registra `Cemento`, S/400, misma clasificación, pagado por José.
4. Consulta `Socios y capital → Desembolsos por socio`: José S/1900; el segundo socio S/0; diferencia S/1900.
5. Cuando el otro socio pague, regístralo con **su identidad** en el campo Pagado por (aunque José sea quien capture el movimiento). El dashboard y la auditoría mostrarán quién **registró** y quién **pagó**.
6. `Administración → Vida útil de corrales`: define los meses de uso para el costo referencial por animal (36 iniciales).

## 4. Comprar chanchos

1. `Inventario → Lotes / compras` → Nuevo lote. Nombre del proveedor, cantidad, monto total y quién pagó.
2. Se crea un lote más una ficha **por animal**. El importe de compra se registra **una vez** como desembolso y se reparte entre los animales. No vuelvas a registrarlo en Gastos.
3. `Inventario → Chanchos` → toca un animal → Editar ficha: coloca su arete, sexo, fecha de nacimiento si se conoce y peso.
4. Si un arete ya fue utilizado, aparece un error de validación. Un arete histórico no se reutiliza automáticamente.
5. Usa filtros por sexo, etapa, edad (meses), peso, código o arete.

## 5. Comprar alimento, consumirlo y agotarlo

1. `Gastos y compras` → categoría `Alimento`, `Maíz`, S/80, `50 kg`, pagado por un socio o caja.
2. Al guardar, la partida de maíz **entra al inventario**, pero **no se imputa aún al costo de los chanchos**.
3. La app pregunta **«¿Quieres actualizar algún alimento anterior como agotado?»**. Al pulsar Sí muestra compras anteriores con stock. Elige la anterior y el lote que la consumió, y confirma. Al pulsar No, finaliza sin cambiar el anterior.
4. Alternativamente en `Inventario → Almacén` registra consumo parcial, indicando kilos y lote. El costo se asigna automáticamente por partes iguales a los animales activos de ese lote.
5. Cuando el stock llega a 0, la partida queda **Agotada** y muestra los días desde su primer uso. Los cambios aparecen en auditoría y notificaciones.
6. Si fue merma o desperdicio, márcalo expresamente para no atribuirlo como alimento ingerido por los animales.

## 6. Partos, edades, fallecimientos

1. Marca una hembra activa. `Inventario → Partos y crías` → selecciona la madre, padre opcional, nacidas y vivas.
2. Se crean fichas con origen **NACIDO**. Ese origen no cambia; solo cambia la etapa (lactante, destetado, crecimiento, engorde, reproductor).
3. En la ficha puedes editar sexo, arete, peso y fecha de nacimiento. Las crías muestran la edad en días y las mayores en meses.
4. Desde el detalle, abre **Registrar fallecimiento**, completa motivo y observaciones; la fecha se precarga con la actual. La ficha cambia a estado fallecido sin borrarse.

## 7. Venta directa desde chanchos

1. Filtra animales activos por etapa, sexo, edad o peso.
2. Toca uno → `Vender animal` → indica importe, peso final y comprador.
3. La app cambia su estado a vendido, registra el ingreso y fija el uso referencial de corrales calculado al vender.
4. `Inicio → Balance`: ingresos menos costo de compra, alimento consumido, sanidad y uso estimado de corrales por animal vendido. En `Reportes` puedes consultar la venta y exportar PDF.

## 8. Auditoría, permisos y documentos

- La campana muestra eventos como «Administrador operativo · VENTA: CH-008: S/ ...». Tocar una venta o gasto abre su detalle.
- El administrador principal puede gestionar usuarios, permisos por módulo (ver/crear/editar/anular), bloqueo de cuentas, flags de login y auditoría.
- El visualizador no puede modificar datos por los controles de interfaz y repositorio, y sus módulos son de consulta.
- `Reportes` → selecciona el tipo → **Descargar PDF**. Android permite elegir dónde guardarlo.

## Cómo interpretar números (evita dobles conteos)

**Pagado por José ≠ Registrado por José.** La fuente del dinero y el autor de captura son campos independientes.

**Aporte de capital ≠ desembolso.** Depositar S/3000 en caja no aumenta costos ni utilidad; la compra concreta se clasifica según destino.

**Comprar maíz ≠ consumir maíz.** Compra reduce caja o registra pago personal e incrementa stock. El consumo asigna su costo a animales; jamás se duplica.

**Invertir en corrales ≠ pérdida inmediata.** Se muestra como infraestructura y se prorratea referencialmente durante su uso. El prorrateo por fecha/ocupación es una estimación simple, no cálculo fiscal.

**Margen de ventas ≠ recuperación completa del capital.** La app reporta margen de animales vendidos y, por separado, un indicador simple de desembolsos menos ventas, además de retiros efectivamente realizados.
