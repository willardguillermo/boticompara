// Íconos SVG en línea (trazo de 24x24) para no depender de librerías externas
const TRAZOS = {
  cruz: <path d="M9 3h6v6h6v6h-6v6H9v-6H3V9h6z" />,
  salir: <><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" /><path d="M10 17l5-5-5-5" /><path d="M15 12H3" /></>,
  buscar: <><circle cx="11" cy="11" r="7" /><path d="M21 21l-4.3-4.3" /></>,
  mas: <path d="M12 5v14M5 12h14" />,
  editar: <><path d="M12 20h9" /><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z" /></>,
  borrar: <><path d="M3 6h18" /><path d="M8 6V4h8v2" /><path d="M19 6l-1 14H6L5 6" /></>,
  actualizar: <><path d="M21 12a9 9 0 1 1-2.6-6.4" /><path d="M21 3v6h-6" /></>,
  candado: <><rect x="4" y="11" width="16" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></>,
  celular: <><rect x="7" y="2" width="10" height="20" rx="2" /><path d="M11 18h2" /></>,
  tienda: <><path d="M3 9l1.5-5h15L21 9" /><path d="M4 9v11h16V9" /><path d="M3 9h18" /><path d="M9 20v-6h6v6" /></>,
  caja: <><path d="M21 8l-9-5-9 5 9 5 9-5z" /><path d="M3 8v8l9 5 9-5V8" /><path d="M12 13v8" /></>,
  check: <path d="M20 6L9 17l-5-5" />,
  alerta: <><circle cx="12" cy="12" r="9" /><path d="M12 8v4M12 16h.01" /></>,
  reloj: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
  equis: <path d="M18 6L6 18M6 6l12 12" />,
}

export default function Icono({ nombre, className = '' }) {
  return (
    <svg
      className={`icono ${className}`}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {TRAZOS[nombre]}
    </svg>
  )
}
