package de.neemann.assembler.gui;

import de.neemann.assembler.asm.InstructionException;
import de.neemann.assembler.asm.Opcode;
import de.neemann.assembler.asm.Program;
import de.neemann.assembler.asm.formatter.AsmFormatter;
import de.neemann.assembler.asm.formatter.HexFormatter;
import de.neemann.assembler.expression.ExpressionException;
import de.neemann.assembler.gui.utils.*;
import de.neemann.assembler.parser.Macro;
import de.neemann.assembler.parser.Parser;
import de.neemann.assembler.parser.ParserException;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.prefs.Preferences;

/**
 * Main frame of the assembler GUI
 * <p>
 * Created by hneemann on 17.06.14.
 */
public class Main extends JFrame implements ClosingWindowListener.ConfirmSave, AddressListener {
    /**
     * Used to highlight the actual line
     */
    public static final Highlighter.HighlightPainter HIGHLIGHT_PAINTER = new DefaultHighlighter.DefaultHighlightPainter(Color.CYAN);

    private static final String MESSAGE = "ASM 3\n\n"
            + "Simple assembler to create a hex file for a\n"
            + "simple simulated 16 bit processor.\n\n"
            + "Written by H. Neemann in 2015.";

    private static final Preferences PREFS = Preferences.userRoot().node("dt_asm3");
    private static final int MAX_HELP_COLS = 70;
    private final JTextArea source;
    private final ArrayList<AddressListener> addressListeners = new ArrayList<>();
    private File filename;
    private File lastFilename;
    private String sourceOnDisk = "";
    private Program runningProgram;

    /**
     * Creates a new main frame
     *
     * @param fileToOpen the file to open
     */
    public Main(File fileToOpen) {
        super("ASM 3");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setIconImages(IconCreator.createImages("asm32.png", "asm64.png", "asm128.png"));

        addWindowListener(new ClosingWindowListener(this, this));

        ToolTipAction newFile = new ToolTipAction("New", IconCreator.create("document-new.png")) {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                if (ClosingWindowListener.checkForSave(Main.this, Main.this)) {
                    sourceOnDisk = "";
                    source.setText(sourceOnDisk);
                    runningProgram = null;
                    setFilename(null);
                    notifyInvalidateCode();
                }
            }
        }.setToolTip("creates a new file");

