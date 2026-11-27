-- Portal del paciente (feature 002-portal-paciente).
-- Fuente: specs/002-portal-paciente/data-model.md y research.md (R1, R3, R5, R7, R8).
-- Solo añade: no cambia ninguna columna de 001.

-- Búsqueda de fichas por email sin distinguir mayúsculas ni espacios (FR-002, R5).
CREATE INDEX paciente_email_portal ON paciente (clinica_id, lower(btrim(email))) WHERE email IS NOT NULL;

-- Permite la FK compuesta desde cancelacion_portal: la cancelación es de la misma clínica que la cita (FR-028).
ALTER TABLE cita ADD CONSTRAINT cita_id_clinica UNIQUE (id, clinica_id);

-- Una fila por cada solicitud de enlace, exista o no el email (R3): así el límite de FR-004 no
-- revela qué emails existen (FR-003). token_hash es el SHA-256 del secreto del enlace (R1);
-- nulo si el email no coincide con ninguna ficha.
CREATE TABLE solicitud_enlace_portal (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clinica_id         bigint       NOT NULL REFERENCES clinica (id) ON DELETE CASCADE,
    email_normalizado  varchar(254) NOT NULL,
    token_hash         char(64) UNIQUE,
    solicitada_en      timestamptz  NOT NULL,
    caduca_en          timestamptz  NOT NULL,   -- solicitada_en + 10 min (FR-006)
    usada_en           timestamptz,             -- un solo uso (FR-006)
    CHECK (caduca_en > solicitada_en),
    CHECK (usada_en IS NULL OR token_hash IS NOT NULL)
);

CREATE INDEX solicitud_enlace_portal_limite ON solicitud_enlace_portal (clinica_id, email_normalizado, solicitada_en);

-- Cancelaciones hechas por el paciente desde el portal (FR-017) y su revisión en recepción (FR-019–FR-021).
-- La PK garantiza un solo aviso por cita (SC-005).
CREATE TABLE cancelacion_portal (
    cita_id       bigint      PRIMARY KEY,
    clinica_id    bigint      NOT NULL,
    cancelada_en  timestamptz NOT NULL,
    revisada_en   timestamptz,
    FOREIGN KEY (cita_id, clinica_id) REFERENCES cita (id, clinica_id) ON DELETE CASCADE,
    CHECK (revisada_en IS NULL OR revisada_en >= cancelada_en)
);

CREATE INDEX cancelacion_portal_pendientes ON cancelacion_portal (clinica_id, cancelada_en DESC) WHERE revisada_en IS NULL;
