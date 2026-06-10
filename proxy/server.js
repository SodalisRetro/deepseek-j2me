const http = require('http');
const https = require('https');

const DEEPSEEK_API_KEY = process.env.DEEPSEEK_API_KEY || '';
const PROXY_PORT = parseInt(process.env.PROXY_PORT || '8080', 10);
const DEFAULT_MAX_SEARCH_ROUNDS = 15;

var SEARCH_TOOL = {
    type: 'function',
    function: {
        name: 'search_page',
        description: 'Search DuckDuckGo for URLs about a topic. Returns up to 5 results with titles, snippets, and URLs. You MUST then call fetch_page on the most relevant URL to get the actual information. Search only discovers URLs — it does not provide answers.',
        parameters: {
            type: 'object',
            properties: {
                query: {
                    type: 'string',
                    description: 'The search query (use keywords, not full sentences)'
                }
            },
            required: ['query']
        }
    }
};

var FETCH_TOOL = {
    type: 'function',
    function: {
        name: 'fetch_page',
        description: 'Fetch raw HTML of a web page. Returns the HTML directly (script/style stripped). You can parse headings, lists, links, and text from the HTML structure. This is your ONLY way to get actual information. Use offset to paginate long pages.',
        parameters: {
            type: 'object',
            properties: {
                url: {
                    type: 'string',
                    description: 'The full URL of the page to fetch'
                },
                offset: {
                    type: 'integer',
                    description: 'Character offset to start reading from. Default 0. Use 8000, 16000, etc. to paginate through a long page.'
                },
                limit: {
                    type: 'integer',
                    description: 'Max characters to return. Default 8000.'
                }
            },
            required: ['url']
        }
    }
};

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
    var maxRounds = DEFAULT_MAX_SEARCH_ROUNDS;
    var cleanBody = body;

    try {
        var json = JSON.parse(body);
        var hasSearch = !!json.web_search;
        if (hasSearch) {
            enableSearch = true;
            delete json.web_search;
        }
        maxRounds = json.max_search_rounds || DEFAULT_MAX_SEARCH_ROUNDS;
        delete json.max_search_rounds;

        if (!enableSearch) {
            cleanBody = JSON.stringify(json);
            console.log('No search, forwarding directly');
            sendToDeepSeek(cleanBody, res);
            return;
        }

        var userQuery = '';
        var messages = json.messages;
        if (messages && messages.length > 0) {
            var last = messages[messages.length - 1];
            if (last.role === 'user' && last.content) {
                userQuery = last.content.substring(0, 80);
            }
        }
        console.log('Search mode (maxRounds=' + maxRounds + ') user: ' + userQuery);

        var ctx = '=== SYSTEM CONTEXT ===\n' +
            serverTimeContext() + '\n' +
            '\n=== INSTRUCTION ===\n' +
            'You are an AI with REAL web search capabilities via tools. ' +
            'You do NOT have a knowledge cutoff limitation — you have live ' +
            'search_page and fetch_page tools. You MUST use them for any ' +
            'question about current information, weather, news, versions, ' +
            'prices, or any real-world data.\n' +
            '\n' +
            'MANDATORY WORKFLOW:\n' +
            '1. Call search_page ONCE with a keyword query.\n' +
            '2. IMMEDIATELY call fetch_page on the most relevant URL from the ' +
            'results. The response is raw HTML — look for h1-h6 headings, ' +
            'article text, code blocks, and version strings.\n' +
            '3. If search_page returns no results, go directly to URLs you ' +
            'know from training (e.g. minecraft.net, wikipedia.org, etc.).\n' +
            '4. If a page is too long, call fetch_page again with ' +
            'offset=12000, offset=24000, etc. to continue reading.\n' +
            '5. Answer the user once you have the information.\n' +
            '\n' +
            'CRITICAL RULES:\n' +
            '- NEVER refuse to answer because of "knowledge cutoff". You have ' +
            'live tools. ALWAYS search first, then answer.\n' +
            '- search_page + fetch_page is ONE round. Never search twice in a row.\n' +
            '- The server time above is authoritative for time/date questions.\n' +
            '- Do not retry failed searches. Move on to known URLs instead.\n' +
            '- NEVER output JSON, tool_calls, or raw HTML in your answer. ' +
            'ALWAYS respond in plain natural language. ' +
            'Extract the answer from the HTML, do not quote it.\n' +
            '\n' +
            '=== OUTPUT FORMAT (J2ME display constraint) ===\n' +
            'Output plain text only. Do NOT use ANY Markdown formatting: ' +
            'no **bold**, no `code`, no bullet lists with -, no > quotes, ' +
            'no # headings, and especially NO PIPE TABLES with | and ---. ' +
            'Instead, present structured data as labeled lines (title: value) ' +
            'or flat paragraphs. Use plain numbered lists (1. 2. 3.) if needed. ' +
            'Keep responses concise.';

        messages.unshift({
            role: 'system',
            content: ctx
        });

        json.tools = [SEARCH_TOOL, FETCH_TOOL];
        json.tool_choice = 'auto';

        cleanBody = JSON.stringify(json);
        deepSearchLoop(json, res, 0, maxRounds);
    } catch (e) {
        console.log('handleRequest error: ' + e.message);
        sendToDeepSeek(body, res);
    }
}

