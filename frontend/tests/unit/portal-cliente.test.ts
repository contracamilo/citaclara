import { describe, expect, it, vi } from 'vitest';
import { apiPortal, ErrorApi, EVENTO_SESION_PERDIDA, EVENTO_SESION_PORTAL_PERDIDA } from '../../src/api/cliente';
import { esRutaPortal } from '../../src/App';
import { problema, simularFetch } from './datos';

describe('cliente del portal', () => {
  it('[002][FR-029] un 401 del portal emite su propio evento y nunca el de la sesión del panel', async () => {
    simularFetch({
      'GET /api/portal/citas': problema(401, {
        codigo: 'SESION_PORTAL_CADUCADA',
        mensaje: 'Tu acceso ha caducado. Pide un enlace nuevo para ver tus citas.',
      }),
    });
    const panel = vi.fn();
    const portal = vi.fn();
    window.addEventListener(EVENTO_SESION_PERDIDA, panel);
    window.addEventListener(EVENTO_SESION_PORTAL_PERDIDA, portal);

    await expect(apiPortal.obtenerMisCitas()).rejects.toBeInstanceOf(ErrorApi);

    expect(panel).not.toHaveBeenCalled();
    expect(portal).toHaveBeenCalledTimes(1);
    window.removeEventListener(EVENTO_SESION_PERDIDA, panel);
    window.removeEventListener(EVENTO_SESION_PORTAL_PERDIDA, portal);
  });

  it('[002][FR-001] /{clínica}/mis-citas es el portal y /{clínica} sigue siendo el panel', () => {
    expect(esRutaPortal('/eleva/mis-citas')).toBe(true);
    expect(esRutaPortal('/eleva')).toBe(false);
    expect(esRutaPortal('/eleva/')).toBe(false);
  });
});
