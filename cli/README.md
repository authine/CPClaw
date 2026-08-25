# CPClaw CLI

CLI 只是 Remote/MCP 适配器，不包含任何云枢业务判断。运行前确保 CPClaw 服务已启动，并设置 `CPCLAW_API_BASE_URL`；默认主体为当前无登录模式的 `huangj`。

```bash
node cli/cpclaw.mjs delegate run --spec task.json
node cli/cpclaw.mjs task status <taskId>
node cli/cpclaw.mjs task events <taskId>
node cli/cpclaw.mjs task continue <taskId> --token <token> --message "补充范围"
node cli/cpclaw.mjs task cancel <taskId>
```
