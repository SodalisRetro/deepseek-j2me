package io.github.sodalisretro.deepseek;

import java.util.Hashtable;

public class I18n {

    public static final String TITLE_IDLE      = "title.idle";
    public static final String TITLE_THINKING  = "title.thinking";
    public static final String TITLE_ERROR     = "title.error";
    public static final String TITLE_PARSE_ERR = "title.parse_err";

    public static final String CMD_SEND  = "cmd.send";
    public static final String CMD_EXIT  = "cmd.exit";
    public static final String CMD_OK    = "cmd.ok";
    public static final String CMD_BACK  = "cmd.back";
    public static final String CMD_PREV  = "cmd.prev";
    public static final String CMD_NEXT  = "cmd.next";

    public static final String CHAT_HINT       = "chat.hint";
    public static final String CHAT_YOU        = "chat.you";
    public static final String CHAT_ERROR      = "chat.error";
    public static final String ERR_NO_RESPONSE = "err.no_response";
    public static final String ERR_UNKNOWN     = "err.unknown";
    public static final String ERR_PARSE_FAIL  = "err.parse_fail";
    public static final String INPUT_TITLE     = "input.title";
    public static final String SYSTEM_PROMPT   = "system.prompt";

    public static final String CMD_SETTINGS    = "cmd.settings";
    public static final String CMD_SAVE        = "cmd.save";
    public static final String SETTINGS_TITLE  = "settings.title";
    public static final String SETTINGS_HOST   = "settings.host";
    public static final String SETTINGS_PORT   = "settings.port";
    public static final String SETTINGS_SAVED  = "settings.saved";
    public static final String CHAT_SYSTEM     = "chat.system";

    public static final String SETTINGS_SEARCH = "settings.search";
    public static final String SETTINGS_YES    = "settings.yes";
    public static final String SETTINGS_NO     = "settings.no";
    public static final String SETTINGS_MAX_ROUNDS = "settings.max_rounds";

    private static Hashtable strings;

    static {
        String loc = System.getProperty("microedition.locale");
        if (loc != null && loc.startsWith("zh")) {
            strings = zhCN();
        } else {
            strings = en();
        }
    }

    public static String get(String key) {
        String val = (String) strings.get(key);
        return val != null ? val : key;
    }

    private static Hashtable en() {
        Hashtable h = new Hashtable(27);
        h.put(TITLE_IDLE,      "DeepSeek AI  [Idle]");
        h.put(TITLE_THINKING,  "DeepSeek AI  [Thinking...]");
        h.put(TITLE_ERROR,     "DeepSeek AI  [Error]");
        h.put(TITLE_PARSE_ERR, "DeepSeek AI  [Parse error]");

        h.put(CMD_SEND, "Send");
        h.put(CMD_EXIT, "Exit");
        h.put(CMD_OK,   "OK");
        h.put(CMD_BACK, "Back");
        h.put(CMD_PREV, "Prev Send");
        h.put(CMD_NEXT, "Next Send");

        h.put(CHAT_HINT,       "--- Press Send to send message ---");
        h.put(CHAT_YOU,        "You");
        h.put(CHAT_ERROR,      "Error");
        h.put(ERR_NO_RESPONSE, "No response (proxy not running?)");
        h.put(ERR_UNKNOWN,     "Unknown error");
        h.put(ERR_PARSE_FAIL,  "Failed to parse response");
        h.put(INPUT_TITLE,     "Message");
        h.put(SYSTEM_PROMPT,   "You are a helpful assistant. Keep responses concise. Output plain text only. Do NOT use Markdown formatting (no **bold**, no `code`, no bullet lists with -, no > quotes, no # headings). Use plain paragraphs and numbered lists if needed.");

        h.put(CMD_SETTINGS,   "Settings");
        h.put(CMD_SAVE,       "Save");
        h.put(SETTINGS_TITLE, "Settings");
        h.put(SETTINGS_HOST,  "Host");
        h.put(SETTINGS_PORT,  "Port");
        h.put(SETTINGS_SAVED, "Settings saved.");
        h.put(CHAT_SYSTEM,    "System");

        h.put(SETTINGS_SEARCH, "Web Search");
        h.put(SETTINGS_YES,    "Yes");
        h.put(SETTINGS_NO,     "No");
        h.put(SETTINGS_MAX_ROUNDS, "Max Search Rounds");
        return h;
    }

    private static Hashtable zhCN() {
        Hashtable h = new Hashtable(27);
        h.put(TITLE_IDLE,      "DeepSeek AI  [就绪]");
        h.put(TITLE_THINKING,  "DeepSeek AI  [思考...]");
        h.put(TITLE_ERROR,     "DeepSeek AI  [错误]");
        h.put(TITLE_PARSE_ERR, "DeepSeek AI  [解析错误]");

        h.put(CMD_SEND, "发送");
        h.put(CMD_EXIT, "退出");
        h.put(CMD_OK,   "确定");
        h.put(CMD_BACK, "返回");
        h.put(CMD_PREV, "上条");
        h.put(CMD_NEXT, "下条");

        h.put(CHAT_HINT,       "--- 点击发送输入消息 ---");
        h.put(CHAT_YOU,        "你");
        h.put(CHAT_ERROR,      "错误");
        h.put(ERR_NO_RESPONSE, "无响应 (proxy 是否已启动?)");
        h.put(ERR_UNKNOWN,     "未知错误");
        h.put(ERR_PARSE_FAIL,  "解析响应失败");
        h.put(INPUT_TITLE,     "输入消息");
        h.put(SYSTEM_PROMPT,   "You are a helpful assistant. Keep responses concise. Output plain text only. Do NOT use Markdown formatting (no **bold**, no `code`, no bullet lists with -, no > quotes, no # headings). Use plain paragraphs and numbered lists if needed.");

        h.put(CMD_SETTINGS,   "设置");
        h.put(CMD_SAVE,       "保存");
        h.put(SETTINGS_TITLE, "设置");
        h.put(SETTINGS_HOST,  "主机");
        h.put(SETTINGS_PORT,  "端口");
        h.put(SETTINGS_SAVED, "设置已保存。");
        h.put(CHAT_SYSTEM,    "系统");

        h.put(SETTINGS_SEARCH, "联网搜索");
        h.put(SETTINGS_YES,    "是");
        h.put(SETTINGS_NO,     "否");
        h.put(SETTINGS_MAX_ROUNDS, "最大搜索轮次");
        return h;
    }
}
