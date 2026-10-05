package com.bcoworks.blueringoctopuscli.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceCodeScannerTest {

    private final SourceCodeScanner scanner = new SourceCodeScanner();

    @TempDir
    Path root;

    private Path touch(String relative) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "class X {}");
    }

    @Test
    void findsJavaFilesRecursively() throws IOException {
        Path a = touch("A.java");
        Path b = touch("src/main/pkg/B.java");

        List<Path> found = scanner.scanJavaFiles(root.toString());

        assertEquals(2, found.size());
        assertTrue(found.containsAll(List.of(a, b)));
    }

    @Test
    void skipsNonJavaFiles() throws IOException {
        touch("README.md");
        touch("pom.xml");
        touch("Notes.java.txt");

        assertTrue(scanner.scanJavaFiles(root.toString()).isEmpty());
    }

    @Test
    void skipsIgnoredDirectoriesAtAnyDepth() throws IOException {
        Path kept = touch("src/Keep.java");
        touch("target/Generated.java");
        touch("node_modules/x/Y.java");
        touch(".git/Hook.java");
        touch("module/build/Out.java");
        touch("logs/Log.java");

        assertEquals(List.of(kept), scanner.scanJavaFiles(root.toString()));
    }

    @Test
    void ignoredDirectoryNameMatchesWholeSegmentOnly() throws IOException {
        Path kept = touch("targets/Keep.java");

        assertEquals(List.of(kept), scanner.scanJavaFiles(root.toString()));
    }

    @Test
    void ignoredDirectoryAboveRootDoesNotMatter() throws IOException {
        Path buildRoot = root.resolve("build").resolve("project");
        Path file = buildRoot.resolve("A.java");
        Files.createDirectories(buildRoot);
        Files.writeString(file, "class A {}");

        assertEquals(List.of(file), scanner.scanJavaFiles(buildRoot.toString()));
    }

    @Test
    void missingRootThrows() {
        assertThrows(NoSuchFileException.class,
                () -> scanner.scanJavaFiles(root.resolve("yok").toString()));
    }
}
