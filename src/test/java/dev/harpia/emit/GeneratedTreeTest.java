package dev.harpia.emit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class GeneratedTreeTest {

    @Test
    void exposesFilesInStablePathOrder() {
        GeneratedTree tree = new GeneratedTree();
        tree.put("z.txt", "last");
        tree.put("a/nested.txt", "first");

        assertThat(tree.files().keySet()).containsExactlyElementsOf(List.of("a/nested.txt", "z.txt"));
        assertThatThrownBy(() -> tree.files().put("other", "content"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsEscapesPlatformSeparatorsAndCollisions() {
        GeneratedTree tree = new GeneratedTree();
        tree.put("src/App.java", "one");

        assertThatThrownBy(() -> tree.put("../outside", "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tree.put("src\\Other.java", "bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tree.put("src/App.java", "two"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void preservesArtifactTypeAndSourceMappingWithoutChangingTheContentApi() {
        GeneratedTree tree = new GeneratedTree();
        dev.harpia.diag.SourceRef source =
                dev.harpia.diag.SourceRef.of("specs/customer.harpia.md", 7, 1);

        tree.put(new GeneratedFile(
                "src/main/java/Customer.java",
                "class Customer {}\n",
                GeneratedFileType.JAVA_SOURCE,
                java.util.Optional.of(source)));

        assertThat(tree.files()).containsEntry(
                "src/main/java/Customer.java", "class Customer {}\n");
        assertThat(tree.generatedFiles()).singleElement().satisfies(file -> {
            assertThat(file.type()).isEqualTo(GeneratedFileType.JAVA_SOURCE);
            assertThat(file.source()).contains(source);
        });
    }
}
