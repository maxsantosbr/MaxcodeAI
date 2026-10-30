package br.com.maxcodeai;

import static br.com.maxcodeai.view.Atualizador.VERSAO_ATUAL;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

public class MaxcodeAI extends JFrame {

    private static final Preferences PREFS
            = Preferences.userNodeForPackage(MaxcodeAI.class);

    private static final String THEME_KEY = "theme";

    private static final String API_URL
            = "http://localhost:11434/api/chat";

    private static final String MODEL = "qwen2.5-coder:7b";

    private static final File PASTA_DADOS
            = new File(System.getProperty("user.home"), ".maxcodeai");

    private static final File PASTA_CONVERSAS
            = new File(PASTA_DADOS, "conversations");

    private static final File ARQUIVO_HISTORICO_ANTIGO
            = new File(PASTA_DADOS, "historico.dat");

    private boolean contextMenuClick = false;

    private JPanel rootPanel;
    private JPanel sidebar;
    private JPanel topBar;
    private JPanel chatPanel;
    private JPanel messagesPanel;
    private JPanel composerPanel;

    private JTextArea inputArea;
    private JScrollPane messagesScroll;

    private JComboBox<String> themeSelector;

    private JButton sendButton;
    private JButton newChatButton;
    private JButton deleteChatButton;

    private JLabel statusLabel;
    private JLabel notice;
    private JLabel shortcutHint;
    private JLabel conversationTitleLabel;

    private JList<Conversation> conversationList;
    private DefaultListModel<Conversation> conversationModel;

    private JPanel thinkingCard;
    private JLabel thinkingLabel;
    private JLabel elapsedTimeLabel;
    private Component thinkingSpacer;

    private Timer thinkingTimer;
    private Timer elapsedTimer;

    private int thinkingDots;
    private long thinkingStartTime;

    private final List<ChatMessage> history = new ArrayList<>();

    private String currentConversationId;

    private boolean changingConversation;
    private boolean avisoInicialExibido;

    // AGENTE DE DESENVOLVIMENTO
    private SwingWorker<Void, String> workerDesenvolvimento;
    private volatile CicloDesenvolvimento agenteAtual;

    private Color backgroundColor;
    private Color sidebarColor;
    private Color panelColor;
    private Color textColor;
    private Color secondaryTextColor;
    private Color accentColor;
    private Color borderColor;

    // ---------------------------------------------------------
    // MODELOS INTERNOS
    // ---------------------------------------------------------
    private static class ChatMessage {

        final String role;
        final String content;

        ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private static class Conversation {

        final String id;
        String title;

        Conversation(String id, String title) {
            this.id = id;
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    // Ícone que desenha a imagem original em alta qualidade,
    // no tamanho exato da tela (inclusive com escala de 125% ou 150%).
    private static class IconeAltaQualidade implements Icon {

        private final Image imagem;
        private final int largura;
        private final int altura;

        IconeAltaQualidade(Image imagem, int largura, int altura) {
            this.imagem = imagem;
            this.largura = largura;
            this.altura = altura;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();

            try {
                g2.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(
                        RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                g2.drawImage(imagem, x, y, largura, altura, null);

            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return largura;
        }

        @Override
        public int getIconHeight() {
            return altura;
        }
    }

    // Reduz a imagem aos poucos (metade a cada passo) para evitar serrilhado.
    private static BufferedImage reduzirSuave(
            Image origem, int larguraAlvo, int alturaAlvo) {

        int largura = origem.getWidth(null);
        int altura = origem.getHeight(null);

        BufferedImage atual = new BufferedImage(
                largura, altura, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g0 = atual.createGraphics();
        g0.drawImage(origem, 0, 0, null);
        g0.dispose();

        while (largura > larguraAlvo || altura > alturaAlvo) {

            if (largura > larguraAlvo) {
                largura = Math.max(larguraAlvo, largura / 2);
            }

            if (altura > alturaAlvo) {
                altura = Math.max(alturaAlvo, altura / 2);
            }

            BufferedImage proxima = new BufferedImage(
                    largura, altura, BufferedImage.TYPE_INT_ARGB);

            Graphics2D g = proxima.createGraphics();

            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

            g.drawImage(atual, 0, 0, largura, altura, null);
            g.dispose();

            atual = proxima;
        }

        return atual;
    }

    // ---------------------------------------------------------
    // CONSTRUTOR
    // ---------------------------------------------------------
    public MaxcodeAI() {

        applyThemeLookAndFeel(PREFS.get(THEME_KEY, "Escuro"));

        setTitle("MAXCODE AI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 760);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        buildInterface();
        inicializarConversas();
        verificarConexao();

        //icone
        try (java.io.InputStream is
                = getClass().getResourceAsStream("/br/com/maxcodeai/imagens/icone.png")) {

            if (is != null) {
                java.awt.Image icone = javax.imageio.ImageIO.read(is);
                setIconImage(icone.getScaledInstance(
                        256,
                        256,
                        java.awt.Image.SCALE_SMOOTH
                ));
            } else {
                System.err.println("Ícone não encontrado: /br/com/maxcodeai/imagens/icone.png");
            }

        } catch (java.io.IOException e) {
        }
        //icone
    }

    // ---------------------------------------------------------
    // TEMAS
    // ---------------------------------------------------------
    private void applyThemeLookAndFeel(String theme) {

        try {
            switch (theme) {
                case "Claro":
                    FlatLightLaf.setup();
                    break;
                case "Darcula":
                    FlatDarculaLaf.setup();
                    break;
                case "IntelliJ":
                    FlatIntelliJLaf.setup();
                    break;
                default:
                    FlatDarkLaf.setup();
                    break;
            }
        } catch (Exception ex) {
            FlatDarkLaf.setup();
            ex.printStackTrace();
        }

        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 12);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ScrollBar.showButtons", false);
    }

    private void updatePalette() {

        backgroundColor = UIManager.getColor("Panel.background");
        panelColor = UIManager.getColor("TextField.background");
        textColor = UIManager.getColor("Label.foreground");

        secondaryTextColor
                = UIManager.getColor("Label.disabledForeground");

        accentColor = UIManager.getColor("Component.focusColor");
        borderColor = UIManager.getColor("Component.borderColor");

        if (backgroundColor == null) {
            backgroundColor = new Color(30, 32, 38);
        }

        if (panelColor == null) {
            panelColor = backgroundColor;
        }

        if (textColor == null) {
            textColor = Color.WHITE;
        }

        if (secondaryTextColor == null) {
            secondaryTextColor = Color.GRAY;
        }

        if (accentColor == null) {
            accentColor = new Color(102, 126, 234);
        }

        if (borderColor == null) {
            borderColor = Color.GRAY;
        }

        if ("Escuro".equals(PREFS.get(THEME_KEY, "Escuro"))) {
            backgroundColor = new Color(24, 26, 32);
            panelColor = new Color(34, 37, 45);
            accentColor = new Color(102, 126, 234);
            borderColor = new Color(55, 59, 70);
            textColor = new Color(235, 237, 242);
            secondaryTextColor = new Color(155, 160, 172);
        }

        sidebarColor = new Color(19, 21, 27);

        if (!"Escuro".equals(PREFS.get(THEME_KEY, "Escuro"))) {
            sidebarColor = backgroundColor;
        }
    }

    // ---------------------------------------------------------
    // INTERFACE
    // ---------------------------------------------------------
    private void buildInterface() {

        updatePalette();

        rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBackground(backgroundColor);

        buildSidebar();
        buildMainArea();

        rootPanel.add(sidebar, BorderLayout.WEST);
        rootPanel.add(chatPanel, BorderLayout.CENTER);

        setContentPane(rootPanel);
    }

    private void buildSidebar() {

        sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(245, 0));
        sidebar.setBackground(sidebarColor);
        sidebar.setBorder(new EmptyBorder(22, 14, 16, 14));

        System.out.println("Pasta de execução: "
                + System.getProperty("user.dir"));

        URL caminhoLogo = getClass().getResource(
                "/br/com/maxcodeai/imagens/logo-maxcodeai.png");

        System.out.println("Localizou a imagem: " + (caminhoLogo != null));

        JLabel logo;

        if (caminhoLogo != null) {

            ImageIcon imagemOriginal = new ImageIcon(caminhoLogo);

            logo = new JLabel(new IconeAltaQualidade(
                    reduzirSuave(imagemOriginal.getImage(), 204, 184),
                    102, 92));

        } else {

            logo = new JLabel("MAXCODE AI");
            logo.setFont(new Font("SansSerif", Font.BOLD, 21));
            logo.setForeground(accentColor);

            System.err.println(
                    "Logo não encontrada: "
                    + "/br/com/maxcodeai/imagens/logo-maxcodeai.png");
        }

        JPanel painelLogo = new JPanel(new BorderLayout());
        painelLogo.setOpaque(false);
        painelLogo.setAlignmentX(Component.LEFT_ALIGNMENT);
        painelLogo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));

        logo.setHorizontalAlignment(SwingConstants.CENTER);
        painelLogo.add(logo, BorderLayout.CENTER);

        JLabel subtitle = new JLabel("");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 11));
        subtitle.setForeground(secondaryTextColor);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        newChatButton = createSidebarButton("+  Nova conversa");
        newChatButton.addActionListener(e -> criarNovaConversa());

        JLabel conversationsLabel = new JLabel("SUAS CONVERSAS");
        conversationsLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        conversationsLabel.setForeground(secondaryTextColor);
        conversationsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        conversationModel = new DefaultListModel<>();
        conversationList = new JList<>(conversationModel);

        conversationList.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION);
        conversationList.setFont(new Font("SansSerif", Font.PLAIN, 12));
        conversationList.setBackground(sidebarColor);
        conversationList.setForeground(textColor);
        conversationList.setSelectionBackground(accentColor);
        conversationList.setSelectionForeground(Color.WHITE);
        conversationList.setFixedCellHeight(38);
        conversationList.setCellRenderer(new ConversationRenderer());

