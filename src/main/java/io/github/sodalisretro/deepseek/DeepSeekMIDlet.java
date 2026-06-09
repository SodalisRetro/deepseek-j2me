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
    private TextBox inputBox;
    private StringItem chatLogItem;
    private Command sendCommand;
    private Command exitCommand;
    private Command okCommand;
    private Command backCommand;

    private HttpClient httpClient;
    private Vector messages;
    private StringBuffer chatLog;

    private String currentUserMessage;
    private boolean running;
    private static final int MAX_HISTORY = 10;
    private static final int MAX_LOG_CHARS = 4000;
    private static final String PROXY_URL = "http://localhost:8080/";
    private static final String HINT = "--- Press Send to send message ---";

    public DeepSeekMIDlet() {
        System.out.println("[MIDlet] constructor start");
        display = Display.getDisplay(this);
        httpClient = new HttpClient(PROXY_URL);
        messages = new Vector();
        chatLog = new StringBuffer();
        chatLog.append(HINT);
        running = false;

        chatForm = new Form("DeepSeek AI  [Idle]");

        chatLogItem = new StringItem(null, HINT);
        chatLogItem.setLayout(Item.LAYOUT_2);
        chatForm.append(chatLogItem);

        sendCommand = new Command("Send", Command.OK, 1);
        exitCommand = new Command("Exit", Command.EXIT, 2);
        chatForm.addCommand(sendCommand);
        chatForm.addCommand(exitCommand);
        chatForm.setCommandListener(this);

        inputBox = new TextBox("Message", "", 1000, TextField.ANY);
        okCommand = new Command("OK", Command.OK, 1);
        backCommand = new Command("Back", Command.BACK, 2);
        inputBox.addCommand(okCommand);
        inputBox.addCommand(backCommand);
        inputBox.setCommandListener(this);

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
                    inputBox.setString("");
                    display.setCurrent(chatForm);
                    sendMessage(text);
                }
            } else if (c == backCommand) {
                inputBox.setString("");
                display.setCurrent(chatForm);
            }
        } else if (d == chatForm) {
            if (c == sendCommand) {
                if (running) {
                    return;
                }
                display.setCurrent(inputBox);
            } else if (c == exitCommand) {
                notifyDestroyed();
            }
        }
    }

    private void sendMessage(String text) {
        currentUserMessage = text;
        appendChat("You", currentUserMessage);
        addHistoryMessage("user", currentUserMessage);
        chatForm.setTitle("DeepSeek AI  [Thinking...]");
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
            appendChat("Error", "No response (proxy not running?)");
            chatForm.setTitle("DeepSeek AI  [Error]");
            return;
        }
        try {
            Object parsed = JsonParser.parse(response);
            if (parsed instanceof Hashtable) {
                Hashtable root = (Hashtable) parsed;
                Object error = root.get("error");
                if (error instanceof Hashtable) {
                    String errMsg = (String) ((Hashtable) error).get("message");
                    appendChat("Error", errMsg != null ? errMsg : "Unknown error");
                    chatForm.setTitle("DeepSeek AI  [Error]");
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
                                    chatForm.setTitle("DeepSeek AI  [Idle]");
                                    return;
                                }
                            }
                        }
                    }
                }
            }
            appendChat("Error", "Failed to parse response");
            chatForm.setTitle("DeepSeek AI  [Parse error]");
        } catch (Exception e) {
            appendChat("Error", e.toString());
            chatForm.setTitle("DeepSeek AI  [Error]");
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
            if (nl > HINT.length() && nl < chatLog.length()) {
                chatLog.delete(HINT.length() + 1, nl + 1);
            }
        }

        chatLogItem.setText(chatLog.toString());
    }

    private String buildRequestBody(String userMessage) {
        StringBuffer sb = new StringBuffer();
        sb.append("{\"model\":\"deepseek-chat\",\"messages\":[");

        sb.append("{\"role\":\"system\",\"content\":\"You are a helpful assistant. Keep responses concise.\"}");

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
