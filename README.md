# Joyous para Android

Proyecto Android con compilación automática de APK desde GitHub Actions. El nombre visible de la app es **Joyous**.

## Desde el móvil

1. Crea un repositorio **privado** nuevo en GitHub con rama `main`.
2. Sube los archivos y carpetas de este proyecto a la raíz del repositorio (incluida `.github/workflows/android.yml`). Si usas un ZIP, descomprímelo antes de subir sus archivos: GitHub no extrae automáticamente los ZIP subidos.
3. En GitHub, abre **Actions** → **Compilar Joyous APK** → **Run workflow** (o espera a la ejecución automática tras el push).
4. Cuando termine en verde, abre la ejecución y descarga el artefacto **Joyous-APK**.
5. Extrae el ZIP descargado e instala `app-debug.apk` en Android. Es una compilación de depuración para uso personal.

El APK de debug puede ser bloqueado por el sistema hasta autorizar la instalación desde tu navegador/gestor de archivos. Descarga únicamente el APK de tu propio repositorio.

## Datos existentes

El almacenamiento de Brave no se comparte automáticamente con el de la app. Exporta tus tareas desde Joyous en Brave e impórtalas en la app. No desinstales ni borres datos de Brave hasta comprobar que se han transferido.

## Notas

- Requiere GitHub Actions habilitado y acceso a los repositorios de Google/Maven durante la compilación.
- El proyecto incluye HTML, sonidos y animaciones para funcionar sin conexión una vez instalado.
- No se ha podido compilar ni probar el APK en este entorno; el resultado se verificará en el registro de GitHub Actions.

## Actualización 1.1

Consulta `CAMBIOS_NUEVOS.txt` para las funciones, límites y pruebas en el móvil. Las notificaciones son exclusivas de Android. El HTML del navegador no solicita permisos ni emite avisos.

## Icono y arranque por tema (1.15)

Cambiar el tema desde Ajustes actualiza la variante de lanzador y el tema de arranque nativo. Cada variante utiliza la mascota de Joyous y su paleta. En Android 12 o posterior también se persiste el tema del splash del sistema. El lanzador puede tardar en refrescar el icono y algunos accesos directos antiguos pueden conservar su imagen anterior.

Instala la actualización con el mismo identificador y la misma firma. No desinstales la versión actual para actualizar. Tras instalarla, abre Joyous una vez para sincronizar el tema guardado.

El HTML del navegador sigue siendo la versión web actual; los recursos del icono y el arranque están dentro del proyecto Android. La compilación y la prueba real de las seis variantes deben realizarse en GitHub Actions y Android.

## Datos y opciones 1.17

Exportar copia completa incluye los ocho apartados de almacenamiento de Joyous. Restaurar una copia antigua solo reemplaza los apartados presentes. Es recomendable conservar una copia antes de actualizar.

«Una vez» no tiene fecha límite: cuenta una sola vez y, mientras está pendiente, no rompe las rachas repetidamente. Las repeticiones nuevas se aplican desde hoy; los días anteriores conservan su calendario. Archivar conserva el historial.

Las opciones secundarias están en desplegables: Todas las tareas, Archivadas, Historial de agua, Entrenamiento anterior y Avisos y horarios (solo Android).

Las pruebas locales se ejecutan con `node tests/core.test.cjs` y `node tests/ui.test.cjs`. La compilación de la APK y las pruebas visuales/en dispositivo siguen pendientes.
