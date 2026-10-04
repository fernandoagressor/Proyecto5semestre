# Artefactos de migración

Esta carpeta conserva los archivos versionados de la migración MySQL → MongoDB.

```text
migration/
├── moon-modeler/          # archivos editables .dmm y diagramas exportados
├── relational-migrator/   # proyectos exportados .relmig
├── schema/                # validadores, colecciones e índices generados
├── reports/               # análisis previo y resultados sin datos sensibles
└── validation/            # consultas y matrices de reconciliación
```

No se deben guardar contraseñas, cadenas de conexión con credenciales, copias
de bases reales ni datos personales en esta carpeta.
