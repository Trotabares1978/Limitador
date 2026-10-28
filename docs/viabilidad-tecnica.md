# Informe técnico de viabilidad — Limitador

Fecha de revisión: 2026-10-28

## Objetivos confirmados
- Hotspot existente SISTA en Xiaomi/HyperOS.
- Sin root; Shizuku iniciado por depuración inalámbrica.
- Temporizador individual de 10 minutos por cliente.
- Al vencer, desconectar solo ese cliente y permitir que vuelva a conectarse para otra sesión.
- No apagar el hotspot completo.
- Investigar límite de velocidad por cliente.

## Qué muestra la investigación
1. Android AOSP tiene listas de clientes bloqueados/permitidos en `SoftApConfiguration`, pero son APIs del sistema y dependen de soporte de hardware/HAL (`SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT`). No se puede asumir que HyperOS exponga esa capacidad a una app.
2. Delta obtiene privilegios ADB mediante Shizuku y usa APIs ocultas de Android (`IWifiManager`, `SoftApConfiguration`, `ITetheringConnector`) para leer y cambiar la configuración del hotspot.
3. En el código público revisado de Delta, `SoftApBlocklistManager` agrega/quita direcciones de la lista bloqueada. Sin embargo, `SoftApController.updateSoftApConfiguration()` cuando el hotspot está encendido hace explícitamente `stopSoftAp()`, espera a que se apague, y luego ejecuta `startSoftAp()`. Por lo tanto, copiar sin cambios ese camino puede interrumpir a todos los clientes; no cumple el requisito de expulsión individual sin afectar al resto.
4. La documentación de Android confirma que el control de clientes y la desconexión forzada dependen de las capacidades del dispositivo. No encontramos una API pública universal para expulsar arbitrariamente un cliente del hotspot sin modificar la configuración.
5. La API de configuración de hotspot no ofrece un control general de tasa por dispositivo. Shizuku no otorga automáticamente capacidades de administrador de red/kernel como `NET_ADMIN`; no debe asumirse que `iptables`/`tc` puedan aplicarse desde una app sin root.
6. El contador local de la primera pantalla es solo un prototipo y no detecta clientes reales ni realiza expulsiones.

## Conclusión
La expulsión individual sin bloqueo persistente sigue siendo una hipótesis que hay que probar en el equipo real. Antes de desarrollar un temporizador que actúe automáticamente, hay que comprobar en HyperOS:
- si se puede leer la lista real de clientes;
- si la capacidad `CLIENT_FORCE_DISCONNECT` está disponible;
- si actualizar solo la lista bloqueada desconecta al cliente objetivo;
- si retirar inmediatamente esa MAC de la lista permite reconexión y no deja bloqueo residual;
- si la operación no reinicia el hotspot ni desconecta al resto.

No se debe implementar como función real ni mostrar "desconectado" hasta verificar el resultado en el sistema.

## Plan de prueba seguro
1. Leer y mostrar la versión Android/HyperOS, estado Shizuku y capacidades reportadas.
2. Identificar un dispositivo de prueba y su MAC actual (tener en cuenta MAC aleatoria del cliente).
3. Intentar leer la lista de clientes sin cambiar configuración.
4. Si el dispositivo soporta desconexión selectiva, realizar una prueba manual con un único cliente y verificar desde otro cliente que SISTA no se reinicie.
5. Probar la secuencia de bloqueo temporal y retirada, y verificar reconexión.
6. Solo después activar temporizadores automáticos.

## Límite de velocidad
Para limitar el tráfico por cliente hace falta una capa que pueda medir y aplicar políticas al tráfico reenviado. Las APIs ordinarias del hotspot no lo exponen. Opciones realistas:
- router externo con QoS/limitación por cliente (la solución más fiable);
- una solución de tethering basada en TUN/encaminamiento que procese el tráfico, si se confirma compatibilidad y se acepta su complejidad;
- no prometer limitación por dispositivo con Shizuku solamente hasta demostrarlo en el hardware real.

## Fuentes
- Android Open Source Project, Wi-Fi hotspot (Soft AP): https://source.android.com/docs/core/connect/wifi-softap
- Android SoftApConfiguration: https://developer.android.com/reference/android/net/wifi/SoftApConfiguration
- Delta, README y compatibilidad: https://github.com/supershadoe/delta
- Delta, SoftApBlocklistManager: https://github.com/supershadoe/delta/blob/main/data/src/main/kotlin/dev/shadoe/delta/data/softap/SoftApBlocklistManager.kt
- Delta, SoftApController: https://github.com/supershadoe/delta/blob/main/data/src/main/kotlin/dev/shadoe/delta/data/softap/SoftApController.kt
- Delta FAQ / Shizuku: https://delta.shadoe.dev/faq/
- Shizzi, tethering rootless via TUN: https://github.com/carlelieser/shizzi
