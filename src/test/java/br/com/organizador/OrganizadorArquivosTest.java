package br.com.organizador;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OrganizadorArquivosTest {
    @Test
    public void testOrganizadorPorExtensao() throws Exception {
        Path tempDir = Files.createTempDirectory("organizador-test");
        Path arquivo = tempDir.resolve("teste.txt");
        Files.createFile(arquivo);
    OrganizadorArquivos organizador = new OrganizadorArquivos(tempDir);
        organizador.organizarArquivo(arquivo);
    assertTrue(Files.exists(tempDir.resolve("Documentos").resolve("teste.txt")));
    }
}
