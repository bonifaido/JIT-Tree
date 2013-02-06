/*
 *  Copyright (c) 2013, Nandor Kracser (bonifaido@gmail.com)
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *  
 *  1. Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *  
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *  
 *  3. Neither the name of the copyright holders nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *  
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 *  THE POSSIBILITY OF SUCH DAMAGE.
 */
package me.nandork.jittree;

import com.sun.hotspot.tools.compiler.CallSite;
import com.sun.hotspot.tools.compiler.Compilation;
import com.sun.hotspot.tools.compiler.LogEvent;
import com.sun.hotspot.tools.compiler.LogParser;
import com.sun.hotspot.tools.compiler.Method;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;

class JITTreeCellHighLighter extends DefaultTreeCellRenderer {

    private Set<TreeNode> matchingNodes;

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        Component c = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        JITNode node = (JITNode) value;
        CallSite callSite = node.callSite();
        if (node.toString().contains("(0 bytes)")) { // native
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

    public void setMatchingNodes(Set<TreeNode> matchingNodes) {
        this.matchingNodes = matchingNodes;
    }
}

/**
 * This class is kinda immutable (despite of the super class),
 * that's why a lot of values are cached.
 */
class JITNode extends DefaultMutableTreeNode {

    private final CallSite callSite;
    private final List<CallSite> callSites;
    private final String asString;
    private final TreeNode[] childCache;
    private TreeNode[] pathCache;

    public JITNode(CallSite callSite) {
        this(callSite, null);
    }

    public JITNode(CallSite callSite, JITNode parent) {
        this.callSite = callSite;
        setParent(parent);
        List<CallSite> innerCallSites = callSite.getCalls();
        callSites = innerCallSites != null ? innerCallSites : Collections.EMPTY_LIST;

        String reason = callSite.getReason();
        asString = callSite.getMethod().toString() + " " + (reason != null ? reason : "");

        childCache = new TreeNode[callSites.size()];
    }

    public CallSite callSite() {
        return callSite;
    }

    @Override
    public TreeNode getChildAt(int childIndex) {
        if (childCache[childIndex] == null) {
            CallSite cs = callSites.get(childIndex);
            childCache[childIndex] = new JITNode(cs, this);
        }
        return childCache[childIndex];
    }

    @Override
    public int getChildCount() {
        return callSites.size();
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    // cached version
    @Override
    public TreeNode[] getPath() {
        if (pathCache == null)
            pathCache = super.getPath();
        return pathCache;
    }

    // TODO should be cached locally in function
    @Override
    public Enumeration children() {
        List<TreeNode> childNodes = new ArrayList<>(callSites.size());
        for (int i = 0; i < callSites.size(); i++) {
            childNodes.add(getChildAt(i));
        }
        return Collections.enumeration(childNodes);
    }

    // TODO should be cached locally in function
    public List<JITNode> allChildNodes() {
        List<JITNode> childs = new ArrayList<>();
        Enumeration e = breadthFirstEnumeration();
        while (e.hasMoreElements()) {
            childs.add((JITNode) e.nextElement());
        }
        return childs;
    }

    @Override
    public String toString() {
        return asString;
    }
}

class DelayedDocumentListener implements DocumentListener {
    private final Timer timer;
    private DocumentEvent lastEvent;

    public DelayedDocumentListener(DocumentListener delegate) {
        this(delegate, 500);
    }

    public DelayedDocumentListener(final DocumentListener delegate, int delay) {
        timer = new Timer(delay, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                timer.stop();
                if (lastEvent.getType() == DocumentEvent.EventType.INSERT) {
                    delegate.insertUpdate(lastEvent);
                } else if (lastEvent.getType() == DocumentEvent.EventType.REMOVE) {
                    delegate.removeUpdate(lastEvent);
                } else {
                    delegate.changedUpdate(lastEvent);
                }
            }
        });
    }

    private void storeUpdate(DocumentEvent e) {
        lastEvent = e;
        timer.restart();
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        storeUpdate(e);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        storeUpdate(e);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        storeUpdate(e);
    }
}

public class JITTree extends JFrame {

    private final JTextField searchField = new JTextField();
    private final JTree tree = new JTree(new Object[0]);
    private List<JITNode> allNodes = Collections.EMPTY_LIST;
    private JITTreeCellHighLighter cellRenderer = new JITTreeCellHighLighter();

    public JITTree() {
        super("JIT Tree");
        tree.setCellRenderer(cellRenderer);
        setLayout(new BorderLayout());
        add(searchField, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);
        setJMenuBar(buildMenuBar());
        setSize(500, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        searchField.getDocument().addDocumentListener(new DelayedDocumentListener(new DocumentListener() {
            private Set<TreeNode> matchingNodes = new HashSet<>();

            private void search() {
                String term = searchField.getText();
                if (term.isEmpty()) {
                    cellRenderer.setMatchingNodes(null);
                } else {
                    matchingNodes.clear();
                    System.out.println("Searching for " + term);
                    for (JITNode node : allNodes) {
                        if (node.toString().contains(term)) {
                            Collections.addAll(matchingNodes, node.getPath());
                        }
                    }
                    cellRenderer.setMatchingNodes(matchingNodes);
                }
                tree.repaint();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                search();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                search();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        }));
    }

    private void setRootNode(JITNode node) {
        allNodes = node.allChildNodes();
        tree.setModel(new DefaultTreeModel(node));
    }

    private JITNode loadFromFile(String fileName) throws Exception {
        List<LogEvent> events = LogParser.parse(fileName, true);
        Collections.sort(events, LogParser.sortByStart);

        List<CallSite> callSites = new ArrayList<>();

        for (LogEvent e : events) {
            if (e instanceof Compilation) {
                Compilation c = (Compilation) e;
                CallSite cs = c.getCall();
                cs.setMethod(c.getMethod());
                callSites.add(cs);
            }
        }

        Method m = new Method();
        m.setHolder("Main");
        m.setName("main");

        CallSite rootCallSite = new CallSite();
        rootCallSite.setMethod(m);
        rootCallSite.setCalls(callSites);

        return new JITNode(rootCallSite);
    }

    private void loadFile(String fileName) {
        try {
            JITNode rootNode = loadFromFile(fileName);
            setRootNode(rootNode);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, fileName + " is not parsable",
                    "Unable to load file", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem open = new JMenuItem("Open");
        file.add(open);
        open.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser(".");
                int state = chooser.showOpenDialog(JITTree.this);
                if (state == JFileChooser.APPROVE_OPTION) {
                    loadFile(chooser.getSelectedFile().toString());
                }
            }
        });
        menuBar.add(file);
        return menuBar;
    }

    public static void main(final String[] args) throws Exception {

        System.setProperty("apple.laf.useScreenMenuBar", "true");
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JITTree jitTree = new JITTree();
                if (args.length > 0) {
                    jitTree.loadFile(args[0]);
                }
                jitTree.setVisible(true);
            }
        });
    }
}
