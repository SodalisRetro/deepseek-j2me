package io.github.sodalisretro.deepseek;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;

public class DeepSeekMIDlet extends MIDlet implements CommandListener, Runnable {

    private Display display;
    private Form chatForm;
    private Form settingsForm;
    private TextBox inputBox;
    private StringItem chatLogItem;
    private TextField hostField;
    private TextField portField;
    private Command sendCommand;
    private Command exitCommand;
    private Command settingsCommand;
    private Command okCommand;
    private Command backCommand;
    private Command prevCommand;
    private Command nextCommand;
    private Command saveCommand;
    private Command settingsBackCommand;

    private HttpClient httpClient;
    private Vector messages;
    private StringBuffer chatLog;
    private Vector inputHistory;
    private int historyPos;

    private String currentUserMessage;
    private boolean running;
    private static final int MAX_HISTORY = 10;
    private static final int MAX_LOG_CHARS = 4000;
    private static final int MAX_INPUT_HISTORY = 20;

    public DeepSeekMIDlet() {
        System.out.println("[MIDlet] constructor start");
        display = Display.getDisplay(this);
        messages = new Vector();
        chatLog = new StringBuffer();
        chatLog.append(I18n.get(I18n.CHAT_HINT));
        inputHistory = new Vector();
        historyPos = -1;
        running = false;

        httpClient = new HttpClient(Settings.getProxyUrl());

        chatForm = new Form(I18n.get(I18n.TITLE_IDLE));

        chatLogItem = new StringItem(null, I18n.get(I18n.CHAT_HINT));
        chatLogItem.setLayout(Item.LAYOUT_2);
        chatForm.append(chatLogItem);

        sendCommand = new Command(I18n.get(I18n.CMD_SEND), Command.OK, 1);
        exitCommand = new Command(I18n.get(I18n.CMD_EXIT), Command.EXIT, 2);
        settingsCommand = new Command(I18n.get(I18n.CMD_SETTINGS), Command.HELP, 3);
        chatForm.addCommand(sendCommand);
        chatForm.addCommand(exitCommand);
        chatForm.addCommand(settingsCommand);
        chatForm.setCommandListener(this);

        inputBox = new TextBox(I18n.get(I18n.INPUT_TITLE), "", 1000, TextField.ANY);
        okCommand = new Command(I18n.get(I18n.CMD_OK), Command.OK, 1);
        backCommand = new Command(I18n.get(I18n.CMD_BACK), Command.BACK, 2);
        prevCommand = new Command(I18n.get(I18n.CMD_PREV), Command.HELP, 3);
        nextCommand = new Command(I18n.get(I18n.CMD_NEXT), Command.STOP, 4);
        inputBox.addCommand(okCommand);
        inputBox.addCommand(backCommand);
        inputBox.addCommand(prevCommand);
        inputBox.addCommand(nextCommand);
        inputBox.setCommandListener(this);

        settingsForm = new Form(I18n.get(I18n.SETTINGS_TITLE));
        hostField = new TextField(I18n.get(I18n.SETTINGS_HOST) + ": ", Settings.getHost(), 100, TextField.URL);
        portField = new TextField(I18n.get(I18n.SETTINGS_PORT) + ": ", Settings.getPort(), 6, TextField.NUMERIC);
        settingsForm.append(hostField);
        settingsForm.append(portField);
        saveCommand = new Command(I18n.get(I18n.CMD_SAVE), Command.OK, 1);
        settingsBackCommand = new Command(I18n.get(I18n.CMD_BACK), Command.BACK, 2);
        settingsForm.addCommand(saveCommand);
        settingsForm.addCommand(settingsBackCommand);
        settingsForm.setCommandListener(this);

        display.setCurrent(chatForm);
        System.out.println("[MIDlet] constructor done");
    }

    public void startApp() {}

    public void pauseApp() {}

    public void destroyApp(boolean unconditional) {}

