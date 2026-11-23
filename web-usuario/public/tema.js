// Pone el tema antes del primer pintado, para que la página no parpadee. Es un archivo aparte (no un
// script en línea) porque la CSP solo permite scripts propios. La lógica del interruptor está en
// src/utils/tema.js; src/utils/tema.test.js comprueba que las dos elijan lo mismo.
;(() => {
  let guardado = null
  try {
    guardado = localStorage.getItem('boticompara.tema')
  } catch {
    // Sin almacenamiento disponible se sigue la preferencia del sistema
  }
  const sistemaOscuro = window.matchMedia('(prefers-color-scheme: dark)').matches
  const oscuro = guardado === 'oscuro' || (guardado !== 'claro' && sistemaOscuro)
  const raiz = document.documentElement
  raiz.dataset.tema = oscuro ? 'oscuro' : 'claro'
  raiz.style.colorScheme = oscuro ? 'dark' : 'light'
})()
