package com.bcoworks.blueringoctopuscli.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompileCheckTest {

    @Test
    void aClassThatCompilesHasNoProblems() {
        CompileCheck.Result result = CompileCheck.check("Greeter", """
                import java.util.List;

                public class Greeter {
                    public String greet(List<String> names) {
                        return "Hello " + String.join(", ", names);
                    }
                }
                """);

        assertTrue(result.checked());
        assertTrue(result.compiles(), result.problems().toString());
        assertTrue(result.libraries().isEmpty());
    }

    @Test
    void aSyntaxErrorIsReportedWithItsLineAndText() {
        CompileCheck.Result result = CompileCheck.check("A", "public class A {\n    int x = 1\n}\n");

        assertFalse(result.compiles());
        CompileCheck.Problem problem = result.problems().get(0);
        assertEquals(2, problem.line());
        assertTrue(problem.message().contains("';' expected"), problem.message());
        assertEquals("int x = 1", problem.sourceLine());
    }

    @Test
    void aMethodThatDoesNotExistInTheJdkIsAProblem() {
        CompileCheck.Result result = CompileCheck.check("A", """
                import java.util.List;

                public class A {
                    void f(List<String> list) {
                        list.push("x");
                    }
                }
                """);

        assertEquals(1, result.problems().size(), result.problems().toString());
        assertEquals(5, result.problems().get(0).line());
        assertTrue(result.problems().get(0).message().contains("cannot find symbol"));
        assertTrue(result.problems().get(0).message().contains("push"));
    }

    @Test
    void aJdkClassThatDoesNotExistIsAProblemAndSoIsAJdkPackageThatDoesNotExist() {
        CompileCheck.Result result = CompileCheck.check("A", """
                import java.util.Lisst;
                import java.utils.Map;

                public class A {
                }
                """);

        assertEquals(2, result.problems().size(), result.problems().toString());
        assertTrue(result.libraries().isEmpty());
    }

    @Test
    void aMissingLibraryIsNotAProblemOfTheCodeButIsReported() {
        CompileCheck.Result result = CompileCheck.check("ProductController", """
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;
                import java.util.List;

                @RestController
                public class ProductController {
                    @GetMapping("/products")
                    public ResponseEntity<List<String>> list() {
                        return ResponseEntity.ok(List.of("a"));
                    }
                }
                """);

        assertTrue(result.compiles(), result.problems().toString());
        assertEquals(Set.of("org.springframework.http", "org.springframework.web.bind.annotation"), result.libraries());
    }

    @Test
    void aRealMistakeNextToAMissingLibraryIsStillFound() {
        CompileCheck.Result result = CompileCheck.check("A", """
                import org.springframework.stereotype.Service;

                @Service
                public class A {
                    int length(String s) {
                        return s.size();
                    }
                }
                """);

        assertEquals(1, result.problems().size(), result.problems().toString());
        assertEquals(6, result.problems().get(0).line());
        assertEquals(Set.of("org.springframework.stereotype"), result.libraries());
    }

    @Test
    void aStaticImportAndAWildcardFromAMissingLibraryAreNotProblemsEither() {
        CompileCheck.Result result = CompileCheck.check("A", """
                import static org.assertj.core.api.Assertions.assertThat;
                import org.mockito.*;

                public class A {
                    void f() {
                        assertThat(1);
                        Mock m = null;
                    }
                }
                """);

        assertTrue(result.compiles(), result.problems().toString());
        assertEquals(Set.of("org.assertj.core.api", "org.mockito"), result.libraries());
    }

    @Test
    void implementingAnInterfaceFromAMissingLibraryIsNotAProblem() {
        CompileCheck.Result result = CompileCheck.check("A", """
                import org.springframework.context.ApplicationListener;

                public class A implements ApplicationListener<Object> {
                    @Override
                    public void onApplicationEvent(Object event) {
                    }
                }
                """);

        assertTrue(result.compiles(), result.problems().toString());
    }

    @Test
    void aFileNamedAfterTheWrongTypeIsAProblem() {
        CompileCheck.Result result = CompileCheck.check("Helper", "public class Main {\n}\n");

        assertEquals(1, result.problems().size());
        assertTrue(result.problems().get(0).message().contains("should be declared in a file named"));
    }

    @Test
    void thePackageDeclarationDoesNotNeedAFolder() {
        CompileCheck.Result result = CompileCheck.check("A", "package com.example.demo;\n\npublic class A {\n}\n");

        assertTrue(result.compiles(), result.problems().toString());
    }
}
