# Auditoría profunda de Limitador para Redmi Pad 2

Fecha: 2026-10-28  
Repositorio auditado: https://github.com/Trotabares1978/Limitador  
Dispositivo objetivo: Xiaomi Redmi Pad 2 Wi-Fi, 8 GB RAM / 256 GB, pantalla 11".

## Resumen ejecutivo

El proyecto actual compila, pero todavía es un prototipo de interfaz. No descubre clientes conectados, no modifica el hotspot, no expulsa dispositivos ni limita velocidades. El próximo objetivo no debería ser añadir el temporizador real directamente, sino crear una capa de diagnóstico de solo lectura para la tablet y verificar las capacidades que HyperOS expone a través de Shizuku.

Hay una vía experimental plausible para controlar tráfico en Android 16 mediante un TUN de prueba para el upstream del hotspot. Shizzi demuestra que se puede encaminar el tráfico de clientes del hotspot a través de un datapath propio usando Shizuku en Android 13+, pero no implementa el límite de velocidad por cliente que necesita Limitador. Tampoco demuestra compatibilidad con la Redmi Pad 2. No debe incorporarse como si fuera una solución validada.

## Identificación del dispositivo y del sistema

La Redmi Pad 2 es distinta de la Redmi Pad original y de la Redmi Pad SE. El nombre en clave publicado por la comunidad de firmware es `taiko`; la familia de números de modelo incluye `25040RP0AL` para América Latina. La ficha comercial que se compartió indica “HyperOS” y versión “15”, pero una ficha de tienda no demuestra cuál firmware está instalado hoy en la unidad concreta.

Al 28 de octubre de 2026, rastreadores de firmware publican variantes de Redmi Pad 2 con HyperOS 3 y Android 16. Las versiones disponibles se diferencian por región; no debe suponerse que la ROM de la tablet sea la global, europea o china. Limitador deberá mostrar los valores reales de `Build.DISPLAY`, `Build.VERSION.SDK_INT`, `Build.VERSION.INCREMENTAL` y, con consentimiento, el fingerprint/region build útil para diagnosticar. No se debe cambiar ROM ni recomendar flasheo para este proyecto.

Fuentes:
- Xiaomi Latinoamérica, producto Redmi Pad 2: https://www.mi.com/latin-es/product/redmi-pad-2/
- Seguimiento de firmware Redmi Pad 2 / taiko: https://miuirom.org/es/tablets/redmi-pad-2
- Modelo/codename/números de modelo y estado de kernel publicado por la comunidad: https://github.com/MiCode/Xiaomi_Kernel_OpenSource/issues/41064

## Objetivo A: temporizador individual de 10 minutos

### Lo que ofrece Android

AOSP define `SoftApConfiguration` con listas de clientes bloqueados/permitidos. Su uso depende del soporte de `SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT`, comunicado por `SoftApCapability`; el framework advierte que las listas no funcionan correctamente sin esa capacidad del dispositivo/HAL. La existencia de una opción de “bloquear dispositivo” en la interfaz de Xiaomi no demuestra que una aplicación pueda desconectarlo de inmediato mediante una API interna ni que la actualización de la configuración no interrumpa el hotspot.

Fuente:
- https://source.android.com/docs/core/connect/wifi-softap
- https://android.googlesource.com/platform/packages/modules/Wifi/+/refs/heads/main/framework/java/android/net/wifi/SoftApConfiguration.java
- https://android.googlesource.com/platform/packages/modules/Wifi/+/refs/heads/main/service/java/com/android/server/wifi/SoftApManager.java

### Riesgo detectado en implementaciones existentes

Delta es una referencia útil de gestión de hotspot mediante Shizuku y APIs ocultas, pero su ruta actual de actualización de configuración detiene el Soft AP y vuelve a iniciarlo. Copiar esa secuencia violaría el requisito de no desconectar a los demás clientes. Además, Delta registra un problema en un Redmi Note 8 Pro con Android 11 donde faltaba el método interno `IWifiManager.registerSoftApCallback`; no es el mismo modelo y no prueba que la Redmi Pad 2 falle, pero sí confirma que los contratos ocultos de Wi-Fi no se pueden asumir entre fabricantes/versiones.

