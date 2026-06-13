package io.github.sodalisretro.deepseek;

import com.sun.lwuit.Button;
import com.sun.lwuit.CheckBox;
import com.sun.lwuit.Command;
import com.sun.lwuit.Container;
import com.sun.lwuit.Dialog;
import com.sun.lwuit.Display;
import com.sun.lwuit.Form;
import com.sun.lwuit.Label;
import com.sun.lwuit.TextArea;
import com.sun.lwuit.TextField;
import com.sun.lwuit.events.ActionEvent;
import com.sun.lwuit.events.ActionListener;
import com.sun.lwuit.html.HTMLComponent;
import com.sun.lwuit.layouts.BorderLayout;
import com.sun.lwuit.layouts.BoxLayout;
import java.io.IOException;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.midlet.MIDlet;

public class DeepSeekMIDlet extends MIDlet implements ActionListener, Runnable {

    private Form chatForm;
    private Form settingsForm;
    private Container chatContainer;
    private TextField inputField;
    private Dialog promptDialog;
    private TextArea promptArea;
    private TextField hostField;
    private TextField portField;
    private TextField roundsField;
    private com.sun.lwuit.ComboBox searchList;
    private CheckBox searchCheck;

    private Command exitCommand;
    private Command settingsCommand;
    private Command prevCommand;
    private Command nextCommand;
    private Command inputCommand;
    private Command saveCommand;
    private Command settingsBackCommand;
    private Command promptCommand;
    private Command promptOkCommand;
    private Command promptBackCommand;
    private Command promptResetCommand;

    private HttpClient httpClient;
    private Vector messages;
    private Vector inputHistory;
    private int historyPos;

    private String currentUserMessage;
    private boolean running;
    private boolean webSearch;
    private boolean currentSearch;
    private int maxSearchRounds;
    private String systemPrompt;
    private static final int MAX_HISTORY = 10;
    private static final int MAX_FORM_ITEMS = 30;
    private static final int MAX_INPUT_HISTORY = 20;

