const http = require('http');
const https = require('https');

const DEEPSEEK_API_KEY = process.env.DEEPSEEK_API_KEY || '';
const PROXY_PORT = parseInt(process.env.PROXY_PORT || '8080', 10);

http.createServer((req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }

    if (req.method !== 'POST') {
        res.writeHead(405, { 'Content-Type': 'text/plain' });
        res.end('Method Not Allowed');
        return;
    }

    var body = '';
    req.on('data', function (chunk) {
        body += chunk;
    });

    req.on('end', function () {
        if (body.length === 0) {
            res.writeHead(400, { 'Content-Type': 'text/plain' });
            res.end('Empty body');
            return;
        }
        handleRequest(body, res);
    });
}).listen(PROXY_PORT, function () {
    console.log('DeepSeek J2ME proxy running on http://localhost:' + PROXY_PORT);
});

function serverTimeContext() {
    var now = new Date();
    return 'Current server time: ' + now.toISOString() +
           ' (day: ' + now.toLocaleString('en-US', { weekday: 'long' }) +
           ', year: ' + now.getFullYear() + ')';
}

function handleRequest(body, res) {
    var enableSearch = false;
    var searchQuery = '';
    var cleanBody = body;

    try {
        var json = JSON.parse(body);
        var hasSearch = !!json.web_search;
        if (hasSearch) {
            enableSearch = true;
            var messages = json.messages;
            if (messages && messages.length > 0) {
                var last = messages[messages.length - 1];
                if (last.role === 'user' && last.content) {
                    searchQuery = last.content;
                }
            }
            delete json.web_search;
            cleanBody = JSON.stringify(json);
        }
        if (!enableSearch) {
            var lastMsg = (json.messages && json.messages.length > 0) ?
                json.messages[json.messages.length - 1].content : 'none';
            console.log('No search (web_search=' + hasSearch + ') query: ' + lastMsg.substring(0, 60));
        }
    } catch (e) {
        sendToDeepSeek(body, res);
        return;
    }

    if (!enableSearch || !searchQuery) {
        sendToDeepSeek(body, res);
        return;
    }

    console.log('Search query: ' + searchQuery);
    ddgSearch(searchQuery, function (err, results) {
        if (err) {
            console.log('Search error: ' + err.message + ', using time only');
        }
        if (results) {
            console.log('Search results:\n' + results);
        } else {
            console.log('No search results parsed');
        }

        try {
            var json = JSON.parse(cleanBody);
            var messages = json.messages;

            var ctx = '=== SYSTEM CONTEXT ===\n' +
                serverTimeContext() + '\n';

            if (results) {
                ctx += '\n=== WEB SEARCH RESULTS ===\n' +
                    results + '\n';
            }

            ctx += '\n=== INSTRUCTION ===\n' +
                'Use the context above to answer the user accurately. ' +
                'For time, date, weather, news, or ' +
                'any real-world information, always use the provided ' +
                'context. The server time is authoritative for time/date ' +
                'questions. Cite web results when relevant.';

            messages.unshift({
                role: 'system',
                content: ctx
            });

            cleanBody = JSON.stringify(json);
        } catch (e) {
            console.log('JSON modify failed: ' + e.message);
        }

        sendToDeepSeek(cleanBody, res);
    });
}

function ddgSearch(query, callback) {
    var q = encodeURIComponent(query);
    var url = 'https://lite.duckduckgo.com/lite/?q=' + q;

    https.get(url, function (res) {
        var text = '';
        res.on('data', function (chunk) { text += chunk; });
        res.on('end', function () {
            callback(null, parseDDGLite(text));
        });
    }).on('error', function (err) {
        callback(err);
    }).setTimeout(8000, function () {
        this.destroy();
        callback(new Error('timeout'));
    });
}

function parseDDGLite(html) {
    var results = [];
    var titleRe = /<a[^>]*class='result-link'[^>]*>([\s\S]*?)<\/a>/g;
    var snippetRe = /<td class='result-snippet'>([\s\S]*?)<\/td>/g;

    var titles = [];
    var m;
    while ((m = titleRe.exec(html)) !== null) {
        titles.push(stripHtml(m[1]));
    }

    var snippets = [];
    while ((m = snippetRe.exec(html)) !== null) {
        var s = stripHtml(m[1]);
        if (s.length > 10) {
            snippets.push(s);
        }
    }

    for (var i = 0; i < Math.min(titles.length, snippets.length, 5); i++) {
        results.push((i + 1) + '. ' + titles[i] + '\n   ' + snippets[i]);
    }

    return results.length > 0 ? results.join('\n\n') : null;
}

function stripHtml(str) {
    return str.replace(/<[^>]*>/g, '').replace(/&amp;/g, '&').replace(/&lt;/g, '<')
              .replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#x27;/g, "'")
              .replace(/&nbsp;/g, ' ').replace(/\s+/g, ' ').trim();
}

function stripMarkdown(text) {
    if (!text) return text;

    text = text.replace(/^#{1,6}\s+/gm, '');

    text = text.replace(/\*\*\*(.+?)\*\*\*/g, '$1');
    text = text.replace(/\*\*(.+?)\*\*/g, '$1');
    text = text.replace(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g, '$1');

    text = text.replace(/`{3}[\s\S]*?`{3}/g, function (m) {
        return '\n' + m.replace(/`{3}\w*\n?/g, '').replace(/`{3}/g, '') + '\n';
    });
    text = text.replace(/`(.+?)`/g, '$1');

    text = text.replace(/^[*-]\s+/gm, '\u2022 ');

    text = text.replace(/^>\s?/gm, '| ');

    text = text.replace(/\[(.+?)\]\(.+?\)/g, '$1');

    text = text.replace(/^(\d+)\.\s+/gm, '$1. ');

    text = text.replace(/\n{3,}/g, '\n\n');

    return text.trim();
}

function stripResponseMarkdown(body) {
    try {
        var json = JSON.parse(body);
        var choices = json.choices;
        if (choices && choices.length > 0) {
            var msg = choices[0].message;
            if (msg && msg.content) {
                msg.content = stripMarkdown(msg.content);
            }
        }
        return JSON.stringify(json);
    } catch (e) {
        return body;
    }
}

function sendToDeepSeek(body, res) {
    var encodedBody = Buffer.from(body, 'utf8');

    var apiReq = https.request({
        hostname: 'api.deepseek.com',
        port: 443,
        path: '/chat/completions',
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + DEEPSEEK_API_KEY,
            'Content-Length': encodedBody.length,
            'Accept': 'application/json'
        }
    }, function (apiRes) {
        var responseBody = '';
        apiRes.on('data', function (chunk) {
            responseBody += chunk;
        });
        apiRes.on('end', function () {
            console.log('--- DeepSeek API status: ' + apiRes.statusCode + ' ---');
            if (apiRes.statusCode !== 200) {
                console.log(responseBody);
            }
            console.log('--- /api response ---');

            var clean = stripResponseMarkdown(responseBody);

            res.writeHead(apiRes.statusCode, { 'Content-Type': 'application/json' });
            res.end(clean);
        });
    });

    apiReq.on('error', function (err) {
        console.error('DeepSeek API error:', err.message);
        res.writeHead(502, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: { message: 'Proxy error: ' + err.message } }));
    });

    apiReq.write(encodedBody);
    apiReq.end();
}
