import { describe, expect, it } from 'vitest';
import { formatearSemana } from '../../src/formato/fechas';
import { formatearDecimas, textoCitas } from '../../src/formato/porcentaje';
import { identificadorDeRuta, vistaDeRuta } from '../../src/navegacion';
import { normalizarEspacios } from './datos';

describe('formato de la analítica', () => {
  it('[004][FR-010] formatearDecimas(93) da «9,3 %» y respeta los extremos', () => {
    expect(normalizarEspacios(formatearDecimas(93))).toBe('9,3 %');
    expect(normalizarEspacios(formatearDecimas(1000))).toBe('100,0 %');
    expect(normalizarEspacios(formatearDecimas(0))).toBe('0,0 %');
    expect(normalizarEspacios(formatearDecimas(545))).toBe('54,5 %');
    expect(normalizarEspacios(formatearDecimas(143))).toBe('14,3 %');
  });

  it('[004][FR-011] sin base de cálculo se muestra «Sin datos», nunca 0 %', () => {
    expect(formatearDecimas(null)).toBe('Sin datos');
    expect(formatearDecimas(null)).not.toContain('0');
  });

  it('[004][FR-014] la semana se etiqueta con fecha de inicio y fin dd/mm/aaaa', () => {
    expect(formatearSemana({ inicio: '2026-11-23', fin: '2026-11-29' })).toBe('23/11/2026 – 29/11/2026');
  });

  it('[004][FR-005] «1 cita» y «39 citas» en singular y plural', () => {
    expect(textoCitas(1)).toBe('1 cita');
    expect(textoCitas(39)).toBe('39 citas');
  });

  it('[004][FR-001] vistaDeRuta distingue la analítica de la agenda y el identificador es el primer segmento', () => {
    expect(vistaDeRuta('/eleva/analitica')).toBe('analitica');
    expect(vistaDeRuta('/eleva')).toBe('agenda');
    expect(vistaDeRuta('/eleva/otra')).toBe('agenda');
    expect(identificadorDeRuta('/eleva/analitica')).toBe('eleva');
  });
});