Fuentes:
- Código de Delta: https://github.com/supershadoe/delta/blob/main/data/src/main/kotlin/dev/shadoe/delta/data/softap/SoftApController.kt
- Problema de compatibilidad Xiaomi en Delta: https://github.com/supershadoe/delta/issues/119
- Nota de arquitectura/variaciones entre fabricantes: https://github.com/supershadoe/delta/issues/123

### Comportamiento requerido

La secuencia de una sesión debe ser:

1. Identificar un cliente conectado por su MAC observada en la sesión y mantener su IP como dato auxiliar, no como identidad única.
2. Iniciar un cronómetro de 10 minutos al comenzar una sesión real; no crear sesiones a partir de dispositivos inventados ni de un simple botón de demostración.
3. Al vencer, solicitar una desconexión dirigida a ese cliente mediante una operación de sistema que no reinicie el Soft AP.
4. Verificar desde la lista de clientes que ese dispositivo salió y, desde otro cliente de prueba, verificar que la red SISTA siguió funcionando.
5. Retirar inmediatamente cualquier bloqueo temporal que se hubiera aplicado, solo después de verificar que retirarlo no reconecta automáticamente al cliente ni genera efectos colaterales. El estado final debe permitir que el cliente se reconecte y empiece otra sesión de 10 minutos.
6. Si la capacidad o la operación no están disponibles, mostrar “no compatible / no aplicado” y no simular éxito.

Las MAC aleatorias de los clientes y los cambios de IP deben tratarse explícitamente. No usar únicamente la IP para identificar dispositivos.

## Objetivo B: límite de velocidad por cliente

Las API ordinarias de hotspot no presentan un control general de tasa por dispositivo. Shizuku da un proceso con privilegios ADB/shell, pero no equivale a root ni proporciona automáticamente `NET_ADMIN`; el proyecto Shizzi documenta que operaciones como `ip rule`, `ip route` e `iptables` pueden fallar por permisos del kernel/SELinux aunque Shizuku esté activo.

Shizzi presenta una alternativa técnica diferente: crea un TUN, lo registra como test network y hace que el tethering use ese upstream, procesando el tráfico con un datapath propio. Su documentación indica compatibilidad funcional en Android 13+ y un dispositivo Android 16 conocido; también advierte que las interfaces ocultas del fabricante pueden faltar. Esto hace razonable estudiar el enfoque para HyperOS 3 / Android 16, pero **no** confirma compatibilidad con taiko ni incluye ya un limitador por cliente. Habría que agregar clasificación de tráfico IPv4 e IPv6 por cliente, colas/token bucket o un scheduler equivalente, medición y pruebas de fugas de tráfico. Un error en esta capa podría dejar sin Internet a todos los clientes o cambiar el camino de salida del tráfico, por lo que requiere modo de diagnóstico y una reversión segura.

Fuentes:
- Shizzi, arquitectura: https://github.com/carlelieser/shizzi
- Shizzi, compatibilidad de Android y límites del UID shell: https://github.com/carlelieser/shizzi/blob/main/docs/android-compatibility.md
- Shizuku, límites de los privilegios ADB y APIs ocultas: https://github.com/RikkaApps/Shizuku

Alternativas según prioridad:
- La más fiable para QoS por cliente es un router que implemente límites por dispositivo.
- Para que Limitador funcione solo sobre la Redmi Pad 2, primero se puede investigar Shizzi como referencia de datapath, aislando su prueba de compatibilidad del código de expulsión.
- No prometer control real de velocidad hasta que una prueba de carga en la Redmi Pad 2 confirme tasas aplicadas por separado a dos clientes.

## Auditoría del código actual