function deepSearchLoop(json, res, round, maxRounds) {
    var roundLabel = round === 0 ? 'initial' : ('tool round ' + round);
    console.log('--- Deep search ' + roundLabel + ' ---');

    var body = JSON.stringify(json);

    sendRequest(body, function (err, statusCode, responseBody) {
        if (err || statusCode !== 200) {
            if (err) console.error('DeepSeek API error:', err.message);
            if (statusCode !== 200 && responseBody) console.log(responseBody);
            res.writeHead(statusCode || 502, { 'Content-Type': 'application/json' });
            res.end(responseBody || JSON.stringify({ error: { message: 'Proxy error: ' + (err ? err.message : 'API error') } }));
            return;
        }

        var parsed;
        try {
            parsed = JSON.parse(responseBody);
        } catch (e) {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(responseBody);
            return;
        }

        var choices = parsed.choices;
        if (!choices || choices.length === 0) {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(stripResponseMarkdown(responseBody));
            return;
        }

        var choice = choices[0];
        var finishReason = choice.finish_reason;
        var message = choice.message;

        if (finishReason === 'stop' || !message) {
            if (round === 0 && message && message.content && !message.tool_calls) {
                console.log('Model refused tools on round 0, forcing tool use');
                json.messages.push(message);
                json.messages.push({
                    role: 'system',
                    content: 'You refused to use your tools. You MUST call search_page now. ' +
                        'You have live web access. Do not claim you cannot access real-time data. ' +
                        'Call search_page immediately with a relevant query.'
                });
                delete json.tools;
                delete json.tool_choice;
                sendRequest(JSON.stringify(json), function (err2, sc2, rb2) {
                    if (err2) {
                        res.writeHead(502, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ error: { message: 'Proxy error: ' + err2.message } }));
                        return;
                    }
                    res.writeHead(sc2, { 'Content-Type': 'application/json' });
                    res.end(stripResponseMarkdown(rb2));
                });
                return;
            }
            console.log('Deep search complete after ' + round + ' tool rounds');
            console.log('--- /deep search ---');
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(stripResponseMarkdown(responseBody));
            return;
        }

        if (round >= maxRounds) {
            console.log('Max rounds reached (' + maxRounds + '), forcing final answer');

            if (finishReason === 'tool_calls' && message.tool_calls && message.tool_calls.length > 0) {
                json.messages.push(message);
                executeToolCalls(json, message.tool_calls, function () {
                    delete json.tools;
                    delete json.tool_choice;
                    sendRequest(JSON.stringify(json), function (err2, sc2, rb2) {
                        if (err2) {
                            res.writeHead(502, { 'Content-Type': 'application/json' });
                            res.end(JSON.stringify({ error: { message: 'Proxy error: ' + err2.message } }));
                            return;
                        }
                        res.writeHead(sc2, { 'Content-Type': 'application/json' });
                        res.end(stripResponseMarkdown(rb2));
                    });
                });
                return;
            }

            if (message && message.content) {
                json.messages.push(message);
                delete json.tools;
                delete json.tool_choice;
                sendRequest(JSON.stringify(json), function (err2, sc2, rb2) {
                    if (err2) {
                        res.writeHead(502, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ error: { message: 'Proxy error: ' + err2.message } }));
                        return;
                    }
                    res.writeHead(sc2, { 'Content-Type': 'application/json' });
                    res.end(stripResponseMarkdown(rb2));
                });
            } else {
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(stripResponseMarkdown(responseBody));
            }
            return;
        }

        if (finishReason !== 'tool_calls') {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(stripResponseMarkdown(responseBody));
            return;
        }

        var toolCalls = message.tool_calls;
        if (!toolCalls || toolCalls.length === 0) {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(stripResponseMarkdown(responseBody));
            return;
        }

        json.messages.push(message);

        executeToolCalls(json, toolCalls, function () {
            deepSearchLoop(json, res, round + 1, maxRounds);
        });
    });
}

