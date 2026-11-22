// Adaptación de BotiCompara para los componentes de React Bits que pintan en <canvas> o mezclan
// colores en JavaScript: el canvas no entiende var(--x), así que se lee el valor real de :root.
// Así los componentes usan las variables de index.css y no colores fijos.
export function resolverColor(valor) {
  const variable = /^var\((--[\w-]+)\)$/.exec(String(valor ?? '').trim())
  if (!variable || typeof document === 'undefined') return valor
  return getComputedStyle(document.documentElement).getPropertyValue(variable[1]).trim() || valor
}
