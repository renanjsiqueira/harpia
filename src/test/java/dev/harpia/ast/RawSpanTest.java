package dev.harpia.ast;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harpia.source.SourceFile;
import org.junit.jupiter.api.Test;

class RawSpanTest {

    @Test
    void preservesMarkdownSensitiveTokensFromTheOriginalSource() {
        SourceFile source = new SourceFile("specs/raw.harpia.md", """
                # Order_Item

                ## Data

                - customer_id: UUID -> Customer

                ## Get Order

                ### Endpoint

                GET /orders/{id}

                ### Flow

                ```flow
                order_item = load Order by id
                return order_item -> response
                ```
                """);

        MarkdownStructure structure = MarkdownStructure.parse(source);

        assertThat(structure.blocks())
                .filteredOn(block -> block.kind() == BlockNode.Kind.HEADING)
                .extracting(BlockNode::headingText)
                .contains("Order_Item");
        assertThat(structure.blocks())
                .filteredOn(block -> block.kind() == BlockNode.Kind.LIST_ITEM)
                .extracting(BlockNode::listItemText)
                .contains("customer_id: UUID -> Customer");
        assertThat(structure.blocks())
                .filteredOn(block -> block.kind() == BlockNode.Kind.PARAGRAPH)
                .extracting(block -> block.raw().text())
                .contains("GET /orders/{id}");
        assertThat(structure.blocks())
                .filteredOn(block -> block.kind() == BlockNode.Kind.FENCED_CODE)
                .flatExtracting(BlockNode::fencedBodyLines)
                .extracting(RawSpan::text)
                .containsExactly("order_item = load Order by id", "return order_item -> response");
    }

    @Test
    void keepsPhysicalLineNumbersForFencedBodyLines() {
        SourceFile source = new SourceFile("specs/flow.harpia.md", """
                ### Flow

                ```flow
                validate input
                return nothing
                ```
                """);

        BlockNode fence = MarkdownStructure.parse(source).blocks().stream()
                .filter(block -> block.kind() == BlockNode.Kind.FENCED_CODE)
                .findFirst()
                .orElseThrow();

        assertThat(fence.fencedBodyLines())
                .extracting(RawSpan::line, RawSpan::column, RawSpan::text)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(4, 1, "validate input"),
                        org.assertj.core.groups.Tuple.tuple(5, 1, "return nothing"));
    }
}
