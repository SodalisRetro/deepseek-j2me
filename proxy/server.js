const http = require('http');

const DEEPSEEK_API_KEY = process.env.DEEPSEEK_API_KEY || '';
const PROXY_PORT = parseInt(process.env.PROXY_PORT || '8080', 10);
const DEEPSEEK_URL = 'https://api.deepseek.com/chat/completions';

function createRequestBody(messages) {
    return JSON.stringify({
        model: 'deepseek-chat',
        messages: messages,
        stream: false
    });
}

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

    let body = '';
    req.on('data', function (chunk) {
        body += chunk;
    });

    req.on('end', function () {
        if (body.length === 0) {
            res.writeHead(400, { 'Content-Type': 'text/plain' });
            res.end('Empty body');
            return;
        }
        sendToDeepSeek(body, res);
    });
}).listen(PROXY_PORT, function () {
    console.log('DeepSeek J2ME proxy running on http://localhost:' + PROXY_PORT);
});

function sendToDeepSeek(body, res) {
    const encodedBody = Buffer.from(body, 'utf8');

    const options = {
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
    };

    const https = require('https');
    const apiReq = https.request(options, function (apiRes) {
        let responseBody = '';
        apiRes.on('data', function (chunk) {
            responseBody += chunk;
        });
        apiRes.on('end', function () {
            res.writeHead(apiRes.statusCode, { 'Content-Type': 'application/json' });
            res.end(responseBody);
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
