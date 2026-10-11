package in.scrollcount.probe;

import java.util.*;

public final class YouTubeLabelsTest {
    static final class Node implements YouTubeLabels.Node {
        String text, description;
        boolean readable=true, closed;
        List<Node> children=new ArrayList<>();
        Node(String text,String description,Node... children) {
            this.text=text; this.description=description;
            this.children.addAll(Arrays.asList(children));
        }
        public boolean readable(){return readable;}
        public CharSequence text(){if(!readable)throw new AssertionError("blocked text");return text;}
        public CharSequence description(){if(!readable)throw new AssertionError("blocked description");return description;}
        public int children(){return children.size();}
        public Node child(int i){return children.get(i);}
        public void close(){closed=true;}
    }
    static Set<String> read(Node node) {
        Set<String> labels=new TreeSet<>();
        YouTubeLabels.collect(node,52,labels::add);
        return labels;
    }
    static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Node text=new Node("  Title\n here  ",null);
        Node description=new Node(null,"Title here");
        check(read(text).equals(read(description)),"text and description have identical normalized identity");
        Node child=new Node("Channel",null);
        check(read(new Node(null,"Channel, Subscribe",child)).equals(Collections.singleton("Channel")),
                "descendant label excludes parent action summary");
        check(child.closed,"child node released");
        check(read(new Node(null,"Description-only title",new Node(null,null))).contains("Description-only title"),
                "accessible parent description retained when descendants expose no labels");
        check(read(new Node("Title",null,new Node("Title",null))).size()==1,"parent summary counted once");
        Node blocked=new Node("private","private");blocked.readable=false;
        check(read(blocked).isEmpty(),"hidden/password/editable subtree not read");
        Node duplicate=new Node(null,null,new Node("Title",null),new Node(null,"Title"));
        check(read(duplicate).size()==1,"duplicate text/description wrappers do not change identity");
        Node wide=new Node(null,null);
        for(int i=0;i<200;i++)wide.children.add(new Node("label"+i,null));
        check(read(wide).size()==79,"bounded traversal");
        // A title without text can change on every actual Short, including slow skips.
        FastFingerprintTracker tracker=new FastFingerprintTracker();tracker.seed("Short 0");
        for(int i=1;i<=20;i++) {
            long at=10000+i*5000;tracker.movement(at);
            String label=read(new Node(null,"Short "+i)).iterator().next();
            check(!tracker.observe(label,at+2000,true),"late label first observation");
            check(tracker.observe(label,at+2070,true),"late description-only Short accepted");
            check(!tracker.observe(label,at+2140,true),"duplicate read excluded");
        }
        System.out.println("PASS: scoped description-only labels, canonical identity, privacy guards, node release, bounded reads and 20 delayed slow transitions");
    }
}
