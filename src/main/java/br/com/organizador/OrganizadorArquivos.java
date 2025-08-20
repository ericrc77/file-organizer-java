package br.com.organizador;

import java.io.IOException;
import java.nio.file.*;

public class OrganizadorArquivos {
    private final Path pastaMonitorada;

    public OrganizadorArquivos(Path pastaMonitorada) {
        this.pastaMonitorada = pastaMonitorada;
    }

    public void iniciar() throws IOException, InterruptedException {
        // Organiza arquivos já presentes na pasta
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pastaMonitorada)) {
            for (Path arquivo : stream) {
                if (Files.isRegularFile(arquivo)) {
                    organizarArquivo(arquivo);
                }
            }
        }

        WatchService watchService = FileSystems.getDefault().newWatchService();
        pastaMonitorada.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
        System.out.println("Monitorando: " + pastaMonitorada.toAbsolutePath());
        while (true) {
            WatchKey key = watchService.take();
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                    Path arquivo = pastaMonitorada.resolve((Path) event.context());
                    organizarArquivo(arquivo);
                }
            }
            key.reset();
        }
    }

    public void organizarArquivo(Path arquivo) {
        try {
            if (Files.isDirectory(arquivo)) return;
            String categoria = getCategoria(arquivo.getFileName().toString());
            Path destino = pastaMonitorada.resolve(categoria).resolve(arquivo.getFileName());
            Files.createDirectories(destino.getParent());
            Files.move(arquivo, destino, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Movido: " + arquivo.getFileName() + " -> " + destino.getParent().getFileName());
        } catch (Exception e) {
            System.err.println("Erro ao mover arquivo: " + arquivo + " - " + e.getMessage());
        }
    }

    private String getCategoria(String nomeArquivo) {
        String extensao = getExtensao(nomeArquivo);
        switch (extensao) {
            case "jpg": case "jpeg": case "png": case "gif": case "bmp": case "svg":
                return "Imagens";
            case "pdf":
                return "PDFs";
            case "doc": case "docx": case "odt": case "txt": case "rtf":
                return "Documentos";
            case "xls": case "xlsx": case "ods":
                return "Planilhas";
            case "ppt": case "pptx": case "odp":
                return "Apresentações";
            case "zip": case "rar": case "7z": case "tar": case "gz":
                return "Compactados";
            case "exe": case "msi": case "bat": case "sh":
                return "Executáveis";
            case "mp3": case "wav": case "ogg": case "flac":
                return "Áudio";
            case "mp4": case "avi": case "mov": case "wmv": case "mkv":
                return "Vídeos";
            default:
                return "Outros";
        }
    }

    private String getExtensao(String nomeArquivo) {
        int i = nomeArquivo.lastIndexOf('.');
        return (i > 0) ? nomeArquivo.substring(i + 1).toLowerCase() : "";
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.out.println("Uso: java -jar organizador-arquivos.jar <pasta>");
            System.out.println("Organiza por categoria: Imagens, Documentos, PDFs, etc.");
            return;
        }
        Path pasta = Paths.get(args[0]);
        new OrganizadorArquivos(pasta).iniciar();
    }
}