Revisión del árbol `main`:

- `MainActivity.kt` muestra estado de Shizuku y sesiones de demostración locales.
- `DemoSession` contiene solo un identificador, una etiqueta ficticia y una hora local.
- El botón crea una sesión de prueba y el contador calcula cuánto tiempo queda, pero al vencer solo muestra “Tiempo agotado (sin expulsión real)”. No existe una ruta de desconexión de red.
- La lista se mantiene en `remember { mutableStateListOf(...) }`: no persiste ante la muerte del proceso/reinicio.
- No hay servicio de primer plano, notificación de sesión, persistencia, recuperación tras reinicio, lectura de MAC/IP, descubrimiento de clientes ni listener de Soft AP.
- La dependencia Shizuku está instalada, pero no existe aún un UserService/puente privilegiado que invoque las APIs necesarias.
- El manifiesto no declara servicios de ejecución ni permisos para ellos porque todavía no existen esas funciones. No añadirlos antes de elegir y validar la arquitectura.
- No hay control de velocidad ni pruebas de integración/dispositivo.
- La compilación correcta es necesaria, pero solo prueba que la app se puede empaquetar; no valida ninguna operación de hotspot.

## Arquitectura recomendada, por fases

### Fase 1 — Diagnóstico de solo lectura
Crear una pantalla de diagnóstico que muestre:
- versión real de Android/HyperOS, SDK y fingerprint relevante;
- estado/UID de Shizuku y permiso concedido;
- si existen las interfaces ocultas requeridas y si se pueden consultar;
- estado del Soft AP y lista real de clientes, cuando el fabricante lo permita;
- capacidades reportadas del Soft AP, en particular `CLIENT_FORCE_DISCONNECT`;
- errores exactos clasificados como API ausente, permiso denegado, servicio inaccesible o capacidad no soportada.

Esta fase no debe cambiar configuración ni reiniciar SISTA.

### Fase 2 — Prueba manual y reversible
Con un cliente de prueba, intentar una sola desconexión manual dirigida. Otro cliente debe mantener una conexión activa de prueba. Verificar también la reconexión del primer cliente y que no quede bloqueado. Registrar antes/después el estado del hotspot y las listas de clientes. No activar temporizadores todavía.

### Fase 3 — Temporizadores persistentes
Solo tras superar la fase 2: servicio de sesión persistente, almacenamiento local, notificación visible, hora de vencimiento persistida y recuperación al reabrir la app. Las acciones del servicio deberán ser idempotentes y nunca detener el hotspot completo. Shizuku iniciado por depuración inalámbrica no sobrevive necesariamente al reinicio: Limitador debe indicar claramente cuando el privilegio dejó de estar disponible.

### Fase 4 — QoS experimental
Estudiar el datapath TUN de Shizzi en una rama aislada; primero verificar tethering normal, IPv4, IPv6, DNS, VPN y recuperación al detener la app. Solo después investigar el limitador por cliente. Dejar la función detrás de una comprobación explícita de compatibilidad.

## Verificación CI del repositorio

La ejecución de GitHub Actions `40753169413` terminó con `success`. Compiló `assembleDebug` y subió el artefacto `Limitador-debug-apk` (archivo ZIP de artefacto, no una release firmada de distribución). SHA-256 del ZIP informado por GitHub: `5827bcf465186a122c51d5984cf2034da4f6cce90ee3dc994d57279ab13f1ba9`.

- Ejecución y artefacto: https://github.com/Trotabares1978/Limitador/actions/runs/40753169413

## Dictamen

- **La compilación ya funciona.**
- **La desconexión individual en Redmi Pad 2 sigue sin verificar.**
- **La limitación de velocidad sin root es experimental y no está implementada.**
- El siguiente cambio correcto en el código es el diagnóstico de solo lectura para `taiko`; no una automatización que modifique la lista de bloqueo sin saber si HyperOS reinicia el punto de acceso.
