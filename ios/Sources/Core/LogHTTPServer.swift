import Foundation
#if canImport(Network)
import Network
#endif

#if DEBUG

/// 简单的HTTP服务器用于日志查看
class LogHTTPServer {
    private let port: UInt16
    private var listener: NWListener?
    private var connections: [NWConnection] = []
    
    init(port: UInt16) throws {
        self.port = port
        let parameters = NWParameters.tcp
        self.listener = try NWListener(using: parameters, on: NWEndpoint.Port(rawValue: port)!)
    }
    
    func start() throws {
        listener?.newConnectionHandler = { [weak self] connection in
            self?.connections.append(connection)
            self?.handleConnection(connection)
            connection.start(queue: .main)
        }
        
        listener?.stateUpdateHandler = { state in
            switch state {
            case .ready:
                DMPLogger.info("HTTP服务器已启动，端口: \(self.port)", file: "LogHTTPServer.swift", function: "start", line: 0)
            case .failed(let error):
                DMPLogger.error("HTTP服务器启动失败: \(error.localizedDescription)", file: "LogHTTPServer.swift", function: "start", line: 0)
            default:
                break
            }
        }
        
        listener?.start(queue: .main)
    }
    
    func stop() {
        listener?.cancel()
        connections.forEach { $0.cancel() }
        connections.removeAll()
    }
    
