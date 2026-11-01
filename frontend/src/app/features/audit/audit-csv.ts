import { AuditEvent } from '../../core/models/auditoria.models';

const HEADERS = ['Fecha', 'Módulo', 'Acción', 'Entidad', 'Id entidad', 'Descripción', 'Usuario', 'IP', 'Antes', 'Después', 'Correlación'];

/**
 * Una celda CSV segura: entre comillas, comillas dobladas y, si empieza por = + - @ (fórmula en
 * Excel), con un apóstrofo delante para que no se ejecute al abrir el archivo.
 */
export function csvCell(value: unknown): string {
  let text = value === null || value === undefined ? '' : String(value);
  if (/^[=+\-@\t\r]/.test(text)) text = `'${text}`;
  return `"${text.replace(/"/g, '""')}"`;
}

/** CSV con separador ";" (el que abre Excel en español) y BOM para que respete las tildes. */
export function buildAuditCsv(rows: AuditEvent[], actorName: (id: number | null) => string): string {
  const lines = rows.map((e) =>
    [
      e.ocurridoEn?.replace('T', ' ').slice(0, 19),
      e.modulo,
      e.accion,
      e.entidadTipo,
      e.entidadId,
      e.descripcion,
      actorName(e.actorId),
      e.ipOrigen,
      e.datosAnterioresJson,
      e.datosNuevosJson,
      e.correlationId,
    ]
      .map(csvCell)
      .join(';'),
  );
  return '﻿' + [HEADERS.map(csvCell).join(';'), ...lines].join('\r\n') + '\r\n';
}

export function downloadText(filename: string, content: string, type: string): void {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