        conversationList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()
                    && !changingConversation
                    && !contextMenuClick) {

                Conversation selected = conversationList.getSelectedValue();

                if (selected != null
                        && !selected.id.equals(currentConversationId)) {
                    abrirConversa(selected);
                }
            }
        });

        // Menu de contexto da lista de conversas
        javax.swing.JPopupMenu menuConversas = new javax.swing.JPopupMenu();

        javax.swing.JMenuItem abrirItem
                = new javax.swing.JMenuItem("Abrir conversa");

        javax.swing.JMenuItem excluirItem
                = new javax.swing.JMenuItem("Excluir conversa");

        javax.swing.JMenuItem renomearItem
                = new javax.swing.JMenuItem("Renomear conversa");

// Ação: abrir a conversa selecionada
        abrirItem.addActionListener(e -> {
            Conversation selecionada = conversationList.getSelectedValue();

            if (selecionada != null) {
                abrirConversa(selecionada);
            }
        });

// Ação: excluir a conversa selecionada
        excluirItem.addActionListener(e -> {
            if (conversationList.getSelectedValue() != null) {
                excluirConversa();
            }
        });

        renomearItem.addActionListener(e -> renomearConversa());

// Monta as opções do menu
        menuConversas.add(abrirItem);
        menuConversas.add(renomearItem);
        menuConversas.addSeparator();
        menuConversas.add(excluirItem);