        ToolTipAction open = new ToolTipAction("Open", IconCreator.create("document-open.png")) {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                if (ClosingWindowListener.checkForSave(Main.this, Main.this)) {
                    JFileChooser fc = getjFileChooser();
                    if (fc.showOpenDialog(Main.this) == JFileChooser.APPROVE_OPTION) {
                        try {
                            load(fc.getSelectedFile());
                        } catch (IOException e) {
                            new ErrorMessage("Error loading a file").addCause(e).show(Main.this);
                        }
                    }
                }
            }
        }.setToolTip("Opens a file.");

        ToolTipAction openNew = new ToolTipAction("Open in New Window", IconCreator.create("document-open-new.png")) {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                if (ClosingWindowListener.checkForSave(Main.this, Main.this)) {
                    JFileChooser fc = getjFileChooser();
                    if (fc.showOpenDialog(Main.this) == JFileChooser.APPROVE_OPTION) {
                        new Main(fc.getSelectedFile()).setVisible(true);
                    }
                }
            }
        }.setToolTip("Opens a file in a new Window");

        ToolTipAction save = new ToolTipAction("Save", IconCreator.create("document-save.png")) {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    save();
                } catch (IOException e) {
                    new ErrorMessage("Error storing a file").addCause(e).show(Main.this);
                }
            }
        }.setToolTip("Saves the file to disk.");

        ToolTipAction saveAs = new ToolTipAction("Save As", IconCreator.create("document-save-as.png")) {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    saveAs();
                } catch (IOException e) {
                    new ErrorMessage("Error storing a file").addCause(e).show(Main.this);
                }
            }
        }.setToolTip("Saves the file with a new name to disk.");

        ToolTipAction build = new ToolTipAction("Build", IconCreator.create("preferences.png")) {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    Program program = createProgram();
                    if (program != null)
                        writeHex(program, filename);
                } catch (Throwable e) {
                    new ErrorMessage("Error").addCause(e).show(Main.this);
                }
            }
        }.setToolTip("Converts the source to a hex file.");

        ToolTipAction show = new ToolTipAction("Show Listing", IconCreator.create("listing.png")) {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    Program program;
                    if (runningProgram != null)
                        program = runningProgram;
                    else
                        program = createProgram();
                    if (program != null) {
                        writeHex(program, filename);

                        ByteArrayOutputStream text = new ByteArrayOutputStream();
                        final AsmFormatter asmFormatter = new AsmFormatter(new PrintStream(text, false, "utf-8"));
                        program.traverse(asmFormatter);
                        new ListDialog(Main.this, "Listing", text.toString("utf-8"), asmFormatter.getAddrToLineMap()).setVisible(true);
                    }
                } catch (Throwable e) {
                    new ErrorMessage("Error").addCause(e).show(Main.this);
                }
            }
        }.setToolTip("Converts the source to a listing and shows it.");

        ToolTipAction saveLst = new ToolTipAction("Save Listing") {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    Program program = createProgram();
                    if (program != null)
                        writeLst(program, filename);
                } catch (Throwable e) {
                    new ErrorMessage("Error").addCause(e).show(Main.this);
                }
            }
        }.setToolTip("Converts the source to a listing and writes it to disk.");

        ToolTipAction tabToSpace = new ToolTipAction("Tabs to Spaces") {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                source.setText(new TabToSpaces(source.getText(), source.getTabSize()).convert());
            }
        }.setToolTip("Converts tabs to spaces");

        ToolTipAction helpOpcodes = new ToolTipAction("Show help") {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    TextFormatter tf = new TextFormatter(MAX_HELP_COLS);
                    tf.append("Mnemonics\n\n");
                    for (Opcode op : Opcode.values())
                        tf.append(op.toString()).append("\n\n");
                    tf.append("Macros\n\n");
                    for (Macro m : Parser.getMacros())
                        tf.append(m.toString()).append("\n\n");
                    tf.append(Parser.HELP).append("\n");
                    new ListDialog(Main.this, "Instructions", tf.toString(), (HashMap<Integer, Integer>) null).setVisible(true);
                } catch (Throwable e) {
                    new ErrorMessage("Error").addCause(e).show(Main.this);
                }
            }
        }.setToolTip("Shows a short description of available opcodes.");

        final RemoteInterface remoteInterface = new RemoteInterface();

        ToolTipAction remoteStart = new ToolTipAction("Run", IconCreator.create("media-playback-start.png")) {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    runningProgram = null;
                    Program program = createProgram();
                    if (program != null) {
                        File hex = writeHex(program, filename);
                        if (hex != null) {
                            remoteInterface.start(hex);
                            notifyInvalidateCode();
                        }
                    }
                } catch (Throwable e) {
                    new ErrorMessage("Error").addCause(e).show(Main.this);
                }
            }
        }.setToolTip("Run the progam.");

        ToolTipAction remoteDebug = new ToolTipAction("Debug", IconCreator.create("debug.png")) {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    runningProgram = createProgram();
                    if (runningProgram != null) {
                        File hex = writeHex(runningProgram, filename);
                        if (hex != null) {
                            remoteInterface.debug(hex);
                            notifyInvalidateCode();
                            notifyCodeAddressChange(0);
                        }
                    }
                } catch (Throwable e) {
                    new ErrorMessage("Error").addCause(e).show(Main.this);
                }
            }
        }.setToolTip("Debugs the program.");

        ToolTipAction remoteRun = new ToolTipAction("Run to BRK", IconCreator.create("media-skip-forward.png")) {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    notifyCodeAddressChange(remoteInterface.run());
                } catch (RemoteException e) {
                    new ErrorMessage("Error").addCause(e).show(Main.this);
                }
            }
        }.setToolTip("Run to next BRK command.");
        ToolTipAction remoteStep = new ToolTipAction("Step", IconCreator.create("media-seek-forward.png")) {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    notifyCodeAddressChange(remoteInterface.step());
                } catch (RemoteException e) {
                    new ErrorMessage("Error").addCause(e).show(Main.this);
                }
            }
        }.setToolTip("Single clock step");
        ToolTipAction remoteStop = new ToolTipAction("Stop", IconCreator.create("media-playback-stop.png")) {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                try {
                    remoteInterface.stop();
                    runningProgram = null;
                    notifyInvalidateCode();
                } catch (RemoteException e) {
                    new ErrorMessage("Error").addCause(e).show(Main.this);
                }
            }
        }.setToolTip("Stops the programm.");

        source = new JTextArea(40, 50);
        source.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        if (fileToOpen == null) {
            String n = PREFS.get("name", null);
            if (n != null)
                fileToOpen = new File(n);
        }
        if (fileToOpen != null)
            try {
                load(fileToOpen);
            } catch (IOException e) {
                new ErrorMessage("Error loading a file").addCause(e).show(Main.this);
            }

        JScrollPane scrollPane = new JScrollPane(source);
        scrollPane.setRowHeaderView(new TextLineNumber(source, 3));
        getContentPane().add(scrollPane);


        JMenu file = new JMenu("File");
        file.add(newFile.createJMenuItem());
        file.add(open.createJMenuItem());
        file.add(openNew.createJMenuItem());
        file.add(save.createJMenuItem());
        file.add(saveAs.createJMenuItem());

        JMenu assemble = new JMenu("ASM");
        assemble.add(build.createJMenuItem());
        assemble.add(show.createJMenuItem());
        //assemble.add(showLight.createJMenuItem());
        assemble.add(saveLst.createJMenuItem());
        assemble.add(tabToSpace.createJMenuItem());

        JMenu help = new JMenu("Help");
        help.add(helpOpcodes.createJMenuItem());
        help.add(new JMenuItem(new AbstractAction("About") {
            @Override
            public void actionPerformed(ActionEvent e) {
                InfoDialog.getInstance().showInfo(Main.this, MESSAGE);
            }
        }));

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(file);
        menuBar.add(assemble);
        menuBar.add(help);
        setJMenuBar(menuBar);

        JToolBar toolBar = new JToolBar();
        toolBar.add(open.createJButtonNoText());
        toolBar.add(openNew.createJButtonNoText());
        toolBar.add(save.createJButtonNoText());
        toolBar.add(build.createJButtonNoText());
        toolBar.addSeparator();
        toolBar.add(remoteStart.createJButtonNoText());
        toolBar.add(remoteStop.createJButtonNoText());
        toolBar.addSeparator();
        toolBar.add(remoteDebug.createJButtonNoText());
        toolBar.add(remoteStep.createJButtonNoText());
        toolBar.add(remoteRun.createJButtonNoText());
        getContentPane().add(toolBar, BorderLayout.NORTH);

        pack();

        setLocationRelativeTo(null);

        addAddrListener(this);
    }

    private void notifyCodeAddressChange(int addr) {
        for (AddressListener l : addressListeners)
            l.setCodeAddress(addr);
    }

    private void notifyInvalidateCode() {
        for (AddressListener l : addressListeners)
            l.invalidateCode();
    }

    @Override
    public void setCodeAddress(int codeAddress) {
        if (runningProgram != null && codeAddress >= 0) {
            int line = runningProgram.getLineByAddr(codeAddress);
            if (line > 0) {
                try {
                    int l = line - 1;
                    int lineStart = source.getLineStartOffset(l);
                    int lineEnd = source.getLineEndOffset(l);
                    source.getHighlighter().removeAllHighlights();
                    source.getHighlighter().addHighlight(lineStart, lineEnd, HIGHLIGHT_PAINTER);
                    source.setCaretPosition(lineStart);
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void invalidateCode() {
        source.getHighlighter().removeAllHighlights();
    }

    private JFileChooser getjFileChooser() {
        File parent = null;

        if (filename != null)
            parent = filename.getParentFile();
        else {
            if (lastFilename != null)
                parent = lastFilename.getParentFile();
        }

        JFileChooser fc = new JFileChooser(parent);
        fc.setFileFilter(new FileNameExtensionFilter("Assembler files", "asm"));
        return fc;
    }

    private Program createProgram() throws ExpressionException, InstructionException, IOException, ParserException {
        if (save()) {
            try (Parser p = new Parser(filename)) {
                return p.parseProgram()
                        .optimizeAndLink();
            }
        }
        return null;
    }

    private boolean save() throws IOException {
        if (filename == null)
            return saveAs();
        else {
            writeSource(filename);
            return true;
        }
    }

    private boolean saveAs() throws IOException {
        JFileChooser fc = getjFileChooser();
        if (fc.showSaveDialog(Main.this) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".asm"))
                file = new File(file.getParentFile(), file.getName() + ".asm");
            writeSource(file);
            return true;
        }
        return false;
    }

    private void writeSource(File file) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), "utf-8")) {
            String text = this.source.getText();
            w.write(text);
            sourceOnDisk = text;
            setFilename(file);
        }
    }

    static private File writeHex(Program p, File name) throws IOException, ExpressionException {
        if (name != null) {
            File f = makeFilename(name, ".asm", ".hex");
            try (PrintStream ps = new PrintStream(f, "utf-8")) {
                p.traverse(new HexFormatter(ps));
            }
            return f;
        }
        return null;
    }

    static private void writeLst(Program p, File name) throws IOException, ExpressionException {
        if (name != null) {
            File f = makeFilename(name, ".asm", ".lst");
            try (PrintStream ps = new PrintStream(f, "utf-8")) {
                p.traverse(new AsmFormatter(ps));
            }
        }
    }

    private static File makeFilename(File f, String origExt, String newExt) {
        String name = f.getName();
        if (name.endsWith(origExt)) {
            String newName = name.substring(0, name.length() - origExt.length()) + newExt;
            return new File(f.getParentFile(), newName);
        } else {
            return new File(f.getParentFile(), name + newExt);
        }
    }

    private void load(File file) throws IOException {
        try (Reader in = new InputStreamReader(new FileInputStream(file), "utf-8")) {
            StringBuilder sb = new StringBuilder();

            int c;
            while ((c = in.read()) >= 0)
                sb.append((char) c);

            sourceOnDisk = sb.toString();
            source.setText(sourceOnDisk);
            setFilename(file);
            notifyInvalidateCode();
            runningProgram = null;
        }

    }

    private void setFilename(File file) {
        lastFilename = filename;
        this.filename = file;
        if (file == null)
            setTitle("ASM 3");
        else {
            setTitle("[" + file.getName() + "] ASM 3");
            PREFS.put("name", file.toString());
        }
    }

    /**
     * The main method
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    Main m = new Main(null);
                    m.setVisible(true);
                }
            });
        } else {
            try {
                File file = new File(args[0]);
                try (Parser p = new Parser(file)) {
                    Program prog = p.parseProgram().optimizeAndLink();
                    writeHex(prog, file);
                    writeLst(prog, file);
                }
            } catch (Throwable e) {
                System.out.println(e.getClass().getSimpleName() + ": " + e.getMessage());
                System.exit(1);
            }
        }

    }

    @Override
    public boolean isStateChanged() {
        return !source.getText().equals(sourceOnDisk);
    }

    @Override
    public void saveChanges() {
        try {
            save();
        } catch (IOException e) {
            new ErrorMessage("Error storing a file").addCause(e).show(Main.this);
        }
    }

    /**
     * Adds a address listener
     *
     * @param listener the listener to add
     */
    public void addAddrListener(AddressListener listener) {
        addressListeners.add(listener);
    }

    /**
     * Removes a address listener
     *
     * @param listener the listener to add
     */
    public void removeAddrListener(AddressListener listener) {
        addressListeners.remove(listener);
    }
}
