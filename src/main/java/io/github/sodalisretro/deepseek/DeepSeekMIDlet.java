package io.github.sodalisretro.deepseek;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;

public class DeepSeekMIDlet extends MIDlet implements CommandListener, Runnable {

    private Display display;
    private Form chatForm;
    private TextField inputField;
    private StringItem statusItem;
    private Command sendCommand;
    private Command exitCommand;

    private HttpClient httpClient;
    private Vector messages;

    private String currentUserMessage;
    private boolean running;
    private static final int MAX_HISTORY = 10;
    private static final String PROXY_URL = "http://localhost:8080/";

    public DeepSeekMIDlet() {
        System.out.println("[MIDlet] constructor start");
        display = Display.getDisplay(this);
        httpClient = new HttpClient(PROXY_URL);
        messages = new Vector();
        running = false;

        chatForm = new Form("DeepSeek Chat");

        StringItem header = new StringItem(null, "< DeepSeek AI >\n");
        header.setLayout(StringItem.LAYOUT_CENTER);
        chatForm.append(header);

        statusItem = new StringItem(null, "Input question, press Send\n________________________\n");
        chatForm.append(statusItem);

        inputField = new TextField("> ", "", 500, TextField.ANY);
        chatForm.append(inputField);

        sendCommand = new Command("Send", Command.OK, 1);
        exitCommand = new Command("Exit", Command.EXIT, 2);
        chatForm.addCommand(sendCommand);
        chatForm.addCommand(exitCommand);
        chatForm.setCommandListener(this);

        display.setCurrent(chatForm);
        System.out.println("[MIDlet] constructor done");
    }

    public void startApp() {}

    public void pauseApp() {}

    public void destroyApp(boolean unconditional) {}

    public void commandAction(Command c, Displayable d) {
        System.out.println("[MIDlet] commandAction type=" + c.getCommandType() + " label=" + c.getLabel());
        if (c == sendCommand) {
            if (running) {
                System.out.println("[MIDlet] blocked: already running");
                return;
            }
            String text = inputField.getString();
            System.out.println("[MIDlet] input: '" + text + "'");
            if (text == null || text.length() == 0) {
                System.out.println("[MIDlet] empty input, ignoring");
                return;
            }
            currentUserMessage = text;
            inputField.setString("");
            showChat("You", currentUserMessage);
            addHistoryMessage("user", currentUserMessage);
            statusItem.setText("Thinking...");
            running = true;
            System.out.println("[MIDlet] starting thread");
            new Thread(this).start();
        } else if (c == exitCommand) {
            notifyDestroyed();
        }
    }

    public void run() {
        System.out.println("[MIDlet] run() started");
        String response = null;
        try {
            String requestBody = buildRequestBody(currentUserMessage);
            System.out.println("[MIDlet] request body: " + requestBody);
            response = httpClient.post(requestBody);
            System.out.println("[MIDlet] post() returned: '" + response + "'");
        } catch (IOException e) {
            System.out.println("[MIDlet] IOException: " + e.toString());
            String errMsg = e.getMessage();
            if (errMsg == null) {
                errMsg = e.toString();
            }
            response = "{\"error\":{\"message\":\"" + escapeJson(errMsg) + "\"}}";
            System.out.println("[MIDlet] error response: " + response);
        } catch (Exception e) {
            System.out.println("[MIDlet] Exception: " + e.toString());
            String errMsg = e.getMessage();
            if (errMsg == null) {
                errMsg = e.toString();
            }
            response = "{\"error\":{\"message\":\"" + escapeJson(errMsg) + "\"}}";
            System.out.println("[MIDlet] error response: " + response);
        }
        final String finalResponse = response;
        System.out.println("[MIDlet] finalResponse='" + finalResponse + "', calling callSerially");
        display.callSerially(new Runnable() {
            public void run() {
                System.out.println("[MIDlet] callSerially callback, handling response");
                handleResponse(finalResponse);
                running = false;
            }
        });
    }

    private void handleResponse(String response) {
        System.out.println("[MIDlet] handleResponse, response is " + (response == null ? "NULL" : "len=" + response.length()));
        if (response == null) {
            System.out.println("[MIDlet] response is null, showing error");
            showChat("Error", "No response (proxy not running?)");
            statusItem.setText("Error: no response");
            return;
        }
        try {
            System.out.println("[MIDlet] parsing JSON: '" + response + "'");
            Object parsed = JsonParser.parse(response);
            System.out.println("[MIDlet] parsed type: " + (parsed == null ? "null" : parsed.getClass().getName()));
            if (parsed instanceof Hashtable) {
                Hashtable root = (Hashtable) parsed;
                System.out.println("[MIDlet] root keys: " + root.keys());
                Object error = root.get("error");
                if (error instanceof Hashtable) {
                    String errMsg = (String) ((Hashtable) error).get("message");
                    System.out.println("[MIDlet] API error: " + errMsg);
                    showChat("Error", errMsg != null ? errMsg : "Unknown error");
                    statusItem.setText("Error occurred.");
                    return;
                }
                Object choices = root.get("choices");
                System.out.println("[MIDlet] choices=" + (choices == null ? "null" : choices.getClass().getName()));
                if (choices instanceof Vector) {
                    Vector choiceList = (Vector) choices;
                    System.out.println("[MIDlet] choiceList size=" + choiceList.size());
                    if (choiceList.size() > 0) {
                        Object choice = choiceList.elementAt(0);
                        System.out.println("[MIDlet] choice type=" + (choice == null ? "null" : choice.getClass().getName()));
                        if (choice instanceof Hashtable) {
                            Object message = ((Hashtable) choice).get("message");
                            System.out.println("[MIDlet] message type=" + (message == null ? "null" : message.getClass().getName()));
                            if (message instanceof Hashtable) {
                                String content = (String) ((Hashtable) message).get("content");
                                System.out.println("[MIDlet] content=" + (content == null ? "null" : "'" + content + "'"));
                                if (content != null) {
                                    showChat("DeepSeek", content);
                                    addHistoryMessage("assistant", content);
                                    statusItem.setText("________________________");
                                    return;
                                }
                            }
                        }
                    }
                }
            }
            System.out.println("[MIDlet] response parse failed");
            showChat("Error", "Failed to parse response");
            statusItem.setText("Parse error.");
        } catch (Exception e) {
            System.out.println("[MIDlet] handleResponse Exception: " + e.toString());
            e.printStackTrace();
            showChat("Error", e.toString());
            statusItem.setText("Error: " + e.getMessage());
        }
    }

    private void showChat(String role, String text) {
        System.out.println("[MIDlet] showChat " + role + ": " + text);
        if (messages.size() / 2 >= MAX_HISTORY) {
            chatForm.delete(2);
            chatForm.delete(2);
            messages.removeElementAt(0);
            messages.removeElementAt(0);
        }
        String label = role + ": ";
        StringItem msg = new StringItem(label, text + "\n");
        chatForm.insert(chatForm.size() - 1, msg);
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
