/**
 * 免注册本地 MCP HTTP Demo 服务
 * 模拟天气查询 (get_weather) 与 高危系统重启 (system_reboot) 工具，
 * 供 mcp-manager 控制面网关测试工具聚合、Tool Picker 勾选治理、参数脱敏与实时审计流。
 */
const http = require('http');

const PORT = 19090;

const TOOLS = [
  {
    name: 'get_weather',
    description: '获取指定城市的当前实时天气信息',
    inputSchema: {
      type: 'object',
      properties: {
        city: { type: 'string', description: '城市名称，如 Beijing, Shanghai' },
        token: { type: 'string', description: '调用凭证或敏感信息（网关应自动打码脱敏）' }
      },
      required: ['city']
    }
  },
  {
    name: 'system_reboot',
    description: '危险高危操作：重启生产系统集群（测试 Tool Picker 禁用与阻断）',
    inputSchema: {
      type: 'object',
      properties: {
        reason: { type: 'string', description: '重启原因' }
      },
      required: ['reason']
    }
  }
];

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  if (req.method === 'POST') {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      try {
        const json = JSON.parse(body);
        const { id, method, params } = json;

        if (method === 'ping') {
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ jsonrpc: '2.0', id, result: {} }));
          return;
        }

        if (method === 'tools/list') {
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({
            jsonrpc: '2.0',
            id,
            result: { tools: TOOLS }
          }));
          return;
        }

        if (method === 'tools/call') {
          const toolName = params ? params.name : '';
          const args = params ? params.arguments : {};

          if (toolName === 'get_weather') {
            const city = args.city || '未知城市';
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
              jsonrpc: '2.0',
              id,
              result: {
                content: [
                  { type: 'text', text: `[天气预报] 城市: ${city}, 天气: 晴朗, 温度: 24℃, 空气质量: 优` }
                ],
                isError: false
              }
            }));
            return;
          }

          if (toolName === 'system_reboot') {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({
              jsonrpc: '2.0',
              id,
              result: {
                content: [
                  { type: 'text', text: '警告：已触发集群重启，原因: ' + (args.reason || '无') }
                ],
                isError: false
              }
            }));
            return;
          }

          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({
            jsonrpc: '2.0',
            id,
            error: { code: -32601, message: '未找到工具: ' + toolName }
          }));
          return;
        }

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
          jsonrpc: '2.0',
          id,
          error: { code: -32601, message: '未知方法: ' + method }
        }));
      } catch (err) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ jsonrpc: '2.0', id: null, error: { code: -32700, message: 'Parse error' } }));
      }
    });
  } else {
    res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('MCP Demo Service Running on port ' + PORT);
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[MCP-DEMO] 本地免注册 MCP 服务已在端口 ${PORT} 启动 (http://127.0.0.1:${PORT})`);
});
