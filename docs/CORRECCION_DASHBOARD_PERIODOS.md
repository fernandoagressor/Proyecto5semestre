# Corrección de periodos del dashboard

Fecha: 16 de septiembre de 2026

## Problema identificado

Los endpoints gerencial, comercial y de servicio utilizaban el primer día del
mes actual como fecha inicial cuando Angular no enviaba un rango. Al cambiar de
mes, las ventas, pagos, costos y demás operaciones registradas en meses
anteriores dejaban de formar parte de la consulta. La API respondía con éxito,
por lo que la interfaz mostraba `Actualizado`, pero las tarjetas quedaban en
cero.

Los registros no se eliminaban ni se modificaban: quedaban fuera del rango
temporal implícito.

## Solución aplicada

- El backend usa el histórico completo como periodo predeterminado.
- Angular abre el dashboard con la opción **Todo el historial**.
- Se incorporaron los filtros **Mes actual**, **Últimos 3 meses** y
  **Año actual**.
- La cabecera informa el periodo o la fecha de corte que se está mostrando.
- El proyecto seleccionado se conserva en todas las consultas autorizadas.
- Los endpoints mantienen el filtro obligatorio por empresa y los permisos de
  cada dashboard.
- La vista de cartera continúa siendo una fotografía a una fecha de corte; no
  se mezcla con el rango transaccional de ventas y servicio.

## Contrato HTTP

Los endpoints existentes no cambian de ruta. Los parámetros continúan siendo
opcionales:

```text
GET /api/v1/dashboards/gerencial?proyectoId={id}&desde=AAAA-MM-DD&hasta=AAAA-MM-DD
GET /api/v1/dashboards/comercial?proyectoId={id}&desde=AAAA-MM-DD&hasta=AAAA-MM-DD
GET /api/v1/dashboards/servicio?proyectoId={id}&desde=AAAA-MM-DD&hasta=AAAA-MM-DD
GET /api/v1/dashboards/cartera?proyectoId={id}&corte=AAAA-MM-DD
```

Si `desde` no se envía, los tres primeros endpoints consultan el histórico. Si
`hasta` o `corte` no se envían, se utiliza la fecha actual de Bogotá.

## Regresión cubierta

- Resolución del periodo histórico en backend.
- Conservación de rangos explícitos y rechazo de rangos invertidos.
- Construcción de los cuatro periodos disponibles en Angular.
- Envío de `desde`, `hasta`, `corte` y `proyectoId` al endpoint correcto.
- Ejecución completa de las 73 pruebas frontend.