function executeToolCalls(json, toolCalls, done) {
    var results = new Array(toolCalls.length);
    var idx = 0;

    function next() {
        if (idx >= toolCalls.length) {
            for (var i = 0; i < results.length; i++) {
                json.messages.push(results[i]);
            }
            done();
            return;
        }

        var tc = toolCalls[idx];
        var pos = idx;
        idx++;

        if (tc.type !== 'function' || !tc.function) {
            results[pos] = {
                role: 'tool',
                tool_call_id: tc.id,
                content: 'Error: not a function call'
            };
            next();
            return;
        }

        var name = tc.function.name;
        var parsedArgs = {};
        try {
            parsedArgs = JSON.parse(tc.function.arguments || '{}');
        } catch (e) {}

        if (name === 'search_page') {
            handleSearchPage(parsedArgs, pos, tc);
        } else if (name === 'fetch_page') {
            handleFetchPage(parsedArgs, pos, tc);
        } else {
            results[pos] = {
                role: 'tool',
                tool_call_id: tc.id,
                content: 'Error: unknown tool ' + name
            };
            next();
        }
    }

    function handleSearchPage(args, pos, tc) {
        var query = args.query || '';
        console.log('Tool search [' + pos + ']: ' + query);

        if (!query) {
            results[pos] = {
                role: 'tool',
                tool_call_id: tc.id,
                content: 'Error: empty query'
            };
            next();
            return;
        }

        ddgSearch(query, function (err, searchResults) {
            if (err) {
                results[pos] = {
                    role: 'tool',
                    tool_call_id: tc.id,
                    content: 'Search failed: ' + err.message
                };
            } else if (!searchResults) {
                results[pos] = {
                    role: 'tool',
                    tool_call_id: tc.id,
                    content: 'No results available. The search engine may be rate-limiting. Try using the information already provided instead.'
                };
            } else {
                console.log('Tool results [' + pos + ']:\n' + searchResults);
                results[pos] = {
                    role: 'tool',
                    tool_call_id: tc.id,
                    content: 'Search results for "' + query + '":\n\n' + searchResults
                };
            }
            setTimeout(next, 3000);
        });
    }

    function handleFetchPage(args, pos, tc) {
        var url = args.url || '';
        console.log('Tool fetch [' + pos + ']: ' + url);

        if (!url) {
            results[pos] = {
                role: 'tool',
                tool_call_id: tc.id,
                content: 'Error: empty URL'
            };
            next();
            return;
        }

        fetchPage(url, function (err, pageContent) {
            if (err) {
                results[pos] = {
                    role: 'tool',
                    tool_call_id: tc.id,
                    content: 'Failed to fetch page: ' + err.message
                };
            } else if (!pageContent) {
                results[pos] = {
                    role: 'tool',
                    tool_call_id: tc.id,
                    content: 'Page fetched but no readable content found.'
                };
            } else {
                var offset = parseInt(args.offset) || 0;
                var limit = parseInt(args.limit) || 12000;
                var totalLen = pageContent.length;

                if (offset >= totalLen) {
                    results[pos] = {
                        role: 'tool',
                        tool_call_id: tc.id,
                        content: 'End of page reached (offset=' + offset + ', total=' + totalLen + ' chars). No more content.'
                    };
                } else {
                    var chunk = pageContent.substring(offset, offset + limit);
                    var label = offset > 0 ?
                        ' (segment ' + offset + '-' + Math.min(offset + limit, totalLen) + ' of ' + totalLen + ' chars)' :
                        ' (' + totalLen + ' chars)';
                    console.log('Fetch results [' + pos + ']: ' + totalLen + ' chars, offset=' + offset + ', limit=' + limit);
                    results[pos] = {
                        role: 'tool',
                        tool_call_id: tc.id,
                        content: 'Page content from ' + url + label + ':\n\n' + chunk
                    };
                }
            }
            setTimeout(next, 800);
        });
    }

    next();
}

