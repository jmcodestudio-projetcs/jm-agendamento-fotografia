import * as React from "react"

import { cn } from "@/lib/utils"

/**
 * Moldura de foco inspirada nos brackets de AF (autofoco) de viewfinder de
 * câmera: em vez do anel azul genérico do navegador, os quatro cantos
 * "fecham o foco" no elemento quando ele recebe foco de teclado/clique.
 *
 * Uso: envolver um único campo de formulário.
 *   <AFFrame><Input className="af-frame-input" ... /></AFFrame>
 */
function AFFrame({ className, children }: { className?: string; children: React.ReactNode }) {
  return (
    <div className={cn("af-frame relative", className)}>
      {children}
      <Corner className="-left-[5px] -top-[5px] border-r-0 border-b-0" />
      <Corner className="-right-[5px] -top-[5px] border-l-0 border-b-0" />
      <Corner className="-left-[5px] -bottom-[5px] border-r-0 border-t-0" />
      <Corner className="-right-[5px] -bottom-[5px] border-l-0 border-t-0" />
    </div>
  )
}

function Corner({ className }: { className: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        "af-corner pointer-events-none absolute h-3 w-3 border-2 border-focus",
        className,
      )}
    />
  )
}

export { AFFrame }