// Detecta o clique com o botão direito
        conversationList.addMouseListener(
                new java.awt.event.MouseAdapter() {

            private void mostrarMenu(java.awt.event.MouseEvent e) {

                if (!e.isPopupTrigger()) {
                    return;
                }

                int indice = conversationList.locationToIndex(e.getPoint());

                if (indice < 0) {
                    return;
                }

                java.awt.Rectangle limites
                        = conversationList.getCellBounds(indice, indice);

                // Só mostra o menu quando o clique ocorrer sobre uma conversa
                if (limites == null || !limites.contains(e.getPoint())) {
                    return;
                }

                // Seleciona a conversa sem abri-la automaticamente
                contextMenuClick = true;

                try {
                    conversationList.setSelectedIndex(indice);
                } finally {
                    contextMenuClick = false;
                }

                // Exibe o menu no local do clique
                menuConversas.show(
                        conversationList,
                        e.getX(),
                        e.getY()
                );
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                mostrarMenu(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                mostrarMenu(e);
            }
        });

        JScrollPane conversationScroll = new JScrollPane(conversationList);
        conversationScroll.setBorder(BorderFactory.createEmptyBorder());
        conversationScroll.getViewport().setBackground(sidebarColor);
        conversationScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        deleteChatButton = createSidebarButton("Excluir conversa");
        deleteChatButton.addActionListener(e -> excluirConversa());

        JButton projectsButton = createSidebarButton("Projetos Java");

        projectsButton.addActionListener(e -> {

            int resposta = JOptionPane.showConfirmDialog(
                    this,
                    "Deseja abrir a pasta onde os projetos Java estão salvos?\n\n"
                    + AgenteProgramador.getPastaProjetos(),
                    "Projetos Java",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (resposta == JOptionPane.YES_OPTION) {
                try {
                    File pastaProjetos = new File(
                            AgenteProgramador.getPastaProjetos().toString());

                    if (pastaProjetos.exists()
                            && Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(pastaProjetos);
                    } else {
                        JOptionPane.showMessageDialog(
                                this,
                                "A pasta não existe ou não é possível abri-la.",
                                "Aviso",
                                JOptionPane.WARNING_MESSAGE);
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(
                            this,
                            "Erro ao abrir a pasta:\n" + ex.getMessage(),
                            "Erro",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JButton filesButton = createSidebarButton("Arquivos");

        filesButton.addActionListener(e -> {

            File pastaArquivos = new File(
                    AgenteProgramador.getPastaProjetos().toString());

            int resposta = JOptionPane.showConfirmDialog(
                    this,
                    "Deseja abrir o local onde os arquivos estão salvos?\n\n"
                    + pastaArquivos.getAbsolutePath(),
                    "Arquivos",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (resposta == JOptionPane.YES_OPTION) {
                try {
                    if (pastaArquivos.exists()
                            && Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(pastaArquivos);
                    } else {
                        JOptionPane.showMessageDialog(
                                this,
                                "A pasta não existe ou não é possível abri-la.",
                                "Aviso",
                                JOptionPane.WARNING_MESSAGE);
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(
                            this,
                            "Erro ao abrir a pasta:\n" + ex.getMessage(),
                            "Erro",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JButton settingsButton = createSidebarButton("Configurações");

        settingsButton.addActionListener(e
                -> JOptionPane.showMessageDialog(
                        this,
                        "Modelo configurado: " + MODEL + "\n"
                        + "Servidor: " + API_URL,
                        "Configurações",
                        JOptionPane.INFORMATION_MESSAGE));

        JButton atualizarButton
                = createSidebarButton("Verificar atualizações");

        atualizarButton.addActionListener(e
                -> br.com.maxcodeai.view.Atualizador.abrir(this));

// CORREÇÃO: usa o método de criação de botão já existente.
        JButton aboutButton = createSidebarButton("Sobre");
        aboutButton.addActionListener(e -> mostrarSobre());

        JLabel localLabel = new JLabel("●  EXECUÇÃO LOCAL");
        localLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        localLabel.setForeground(new Color(45, 170, 110));
        localLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        statusLabel = new JLabel("Verificando Ollama...");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        statusLabel.setForeground(secondaryTextColor);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        sidebar.add(painelLogo);
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(subtitle);
        sidebar.add(Box.createVerticalStrut(24));
        sidebar.add(newChatButton);
        sidebar.add(Box.createVerticalStrut(22));
        sidebar.add(conversationsLabel);
        sidebar.add(Box.createVerticalStrut(8));

        conversationScroll.setMinimumSize(new Dimension(180, 100));
        conversationScroll.setPreferredSize(new Dimension(215, 300));
        conversationScroll.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        sidebar.add(conversationScroll);
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(deleteChatButton);
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(projectsButton);
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(filesButton);
        sidebar.add(Box.createVerticalStrut(5));

        sidebar.add(settingsButton);
        sidebar.add(Box.createVerticalStrut(5));

        sidebar.add(atualizarButton);
        sidebar.add(Box.createVerticalStrut(5));

        sidebar.add(aboutButton);
        sidebar.add(Box.createVerticalStrut(9));

        sidebar.add(localLabel);

        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(statusLabel);
    }

    private class ConversationRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {

            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);

            label.setBorder(new EmptyBorder(8, 9, 8, 5));
            label.setFont(new Font("SansSerif", Font.PLAIN, 12));
            label.setToolTipText(value == null ? null : value.toString());

            if (!isSelected) {
                label.setBackground(sidebarColor);
                label.setForeground(textColor);
            }

            return label;
        }
    }

    private JButton createSidebarButton(String text) {

        JButton button = new JButton(text);

        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        button.setPreferredSize(new Dimension(210, 38));
        button.setFont(new Font("SansSerif", Font.PLAIN, 12));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        return button;
    }

    private void buildMainArea() {

        chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(backgroundColor);

        topBar = new JPanel(new BorderLayout());
        topBar.setBackground(backgroundColor);
        topBar.setBorder(new EmptyBorder(14, 24, 14, 24));

        conversationTitleLabel = new JLabel("Nova conversa");
        conversationTitleLabel.setFont(
                new Font("SansSerif", Font.BOLD, 17));
        conversationTitleLabel.setForeground(textColor);

        JPanel themePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        themePanel.setOpaque(false);

        JLabel themeLabel = new JLabel("Tema:");
        themeLabel.setForeground(textColor);

        themeSelector = new JComboBox<>(
                new String[]{"Escuro", "Claro", "Darcula", "IntelliJ"});

        themeSelector.setSelectedItem(PREFS.get(THEME_KEY, "Escuro"));
        themeSelector.setPreferredSize(new Dimension(125, 34));

        themeSelector.addActionListener(e -> {
            String selected = (String) themeSelector.getSelectedItem();
            if (selected != null) {
                changeTheme(selected);
            }
        });

        themePanel.add(themeLabel);
        themePanel.add(themeSelector);

        topBar.add(conversationTitleLabel, BorderLayout.WEST);
        topBar.add(themePanel, BorderLayout.EAST);

        messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));
        messagesPanel.setBackground(backgroundColor);
        messagesPanel.setBorder(new EmptyBorder(22, 24, 22, 24));

        messagesScroll = new JScrollPane(messagesPanel);
        messagesScroll.setBorder(BorderFactory.createEmptyBorder());
        messagesScroll.getViewport().setBackground(backgroundColor);
        messagesScroll.getVerticalScrollBar().setUnitIncrement(16);

        composerPanel = new JPanel(new BorderLayout(0, 8));
        composerPanel.setBackground(backgroundColor);
        composerPanel.setBorder(new EmptyBorder(12, 24, 22, 24));

        inputArea = new JTextArea(3, 30);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        inputArea.setBorder(new EmptyBorder(10, 12, 10, 12));
        inputArea.setBackground(panelColor);
        inputArea.setForeground(textColor);
        inputArea.setCaretColor(textColor);

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createLineBorder(borderColor));
        inputScroll.setPreferredSize(new Dimension(500, 90));

        shortcutHint = new JLabel(
                "Enter para enviar  ·  Shift + Enter para nova linha");
        shortcutHint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        shortcutHint.setForeground(secondaryTextColor);
        shortcutHint.setBorder(new EmptyBorder(5, 3, 0, 3));
        shortcutHint.setVisible(false);

        JPanel inputContainer = new JPanel(new BorderLayout(0, 2));
        inputContainer.setOpaque(false);
        inputContainer.add(inputScroll, BorderLayout.CENTER);
        inputContainer.add(shortcutHint, BorderLayout.SOUTH);

        inputArea.getDocument().addDocumentListener(new DocumentListener() {

            private void atualizar() {
                shortcutHint.setVisible(
                        !inputArea.getText().trim().isEmpty());
                inputContainer.revalidate();
                inputContainer.repaint();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                atualizar();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                atualizar();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                atualizar();
            }
        });

        sendButton = new JButton("Enviar mensagem");
        sendButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        sendButton.setBackground(accentColor);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendButton.addActionListener(e -> sendMessage());

        InputMap inputMap = inputArea.getInputMap(JComponent.WHEN_FOCUSED);

        inputMap.put(KeyStroke.getKeyStroke("ENTER"), "sendMessage");
        inputMap.put(KeyStroke.getKeyStroke("shift ENTER"), "insertNewLine");
        inputMap.put(KeyStroke.getKeyStroke("control ENTER"), "sendMessage");

        inputArea.getActionMap().put("sendMessage", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                sendMessage();
            }
        });

        inputArea.getActionMap().put("insertNewLine", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                inputArea.replaceSelection("\n");
            }
        });

        notice = new JLabel(
                "IA local: as respostas são processadas no seu computador.");
        notice.setFont(new Font("SansSerif", Font.PLAIN, 11));
        notice.setForeground(secondaryTextColor);

        JPanel inputRow = new JPanel(new BorderLayout(10, 0));
        inputRow.setOpaque(false);
        inputRow.add(inputContainer, BorderLayout.CENTER);
        inputRow.add(sendButton, BorderLayout.EAST);

        composerPanel.add(inputRow, BorderLayout.CENTER);
        composerPanel.add(notice, BorderLayout.SOUTH);

        chatPanel.add(topBar, BorderLayout.NORTH);
        chatPanel.add(messagesScroll, BorderLayout.CENTER);
        chatPanel.add(composerPanel, BorderLayout.SOUTH);
    }

    // ---------------------------------------------------------
    // GERENCIAMENTO DAS CONVERSAS
    // ---------------------------------------------------------
    private void inicializarConversas() {

        if (!PASTA_CONVERSAS.exists() && !PASTA_CONVERSAS.mkdirs()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Não foi possível criar a pasta das conversas:\n"
                    + PASTA_CONVERSAS.getAbsolutePath(),
                    "MAXCODE AI",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        File[] arquivos = PASTA_CONVERSAS.listFiles(
                (dir, name) -> name.endsWith(".dat"));

        if ((arquivos == null || arquivos.length == 0)
                && ARQUIVO_HISTORICO_ANTIGO.isFile()) {
            migrarHistoricoAntigo();
        }

        carregarListaConversas();

        if (conversationModel.isEmpty()) {
            criarNovaConversa();
        } else {
            Conversation maisRecente = conversationModel.get(0);
            conversationList.setSelectedValue(maisRecente, true);
            abrirConversa(maisRecente);
        }
    }

    private void migrarHistoricoAntigo() {

        List<ChatMessage> mensagens = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(ARQUIVO_HISTORICO_ANTIGO),
                        StandardCharsets.UTF_8))) {

            String linha;

            while ((linha = reader.readLine()) != null) {

                int separador = linha.indexOf('\t');

                if (separador <= 0) {
                    continue;
                }

                String role = linha.substring(0, separador);
                String valor = linha.substring(separador + 1);

                if (!"user".equals(role) && !"assistant".equals(role)) {
                    continue;
                }

                try {
                    String conteudo = new String(
                            Base64.getDecoder().decode(valor),
                            StandardCharsets.UTF_8);

                    mensagens.add(new ChatMessage(role, conteudo));

                } catch (IllegalArgumentException ex) {
                    System.err.println("Mensagem antiga inválida ignorada.");
                }
            }

        } catch (IOException ex) {
            System.err.println("Erro na migração: " + ex.getMessage());
        }

        if (!mensagens.isEmpty()) {

            String id = UUID.randomUUID().toString();
            String titulo = obterTitulo(mensagens);
            File arquivo = arquivoConversa(id);

            if (gravarConversa(arquivo, titulo, mensagens)) {
                System.out.println("Histórico antigo migrado para: " + id);
            }
        }
    }

    private void carregarListaConversas() {

        conversationModel.clear();

        File[] arquivos = PASTA_CONVERSAS.listFiles(
                (dir, name) -> name.endsWith(".dat"));

        if (arquivos == null) {
            return;
        }

        List<Conversation> conversas = new ArrayList<>();

        for (File arquivo : arquivos) {

            String id = arquivo.getName().substring(
                    0, arquivo.getName().length() - 4);

            String titulo = lerTitulo(arquivo);
            conversas.add(new Conversation(id, titulo));
        }

        Collections.sort(conversas, new Comparator<Conversation>() {
            @Override
            public int compare(Conversation a, Conversation b) {
                return Long.compare(
                        arquivoConversa(b.id).lastModified(),
                        arquivoConversa(a.id).lastModified());
            }
        });

        for (Conversation conversa : conversas) {
            conversationModel.addElement(conversa);
        }
    }

    private void criarNovaConversa() {

        if (!sendButton.isEnabled()) {
            return;
        }

        String id = UUID.randomUUID().toString();
        Conversation conversa = new Conversation(id, "Nova conversa");

        history.clear();
        currentConversationId = id;

        if (!salvarConversaAtual()) {

            currentConversationId = null;

            JOptionPane.showMessageDialog(
                    this,
                    "Não foi possível salvar a nova conversa.",
                    "MAXCODE AI",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        changingConversation = true;
        conversationModel.insertElementAt(conversa, 0);
        conversationList.setSelectedValue(conversa, true);
        changingConversation = false;

        conversationTitleLabel.setText(conversa.title);
        mostrarBoasVindas();

        inputArea.setText("");
        inputArea.requestFocusInWindow();

        atualizarBotoesConversa();
    }

    private void abrirConversa(Conversation conversa) {

        if (!sendButton.isEnabled()) {
            selecionarConversaAtual();
            return;
        }

        if (currentConversationId != null) {
            salvarConversaAtual();
        }

        ocultarIndicadorPensando();

        currentConversationId = conversa.id;
        history.clear();

        carregarMensagens(conversa.id);

        conversationTitleLabel.setText(conversa.title);
        reconstruirMensagens();

        inputArea.setText("");

        atualizarBotoesConversa();
    }

    private void excluirConversa() {

        if (!sendButton.isEnabled()) {
            return;
        }

        Conversation selecionada = conversationList.getSelectedValue();

        if (selecionada == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Selecione uma conversa para excluir.",
                    "MAXCODE AI",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int resposta = JOptionPane.showConfirmDialog(
                this,
                "Deseja realmente excluir esta conversa?\n\n"
                + selecionada.title
                + "\n\nEssa ação não pode ser desfeita.",
                "Confirmar exclusão",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (resposta != JOptionPane.YES_OPTION) {
            return;
        }

        boolean eraAtual = selecionada.id.equals(currentConversationId);
        File arquivo = arquivoConversa(selecionada.id);

        if (arquivo.exists() && !arquivo.delete()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Não foi possível excluir o arquivo da conversa.",
                    "MAXCODE AI",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        changingConversation = true;
        conversationModel.removeElement(selecionada);
        changingConversation = false;

        if (eraAtual) {
            history.clear();
            currentConversationId = null;
        }

        if (conversationModel.isEmpty()) {
            criarNovaConversa();
        } else {
            Conversation proxima = conversationModel.get(0);
            conversationList.setSelectedValue(proxima, true);
            abrirConversa(proxima);
        }

        atualizarBotoesConversa();
    }

    private void renomearConversa() {

        if (!sendButton.isEnabled()) {
            return;
        }

        Conversation selecionada = conversationList.getSelectedValue();

        if (selecionada == null) {
            return;
        }

        String novoTitulo = JOptionPane.showInputDialog(
                this,
                "Digite o novo nome da conversa:",
                selecionada.title
        );

        if (novoTitulo == null) {
            return;
        }

        novoTitulo = novoTitulo.trim();

        if (novoTitulo.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "O nome da conversa não pode ficar vazio.",
                    "MAXCODE AI",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        if (novoTitulo.length() > 100) {
            JOptionPane.showMessageDialog(
                    this,
                    "O nome pode ter no máximo 100 caracteres.",
                    "MAXCODE AI",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        File arquivo = arquivoConversa(selecionada.id);

        if (!arquivo.isFile()) {
            JOptionPane.showMessageDialog(
                    this,
                    "O arquivo da conversa não foi encontrado.",
                    "MAXCODE AI",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        List<ChatMessage> mensagens;

        if (selecionada.id.equals(currentConversationId)) {
            mensagens = new ArrayList<>(history);
        } else {
            mensagens = new ArrayList<>();
            List<ChatMessage> historicoOriginal = new ArrayList<>(history);

            try {
                history.clear();

                if (!carregarMensagens(selecionada.id)) {
                    JOptionPane.showMessageDialog(
                            this,
                            "Não foi possível carregar as mensagens da conversa.",
                            "MAXCODE AI",
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }

                mensagens.addAll(history);

            } finally {
                history.clear();
                history.addAll(historicoOriginal);
            }
        }

        if (!gravarConversa(arquivo, novoTitulo, mensagens)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Não foi possível salvar o novo nome.",
                    "MAXCODE AI",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        selecionada.title = novoTitulo;

        int indice = conversationList.getSelectedIndex();

        if (indice >= 0) {
            conversationModel.setElementAt(selecionada, indice);
            conversationList.setSelectedIndex(indice);
        }

        if (selecionada.id.equals(currentConversationId)) {
            conversationTitleLabel.setText(novoTitulo);
        }

        JOptionPane.showMessageDialog(
                this,
                "Conversa renomeada com sucesso.",
                "MAXCODE AI",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void selecionarConversaAtual() {

        if (currentConversationId == null) {
            return;
        }

        for (int i = 0; i < conversationModel.size(); i++) {

            Conversation c = conversationModel.get(i);

            if (c.id.equals(currentConversationId)) {
                changingConversation = true;
                conversationList.setSelectedIndex(i);
                changingConversation = false;
                return;
            }
        }
    }

    private void atualizarBotoesConversa() {

        boolean habilitado = sendButton.isEnabled();

        newChatButton.setEnabled(habilitado);
        deleteChatButton.setEnabled(habilitado);
        conversationList.setEnabled(habilitado);
    }

    private File arquivoConversa(String id) {
        return new File(PASTA_CONVERSAS, id + ".dat");
    }

    private String lerTitulo(File arquivo) {

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(arquivo),
                        StandardCharsets.UTF_8))) {

            String linha = reader.readLine();

            if (linha != null && linha.startsWith("TITLE\t")) {
                return new String(
                        Base64.getDecoder().decode(linha.substring(6)),
                        StandardCharsets.UTF_8);
            }

        } catch (Exception ex) {
            System.err.println("Erro ao ler título: " + ex.getMessage());
        }

        return "Conversa";
    }

    private boolean carregarMensagens(String id) {

        File arquivo = arquivoConversa(id);

        if (!arquivo.isFile()) {
            return false;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(arquivo),
                        StandardCharsets.UTF_8))) {

            String linha;
            boolean primeiraLinha = true;

            while ((linha = reader.readLine()) != null) {

                if (primeiraLinha) {
                    primeiraLinha = false;

                    if (linha.startsWith("TITLE\t")) {
                        continue;
                    }
                }

                int separador = linha.indexOf('\t');

                if (separador <= 0) {
                    continue;
                }

                String role = linha.substring(0, separador);
                String valor = linha.substring(separador + 1);

                if (!"user".equals(role) && !"assistant".equals(role)) {
                    continue;
                }

                try {
                    String conteudo = new String(
                            Base64.getDecoder().decode(valor),
                            StandardCharsets.UTF_8);

                    history.add(new ChatMessage(role, conteudo));

                } catch (IllegalArgumentException ex) {
                    System.err.println("Mensagem inválida ignorada.");
                }
            }

            return true;

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Erro ao carregar conversa:\n" + ex.getMessage(),
                    "MAXCODE AI",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private boolean salvarConversaAtual() {

        if (currentConversationId == null) {
            return false;
        }

        if (!PASTA_CONVERSAS.exists() && !PASTA_CONVERSAS.mkdirs()) {
            return false;
        }

        File arquivo = arquivoConversa(currentConversationId);
        String titulo = obterTitulo(history);

        if (!gravarConversa(arquivo, titulo, history)) {
            return false;
        }

        atualizarTituloNaLista(currentConversationId, titulo);

        return true;
    }

    private boolean gravarConversa(
            File arquivo,
            String titulo,
            List<ChatMessage> mensagens) {

        File temporario = new File(
                arquivo.getParentFile(),
                arquivo.getName() + ".tmp");

        String tituloCodificado = Base64.getEncoder().encodeToString(
                titulo.getBytes(StandardCharsets.UTF_8));

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(temporario),
                        StandardCharsets.UTF_8))) {

            writer.write("TITLE\t" + tituloCodificado);
            writer.newLine();

            for (ChatMessage mensagem : mensagens) {

                String conteudoCodificado = Base64.getEncoder()
                        .encodeToString(
                                mensagem.content.getBytes(
                                        StandardCharsets.UTF_8));

                writer.write(mensagem.role);
                writer.write("\t");
                writer.write(conteudoCodificado);
                writer.newLine();
            }

        } catch (IOException ex) {
            System.err.println(
                    "Erro ao gravar conversa: " + ex.getMessage());
            return false;
        }

        try {
            Files.move(
                    temporario.toPath(),
                    arquivo.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            return true;

        } catch (IOException ex) {

            try {
                Files.move(
                        temporario.toPath(),
                        arquivo.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                return true;

            } catch (IOException ex2) {
                System.err.println(
                        "Erro ao finalizar arquivo: " + ex2.getMessage());
                temporario.delete();
                return false;
            }
        }
    }

    private String obterTitulo(List<ChatMessage> mensagens) {

        for (ChatMessage mensagem : mensagens) {

            if ("user".equals(mensagem.role)
                    && !mensagem.content.trim().isEmpty()) {

                String titulo = mensagem.content.trim()
                        .replace('\n', ' ')
                        .replace('\r', ' ');

                if (titulo.length() > 32) {
                    titulo = titulo.substring(0, 32).trim() + "...";
                }

                return titulo;
            }
        }

        return "Nova conversa";
    }

    private void atualizarTituloNaLista(String id, String titulo) {

        for (int i = 0; i < conversationModel.size(); i++) {

            Conversation conversa = conversationModel.getElementAt(i);

            if (conversa.id.equals(id)) {

                conversa.title = titulo;
                conversationModel.setElementAt(conversa, i);

                if (id.equals(currentConversationId)) {
                    conversationTitleLabel.setText(titulo);
                }

                return;
            }
        }
    }

    // ---------------------------------------------------------
    // MENSAGENS E INTEGRAÇÃO COM O OLLAMA
    // ---------------------------------------------------------
    private void mostrarBoasVindas() {

        messagesPanel.removeAll();

        addMessage(
                "MAXCODE AI",
                "Olá! Sou o MAXCODE AI, seu assistente de programação.\n\n"
                + "Você pode perguntar sobre Java, SQL, erros e "
                + "desenvolvimento de software.\n\n"
                + "Para criar um projeto automaticamente, use:\n"
                + "/programar NomeDoProjeto descrição do programa\n\n"
                + "Exemplo:\n"
                + "/programar CadastroClientes Criar um cadastro "
                + "de clientes com Java Swing.\n\n"
                + "Suas conversas são salvas localmente no computador.");
    }

    private void reconstruirMensagens() {

        messagesPanel.removeAll();

        if (history.isEmpty()) {
            mostrarBoasVindas();
            return;
        }

        for (ChatMessage mensagem : history) {
            String autor = "user".equals(mensagem.role)
                    ? "Você" : "MAXCODE AI";

            addMessage(autor, mensagem.content);
        }

        messagesPanel.revalidate();
        messagesPanel.repaint();

        rolarParaFinal();
    }

    private void verificarConexao() {

        SwingWorker<Boolean, Void> worker
                = new SwingWorker<Boolean, Void>() {

            @Override
            protected Boolean doInBackground() {

                HttpURLConnection conexao = null;

                try {
                    conexao = (HttpURLConnection) new URL(
                            "http://localhost:11434/api/tags")
                            .openConnection();

                    conexao.setConnectTimeout(3000);
                    conexao.setReadTimeout(3000);
                    conexao.setRequestMethod("GET");

                    return conexao.getResponseCode() == 200;

                } catch (Exception ex) {
                    return false;

                } finally {
                    if (conexao != null) {
                        conexao.disconnect();
                    }
                }
            }

            @Override
            protected void done() {

                try {
                    boolean conectado = get();

                    statusLabel.setText(
                            conectado
                                    ? "Modelo local disponível"
                                    : "Ollama não respondeu");

                    statusLabel.setForeground(
                            conectado
                                    ? new Color(45, 170, 110)
                                    : new Color(220, 100, 100));

                } catch (Exception ex) {
                    statusLabel.setText("Falha na conexão");
                }
            }
        };

        worker.execute();
    }

    // ---------------------------------------------------------
    // ENVIO DE MENSAGENS
    // ---------------------------------------------------------
    private void sendMessage() {

        if (!sendButton.isEnabled()) {
            return;
        }

        final String pergunta = inputArea.getText().trim();

        if (pergunta.isEmpty()) {
            return;
        }

        if (pergunta.startsWith("/programar ")) {
            iniciarDesenvolvimento(
                    pergunta.substring("/programar ".length()).trim(),
                    pergunta);
            return;
        }

        if (currentConversationId == null) {
            criarNovaConversa();
        }

        if (currentConversationId == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Não foi possível iniciar uma conversa.",
                    "MAXCODE AI",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        inputArea.setText("");

        addMessage("Você", pergunta);
        history.add(new ChatMessage("user", pergunta));
        salvarConversaAtual();

        sendButton.setEnabled(false);
        sendButton.setText("Pensando...");
        atualizarBotoesConversa();

        notice.setText(
                !avisoInicialExibido
                        ? "A primeira resposta pode demorar enquanto o modelo carrega."
                        : "A IA está preparando a resposta.");

        avisoInicialExibido = true;

        mostrarIndicadorPensando();

        SwingWorker<String, Void> worker
                = new SwingWorker<String, Void>() {

            @Override
            protected String doInBackground() throws Exception {
                return consultarOllama();
            }

            @Override
            protected void done() {

                try {
                    String resposta = get();

                    history.add(new ChatMessage("assistant", resposta));
                    salvarConversaAtual();
                    addMessage("MAXCODE AI", resposta);

                    statusLabel.setText("Modelo local disponível");
                    statusLabel.setForeground(new Color(45, 170, 110));

                } catch (Exception ex) {

                    Throwable causa = ex.getCause();

                    String detalhe = causa != null
                            ? causa.getMessage()
                            : ex.getMessage();

                    String erro
                            = "Não consegui obter uma resposta do Ollama.\n\n"
                            + "Detalhe: " + detalhe
                            + "\n\nVerifique se o Ollama está em execução "
                            + "e se o modelo " + MODEL
                            + " está instalado.";

                    addMessage("MAXCODE AI", erro);
                    history.add(new ChatMessage("assistant", erro));
                    salvarConversaAtual();

                    statusLabel.setText("Falha na resposta");
                    statusLabel.setForeground(new Color(220, 100, 100));

                } finally {
                    ocultarIndicadorPensando();

                    sendButton.setEnabled(true);
                    sendButton.setText("Enviar mensagem");

                    atualizarBotoesConversa();

                    notice.setText(
                            "IA local: as respostas são processadas "
                            + "no seu computador.");

                    inputArea.requestFocusInWindow();
                }
            }
        };

        worker.execute();
    }

    // ---------------------------------------------------------
    // AGENTE DE DESENVOLVIMENTO AUTOMÁTICO
    // ---------------------------------------------------------
    private void iniciarDesenvolvimento(
            final String pedido,
            final String mensagemOriginal) {

        if (pedido == null || pedido.trim().isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Informe o nome do projeto e o que deseja desenvolver.\n\n"
                    + "Exemplo:\n"
                    + "/programar CadastroClientes Criar um cadastro "
                    + "de clientes com Java Swing",
                    "MAXCODE AI",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] partes = pedido.split("\\s+", 2);
        final String nomeProjeto = partes[0];

        final String objetivo = partes.length > 1
                ? partes[1].trim()
                : "";

        if (objetivo.isEmpty()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Depois do nome do projeto, descreva "
                    + "o programa que deseja criar.",
                    "MAXCODE AI",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (currentConversationId == null) {
            criarNovaConversa();
        }

        if (currentConversationId == null) {
            return;
        }

        inputArea.setText("");

        addMessage("Você", mensagemOriginal);
        history.add(new ChatMessage("user", mensagemOriginal));
        salvarConversaAtual();

        sendButton.setEnabled(false);
        sendButton.setText("Desenvolvendo...");
        atualizarBotoesConversa();

        notice.setText("O agente está criando e compilando o projeto.");

        mostrarIndicadorPensando();

        workerDesenvolvimento = new SwingWorker<Void, String>() {

            private Path projetoCriado;

            @Override
            protected Void doInBackground() throws Exception {

                publish("[AGENTE] Preparando o projeto " + nomeProjeto + "...");

                projetoCriado = AgenteProgramador.criarProjeto(nomeProjeto);

                publish("[AGENTE] Pasta do projeto: " + projetoCriado);

                agenteAtual = new CicloDesenvolvimento(
                        new CicloDesenvolvimento.Ouvinte() {

                    @Override
                    public void aoAtualizar(String mensagem) {
                        publish("[ANDAMENTO] " + mensagem);
                    }

                    @Override
                    public void aoConcluir(String resultado) {
                        publish("[CONCLUÍDO] " + resultado);
                    }

                    @Override
                    public void aoPerguntar(String pergunta) {
                        publish("[PRECISA DE INFORMAÇÃO] " + pergunta);
                    }

                    @Override
                    public void aoFalhar(String erro) {
                        publish("[ERRO] " + erro);
                    }
                });

                agenteAtual.iniciar(projetoCriado, objetivo);

                return null;
            }

            @Override
            protected void process(List<String> mensagens) {

                for (String mensagem : mensagens) {
                    addMessage("MAXCODE AI", mensagem);
                    history.add(new ChatMessage("assistant", mensagem));
                }

                salvarConversaAtual();
            }

            @Override
            protected void done() {

                try {
                    get();

                    if (projetoCriado != null) {
                        String local = "Pasta do projeto:\n" + projetoCriado;

                        addMessage("MAXCODE AI", local);
                        history.add(new ChatMessage("assistant", local));
                    }

                } catch (Exception ex) {

                    Throwable causa = ex.getCause();

                    String detalhe = causa != null
                            ? causa.getMessage()
                            : ex.getMessage();

                    String erro = "Não foi possível executar o agente.\n"
                            + detalhe;

                    addMessage("MAXCODE AI", erro);
                    history.add(new ChatMessage("assistant", erro));

                } finally {

                    salvarConversaAtual();
                    ocultarIndicadorPensando();

                    sendButton.setEnabled(true);
                    sendButton.setText("Enviar mensagem");

                    atualizarBotoesConversa();

                    notice.setText(
                            "IA local: as respostas são processadas "
                            + "no seu computador.");

                    inputArea.requestFocusInWindow();

                    workerDesenvolvimento = null;
                    agenteAtual = null;
                }
            }
        };

        workerDesenvolvimento.execute();
    }

    // ---------------------------------------------------------
    // INDICADOR DE PROCESSAMENTO
    // ---------------------------------------------------------
    private void mostrarIndicadorPensando() {

        ocultarIndicadorPensando();

        thinkingCard = new JPanel();
        thinkingCard.setLayout(new BoxLayout(thinkingCard, BoxLayout.Y_AXIS));
        thinkingCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        thinkingCard.setBackground(panelColor);
        thinkingCard.setBorder(new EmptyBorder(12, 16, 12, 16));
        thinkingCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        JLabel author = new JLabel("MAXCODE AI");
        author.setFont(new Font("SansSerif", Font.BOLD, 13));
        author.setForeground(accentColor);
        author.setAlignmentX(Component.LEFT_ALIGNMENT);

        thinkingLabel = new JLabel("●  ●  ●   A IA está pensando...");
        thinkingLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        thinkingLabel.setForeground(secondaryTextColor);
        thinkingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        elapsedTimeLabel = new JLabel("Tempo de espera: 00:00");
        elapsedTimeLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        elapsedTimeLabel.setForeground(secondaryTextColor);
        elapsedTimeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        thinkingCard.add(author);
        thinkingCard.add(Box.createVerticalStrut(7));
        thinkingCard.add(thinkingLabel);
        thinkingCard.add(Box.createVerticalStrut(5));
        thinkingCard.add(elapsedTimeLabel);

        thinkingSpacer = Box.createVerticalStrut(14);

        messagesPanel.add(thinkingCard);
        messagesPanel.add(thinkingSpacer);

        thinkingDots = 0;
        thinkingStartTime = System.nanoTime();

        thinkingTimer = new Timer(400, e -> {

            if (thinkingLabel == null) {
                return;
            }

            thinkingDots = (thinkingDots + 1) % 4;

            StringBuilder pontos = new StringBuilder();

            for (int i = 0; i < thinkingDots; i++) {
                pontos.append("● ");
            }

            if (pontos.length() == 0) {
                pontos.append("●");
            }

            thinkingLabel.setText(pontos + "   A IA está pensando...");
        });

        thinkingTimer.start();

        atualizarTempoEspera();

        elapsedTimer = new Timer(1000, e -> atualizarTempoEspera());
        elapsedTimer.start();

        messagesPanel.revalidate();
        messagesPanel.repaint();

        rolarParaFinal();
    }

    private void atualizarTempoEspera() {

        if (elapsedTimeLabel == null || thinkingStartTime == 0L) {
            return;
        }

        long tempo = (System.nanoTime() - thinkingStartTime)
                / 1_000_000_000L;

        elapsedTimeLabel.setText(
                String.format(
                        "Tempo de espera: %02d:%02d",
                        tempo / 60,
                        tempo % 60));
    }

    private void ocultarIndicadorPensando() {

        if (thinkingTimer != null) {
            thinkingTimer.stop();
            thinkingTimer = null;
        }

        if (elapsedTimer != null) {
            elapsedTimer.stop();
            elapsedTimer = null;
        }

        if (messagesPanel != null) {

            if (thinkingCard != null) {
                messagesPanel.remove(thinkingCard);
            }

            if (thinkingSpacer != null) {
                messagesPanel.remove(thinkingSpacer);
            }

            messagesPanel.revalidate();
            messagesPanel.repaint();
        }

        thinkingCard = null;
        thinkingLabel = null;
        elapsedTimeLabel = null;
        thinkingSpacer = null;
        thinkingStartTime = 0L;
    }

    private void rolarParaFinal() {

        SwingUtilities.invokeLater(() -> {

            if (messagesScroll != null) {
                JScrollBar bar = messagesScroll.getVerticalScrollBar();
                bar.setValue(bar.getMaximum());
            }
        });
    }

    // ---------------------------------------------------------
    // COMUNICAÇÃO COM O OLLAMA
    // ---------------------------------------------------------
    private String consultarOllama() throws Exception {

        HttpURLConnection conexao = null;

        try {
            conexao = (HttpURLConnection) new URL(API_URL).openConnection();

            conexao.setRequestMethod("POST");
            conexao.setConnectTimeout(10000);
            conexao.setReadTimeout(300000);
            conexao.setDoOutput(true);

            conexao.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8");

            StringBuilder json = new StringBuilder();

            json.append("{\"model\":\"")
                    .append(MODEL)
                    .append("\",\"stream\":false,\"messages\":[");

            json.append("{\"role\":\"system\",\"content\":");

            json.append(jsonString(
                    "Você é o MAXCODE AI, um assistente de programação. "
                    + "Responda em português brasileiro, com clareza e exemplos "
                    + "práticos. Quando escrever código, use blocos Markdown "
                    + "com três crases e identifique a linguagem, por exemplo "
                    + "```java. Explique como usar o código."));

            json.append("}");

            for (ChatMessage msg : history) {
                json.append(",{\"role\":")
                        .append(jsonString(msg.role))
                        .append(",\"content\":")
                        .append(jsonString(msg.content))
                        .append("}");
            }

            json.append("]}");

            byte[] dados = json.toString().getBytes(StandardCharsets.UTF_8);

            try (OutputStream saida = conexao.getOutputStream()) {
                saida.write(dados);
            }

            int codigo = conexao.getResponseCode();

            InputStream fluxo = codigo >= 200 && codigo < 300
                    ? conexao.getInputStream()
                    : conexao.getErrorStream();

            String respostaJson;

            try (InputStream entrada = fluxo; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

                byte[] bloco = new byte[4096];
                int quantidade;

                while ((quantidade = entrada.read(bloco)) != -1) {
                    buffer.write(bloco, 0, quantidade);
                }

                respostaJson = new String(
                        buffer.toByteArray(),
                        StandardCharsets.UTF_8);
            }

            if (codigo < 200 || codigo >= 300) {
                throw new Exception(
                        "Ollama retornou HTTP " + codigo + ": " + respostaJson);
            }

            String conteudo = extrairConteudo(respostaJson);

            if (conteudo == null || conteudo.trim().isEmpty()) {
                throw new Exception(
                        "O Ollama respondeu, mas não retornou texto.");
            }

            return conteudo;

        } finally {
            if (conexao != null) {
                conexao.disconnect();
            }
        }
    }

    private String jsonString(String texto) {

        StringBuilder resultado = new StringBuilder("\"");

        for (int i = 0; i < texto.length(); i++) {

            char c = texto.charAt(i);

            switch (c) {
                case '"':
                    resultado.append("\\\"");
                    break;
                case '\\':
                    resultado.append("\\\\");
                    break;
                case '\b':
                    resultado.append("\\b");
                    break;
                case '\f':
                    resultado.append("\\f");
                    break;
                case '\n':
                    resultado.append("\\n");
                    break;
                case '\r':
                    resultado.append("\\r");
                    break;
                case '\t':
                    resultado.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        resultado.append(String.format("\\u%04x", (int) c));
                    } else {
                        resultado.append(c);
                    }
            }
        }

        return resultado.append('"').toString();
    }

    private String extrairConteudo(String json) throws Exception {

        int messagePos = json.indexOf("\"message\"");

        if (messagePos < 0) {
            throw new Exception("Campo 'message' ausente na resposta.");
        }

        int contentPos = json.indexOf("\"content\"", messagePos);

        if (contentPos < 0) {
            throw new Exception("Campo 'content' ausente na resposta.");
        }

        int inicio = json.indexOf(':', contentPos) + 1;

        while (inicio < json.length()
                && Character.isWhitespace(json.charAt(inicio))) {
            inicio++;
        }

        if (inicio >= json.length() || json.charAt(inicio) != '"') {
            throw new Exception("Formato inesperado na resposta.");
        }

        inicio++;

        StringBuilder texto = new StringBuilder();
        boolean escape = false;

        for (int i = inicio; i < json.length(); i++) {

            char c = json.charAt(i);

            if (escape) {

                switch (c) {
                    case '"':
                        texto.append('"');
                        break;
                    case '\\':
                        texto.append('\\');
                        break;
                    case '/':
                        texto.append('/');
                        break;
                    case 'b':
                        texto.append('\b');
                        break;
                    case 'f':
                        texto.append('\f');
                        break;
                    case 'n':
                        texto.append('\n');
                        break;
                    case 'r':
                        texto.append('\r');
                        break;
                    case 't':
                        texto.append('\t');
                        break;
                    case 'u':
                        if (i + 4 >= json.length()) {
                            throw new Exception("Escape Unicode incompleto.");
                        }

                        texto.append((char) Integer.parseInt(
                                json.substring(i + 1, i + 5), 16));

                        i += 4;
                        break;
                    default:
                        texto.append(c);
                }

                escape = false;

            } else if (c == '\\') {
                escape = true;

            } else if (c == '"') {
                return texto.toString();

            } else {
                texto.append(c);
            }
        }

        throw new Exception("Resposta JSON incompleta.");
    }

    // ---------------------------------------------------------
    // EXIBIÇÃO DE MENSAGENS E CÓDIGO
    // ---------------------------------------------------------
    private void addMessage(String author, String message) {

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBackground(panelColor);
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100000));

        JLabel authorLabel = new JLabel(author);
        authorLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        authorLabel.setForeground(accentColor);
        authorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(authorLabel);
        card.add(Box.createVerticalStrut(10));

        String[] linhas = message.split("\n", -1);

        StringBuilder normal = new StringBuilder();
        StringBuilder codigo = new StringBuilder();

        String linguagem = "";
        boolean dentroCodigo = false;

        for (String linha : linhas) {

            if (linha.trim().startsWith("```")) {

                if (!dentroCodigo) {

                    adicionarTextoNormal(card, normal);
                    normal.setLength(0);

                    dentroCodigo = true;
                    linguagem = linha.trim().substring(3).trim().toLowerCase();
                    codigo.setLength(0);

                } else {

                    adicionarBlocoCodigo(card, codigo.toString(), linguagem);

                    codigo.setLength(0);
                    linguagem = "";
                    dentroCodigo = false;
                }

            } else if (dentroCodigo) {

                if (codigo.length() > 0) {
                    codigo.append('\n');
                }

                codigo.append(linha);

            } else {

                if (normal.length() > 0) {
                    normal.append('\n');
                }

                normal.append(linha);
            }
        }

        if (dentroCodigo) {
            adicionarBlocoCodigo(card, codigo.toString(), linguagem);
        }

        adicionarTextoNormal(card, normal);

        messagesPanel.add(card);
        messagesPanel.add(Box.createVerticalStrut(14));

        messagesPanel.revalidate();
        messagesPanel.repaint();

        rolarParaFinal();
    }

    private void adicionarTextoNormal(
            JPanel destino,
            StringBuilder conteudo) {

        String texto = conteudo.toString().trim();

        if (texto.isEmpty()) {
            return;
        }

        JTextArea area = new JTextArea(texto);

        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setForeground(textColor);
        area.setFont(new Font("SansSerif", Font.PLAIN, 13));
        area.setBorder(BorderFactory.createEmptyBorder());
        area.setAlignmentX(Component.LEFT_ALIGNMENT);

        area.setRows(Math.min(25, Math.max(1, area.getLineCount())));
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1000));

        destino.add(area);
        destino.add(Box.createVerticalStrut(8));
    }

    private void adicionarBlocoCodigo(
            JPanel destino,
            String codigo,
            String linguagem) {

        JPanel codePanel = new JPanel(new BorderLayout(0, 6));
        codePanel.setBackground(new Color(25, 28, 35));

        codePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(65, 70, 82)),
                new EmptyBorder(8, 8, 8, 8)));

        codePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        codePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 500));

        JLabel languageLabel = new JLabel(
                linguagem.isEmpty() ? "Código" : linguagem.toUpperCase());

        languageLabel.setForeground(new Color(180, 190, 210));
        languageLabel.setFont(new Font("SansSerif", Font.BOLD, 11));

        JButton copyButton = new JButton("Copiar código");
        copyButton.setFont(new Font("SansSerif", Font.PLAIN, 11));
        copyButton.setFocusPainted(false);
        copyButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        copyButton.addActionListener(e -> {

            try {
                Toolkit.getDefaultToolkit()
                        .getSystemClipboard()
                        .setContents(new StringSelection(codigo), null);

                copyButton.setText("Copiado!");

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Não foi possível copiar o código.",
                        "MAXCODE AI",
                        JOptionPane.WARNING_MESSAGE);
            }
        });

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(languageLabel, BorderLayout.WEST);
        header.add(copyButton, BorderLayout.EAST);

        RSyntaxTextArea codeArea = new RSyntaxTextArea();

        codeArea.setText(codigo);
        codeArea.setEditable(false);
        codeArea.setCodeFoldingEnabled(true);
        codeArea.setAntiAliasingEnabled(true);
        codeArea.setTabSize(4);
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        codeArea.setBackground(new Color(25, 28, 35));
        codeArea.setForeground(new Color(225, 230, 240));
        codeArea.setCaretColor(Color.WHITE);
        codeArea.setSyntaxEditingStyle(determinarSintaxe(linguagem));

        RTextScrollPane scroll = new RTextScrollPane(codeArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        scroll.setPreferredSize(new Dimension(
                650,
                Math.min(
                        350,
                        Math.max(65, codigo.split("\n", -1).length * 19 + 15))));

        codePanel.add(header, BorderLayout.NORTH);
        codePanel.add(scroll, BorderLayout.CENTER);

        destino.add(codePanel);
        destino.add(Box.createVerticalStrut(10));
    }

    private String determinarSintaxe(String linguagem) {

        if (linguagem == null) {
            return SyntaxConstants.SYNTAX_STYLE_NONE;
        }

        switch (linguagem.toLowerCase()) {
            case "java":
                return SyntaxConstants.SYNTAX_STYLE_JAVA;
            case "sql":
                return SyntaxConstants.SYNTAX_STYLE_SQL;
            case "xml":
            case "html":
                return SyntaxConstants.SYNTAX_STYLE_XML;
            case "json":
                return SyntaxConstants.SYNTAX_STYLE_JSON;
            case "javascript":
            case "js":
                return SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            case "python":
            case "py":
                return SyntaxConstants.SYNTAX_STYLE_PYTHON;
            case "css":
                return SyntaxConstants.SYNTAX_STYLE_CSS;
            case "php":
                return SyntaxConstants.SYNTAX_STYLE_PHP;
            case "bash":
            case "sh":
                return SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL;
            case "c":
                return SyntaxConstants.SYNTAX_STYLE_C;
            case "cpp":
            case "c++":
                return SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS;
            default:
                return SyntaxConstants.SYNTAX_STYLE_NONE;
        }
    }

    // ---------------------------------------------------------
    // ALTERAÇÃO DOS TEMAS
    // ---------------------------------------------------------
    private void changeTheme(String theme) {

        if (theme == null
                || theme.equals(PREFS.get(THEME_KEY, "Escuro"))) {
            return;
        }

        PREFS.put(THEME_KEY, theme);

        applyThemeLookAndFeel(theme);
        updatePalette();

        SwingUtilities.updateComponentTreeUI(this);

        rootPanel.setBackground(backgroundColor);
        sidebar.setBackground(sidebarColor);
        chatPanel.setBackground(backgroundColor);
        topBar.setBackground(backgroundColor);
        messagesPanel.setBackground(backgroundColor);

        messagesScroll.getViewport().setBackground(backgroundColor);
        composerPanel.setBackground(backgroundColor);

        conversationList.setBackground(sidebarColor);
        conversationList.setForeground(textColor);

        inputArea.setBackground(panelColor);
        inputArea.setForeground(textColor);
        inputArea.setCaretColor(textColor);

        shortcutHint.setForeground(secondaryTextColor);
        notice.setForeground(secondaryTextColor);
        conversationTitleLabel.setForeground(textColor);

        atualizarCoresMensagens();
        updateComposerColors();

        if (thinkingCard != null) {
            thinkingCard.setBackground(panelColor);
            thinkingLabel.setForeground(secondaryTextColor);
            elapsedTimeLabel.setForeground(secondaryTextColor);
        }

        revalidate();
        repaint();
    }

    private void atualizarCoresMensagens() {

        for (Component componente : messagesPanel.getComponents()) {

            if (!(componente instanceof JPanel)) {
                continue;
            }

            JPanel card = (JPanel) componente;
            card.setBackground(panelColor);

            for (Component filho : card.getComponents()) {

                if (filho instanceof JLabel) {
                    filho.setForeground(accentColor);

                } else if (filho instanceof JTextArea) {
                    filho.setForeground(textColor);
                }
            }
        }
    }

    private void updateComposerColors() {

        notice.setForeground(secondaryTextColor);
        shortcutHint.setForeground(secondaryTextColor);

        sendButton.setBackground(accentColor);
        sendButton.setForeground(Color.WHITE);
    }

    // ---------------------------------------------------------
    // INICIALIZAÇÃO
    // ---------------------------------------------------------
    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            MaxcodeAI app = new MaxcodeAI();
            app.setVisible(true);
        });
    }

    // ---------------------------------------------------------
    // JANELA SOBRE
    // ---------------------------------------------------------
    private void mostrarSobre() {

        JDialog dialog = new JDialog(this, "Sobre", true);

        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        JPanel painel = new JPanel(new BorderLayout(10, 15));
        painel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        painel.setBackground(UIManager.getColor("Panel.background"));

        // Logotipo
        JLabel logoLabel;

        URL recurso = getClass().getResource(
                "/br/com/maxcodeai/imagens/logo-maxcodeai.png");

        if (recurso != null) {

            ImageIcon icone = new ImageIcon(recurso);

            logoLabel = new JLabel(new IconeAltaQualidade(
                    reduzirSuave(icone.getImage(), 180, 162),
                    90, 81));

        } else {

            logoLabel = new JLabel(
                    "MAXCODE AI",
                    SwingConstants.CENTER);

            logoLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        }

        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Informações do software
        JLabel titulo = new JLabel("Maxcode AI", SwingConstants.CENTER);
        titulo.setFont(new Font("SansSerif", Font.BOLD, 18));

        JLabel versao = new JLabel("Versão " + VERSAO_ATUAL, SwingConstants.CENTER);

        JLabel descricao = new JLabel(
                "<html><div style='text-align:center; padding: 5px;'>"
                + "Seu Assistente inteligente.<br>"
                + "</div></html>",
                SwingConstants.CENTER);

        descricao.setFont(new Font("SansSerif", Font.PLAIN, 13));
        descricao.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

        JLabel desenvolvedor = new JLabel(
                "Desenvolvido por Maxwell Souza dos Santos",
                SwingConstants.CENTER);

        JLabel cnpj = new JLabel(
                "CNPJ: 54.718.287/0001-19",
                SwingConstants.CENTER);

        JPanel informacoes = new JPanel(new GridLayout(0, 1, 0, 1));
        informacoes.setOpaque(false);

        informacoes.add(titulo);
        informacoes.add(versao);
        informacoes.add(descricao);
        informacoes.add(desenvolvedor);
        informacoes.add(cnpj);

        JPanel superior = new JPanel(new BorderLayout(0, 12));
        superior.setOpaque(false);
        superior.add(logoLabel, BorderLayout.NORTH);
        superior.add(informacoes, BorderLayout.CENTER);

        JButton fechar = new JButton("Fechar");
        fechar.addActionListener(e -> dialog.dispose());

        JPanel rodape = new JPanel();
        rodape.setOpaque(false);
        rodape.add(fechar);

        painel.add(superior, BorderLayout.CENTER);
        painel.add(rodape, BorderLayout.SOUTH);

        dialog.setContentPane(painel);
        dialog.setSize(480, 350);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
}
