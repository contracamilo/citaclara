# Checklist de calidad de la especificación: Panel de analítica

**Propósito**: validar que la spec está completa y es de calidad antes de pasar a planificar
**Creado**: 2026-11-27
**Feature**: [spec.md](../spec.md)

## Calidad del contenido

- [x] Sin detalles de implementación (lenguajes, frameworks, APIs)
- [x] Centrada en el valor para el usuario y el negocio
- [x] Redactada para perfiles no técnicos
- [x] Todas las secciones obligatorias completadas

## Completitud de requisitos

- [x] No quedan marcadores [NEEDS CLARIFICATION] (FR-008 y FR-009 resueltos por Sara: tasa B, ocupación A)
- [x] Requisitos comprobables y sin ambigüedad
- [x] Criterios de éxito medibles
- [x] Criterios de éxito independientes de la tecnología
- [x] Todos los escenarios de aceptación definidos
- [x] Casos límite identificados
- [x] Alcance claramente acotado (solo lectura; fuera de alcance declarado)
- [x] Dependencias y supuestos identificados

## Preparación de la feature

- [x] Todos los requisitos funcionales tienen criterio de aceptación
- [x] Los escenarios de usuario cubren los flujos principales
- [x] La feature cumple los resultados medibles de los criterios de éxito
- [x] Sin detalles de implementación en la spec

## Constitución

- [x] Principio II: importes al céntimo, formato español, semanas y fechas sin ambigüedad
- [x] Principio III: la feature no escribe en la agenda (FR-003), por lo que no exige pruebas de solape
- [x] Principio V: cifras reproducibles con la semilla, citadas por posición relativa
- [x] Principio IV: sin alcance fantasma (exportaciones, filtros y «ahorro» declarados fuera)

## Notas

- Decisiones de Sara (2026-11-27): tasa de no asistencia sobre todas las citas cerradas; ocupación sobre minutos completados.
- Las cifras de la spec se han calculado ejecutando el generador de la semilla (`GeneradorSemilla`) y cuadran con `datos-demostracion.md` (873 completadas, 106 no asistidas, 85 canceladas, 34.870,00 €).
- Las cifras semanales solo son citables por posición con la semilla cargada un lunes (FR-033 de la spec 001 impide citar por semana natural).
