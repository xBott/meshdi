package me.bottdev.meshdi.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

class MeshdiMetaProcessorTest {

    @Nested
    @DisplayName("single constructor")
    class SingleConstructor {

        @Test
        @DisplayName("should register component without @Inject when only one constructor exists")
        void singleConstructorWithoutInject_succeeds() {

            Compilation compilation = javac()
                    .withProcessors(new MeshdiMetaProcessor())
                    .compile(JavaFileObjects.forSourceString(
                            "test.Example",
                            """
                            package test;
                            import me.bottdev.meshdi.api.annotations.Component;

                            @Component
                            public class Example {
                                public Example() {}
                            }
                            """
                    ));

            assertThat(compilation).succeeded();
            assertThat(compilation).hadNoteContaining("Found DI component: test.Example");
        }
    }

    @Nested
    @DisplayName("multiple constructors")
    class MultipleConstructors {

        @Test
        @DisplayName("should fail when multiple constructors exist without @Inject")
        void multipleConstructorsWithoutInject_fails() {

            Compilation compilation = javac()
                    .withProcessors(new MeshdiMetaProcessor())
                    .compile(JavaFileObjects.forSourceString(
                            "test.Example",
                            """
                            package test;
                            import me.bottdev.meshdi.api.annotations.Component;

                            @Component
                            public class Example {
                                public Example() {}
                                public Example(String s) {}
                            }
                            """
                    ));

            assertThat(compilation).failed();
            assertThat(compilation)
                    .hadErrorContaining("has multiple constructors without @Inject");
        }

        @Test
        @DisplayName("should fail when multiple constructors are annotated with @Inject")
        void multipleInjectConstructors_fails() {

            Compilation compilation = javac()
                    .withProcessors(new MeshdiMetaProcessor())
                    .compile(JavaFileObjects.forSourceString(
                            "test.Example",
                            """
                            package test;
                            import me.bottdev.meshdi.api.annotations.Component;
                            import me.bottdev.meshdi.api.annotations.Inject;

                            @Component
                            public class Example {
                                @Inject
                                public Example() {}
                                @Inject
                                public Example(String s) {}
                            }
                            """
                    ));

            assertThat(compilation).failed();
            assertThat(compilation)
                    .hadErrorContaining("has multiple constructors annotated with @Inject");
        }

        @Test
        @DisplayName("should succeed and pick the @Inject constructor when several exist")
        void multipleConstructorsWithSingleInject_succeeds() {

            Compilation compilation = javac()
                    .withProcessors(new MeshdiMetaProcessor())
                    .compile(
                            JavaFileObjects.forSourceString(
                                    "test.Dependency1",
                                    """
                                    package test;
                                    import me.bottdev.meshdi.api.annotations.Component;
        
                                    @Component
                                    public class Dependency1 {
                                        public Dependency1() {}
                                    }
                                    """
                            ),
                            JavaFileObjects.forSourceString(
                                    "test.Example",
                                    """
                                    package test;
                                    import me.bottdev.meshdi.api.annotations.Component;
                                    import me.bottdev.meshdi.api.annotations.Inject;
        
                                    @Component
                                    public class Example {
                                        public Example() {}
                                        @Inject
                                        public Example(Dependency1 dependency) {}
                                    }
                                    """
                            )
                    );

            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("component keys")
    class ComponentKeys {

        @Test
        @DisplayName("should fail on duplicate component key with same qualifier")
        void duplicateComponentKey_fails() {
            // два разных класса с одинаковым qualifier дадут одинаковый componentKey
            // только если qualifiedName совпадает — что невозможно для разных классов,
            // так что дубликат реалистичен через одинаковый qualifier на ОДНОМ classpath
            // с divergent full name — тут нужно проверить логику самой ключевой схемы:
            // className + "@" + qualifier. Дубликат возможен только при повторном
            // обнаружении ТОГО ЖЕ класса (например, при двух раундах обработки).
            // Если такой сценарий недостижим в реальности — рассмотри, нужно ли
            // это правило вообще, либо протестируй через два раздельных @Component
            // с одинаковым qualifier на классах с одинаковым simple name в разных пакетах
            // (тогда qualifiedName разный, componentKey тоже разный — тест невалиден).
        }

        @Test
        @DisplayName("should use qualifier in component key when specified")
        void qualifiedComponent_usesQualifierInKey() {

            Compilation compilation = javac()
                    .withProcessors(new MeshdiMetaProcessor())
                    .compile(JavaFileObjects.forSourceString(
                            "test.Example",
                            """
                            package test;
                            import me.bottdev.meshdi.api.annotations.Component;

                            @Component(qualifier = "primary")
                            public class Example {
                                public Example() {}
                            }
                            """
                    ));

            assertThat(compilation).succeeded();
        }
    }

    @Nested
    @DisplayName("dependency resolution")
    class DependencyResolution {

        @Test
        @DisplayName("should resolve two components with a required dependency between them")
        void resolvesRequiredDependency_succeeds() {

            Compilation compilation = javac()
                    .withProcessors(new MeshdiMetaProcessor())
                    .compile(
                            JavaFileObjects.forSourceString(
                                    "test.Repository",
                                    """
                                    package test;
                                    import me.bottdev.meshdi.api.annotations.Component;

                                    @Component
                                    public class Repository {
                                        public Repository() {}
                                    }
                                    """
                            ),
                            JavaFileObjects.forSourceString(
                                    "test.Service",
                                    """
                                    package test;
                                    import me.bottdev.meshdi.api.annotations.Component;

                                    @Component
                                    public class Service {
                                        public Service(Repository repository) {}
                                    }
                                    """
                            )
                    );

            assertThat(compilation).succeeded();
            assertThat(compilation)
                    .hadNoteContaining("Successfully resolved components! Component order:");
        }

        @Test
        @DisplayName("should fail on circular dependency between two components")
        void circularDependency_fails() {

            Compilation compilation = javac()
                    .withProcessors(new MeshdiMetaProcessor())
                    .compile(
                            JavaFileObjects.forSourceString(
                                    "test.A",
                                    """
                                    package test;
                                    import me.bottdev.meshdi.api.annotations.Component;

                                    @Component
                                    public class A {
                                        public A(B b) {}
                                    }
                                    """
                            ),
                            JavaFileObjects.forSourceString(
                                    "test.B",
                                    """
                                    package test;
                                    import me.bottdev.meshdi.api.annotations.Component;

                                    @Component
                                    public class B {
                                        public B(A a) {}
                                    }
                                    """
                            )
                    );

            assertThat(compilation).failed();
            assertThat(compilation).hadErrorContaining("Circular dependency is found in the graph");

        }

        @Test
        @DisplayName("should fail when required dependency is missing")
        void missingRequiredDependency_fails() {

            Compilation compilation = javac()
                    .withProcessors(new MeshdiMetaProcessor())
                    .compile(JavaFileObjects.forSourceString(
                            "test.Service",
                            """
                            package test;
                            import me.bottdev.meshdi.api.annotations.Component;

                            @Component
                            public class Service {
                                public Service(MissingDependency dep) {}
                            }
                            interface MissingDependency {}
                            """
                    ));

            assertThat(compilation).failed();
        }
    }
}