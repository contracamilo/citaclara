# Contrato: formato del informe de auditoría

**Spec**: [../spec.md](../spec.md) (FR-001, FR-002, FR-013, FR-018) | Lo valida `InformeAuditoriaTest`.

El informe es `specs/005-security-audit/informe-auditoria.md`. Si no respeta este formato, el test falla y el merge se bloquea. Entidades y reglas: [../data-model.md](../data-model.md).

## 1. Cabecera de reproducibilidad (obligatoria)

Una lista con estas claves exactas, cada una con valor:

```markdown
- **Commit auditado**: <hash de 7 a 40 caracteres hexadecimales>
- **Fecha**: DD/MM/AAAA
- **Edición OWASP**: OWASP Top 10:2025
- **Alcance**: <texto>
- **Método**: <texto>
- **Herramientas**: <texto con versiones>
```

## 2. Veredictos por categoría (obligatorios)

Una tabla con **exactamente diez filas**, una por A01–A10 (sin omitir ninguna), con las columnas `Categoría | Veredicto | Hallazgos | Evidencia`.

- `Veredicto` ∈ {`cumple`, `cumple con reservas`, `no cumple`, `no aplica`}.
- `no aplica` exige una justificación en la columna `Evidencia`.
- `Hallazgos` lista los `H-nn` asociados o `—` si el veredicto es `cumple`.

## 3. Ficha de hallazgo (una por hallazgo)

Encabezado `### H-nn · <título>` seguido de una lista con estas claves exactas:

```markdown
- **Categoría**: A0n
- **Severidad**: crítica | alta | media | baja
- **Datos afectados**: pacientes, pagos, sesión, agenda, configuración (los que apliquen)
- **Parte**: servidor | interfaz | entorno (una o varias)
- **Verificación**: <pasos reproducibles o referencia a `[005][FR-xxx]…` del test>
- **Mejora**: <acción propuesta>
- **Esfuerzo**: S | M | L
- **Criterio de aceptación**: <verificable>
- **Estado**: abierto | corregido | derivado
- **Toca**: <spec ajena o «ninguna»>
```

Reglas que comprueba el test:

1. `H-nn` únicos y sin huecos de numeración.
2. `Mejora` y `Criterio de aceptación` no vacíos si la severidad es crítica o alta.
3. `Estado: derivado` exige una referencia a la solicitud registrada en `specs/MAPA.md`.
4. `Estado: corregido` exige el identificador del test (`[005]…`) que lo comprueba.
5. Ninguna ficha contiene datos personales reales, claves vigentes ni secretos (las credenciales de demostración se mencionan solo como «clave de demostración»).

## 4. Secciones fijas finales

- **Inventario de datos personales** (tabla según data-model).
- **Resultado de las herramientas automáticas** (dependencias, secretos).
- **Requisitos para otras features** (002, 003, 004 y la futura de pagos).
- **Reauditoría** (abiertos/cerrados frente al informe anterior; vacía en el primero).
- **Plan de seguimiento** (hallazgos medios y bajos, y specs propuestas).
