package me.nandork.jittree;

import com.sun.hotspot.tools.compiler.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

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
        if (isOSX()) {
            open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.META_MASK));
        } else {
            open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_MASK));
        }
        menuBar.add(file);
        return menuBar;
    }

    private boolean isOSX() {
        return System.getProperty("os.name").contains("OS X");
    }
}
