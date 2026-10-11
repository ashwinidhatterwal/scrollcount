package in.scrollcount.probe;

import java.util.function.Consumer;

/** Reads only a caller-scoped title/channel subtree; never an action rail or entire page. */
final class YouTubeLabels {
    interface Node {
        boolean readable();
        CharSequence text();
        CharSequence description();
        int children();
        Node child(int index);
        void close();
    }

    static void collect(Node node, int maxDepth, Consumer<String> output) {
        collect(node, 0, maxDepth, new int[]{0}, output);
    }

    private static boolean collect(Node node, int depth, int maxDepth, int[] visited,
                                   Consumer<String> output) {
        if (visited[0]++ >= 80 || depth > maxDepth || !node.readable()) return false;
        String text = normalize(node.text());
        if (!text.isEmpty()) {
            output.accept(text);
            return true; // A parent may summarize its children.
        }
        boolean found = false;
        for (int i = 0; i < node.children() && visited[0] < 80; i++) {
            Node child = node.child(i);
            if (child == null) continue;
            try {
                found |= collect(child, depth + 1, maxDepth, visited, output);
            } finally {
                child.close();
            }
        }
        // Prefer descendant text/labels; parent descriptions often summarize the same content.
        if (!found) {
            String description = normalize(node.description());
            if (!description.isEmpty()) {
                output.accept(description);
                found = true;
            }
        }
        return found;
    }

    private static String normalize(CharSequence value) {
        return value == null ? "" : value.toString().trim().replaceAll("\\s+", " ");
    }
}
