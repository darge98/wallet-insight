import { describe, expect, it } from 'vitest';

import { isCompactJwt } from './personal-token';

describe('isCompactJwt', () => {
  it('accetta un JWT compatto', () => {
    expect(isCompactJwt('eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJl')).toBe(true);
  });

  it('tollera spazi incollati insieme al token', () => {
    expect(isCompactJwt('  eyJhbGci.eyJzdWIi.firma \n')).toBe(true);
  });

  it('rifiuta un valore senza i tre segmenti', () => {
    expect(isCompactJwt('eyJhbGci.eyJzdWIi')).toBe(false);
  });

  it('rifiuta caratteri fuori dall’alfabeto base64url', () => {
    expect(isCompactJwt('eyJhbGci.eyJz+dWIi.firma')).toBe(false);
  });

  it('rifiuta la stringa vuota', () => {
    expect(isCompactJwt('   ')).toBe(false);
  });
});
