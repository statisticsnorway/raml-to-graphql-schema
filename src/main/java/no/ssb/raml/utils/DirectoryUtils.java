package no.ssb.raml.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class DirectoryUtils {

    public static Path currentPath() {
        return Paths.get("").toAbsolutePath();
    }

    public static Path resolveRelativeFilePath(String relativeFile) {
        return currentPath().resolve(relativeFile);
    }

    public static Path resolveRelativeFolderPath(String folderLocation, String relativeFile) {
        return Paths.get(folderLocation).toAbsolutePath().resolve(relativeFile);
    }

    public static Path createFolder(Path folderPath) {
        if (!folderPath.toFile().getAbsoluteFile().exists()) {
            boolean created = folderPath.toFile().mkdirs();
            if (!created) {
                System.err.format("Folder '%s' could not be created.\n", folderPath.toString());
            }
        } else {
            if (!folderPath.toFile().isDirectory()) {
                System.err.format("Parameter '%s' is not a directory.\n", folderPath.toString());
            }
            if (!folderPath.toFile().canWrite()) {
                System.err.format("Folder '%s' cannot be written.\n", folderPath.toString());
            }
        }
        return currentPath().resolve(folderPath);
    }

    public static String readFileContent(Path fileLocation) {
        String content = "";
        try {
            content = new String(Files.readAllBytes(resolveRelativeFilePath(fileLocation.toString())),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return content;
    }

    public static void deleteOnExit(Path path) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                delete(path);
            }
        });
    }

    public static void delete(Path path) {
        if (!path.toFile().getAbsoluteFile().exists()) {
            return;
        }

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                        throws IOException {
                    Files.deleteIfExists(dir);
                    return super.postVisitDirectory(dir, exc);
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    Files.deleteIfExists(file);
                    return super.visitFile(file, attrs);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