    public void commandAction(Command c, Displayable d) {
        if (d == inputBox) {
            if (c == okCommand) {
                String text = inputBox.getString();
                if (text != null && text.length() > 0) {
                    addInputHistory(text);
                    inputBox.setString("");
                    historyPos = -1;
                    display.setCurrent(chatForm);
                    sendMessage(text);
                }
            } else if (c == backCommand) {
                inputBox.setString("");
                historyPos = -1;
                display.setCurrent(chatForm);
            } else if (c == prevCommand) {
                navigateHistory(true);
            } else if (c == nextCommand) {
                navigateHistory(false);
            }
        } else if (d == settingsForm) {
            if (c == saveCommand) {
                String host = hostField.getString().trim();
                String port = portField.getString().trim();
                if (host.length() > 0 && port.length() > 0) {
                    Settings.save(host, port);
                    httpClient.setProxyUrl(Settings.getProxyUrl());
                    appendChat(I18n.get(I18n.CHAT_SYSTEM), I18n.get(I18n.SETTINGS_SAVED));
                }
                display.setCurrent(chatForm);
            } else if (c == settingsBackCommand) {
                hostField.setString(Settings.getHost());
                portField.setString(Settings.getPort());
                display.setCurrent(chatForm);
            }
        } else if (d == chatForm) {
            if (c == sendCommand) {
                if (running) {
                    return;
                }
                display.setCurrent(inputBox);
            } else if (c == settingsCommand) {
                hostField.setString(Settings.getHost());
                portField.setString(Settings.getPort());
                display.setCurrent(settingsForm);
            } else if (c == exitCommand) {
                notifyDestroyed();
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
        if (size == 0) {
            return;
        }
        if (historyPos == -1) {
            historyPos = prev ? size - 1 : 0;
        } else {
            if (prev) {
                historyPos--;
                if (historyPos < 0) {
                    historyPos = size - 1;
                }
            } else {
                historyPos++;
                if (historyPos >= size) {
                    historyPos = 0;
                }
            }
        }
        String text = (String) inputHistory.elementAt(historyPos);
        inputBox.setString(text);
    }

    private void sendMessage(String text) {
        currentUserMessage = text;
        appendChat(I18n.get(I18n.CHAT_YOU), currentUserMessage);
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
            if (errMsg == null) {
                errMsg = e.toString();
            }
            response = "{\"error\":{\"message\":\"" + escapeJson(errMsg) + "\"}}";
        } catch (Exception e) {
            String errMsg = e.getMessage();
            if (errMsg == null) {
                errMsg = e.toString();
            }
            response = "{\"error\":{\"message\":\"" + escapeJson(errMsg) + "\"}}";
        }
        final String finalResponse = response;
        display.callSerially(new Runnable() {
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
                                    appendChat("DeepSeek", content);
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
        chatLog.append('\n');
        chatLog.append(role);
        chatLog.append(": ");
        chatLog.append(text);

        if (chatLog.length() > MAX_LOG_CHARS) {
            int cut = chatLog.length() - MAX_LOG_CHARS;
            int nl = chatLog.toString().indexOf('\n', cut);
            int hintLen = I18n.get(I18n.CHAT_HINT).length();
            if (nl > hintLen && nl < chatLog.length()) {
                chatLog.delete(hintLen + 1, nl + 1);
            }
        }

        chatLogItem.setText(chatLog.toString());
    }

    private String buildRequestBody(String userMessage) {
        StringBuffer sb = new StringBuffer();
        sb.append("{\"model\":\"deepseek-chat\",\"messages\":[");

        sb.append("{\"role\":\"system\",\"content\":\"");
        sb.append(escapeJson(I18n.get(I18n.SYSTEM_PROMPT)));
        sb.append("\"}");

        for (int i = 0; i < messages.size(); i++) {
            sb.append(",");
            sb.append((String) messages.elementAt(i));
        }

        sb.append(",{\"role\":\"user\",\"content\":\"");
        sb.append(escapeJson(userMessage));
        sb.append("\"}");

        sb.append("],\"stream\":false}");
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
        if (text == null) {
            return "";
        }
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