function fetchPage(url, callback) {
    var options = {
        headers: {
            'User-Agent': 'Mozilla/5.0 (compatible; DeepSeekJ2ME/1.0)',
            'Accept': 'text/html,text/plain'
        }
    };

    var req = (url.indexOf('https://') === 0 ? https : http).get(url, options, function (res) {
        if (res.statusCode !== 200) {
            var body = '';
            res.on('data', function (chunk) { body += chunk; });
            res.on('end', function () {
                callback(new Error('HTTP ' + res.statusCode));
            });
            return;
        }
        var body = '';
        res.on('data', function (chunk) { body += chunk; });
        res.on('end', function () {
            body = body.replace(/<script[\s\S]*?<\/script>/gi, '');
            body = body.replace(/<style[\s\S]*?<\/style>/gi, '');
            body = body.replace(/<noscript[\s\S]*?<\/noscript>/gi, '');
            body = body.replace(/<svg[\s\S]*?<\/svg>/gi, '');
            body = body.replace(/\s+data-[a-z-]+="[^"]*"/gi, '');
            body = body.replace(/<!--[\s\S]*?-->/g, '');
            body = body.replace(/\n\s*\n/g, '\n');
            body = body.replace(/\t/g, ' ');
            body = body.replace(/ {2,}/g, ' ');
            callback(null, body);
        });
    });

    req.on('error', function (err) {
        callback(err);
    });

    req.setTimeout(8000, function () {
        req.destroy();
        callback(new Error('timeout'));
    });
}

function ddgSearch(query, callback) {
    var q = encodeURIComponent(query);
    var url = 'https://lite.duckduckgo.com/lite/?q=' + q;

    var options = {
        headers: {
            'User-Agent': 'Mozilla/5.0 (compatible; DeepSeekJ2ME/1.0)'
        }
    };

    https.get(url, options, function (res) {
        var text = '';
        res.on('data', function (chunk) { text += chunk; });
        res.on('end', function () {
            var sc = res.statusCode;
            console.log('DDG status=' + sc + ' len=' + text.length + ' for "' + query.substring(0, 50) + '"');
            if (sc === 202 || sc === 403 || sc === 429) {
                console.log('DDG blocked/rate-limited (status ' + sc + ')');
                callback(null, null);
                return;
            }
            if (sc !== 200) {
                console.log('DDG unexpected status ' + sc);
                callback(null, null);
                return;
            }
            callback(null, parseDDGLite(text));
        });
    }).on('error', function (err) {
        console.log('DDG error: ' + err.message);
        callback(err);
    }).setTimeout(8000, function () {
        this.destroy();
        callback(new Error('timeout'));
    });
}

function parseDDGLite(html) {
    var results = [];
    var linkRe = /<a[^>]*class='result-link'[^>]*href='([^']*)'[^>]*>([\s\S]*?)<\/a>/g;
    var snippetRe = /<td class='result-snippet'>([\s\S]*?)<\/td>/g;

    var links = [];
    var m;
    while ((m = linkRe.exec(html)) !== null) {
        links.push({ title: stripHtml(m[2]), url: m[1] });
    }

    var snippets = [];
    while ((m = snippetRe.exec(html)) !== null) {
        var s = stripHtml(m[1]);
        if (s.length > 10) {
            snippets.push(s);
        }
    }

    for (var i = 0; i < Math.min(links.length, snippets.length, 5); i++) {
        results.push((i + 1) + '. ' + links[i].title + '\n   ' + snippets[i] +
                     '\n   URL: ' + links[i].url);
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

function sendRequest(body, callback) {
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
            callback(null, apiRes.statusCode, responseBody);
        });
    });

    apiReq.on('error', function (err) {
        callback(err);
    });

    apiReq.write(encodedBody);
    apiReq.end();
}

function sendToDeepSeek(body, res) {
    sendRequest(body, function (err, statusCode, responseBody) {
        if (err) {
            res.writeHead(502, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ error: { message: 'Proxy error: ' + err.message } }));
            return;
        }

        var clean = stripResponseMarkdown(responseBody);
        res.writeHead(statusCode, { 'Content-Type': 'application/json' });
        res.end(clean);
    });
}
