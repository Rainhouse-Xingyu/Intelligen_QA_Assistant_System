import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, "..");
const logsDir = path.join(rootDir, "logs");
const port = Number(process.env.LOG_VIEWER_PORT || 5174);
const allowedLogs = new Set(["backend.log", "model-service.log", "frontend.log", "log-viewer.log"]);

const page = `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Project Logs</title>
  <style>
    :root {
      color-scheme: dark;
      font-family: "Segoe UI", Arial, sans-serif;
      background: #101317;
      color: #e9edf1;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      min-height: 100vh;
      display: grid;
      grid-template-rows: auto 1fr;
      background: #101317;
    }
    header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 14px 18px;
      border-bottom: 1px solid #2b333d;
      background: #171b21;
    }
    h1 {
      margin: 0;
      font-size: 18px;
      font-weight: 650;
      letter-spacing: 0;
    }
    select, button, input {
      height: 34px;
      border: 1px solid #3b4652;
      background: #20262e;
      color: #f3f5f7;
      border-radius: 6px;
      padding: 0 10px;
      font: inherit;
    }
    button { cursor: pointer; }
    input {
      min-width: 220px;
      flex: 1 1 220px;
    }
    .spacer { flex: 1; }
    .status {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      color: #aeb8c4;
      font-size: 13px;
      white-space: nowrap;
    }
    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #6ee7b7;
      box-shadow: 0 0 12px #6ee7b7;
    }
    main {
      overflow: hidden;
      display: grid;
      grid-template-rows: auto 1fr;
    }
    .toolbar {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 18px;
      border-bottom: 1px solid #252d36;
      background: #131820;
    }
    pre {
      margin: 0;
      padding: 16px 18px 28px;
      overflow: auto;
      white-space: pre-wrap;
      word-break: break-word;
      font: 13px/1.55 Consolas, "Cascadia Mono", monospace;
      color: #dbe3ea;
      background: #0c0f13;
    }
    mark {
      color: #0c0f13;
      background: #fde68a;
      border-radius: 2px;
      padding: 0 2px;
    }
    @media (max-width: 760px) {
      header, .toolbar { align-items: stretch; flex-wrap: wrap; }
      .spacer { display: none; }
      select, button, input { width: 100%; }
      .status { width: 100%; }
    }
  </style>
</head>
<body>
  <header>
    <h1>Project Logs</h1>
    <span class="status"><span class="dot"></span><span id="state">live</span></span>
    <span class="spacer"></span>
    <button id="openApp">Open App</button>
    <button id="openApi">Open API</button>
  </header>
  <main>
    <div class="toolbar">
      <select id="logName">
        <option value="backend.log">backend.log</option>
        <option value="model-service.log">model-service.log</option>
        <option value="frontend.log">frontend.log</option>
        <option value="log-viewer.log">log-viewer.log</option>
      </select>
      <input id="filter" placeholder="Filter text">
      <button id="refresh">Refresh</button>
      <button id="pause">Pause</button>
    </div>
    <pre id="output">Loading...</pre>
  </main>
  <script>
    const output = document.querySelector("#output");
    const logName = document.querySelector("#logName");
    const filter = document.querySelector("#filter");
    const state = document.querySelector("#state");
    let paused = false;
    let lastText = "";

    function escapeHtml(value) {
      return value.replace(/[&<>"']/g, char => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
      }[char]));
    }

    function render(text) {
      text = text.replace(/\\u001b\\[[0-9;]*m/g, "");
      const needle = filter.value.trim().toLowerCase();
      const lines = needle
        ? text.split("\\n").filter(line => line.toLowerCase().includes(needle))
        : text.split("\\n");
      let html = escapeHtml(lines.join("\\n"));
      if (needle) {
        let escapedNeedle = needle;
        for (const char of "\\\\.*+?^$(){}|[]") {
          escapedNeedle = escapedNeedle.split(char).join("\\\\" + char);
        }
        html = html.replace(new RegExp(escapedNeedle, "gi"), match => "<mark>" + match + "</mark>");
      }
      output.innerHTML = html || "No matching log lines.";
      output.scrollTop = output.scrollHeight;
    }

    async function loadLog() {
      if (paused) return;
      try {
        const response = await fetch("/api/logs/" + encodeURIComponent(logName.value), { cache: "no-store" });
        const payload = await response.json();
        lastText = payload.content || "";
        render(lastText);
        state.textContent = "live - " + new Date().toLocaleTimeString();
      } catch (error) {
        state.textContent = "error";
        output.textContent = String(error);
      }
    }

    document.querySelector("#refresh").addEventListener("click", loadLog);
    document.querySelector("#pause").addEventListener("click", event => {
      paused = !paused;
      event.target.textContent = paused ? "Resume" : "Pause";
      state.textContent = paused ? "paused" : "live";
    });
    filter.addEventListener("input", () => render(lastText));
    logName.addEventListener("change", loadLog);
    document.querySelector("#openApp").addEventListener("click", () => window.open("http://localhost:5173", "_blank"));
    document.querySelector("#openApi").addEventListener("click", () => window.open("http://localhost:8081", "_blank"));
    loadLog();
    setInterval(loadLog, 1500);
  </script>
</body>
</html>`;

async function readTail(fileName) {
  if (!allowedLogs.has(fileName)) {
    return { status: 404, body: { error: "Unknown log file" } };
  }

  const filePath = path.join(logsDir, fileName);
  try {
    const stat = await fs.stat(filePath);
    const length = Math.min(stat.size, 512 * 1024);
    const file = await fs.open(filePath, "r");
    try {
      const buffer = Buffer.alloc(length);
      await file.read(buffer, 0, length, stat.size - length);
      return { status: 200, body: { name: fileName, content: buffer.toString("utf8") } };
    } finally {
      await file.close();
    }
  } catch (error) {
    if (error.code === "ENOENT") {
      return { status: 200, body: { name: fileName, content: "Log file has not been created yet." } };
    }
    return { status: 500, body: { error: error.message } };
  }
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  if (url.pathname === "/") {
    res.writeHead(200, { "content-type": "text/html; charset=utf-8" });
    res.end(page);
    return;
  }

  if (url.pathname.startsWith("/api/logs/")) {
    const fileName = decodeURIComponent(url.pathname.slice("/api/logs/".length));
    const result = await readTail(fileName);
    res.writeHead(result.status, {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store"
    });
    res.end(JSON.stringify(result.body));
    return;
  }

  res.writeHead(404, { "content-type": "text/plain; charset=utf-8" });
  res.end("Not found");
});

await fs.mkdir(logsDir, { recursive: true });

server.listen(port, "127.0.0.1", () => {
  console.log(`Log viewer running at http://localhost:${port}`);
});
