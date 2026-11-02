export interface CatalogCompany {
  codigo: string;
  nombre: string;
  logoUrl: string | null;
  ciudad: string | null;
  telefono: string | null;
  correo: string | null;
  proyectos: CatalogProject[];
}

export interface CatalogProject {
  id: number;
  codigo: string;
  nombre: string;
  ciudad: string;
  departamento: string;
  descripcion: string | null;
  imagenUrl: string | null;
  porcentajeAvance: number;
  lotesDisponibles: number;
  precioDesde: number | null;
  areaMinima: number | null;
  areaMaxima: number | null;
}

export interface CatalogImage {
  id: number;
  url: string;
  titulo: string | null;
}

export interface CatalogLot {
  id: number;
  codigo: string;
  numero: string;
  etapa: string;
  manzana: string;
  areaM2: number;
  frenteM: number | null;
  fondoM: number | null;
  valor: number;
  ubicacion: string | null;
}

export interface CatalogProjectDetail {
  proyecto: CatalogProject;
  direccion: string | null;
  imagenes: CatalogImage[];
  lotes: CatalogLot[];
}

export interface CatalogContact {
  proyectoId: number | null;
  nombres: string;
  telefono: string | null;
  correo: string | null;
  mensaje: string | null;
  aceptaTratamiento: boolean;
  sitioWeb: string;
}

export interface CatalogStatus {
  codigo: string;
  publico: boolean;
}
