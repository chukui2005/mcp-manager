#!/usr/bin/env node
/**
 * 免注册本地受控 STDIO MCP Server
 * 基于标准输入输出（stdin / stdout）接收 JSON-RPC 2.0 并应答
 * 暴露本地文件和计算工具：local_calc, local_echo
 */
const readline = require('readline');

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false
});

const TOOLS = [
  {
    name: 'local_calc',
    description: '本地极速数学四则运算器 (加减乘除)',
    inputSchema: {
      type: 'object',
      properties: {
        expression: { type: 'string', description: '运算表达式，如 100 * 25 + 50' }
      },
      required: ['expression']
    }
  },
  {
    name: 'local_echo',
    description: '本地回显与时间戳获取工具',
    inputSchema: {
      type: 'object',
      properties: {
        message: { type: 'string', description: '需要回显的消息内容' }
      },
      required: ['message']
    }
  }
];

function sendResponse(id, result, error) {
  const resp = { jsonrpc: '2.0', id };
  if (error) {
    resp.error = error;
  } else {
    resp.result = result;
  }
  process.stdout.write(JSON.stringify(resp) + '\n');
}

rl.on('line', (line) => {
  const trimmed = line.trim();
  if (!trimmed) return;

  try {
    const req = JSON.parse(trimmed);
    const { id, method, params } = req;

    if (method === 'ping') {
      sendResponse(id, {});
      return;
    }

    if (method === 'notifications/initialized') {
      return; // 通知无返回值
    }

    if (method === 'tools/list') {
      sendResponse(id, { tools: TOOLS });
      return;
    }

    if (method === 'tools/call') {
      const toolName = params ? params.name : '';
      const args = params ? params.arguments : {};

      if (toolName === 'local_calc') {
        const expr = String(args.expression || '0').replace(/[^0-9+\-*/.() ]/g, '');
        let val;
        try {
          val = Function(`'use strict'; return (${expr})`)();
        } catch (e) {
          val = '计算错误: ' + e.message;
        }
        sendResponse(id, {
          content: [{ type: 'text', text: `[STDIO 计算器结果] ${expr} = ${val}` }],
          isError: false
        });
        return;
      }

      if (toolName === 'local_echo') {
        const msg = args.message || '';
        const now = new Date().toISOString();
        sendResponse(id, {
          content: [{ type: 'text', text: `[STDIO 本地回显 @ ${now}] ${msg}` }],
          isError: false
        });
        return;
      }

      sendResponse(id, null, { code: -32601, message: '未找到工具: ' + toolName });
      return;
    }

    sendResponse(id, null, { code: -32601, message: '未知方法: ' + method });
  } catch (err) {
    sendResponse(null, null, { code: -32700, message: 'Parse error: ' + err.message });
  }
});
