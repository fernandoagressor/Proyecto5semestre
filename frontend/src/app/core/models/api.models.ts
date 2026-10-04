export interface ApiProblem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  errores?: Record<string, string>;
}

export interface PageResponse<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
  primera: boolean;
  ultima: boolean;
}
