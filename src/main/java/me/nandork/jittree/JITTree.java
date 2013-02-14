/*
 * Copyright 2013 Nandor Kracser (bonifaido@gmail.com)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
import java.awt.event.KeyEvent;
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
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

class JITTreeCellRenderer extends DefaultTreeCellRenderer {

    private Set<TreeNode> matchingNodes;

    public void setMatchingNodes(Set<TreeNode> matchingNodes) {
        this.matchingNodes = matchingNodes;
    }

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
}

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

class DelayedDocumentListener implements DocumentListener {

    private final Timer timer;
    private DocumentEvent lastEvent;

    public DelayedDocumentListener(DocumentListener delegate) {
        this(delegate, 400);
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
    private List<JITNode> allNodes = Collections.emptyList();
    private JITTreeCellRenderer cellRenderer = new JITTreeCellRenderer();

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
                String term = searchField.getText().toLowerCase();
                if (term.isEmpty()) {
                    cellRenderer.setMatchingNodes(null);
                } else {
                    matchingNodes.clear();
                    for (JITNode node : allNodes) {
                        if (node.toString().toLowerCase().contains(term)) {
                            // adding the node's path to enable the... path
                            Collections.addAll(matchingNodes, node.getPath());
                        }
                    }
                    cellRenderer.setMatchingNodes(matchingNodes);
                }
                tree.treeDidChange();
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

    @SuppressWarnings("unchecked")
    private void setRootNode(JITNode node) {
        allNodes = Collections.list(node.breadthFirstEnumeration());
        tree.setModel(new DefaultTreeModel(node));
        // triggers an update event in the document to re-filter the nodes
        searchField.setText(searchField.getText());
    }

    private JITNode parseFile(String fileName) throws Exception {
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
        m.setHolder("main");
        m.setName("main");

        CallSite rootCallSite = new CallSite();
        rootCallSite.setMethod(m);
        rootCallSite.setCalls(callSites);

        return new JITNode(rootCallSite);
    }

    public void loadFile(String fileName) {
        try {
            JITNode rootNode = parseFile(fileName);
            setRootNode(rootNode);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, fileName + " is not parseable",
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
        if (System.getProperty("os.name").contains("OS X")) {
            open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.META_MASK));
        } else {
            open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_MASK));
        }
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
