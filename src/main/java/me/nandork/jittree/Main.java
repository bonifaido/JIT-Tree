package me.nandork.jittree;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
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
