package com.bcoworks.codeanalyzer.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@Service
public class SourceCodeScanner {

    public List<Path> scanJavaFiles(String projectFolderPath) throws IOException {
        try (Stream<Path> paths = Files.walk(Path.of(projectFolderPath))) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
        }
    }
}