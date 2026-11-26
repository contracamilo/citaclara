-- Esquema inicial de CitaClara (feature 001-nucleo-agenda).
-- Fuente: specs/001-nucleo-agenda/data-model.md y research.md (D2, D4, D5, D9, D10, D11).

CREATE EXTENSION IF NOT EXISTS btree_gist;  -- exclusion constraint de RN1 (FR-012, FR-013)
CREATE EXTENSION IF NOT EXISTS unaccent;    -- búsqueda «Lucia» → «Lucía» (FR-018)

-- unaccent() no es IMMUTABLE; este envoltorio permite indexar la búsqueda por nombre.
CREATE FUNCTION inmutable_unaccent(texto text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, texto) $$;

CREATE TABLE clinica (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    identificador  varchar(40)  NOT NULL UNIQUE CHECK (identificador ~ '^[a-z0-9-]+$'),
    nombre         varchar(120) NOT NULL,
    clave_hash     varchar(100) NOT NULL
);

CREATE TABLE horario_atencion (
    clinica_id  bigint   NOT NULL REFERENCES clinica (id) ON DELETE CASCADE,
    dia_semana  smallint NOT NULL CHECK (dia_semana BETWEEN 1 AND 7),
    apertura    time     NOT NULL,
    cierre      time     NOT NULL,
    PRIMARY KEY (clinica_id, dia_semana),
    CHECK (cierre > apertura)
);

CREATE TABLE profesional (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clinica_id    bigint       NOT NULL REFERENCES clinica (id) ON DELETE CASCADE,
    nombre        varchar(120) NOT NULL,
    especialidad  varchar(40)  NOT NULL,
    UNIQUE (id, clinica_id)
);

CREATE TABLE servicio (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clinica_id        bigint       NOT NULL REFERENCES clinica (id) ON DELETE CASCADE,
    nombre            varchar(120) NOT NULL,
    especialidad      varchar(40)  NOT NULL,
    duracion_minutos  integer      NOT NULL CHECK (duracion_minutos > 0 AND duracion_minutos % 5 = 0),
    precio_centimos   integer      NOT NULL CHECK (precio_centimos >= 0),
    UNIQUE (id, clinica_id)
);

CREATE TABLE paciente (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clinica_id  bigint       NOT NULL REFERENCES clinica (id) ON DELETE CASCADE,
    nombre      varchar(120) NOT NULL,
    telefono    varchar(20)  NOT NULL,
    email       varchar(254),
    CONSTRAINT paciente_telefono_unico UNIQUE (clinica_id, telefono),
    UNIQUE (id, clinica_id)
);

CREATE INDEX paciente_nombre_busqueda ON paciente (clinica_id, lower(inmutable_unaccent(nombre)));

CREATE TABLE cita (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clinica_id        bigint       NOT NULL REFERENCES clinica (id) ON DELETE CASCADE,
    profesional_id    bigint       NOT NULL,
    servicio_id       bigint       NOT NULL,
    paciente_id       bigint       NOT NULL,
    inicio            timestamptz  NOT NULL,
    fin               timestamptz  NOT NULL,
    importe_centimos  integer      NOT NULL CHECK (importe_centimos >= 0),
    estado            varchar(12)  NOT NULL
        CHECK (estado IN ('RESERVADA', 'COMPLETADA', 'CANCELADA', 'NO_ASISTIDA')),
    version           integer      NOT NULL DEFAULT 0,
    creada_en         timestamptz  NOT NULL DEFAULT now(),
    CHECK (fin > inicio),
    -- La clínica de la cita coincide con la de su profesional, servicio y paciente (FR-003).
    FOREIGN KEY (profesional_id, clinica_id) REFERENCES profesional (id, clinica_id) ON DELETE CASCADE,
    FOREIGN KEY (servicio_id, clinica_id)    REFERENCES servicio (id, clinica_id)    ON DELETE CASCADE,
    FOREIGN KEY (paciente_id, clinica_id)    REFERENCES paciente (id, clinica_id)    ON DELETE CASCADE,
    -- RN1 (FR-012, FR-013, FR-024; research D2): ninguna escritura, por ningún camino ni con
    -- ninguna concurrencia, puede dejar dos citas ocupantes solapadas del mismo profesional.
    -- Rango semiabierto: las citas contiguas no se solapan.
    CONSTRAINT cita_sin_solape EXCLUDE USING gist (
        profesional_id WITH =,
        tstzrange(inicio, fin, '[)') WITH &&
    ) WHERE (estado IN ('RESERVADA', 'COMPLETADA'))
);

CREATE INDEX cita_profesional_inicio ON cita (profesional_id, inicio);
