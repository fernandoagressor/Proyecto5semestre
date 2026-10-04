package com.inmobiliaria.inmobiliaria_api.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArquitecturaCapasTest {

    private static final Path ROOT = Path.of(
            "src/main/java/com/inmobiliaria/inmobiliaria_api"
    );
    private static final Set<String> CAPAS = Set.of(
            "config",
            "controller",
            "dto",
            "entity",
            "repository",
            "security",
            "service"
    );
    private static final Pattern PACKAGE = Pattern.compile(
            "(?m)^package\\s+([\\w.]+)\\s*;"
    );

    @Test
    void conservaLasSieteCapasPrincipales() throws IOException {
        try (Stream<Path> paths = Files.list(ROOT)) {
            Set<String> carpetas = paths
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());

            assertEquals(CAPAS, carpetas);
        }
    }

    @Test
    void cadaClaseDeclaraElPaqueteCorrespondienteASuRuta() throws IOException {
        try (Stream<Path> paths = Files.walk(ROOT)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                Matcher matcher = PACKAGE.matcher(source);
                assertTrue(matcher.find(), () -> "Falta package en " + path);

                Path relative = ROOT.getParent().getParent().getParent()
                        .relativize(path.getParent());
                String expected = relative.toString().replace(path.getFileSystem().getSeparator(), ".");
                assertEquals(expected, matcher.group(1), () -> "Package incorrecto en " + path);
            }
        }
    }

    @Test
    void noConservaPaquetesDeLaArquitecturaAnterior() throws IOException {
        Pattern legacy = Pattern.compile(
                "\\.(aplicacion|dominio|infraestructura|web|configuracion)(\\.|;)"
        );
        try (Stream<Path> paths = Files.walk(ROOT)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                assertTrue(
                        !legacy.matcher(Files.readString(path)).find(),
                        () -> "Paquete anterior encontrado en " + path
                );
            }
        }
    }

    @Test
    void respetaLaDireccionDeDependenciasEntreCapas() throws IOException {
        Map<String, Set<String>> prohibidas = Map.of(
                "controller", Set.of("repository"),
                "entity", Set.of("config", "controller", "dto", "repository", "security", "service"),
                "repository", Set.of("controller", "dto", "service"),
                "dto", Set.of("controller", "repository", "service"),
                "service", Set.of("controller")
        );

        for (Map.Entry<String, Set<String>> regla : prohibidas.entrySet()) {
            Path layer = ROOT.resolve(regla.getKey());
            try (Stream<Path> paths = Files.walk(layer)) {
                for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(path);
                    for (String destino : regla.getValue()) {
                        String paqueteProhibido = "." + destino + ".";
                        assertTrue(
                                !source.contains("import " + ROOT_PACKAGE + paqueteProhibido),
                                () -> regla.getKey() + " no debe depender de " + destino + " en " + path
                        );
                    }
                }
            }
        }
    }

    private static final String ROOT_PACKAGE = "com.inmobiliaria.inmobiliaria_api";
}