    public DeepSeekMIDlet() {
        System.out.println("[MIDlet] LWUIT constructor start");
        Display.init(this);

        messages = new Vector();
        inputHistory = new Vector();
        historyPos = -1;
        running = false;
        webSearch = Settings.getWebSearch();
        maxSearchRounds = Settings.getMaxSearchRounds();
        systemPrompt = Settings.getSystemPrompt();

        httpClient = new HttpClient(Settings.getProxyUrl());

        chatForm = new Form(I18n.get(I18n.TITLE_IDLE));
        chatForm.setLayout(new BorderLayout());
        chatForm.setScrollable(false);

        chatContainer = new Container(new BoxLayout(BoxLayout.Y_AXIS));
        chatContainer.setScrollableY(true);
        chatContainer.setFocusable(true);
        chatContainer.setNextFocusDown(chatContainer);
        chatContainer.setNextFocusUp(chatContainer);
        chatForm.addComponent(BorderLayout.CENTER, chatContainer);

        chatContainer.addComponent(new Label(" " + I18n.get(I18n.CHAT_HINT)));

        // Bottom input bar: Send button + TextField, Web checkbox below
        Container bottomBar = new Container(new BoxLayout(BoxLayout.Y_AXIS));
        Container inputRow = new Container(new BoxLayout(BoxLayout.X_AXIS));
        final Button sendButton = new Button(I18n.get(I18n.CMD_SEND));
        sendButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                doSend();
            }
        });
        sendButton.getSelectedStyle().setBorder(
            com.sun.lwuit.plaf.Border.createLineBorder(2, 0xFFFFFF));
        inputField = new TextField("", 30);
        inputField.setHint(I18n.get(I18n.INPUT_TITLE));
        inputField.setNextFocusUp(chatContainer);
        inputField.setNextFocusDown(sendButton);
        sendButton.setNextFocusUp(inputField);
        sendButton.setNextFocusLeft(sendButton);
        inputField.getSelectedStyle().setBorder(
            com.sun.lwuit.plaf.Border.createLineBorder(2, 0xFFFFFF));
        inputRow.addComponent(sendButton);
        inputRow.addComponent(inputField);
        searchCheck = new CheckBox(I18n.get(I18n.SETTINGS_SEARCH));
        searchCheck.setSelected(webSearch);
        searchCheck.getSelectedStyle().setBorder(
            com.sun.lwuit.plaf.Border.createLineBorder(2, 0xFFFFFF));
        bottomBar.addComponent(inputRow);
        bottomBar.addComponent(searchCheck);
        chatForm.addComponent(BorderLayout.SOUTH, bottomBar);

        exitCommand = new Command(I18n.get(I18n.CMD_EXIT));
        settingsCommand = new Command(I18n.get(I18n.CMD_SETTINGS));
        prevCommand = new Command(I18n.get(I18n.CMD_PREV));
        nextCommand = new Command(I18n.get(I18n.CMD_NEXT));
        inputCommand = new Command(I18n.get(I18n.INPUT_TITLE));
        chatForm.addCommand(exitCommand);
        chatForm.addCommand(settingsCommand);
        chatForm.addCommand(prevCommand);
        chatForm.addCommand(nextCommand);
        chatForm.addCommand(inputCommand);
        chatForm.addCommandListener(this);

        // OK/fire key jumps to input bar
        chatForm.addGameKeyListener(Display.GAME_FIRE, new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                inputField.requestFocus();
            }
        });

        settingsForm = new Form(I18n.get(I18n.SETTINGS_TITLE));
        settingsForm.setLayout(new BoxLayout(BoxLayout.Y_AXIS));
        hostField = new TextField(Settings.getHost());
        hostField.setHint(I18n.get(I18n.SETTINGS_HOST));
        hostField.setMaxSize(100);
        hostField.setConstraint(TextField.URL);
        portField = new TextField(Settings.getPort());
        portField.setHint(I18n.get(I18n.SETTINGS_PORT));
        portField.setMaxSize(6);
        portField.setConstraint(TextField.NUMERIC);
        String[] searchOptions = {
            I18n.get(I18n.SETTINGS_YES),
            I18n.get(I18n.SETTINGS_NO)
        };
        searchList = new com.sun.lwuit.ComboBox(searchOptions);
        searchList.setSelectedIndex(webSearch ? 0 : 1);
        roundsField = new TextField(String.valueOf(maxSearchRounds));
        roundsField.setHint(I18n.get(I18n.SETTINGS_MAX_ROUNDS));
        roundsField.setMaxSize(2);
        roundsField.setConstraint(TextField.NUMERIC);
        settingsForm.addComponent(new Label(" " + I18n.get(I18n.SETTINGS_HOST)));
        settingsForm.addComponent(hostField);
        settingsForm.addComponent(new Label(" " + I18n.get(I18n.SETTINGS_PORT)));
        settingsForm.addComponent(portField);
        settingsForm.addComponent(new Label(" " + I18n.get(I18n.SETTINGS_SEARCH)));
        settingsForm.addComponent(searchList);
        settingsForm.addComponent(new Label(" " + I18n.get(I18n.SETTINGS_MAX_ROUNDS)));
        settingsForm.addComponent(roundsField);
        saveCommand = new Command(I18n.get(I18n.CMD_SAVE));
        settingsBackCommand = new Command(I18n.get(I18n.CMD_BACK));
        promptCommand = new Command(I18n.get(I18n.CMD_SET_PROMPT));
        settingsForm.addCommand(saveCommand);
        settingsForm.addCommand(settingsBackCommand);
        settingsForm.addCommand(promptCommand);
        settingsForm.addCommandListener(this);

        promptArea = new TextArea(systemPrompt, 10, 30);
        promptDialog = new Dialog(I18n.get(I18n.SETTINGS_PROMPT));
        promptDialog.setLayout(new BorderLayout());
        promptDialog.addComponent(BorderLayout.CENTER, promptArea);
        promptOkCommand = new Command(I18n.get(I18n.CMD_SAVE));
        promptBackCommand = new Command(I18n.get(I18n.CMD_BACK));
        promptResetCommand = new Command(I18n.get(I18n.CMD_RESET));
        promptDialog.addCommand(promptOkCommand);
        promptDialog.addCommand(promptBackCommand);
        promptDialog.addCommand(promptResetCommand);
        promptDialog.addCommandListener(this);

        chatForm.show();
        System.out.println("[MIDlet] LWUIT constructor done");
    }

    public void startApp() {}

    public void pauseApp() {}

    public void destroyApp(boolean unconditional) {}

    private void doSend() {
        if (running) return;
        String text = inputField.getText();
        if (text == null || text.length() == 0) return;
        currentSearch = searchCheck.isSelected();
        addInputHistory(text);
        inputField.setText("");
        historyPos = -1;
        sendMessage(text);
    }

    public void actionPerformed(ActionEvent evt) {
        Command cmd = evt.getCommand();

        if (cmd == promptOkCommand) {
            String text = promptArea.getText();
            systemPrompt = text;
            Settings.saveSystemPrompt(text);
            appendChat(I18n.get(I18n.CHAT_SYSTEM), I18n.get(I18n.PROMPT_SAVED));

        } else if (cmd == promptResetCommand) {
            systemPrompt = I18n.get(I18n.SYSTEM_PROMPT);
            Settings.saveSystemPrompt("");
            promptArea.setText(systemPrompt);
            appendChat(I18n.get(I18n.CHAT_SYSTEM), I18n.get(I18n.PROMPT_RESET));

        } else if (cmd == promptBackCommand) {
            promptArea.setText(systemPrompt);

        } else if (cmd == saveCommand) {
            String host = hostField.getText().trim();
            String port = portField.getText().trim();
            if (host.length() > 0 && port.length() > 0) {
                Settings.save(host, port);
                httpClient.setProxyUrl(Settings.getProxyUrl());
                boolean newSearch = searchList.getSelectedIndex() == 0;
                boolean changed = (newSearch != webSearch);
                if (changed) {
                    webSearch = newSearch;
                    Settings.saveWebSearch(newSearch);
                    searchCheck.setSelected(webSearch);
                }
                int rounds = parseInt(roundsField.getText());
                if (rounds >= 1 && rounds <= 99) {
                    maxSearchRounds = rounds;
                    Settings.saveMaxSearchRounds(rounds);
                }
                appendChat(I18n.get(I18n.CHAT_SYSTEM), I18n.get(I18n.SETTINGS_SAVED));
                if (changed) {
                    appendChat(I18n.get(I18n.CHAT_SYSTEM),
                        I18n.get(I18n.SETTINGS_SEARCH) + " [" +
                        I18n.get(webSearch ? I18n.SETTINGS_YES : I18n.SETTINGS_NO) + "]");
                }
            }
            chatForm.show();

        } else if (cmd == settingsBackCommand) {
            hostField.setText(Settings.getHost());
            portField.setText(Settings.getPort());
            searchList.setSelectedIndex(Settings.getWebSearch() ? 0 : 1);
            roundsField.setText(String.valueOf(Settings.getMaxSearchRounds()));
            chatForm.show();

        } else if (cmd == promptCommand) {
            promptArea.setText(systemPrompt);
            promptDialog.show();

        } else if (cmd == settingsCommand) {
            hostField.setText(Settings.getHost());
            portField.setText(Settings.getPort());
            searchList.setSelectedIndex(webSearch ? 0 : 1);
            roundsField.setText(String.valueOf(maxSearchRounds));
            settingsForm.show();

        } else if (cmd == prevCommand) {
            navigateHistory(true);

        } else if (cmd == nextCommand) {
            navigateHistory(false);

        } else if (cmd == inputCommand) {
            inputField.requestFocus();

        } else if (cmd == exitCommand) {
            if (inputField.hasFocus()) {
                notifyDestroyed();
            } else {
                inputField.requestFocus();
            }
        }
    }

    private void addInputHistory(String text) {
        for (int i = 0; i < inputHistory.size(); i++) {
            if (text.equals((String) inputHistory.elementAt(i))) {
                inputHistory.removeElementAt(i);
                break;
            }
        }
        inputHistory.addElement(text);
        if (inputHistory.size() > MAX_INPUT_HISTORY) {
            inputHistory.removeElementAt(0);
        }
    }

    private void navigateHistory(boolean prev) {
        int size = inputHistory.size();
        if (size == 0) return;
        if (historyPos == -1) {
            historyPos = prev ? size - 1 : 0;
        } else {
            if (prev) {
                historyPos--;
                if (historyPos < 0) historyPos = size - 1;
            } else {
                historyPos++;
                if (historyPos >= size) historyPos = 0;
            }
        }
        inputField.setText((String) inputHistory.elementAt(historyPos));
    }

    private void sendMessage(String text) {
        currentUserMessage = text;
        String label = currentSearch
            ? (I18n.get(I18n.CHAT_YOU) + " [" + I18n.get(I18n.SETTINGS_SEARCH) + "]")
            : I18n.get(I18n.CHAT_YOU);
        appendChat(label, currentUserMessage);
        addHistoryMessage("user", currentUserMessage);
        chatForm.setTitle(I18n.get(I18n.TITLE_THINKING));
        running = true;
        new Thread(this).start();
    }

    public void run() {
        String response = null;
        try {
            String requestBody = buildRequestBody(currentUserMessage);
            response = httpClient.post(requestBody);
        } catch (IOException e) {
            String errMsg = e.getMessage();
            if (errMsg == null) errMsg = e.toString();
            response = "{\"error\":{\"message\":\"" + escapeJson(errMsg) + "\"}}";
        } catch (Exception e) {
            String errMsg = e.getMessage();
            if (errMsg == null) errMsg = e.toString();
            response = "{\"error\":{\"message\":\"" + escapeJson(errMsg) + "\"}}";
        }
        final String finalResponse = response;
        Display.getInstance().callSerially(new Runnable() {
            public void run() {
                handleResponse(finalResponse);
                running = false;
            }
        });
    }

    private void handleResponse(String response) {
        if (response == null) {
            appendChat(I18n.get(I18n.CHAT_ERROR), I18n.get(I18n.ERR_NO_RESPONSE));
            chatForm.setTitle(I18n.get(I18n.TITLE_ERROR));
            return;
        }
        try {
            Object parsed = JsonParser.parse(response);
            if (parsed instanceof Hashtable) {
                Hashtable root = (Hashtable) parsed;
                Object error = root.get("error");
                if (error instanceof Hashtable) {
                    String errMsg = (String) ((Hashtable) error).get("message");
                    appendChat(I18n.get(I18n.CHAT_ERROR), errMsg != null ? errMsg : I18n.get(I18n.ERR_UNKNOWN));
                    chatForm.setTitle(I18n.get(I18n.TITLE_ERROR));
                    return;
                }
                Object choices = root.get("choices");
                if (choices instanceof Vector) {
                    Vector choiceList = (Vector) choices;
                    if (choiceList.size() > 0) {
                        Object choice = choiceList.elementAt(0);
                        if (choice instanceof Hashtable) {
                            Object message = ((Hashtable) choice).get("message");
                            if (message instanceof Hashtable) {
                                String content = (String) ((Hashtable) message).get("content");
                                if (content != null) {
                                    appendHtmlChat("DeepSeek", content);
                                    addHistoryMessage("assistant", content);
                                    chatForm.setTitle(I18n.get(I18n.TITLE_IDLE));
                                    return;
                                }
                            }
                        }
                    }
                }
            }
            appendChat(I18n.get(I18n.CHAT_ERROR), I18n.get(I18n.ERR_PARSE_FAIL));
            chatForm.setTitle(I18n.get(I18n.TITLE_PARSE_ERR));
        } catch (Exception e) {
            appendChat(I18n.get(I18n.CHAT_ERROR), e.toString());
            chatForm.setTitle(I18n.get(I18n.TITLE_ERROR));
        }
    }

    private void appendChat(String role, String text) {
        appendFormItem(role + " [" + nowTime() + "]:\n" + text);
    }

    private void appendHtmlChat(String role, String html) {
        String header = role + " [" + nowTime() + "]:";
        appendHtmlItem(header, html);
    }

    private String nowTime() {
        Calendar cal = Calendar.getInstance();
        StringBuffer sb = new StringBuffer(8);
        int h = cal.get(Calendar.HOUR_OF_DAY);
        if (h < 10) sb.append('0');
        sb.append(h);
        sb.append(':');
        int m = cal.get(Calendar.MINUTE);
        if (m < 10) sb.append('0');
        sb.append(m);
        sb.append(':');
        int s = cal.get(Calendar.SECOND);
        if (s < 10) sb.append('0');
        sb.append(s);
        return sb.toString();
    }

    private int parseInt(String s) {
        if (s == null || s.length() == 0) return 0;
        int val = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                val = val * 10 + (c - '0');
            }
        }
        return val;
    }

    private void appendFormItem(final String text) {
        Runnable r = new Runnable() {
            public void run() {
                while (chatContainer.getComponentCount() >= MAX_FORM_ITEMS) {
                    chatContainer.removeComponent(
                        chatContainer.getComponentAt(
                            chatContainer.getComponentCount() - 1));
                }
                chatContainer.addComponent(0, createMessageLabel("\n" + text));
                chatForm.revalidate();
            }
        };
        if (Display.getInstance().isEdt()) {
            r.run();
        } else {
            Display.getInstance().callSerially(r);
        }
    }

    private TextArea createMessageLabel(String text) {
        TextArea ta = new TextArea(text);
        ta.setEditable(false);
        ta.setFocusable(false);
        ta.setUIID("Label");
        return ta;
    }

    private void appendHtmlItem(final String header, final String html) {
        Runnable r = new Runnable() {
            public void run() {
                while (chatContainer.getComponentCount() >= MAX_FORM_ITEMS) {
                    chatContainer.removeComponent(
                        chatContainer.getComponentAt(
                            chatContainer.getComponentCount() - 1));
                }
                Container item = new Container(new BoxLayout(BoxLayout.Y_AXIS));
                item.getStyle().setMargin(0, 0, 1, 1);
                Label headerLabel = new Label(" " + header);
                item.addComponent(headerLabel);
                HTMLComponent htmlComp = new HTMLComponent();
                htmlComp.setShowImages(false);
                // TODO: Image support — replace with custom DocumentRequestHandler
                // that async-downloads and scales images to fit screen width.
                // For now images are stripped at proxy level (server.js).
                try {
                    htmlComp.setBodyText(html);
                    item.addComponent(htmlComp);
                    chatContainer.addComponent(0, item);
                    chatForm.revalidate();
                } catch (Exception e) {
                    TextArea fallback = new TextArea(html);
                    fallback.setEditable(false);
                    fallback.setFocusable(false);
                    item.addComponent(fallback);
                    chatContainer.addComponent(0, item);
                    chatForm.revalidate();
                }
            }
        };
        if (Display.getInstance().isEdt()) {
            r.run();
        } else {
            Display.getInstance().callSerially(r);
        }
    }

    private void addDirectly(String role, String text) {
        while (chatContainer.getComponentCount() >= MAX_FORM_ITEMS) {
            chatContainer.removeComponent(
                chatContainer.getComponentAt(
                    chatContainer.getComponentCount() - 1));
        }
        chatContainer.addComponent(0, createMessageLabel(
            "\n" + role + " [" + nowTime() + "]:\n" + text));
    }

    private String buildRequestBody(String userMessage) {
        StringBuffer sb = new StringBuffer();
        sb.append("{\"model\":\"deepseek-chat\",\"messages\":[");

        sb.append("{\"role\":\"system\",\"content\":\"");
        sb.append(escapeJson(systemPrompt));
        sb.append("\"}");

        for (int i = 0; i < messages.size(); i++) {
            sb.append(",");
            sb.append((String) messages.elementAt(i));
        }

        sb.append(",{\"role\":\"user\",\"content\":\"");
        sb.append(escapeJson(userMessage));
        sb.append("\"}");

        sb.append("],\"stream\":false");

        if (currentSearch) {
            sb.append(",\"web_search\":true");
            sb.append(",\"max_search_rounds\":").append(maxSearchRounds);
        }

        sb.append("}");
        return sb.toString();
    }

    private void addHistoryMessage(String role, String content) {
        StringBuffer sb = new StringBuffer();
        sb.append("{\"role\":\"");
        sb.append(role);
        sb.append("\",\"content\":\"");
        sb.append(escapeJson(content));
        sb.append("\"}");
        messages.addElement(sb.toString());
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        StringBuffer sb = new StringBuffer();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else sb.append(c);
        }
        return sb.toString();
    }
}