    private func handleConnection(_ connection: NWConnection) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] content, _, isComplete, error in
            guard let self = self, let content = content, error == nil else {
                connection.cancel()
                return
            }
            
            let request = String(data: content, encoding: .utf8) ?? ""
            let response = self.handleRequest(request)
            
            if let responseData = response.data(using: .utf8) {
                connection.send(content: responseData, completion: .contentProcessed { error in
                    if error != nil {
                        connection.cancel()
                    }
                })
            }
            
            if isComplete {
                connection.cancel()
            } else {
                connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { content, _, isComplete, error in
                    // Continue receiving
                }
            }
        }
    }
    
    private func handleRequest(_ request: String) -> String {
        let lines = request.components(separatedBy: "\n")
        guard let firstLine = lines.first else {
            return createErrorResponse(400, "Bad Request")
        }
        
        let parts = firstLine.components(separatedBy: " ")
        guard parts.count >= 2 else {
            return createErrorResponse(400, "Bad Request")
        }
        
        let method = parts[0]
        let path = parts[1]
        
        guard method == "GET" else {
            return createErrorResponse(405, "Method Not Allowed")
        }
        
        switch path {
        case "/", "/index.html":
            return createHTMLResponse()
        case "/api/logs":
            return createJSONResponse(LogBuffer.shared.getRecentLogs(count: 1000))
        case "/api/logs/recent":
            let count = extractCountFromQuery(path, defaultValue: 100)
            return createJSONResponse(LogBuffer.shared.getRecentLogs(count: count))
        case "/api/status":
            return createStatusResponse()
        default:
            return createErrorResponse(404, "Not Found")
        }
    }
    
    private func extractCountFromQuery(_ path: String, defaultValue: Int) -> Int {
        guard let queryStart = path.firstIndex(of: "?") else {
            return defaultValue
        }
        
        let queryString = path[path.index(after: queryStart)...]
        let params = queryString.components(separatedBy: "&")
        
        for param in params {
            let parts = param.components(separatedBy: "=")
            if parts.count == 2 && parts[0] == "count", let count = Int(parts[1]) {
                return count
            }
        }
        
        return defaultValue
    }
    
    private func createHTMLResponse() -> String {
        let html = """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>DMP 日志查看器</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    background: #1e1e1e;
                    color: #d4d4d4;
                    padding: 20px;
                }
                .header {
                    background: #2d2d30;
                    padding: 15px 20px;
                    border-radius: 8px;
                    margin-bottom: 20px;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                }
                .header h1 { color: #4ec9b0; font-size: 20px; }
                .status { color: #4ec9b0; font-size: 14px; }
                .controls {
                    display: flex;
                    gap: 10px;
                    margin-bottom: 20px;
                    flex-wrap: wrap;
                }
                .controls button {
                    background: #0e639c;
                    color: white;
                    border: none;
                    padding: 8px 16px;
                    border-radius: 4px;
                    cursor: pointer;
                    font-size: 14px;
                }
                .controls button:hover { background: #1177bb; }
                .controls button.active { background: #4ec9b0; }
                .log-container {
                    background: #1e1e1e;
                    border: 1px solid #3e3e42;
                    border-radius: 8px;
                    padding: 15px;
                    max-height: 70vh;
                    overflow-y: auto;
                    font-family: 'Menlo', 'Monaco', monospace;
                    font-size: 13px;
                    line-height: 1.6;
                }
                .log-entry { padding: 2px 0; border-bottom: 1px solid #2d2d30; }
                .log-trace { color: #6a9955; }
                .log-debug { color: #569cd6; }
                .log-info { color: #d4d4d4; }
                .log-warn { color: #dcdcaa; }
                .log-error { color: #f44747; }
                .log-fatal { color: #f44747; font-weight: bold; }
                .log-time { color: #808080; }
                .log-traceid { color: #c586c0; }
                .stats {
                    background: #2d2d30;
                    padding: 10px 15px;
                    border-radius: 8px;
                    margin-bottom: 20px;
                    display: flex;
                    gap: 20px;
                    font-size: 13px;
                    flex-wrap: wrap;
                }
                .stat-item { color: #808080; }
                .stat-value { color: #4ec9b0; font-weight: bold; }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>🔍 DMP 日志查看器 (Debug Mode)</h1>
                <span class="status">● 实时连接</span>
            </div>
            
            <div class="stats">
                <div class="stat-item">总日志: <span class="stat-value" id="total-count">0</span></div>
                <div class="stat-item">错误: <span class="stat-value" id="error-count">0</span></div>
                <div class="stat-item">警告: <span class="stat-value" id="warn-count">0</span></div>
                <div class="stat-item">更新: <span class="stat-value" id="last-update">-</span></div>
            </div>

            <div class="controls">
                <button id="btn-all" class="active" onclick="filterLogs('all')">全部</button>
                <button id="btn-error" onclick="filterLogs('error')">错误</button>
                <button id="btn-warn" onclick="filterLogs('warn')">警告</button>
                <button id="btn-info" onclick="filterLogs('info')">信息</button>
                <button id="btn-clear" onclick="clearLogs()">清空</button>
                <button id="btn-autoscroll" class="active" onclick="toggleAutoScroll()">自动滚动 ✓</button>
            </div>

            <div class="log-container" id="log-container">
                <div style="color: #808080; text-align: center; padding: 40px;">
                    等待日志加载...
                </div>
            </div>

            <script>
                let autoScroll = true;
                let currentFilter = 'all';
                let allLogs = [];

                function filterLogs(level) {
                    currentFilter = level;
                    document.querySelectorAll('.controls button').forEach(btn => btn.classList.remove('active'));
                    document.getElementById('btn-' + level).classList.add('active');
                    renderLogs();
                }

                function toggleAutoScroll() {
                    autoScroll = !autoScroll;
                    const btn = document.getElementById('btn-autoscroll');
                    btn.classList.toggle('active');
                    btn.textContent = autoScroll ? '自动滚动 ✓' : '自动滚动';
                }

                function clearLogs() {
                    fetch('/api/logs/clear', { method: 'POST' }).then(() => {
                        allLogs = [];
                        renderLogs();
                    });
                }

                function renderLogs() {
                    const container = document.getElementById('log-container');
                    const filteredLogs = currentFilter === 'all' 
                        ? allLogs 
                        : allLogs.filter(log => log.level.toLowerCase().includes(currentFilter));

                    if (filteredLogs.length === 0) {
                        container.innerHTML = '<div style="color: #808080; text-align: center; padding: 40px;">暂无日志</div>';
                        return;
                    }

                    container.innerHTML = filteredLogs.map(log => {
                        const levelClass = 'log-' + log.level.toLowerCase();
                        const traceIdHtml = log.traceId ? '<span class="log-traceid">[' + log.traceId + ']</span> ' : '';
                        return '<div class="log-entry ' + levelClass + '">' +
                               '<span class="log-time">' + log.time + '</span> ' +
                               traceIdHtml +
                               '[' + log.level + '] ' +
                               log.message +
                               '</div>';
                    }).join('');

                    if (autoScroll) {
                        container.scrollTop = container.scrollHeight;
                    }

                    document.getElementById('total-count').textContent = allLogs.length;
                    document.getElementById('error-count').textContent = allLogs.filter(l => l.level === 'ERROR' || l.level === 'FATAL').length;
                    document.getElementById('warn-count').textContent = allLogs.filter(l => l.level === 'WARN').length;
                    document.getElementById('last-update').textContent = new Date().toLocaleTimeString();
                }

                async function fetchLogs() {
                    try {
                        const response = await fetch('/api/logs/recent?count=100');
                        const data = await response.json();
                        allLogs = data.logs || [];
                        renderLogs();
                    } catch (e) {
                        console.error('获取日志失败:', e);
                    }
                }

                fetchLogs();
                setInterval(fetchLogs, 2000);
            </script>
        </body>
        </html>
        """
        
        return """
        HTTP/1.1 200 OK
        Content-Type: text/html; charset=utf-8
        Content-Length: \(html.utf8.count)
        Connection: close
        
        \(html)
        """
    }
    
    private func createJSONResponse(_ logs: [DMPLogEntry]) -> String {
        let dateFormatter = ISO8601DateFormatter()
        dateFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        
        let jsonLogs = logs.map { entry in
            return """
            {
                "time": "\(dateFormatter.string(from: entry.timestamp))",
                "level": "\(entry.level.description)",
                "tag": "\(entry.tag)",
                "traceId": "\(entry.traceID ?? "")",
                "message": "\(entry.message.replacingOccurrences(of: "\"", with: "\\\""))",
                "file": "\(entry.file)",
                "function": "\(entry.function)",
                "line": \(entry.line)
            }
            """
        }.joined(separator: ",\n")
        
        let json = """
        {"logs": [\n\(jsonLogs)\n], "count": \(logs.count)}
        """
        
        return """
        HTTP/1.1 200 OK
        Content-Type: application/json; charset=utf-8
        Content-Length: \(json.utf8.count)
        Connection: close
        
        \(json)
        """
    }
    
    private func createStatusResponse() -> String {
        let dateFormatter = ISO8601DateFormatter()
        dateFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        
        let status = """
        {
            "status": "running",
            "mode": "debug",
            "timestamp": "\(dateFormatter.string(from: Date()))"
        }
        """
        
        return """
        HTTP/1.1 200 OK
        Content-Type: application/json; charset=utf-8
        Content-Length: \(status.utf8.count)
        Connection: close
        
        \(status)
        """
    }
    
    private func createErrorResponse(_ code: Int, _ message: String) -> String {
        let body = "{\"error\": \"\(message)\"}"
        return """
        HTTP/1.1 \(code) \(message)
        Content-Type: application/json; charset=utf-8
        Content-Length: \(body.utf8.count)
        Connection: close
        
        \(body)
        """
    }
}

#endif