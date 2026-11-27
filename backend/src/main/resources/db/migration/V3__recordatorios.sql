-- Recordatorios de cita por email (feature 003-recordatorios-cita).
-- Fuente: specs/003-recordatorios-cita/data-model.md y research.md (D3, D5, D10).

-- FR-009: teléfono de la clínica para el correo y la pantalla fuera de plazo.
-- Valor técnico por defecto para filas existentes; la semilla de Clínica Eleva lo rellena.
ALTER TABLE clinica ADD COLUMN telefono varchar(20) NOT NULL DEFAULT '+34 900 000 000';
ALTER TABLE clinica ALTER COLUMN telefono DROP DEFAULT;

-- FR-016: quién canceló. Solo la cancelación desde el enlace del correo lo rellena.
ALTER TABLE cita ADD COLUMN cancelada_por varchar(10)
    CHECK (cancelada_por IS NULL OR (cancelada_por = 'PACIENTE' AND estado = 'CANCELADA'));

-- Clave para la FK compuesta de recordatorio (aislamiento por clínica, FR-008).
ALTER TABLE cita ADD CONSTRAINT cita_id_clinica_unico UNIQUE (id, clinica_id);

CREATE TABLE recordatorio (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clinica_id     bigint       NOT NULL REFERENCES clinica (id) ON DELETE CASCADE,
    -- FR-004: como mucho un recordatorio por cita, también ante ejecuciones simultáneas.
    cita_id        bigint       NOT NULL UNIQUE,
    email_destino  varchar(254) NOT NULL,
    -- SHA-256 hexadecimal del enlace de cancelación: el enlace no se puede recuperar de la base de datos.
    enlace_huella  varchar(64)  NOT NULL UNIQUE CHECK (length(enlace_huella) = 64),
    generado_en    timestamptz  NOT NULL,
    FOREIGN KEY (cita_id, clinica_id) REFERENCES cita (id, clinica_id) ON DELETE CASCADE
);
