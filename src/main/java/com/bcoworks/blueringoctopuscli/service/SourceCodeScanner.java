package com.bcoworks.blueringoctopuscli.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class SourceCodeScanner {

    private static final Set<String> IGNORED_DIRS =
            Set.of(".git", ".idea", ".mvn", ".vscode", ".gradle", "target", "build", "node_modules", "logs");

    public List<Path> scanJavaFiles(String projectFolderPath) throws IOException {
        Path root = Path.of(projectFolderPath);
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> !isInIgnoredDir(root, p))
                    .toList();
        }
    }

    private boolean isInIgnoredDir(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path part : relative) {
            if (IGNORED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }
}