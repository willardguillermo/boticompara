// Pruebas del tema claro u oscuro: npm test
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import vm from 'node:vm'
import { resolverTema } from './tema.js'

const scriptInicial = readFileSync(new URL('../../public/tema.js', import.meta.url), 'utf8')

/** Ejecuta public/tema.js con un navegador de mentira y devuelve cómo queda <html>. */
function alCargar(getItem, sistemaOscuro) {
  const documentElement = { dataset: {}, style: {} }
  vm.runInNewContext(scriptInicial, {
    document: { documentElement },
    localStorage: { getItem },
    window: { matchMedia: () => ({ matches: sistemaOscuro }) },
  })
  return documentElement
}

test('resolverTema: la elección manda y sin elección sigue al sistema', () => {
  assert.equal(resolverTema('oscuro', false), 'oscuro')
  assert.equal(resolverTema('claro', true), 'claro')
  assert.equal(resolverTema(null, true), 'oscuro')
  assert.equal(resolverTema(null, false), 'claro')
  assert.equal(resolverTema('otro', true), 'oscuro')
})

test('public/tema.js elige lo mismo que resolverTema', () => {
  for (const guardado of ['oscuro', 'claro', null, 'otro']) {
    for (const sistemaOscuro of [true, false]) {
      const { dataset, style } = alCargar(() => guardado, sistemaOscuro)
      const esperado = resolverTema(guardado, sistemaOscuro)
      assert.equal(dataset.tema, esperado)
      assert.equal(style.colorScheme, esperado === 'oscuro' ? 'dark' : 'light')
    }
  }
})

test('public/tema.js sigue al sistema si localStorage falla', () => {
  const falla = () => {
    throw new Error('bloqueado')
  }
  assert.equal(alCargar(falla, true).dataset.tema, 'oscuro')
  assert.equal(alCargar(falla, false).dataset.tema, 'claro')
})
