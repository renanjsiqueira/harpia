package dev.harpia.parse;

import dev.harpia.ast.BlockNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Groups a heading with every block that belongs to it, up to the next heading of the same level. */
final class Sections {

    private Sections() {
    }

    static List<Section> at(List<BlockNode> blocks, int level) {
        List<Section> result = new ArrayList<>();
        for (int index = 0; index < blocks.size(); index++) {
            BlockNode block = blocks.get(index);
            if (block.kind() != BlockNode.Kind.HEADING || block.headingLevel() != level) {
                continue;
            }
            int end = index + 1;
            while (end < blocks.size()) {
                BlockNode candidate = blocks.get(end);
                if (candidate.kind() == BlockNode.Kind.HEADING
                        && candidate.headingLevel() <= level) {
                    break;
                }
                end++;
            }
            result.add(new Section(block, blocks.subList(index + 1, end)));
            index = end - 1;
        }
        return List.copyOf(result);
    }

    record Section(BlockNode heading, List<BlockNode> content) {
        Section {
            Objects.requireNonNull(heading, "heading");
            content = List.copyOf(content);
        }

        String name() {
            return heading.headingText();
        }
    }
}
