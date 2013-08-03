package me.nandork.jittree;

import com.sun.hotspot.tools.compiler.CallSite;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeNode;
import java.awt.Color;
import java.awt.Component;
import java.util.Set;

class JITTreeCellRenderer extends DefaultTreeCellRenderer {

    private Set<TreeNode> matchingNodes;

    public void setMatchingNodes(Set<TreeNode> matchingNodes) {
        this.matchingNodes = matchingNodes;
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean sel,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
        Component c = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        JITNode node = (JITNode) value;
        CallSite callSite = node.callSite();
        if (callSite.isIntrinsic()) {
            c.setForeground(Color.blue);
        } else if (isNative(node)) {
            c.setForeground(Color.gray);
        } else if (callSite.getReason() != null) {
            c.setForeground(Color.red);
        }
        if (matchingNodes != null) {
            c.setEnabled(matchingNodes.contains(node));
        } else {
            c.setEnabled(true);
        }
        return c;
    }

    private boolean isNative(JITNode node) {
        return node.toString().contains("(0 bytes)");
    }
}
