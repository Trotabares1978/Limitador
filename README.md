# Limitador

Aplicación Android experimental para administrar sesiones de clientes conectados al hotspot SISTA en una tablet Xiaomi/HyperOS, usando Shizuku cuando el sistema lo permita.

## Estado

**Prototipo inicial.** Esta versión crea la estructura Android, una pantalla de estado y una lista de sesiones temporizadas localmente. Todavía no expulsa clientes reales ni aplica límites de velocidad: esas funciones requieren validar qué operaciones admite la versión concreta de HyperOS. La interfaz no simula una expulsión exitosa.

## Objetivos

- Sesión individual de 10 minutos por cliente.
- Al vencer, desconectar únicamente al cliente y permitirle reconectarse para iniciar otra sesión.
- No apagar el hotspot completo.
- Investigar límites de velocidad por dispositivo.
- Sin root; integración opcional con Shizuku.

## Compilar

El workflow de GitHub Actions compila el proyecto con Gradle. Para probar en la tablet, instalar el APK generado por el workflow cuando la compilación esté disponible.
