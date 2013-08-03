package me.nandork.jittree;

import com.sun.hotspot.tools.compiler.CallSite;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * This class is kinda immutable (despite of the super class), that's why a lot
 * of values are cached.
 */
class JITNode extends DefaultMutableTreeNode {

    private final CallSite callSite;
    private final String asString;
    private List<TreeNode> childNodes;
    private TreeNode[] pathCache;

    public JITNode(CallSite callSite) {
        this(callSite, null);
    }

    public JITNode(CallSite callSite, JITNode parent) {
        this.callSite = callSite;
        setParent(parent);
        List<CallSite> innerCallSites = callSite.getCalls();
        innerCallSites = innerCallSites != null ? innerCallSites : Collections.<CallSite>emptyList();

        String reason = callSite.getReason();
        asString = callSite.getMethod().toString() + " " + (reason != null ? reason : "");

        childNodes = new ArrayList<>();
        for (CallSite cs : innerCallSites) {
            childNodes.add(new JITNode(cs, this));
        }
    }

    public CallSite callSite() {
        return callSite;
    }

    @Override
    public TreeNode getChildAt(int childIndex) {
        return childNodes.get(childIndex);
    }

    @Override
    public int getChildCount() {
        return childNodes.size();
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    @Override
    public TreeNode[] getPath() {
        if (pathCache == null) {
            pathCache = super.getPath();
        }
        return pathCache;
    }

    @Override
    public Enumeration children() {
        return Collections.enumeration(childNodes);
    }

    @Override
    public String toString() {
        return asString;
    }
}
