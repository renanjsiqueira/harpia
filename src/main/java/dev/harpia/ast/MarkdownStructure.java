package dev.harpia.ast;

import dev.harpia.source.SourceFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.commonmark.node.BulletList;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/** Structural Markdown view. Inline CommonMark nodes are intentionally never inspected. */
public record MarkdownStructure(SourceFile source, List<BlockNode> blocks) {

    private static final Parser PARSER = Parser.builder()
            .includeSourceSpans(IncludeSourceSpans.BLOCKS)
            .build();

    public MarkdownStructure {
        Objects.requireNonNull(source, "source");
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
    }

    public static MarkdownStructure parse(SourceFile source) {
        Objects.requireNonNull(source, "source");
        Node document = PARSER.parse(source.content());
        List<BlockNode> blocks = new ArrayList<>();
        collect(document.getFirstChild(), source, blocks, 0, false);
        return new MarkdownStructure(source, blocks);
    }

    private static void collect(
            Node node,
            SourceFile source,
            List<BlockNode> blocks,
            int listDepth,
            boolean insideListItem) {
        for (Node current = node; current != null; current = current.getNext()) {
            if (current instanceof Heading heading) {
                blocks.add(BlockNode.heading(RawSpan.from(source, heading), heading.getLevel()));
                continue;
            }
            if (current instanceof FencedCodeBlock fenced) {
                blocks.add(BlockNode.fencedCode(RawSpan.from(source, fenced), fenced.getInfo()));
                continue;
            }
            if (current instanceof ListItem item) {
                blocks.add(BlockNode.listItem(RawSpan.from(source, item), listDepth));
                collect(item.getFirstChild(), source, blocks, listDepth, true);
                continue;
            }
            if (current instanceof Paragraph paragraph) {
                if (!insideListItem) {
                    blocks.add(BlockNode.paragraph(RawSpan.from(source, paragraph)));
                }
                continue;
            }
            int childListDepth = current instanceof BulletList || current instanceof OrderedList
                    ? listDepth + 1
                    : listDepth;
            collect(current.getFirstChild(), source, blocks, childListDepth, insideListItem);
        }
    }
}
