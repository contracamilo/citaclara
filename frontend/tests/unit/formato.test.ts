import { describe, expect, it } from 'vitest';
import { formatearEuros } from '../../src/formato/euros';
import {
  diaSemanaIso,
  esFechaValida,
  formatearFecha,
  formatearFechaLarga,
  formatearFranja,
  formatearHora,
  horasEnPasos,
  sumarDias,
  sumarMinutos,
} from '../../src/formato/fechas';
import { normalizarEspacios } from './datos';

// Intl separa la cifra y «€» con un espacio no separable (U+00A0). El formateador lo conserva
// (evita que el importe se parta) y aquí se normaliza antes de comparar con el texto de la spec.
const euros = (c: number) => normalizarEspacios(formatearEuros(c));

describe('formateador de euros', () => {
  it('[001][FR-017] formatea 4000 céntimos como «40,00 €»', () => {
    expect(euros(4000)).toBe('40,00 €');
  });

  it('[001][FR-017] formatea 5 céntimos como «0,05 €»', () => {
    expect(euros(5)).toBe('0,05 €');
  });

  it('[001][FR-017] formatea 1 000 000 céntimos como «10.000,00 €»', () => {
    expect(euros(1000000)).toBe('10.000,00 €');
  });

  it('[001][FR-017] no agrupa los miles con 4 cifras («1000,00 €»), según CLDR/RAE (research D5)', () => {
    expect(euros(100000)).toBe('1000,00 €');
  });

  it('[001][FR-017] separa cifra y símbolo con un espacio no separable para que no se parta', () => {
    expect(formatearEuros(4000)).toBe('40,00 €');
  });
});

describe('formateador de fechas y horas', () => {
  it('[001][FR-026] formatea «2026-11-26» como «26/11/2026»', () => {
    expect(formatearFecha('2026-11-26')).toBe('26/11/2026');
  });

  it('[001][FR-026] formatea la fecha larga como «jueves, 26/11/2026»', () => {
    expect(formatearFechaLarga('2026-11-26')).toBe('jueves, 26/11/2026');
    expect(formatearFechaLarga('2026-11-29')).toBe('domingo, 29/11/2026');
  });

  it('[001][FR-026] muestra las horas «HH:mm» de 24 h tal cual llegan del API', () => {
    expect(formatearHora('09:05')).toBe('09:05');
    expect(formatearHora('19:30')).toBe('19:30');
    expect(formatearFranja('10:00', '10:45')).toBe('10:00 – 10:45');
  });

  it('[001][FR-026] suma días de calendario sin depender de la zona del dispositivo, también en los cambios de hora', () => {
    expect(sumarDias('2026-11-26', 1)).toBe('2026-11-27');
    expect(sumarDias('2026-11-30', 1)).toBe('2026-12-01');
    expect(sumarDias('2026-10-25', 1)).toBe('2026-10-26');
    expect(sumarDias('2026-10-25', -1)).toBe('2026-10-24');
    expect(sumarDias('2027-03-28', 1)).toBe('2027-03-29');
    expect(sumarDias('2027-01-01', -1)).toBe('2026-12-31');
    expect(diaSemanaIso('2026-11-26')).toBe(4);
    expect(diaSemanaIso('2026-11-29')).toBe(7);
  });

  it('[001][FR-026] valida fechas de calendario «AAAA-MM-DD»', () => {
    expect(esFechaValida('2026-11-26')).toBe(true);
    expect(esFechaValida('2026-02-30')).toBe(false);
    expect(esFechaValida('26/11/2026')).toBe(false);
    expect(esFechaValida('')).toBe(false);
  });

  it('[001][FR-009] calcula horas en pasos de 5 min y la hora de fin orientativa', () => {
    expect(horasEnPasos('09:00', '09:20')).toEqual(['09:00', '09:05', '09:10', '09:15']);
    expect(sumarMinutos('10:00', 45)).toBe('10:45');
    expect(sumarMinutos('19:30', 45)).toBe('20:15');
  });
});
