import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'config/app_config.dart';
import 'models/schema.dart';
import 'net/schema_fetcher.dart';
import 'net/web_schema_listener_stub.dart' if (dart.library.html) 'net/web_schema_listener.dart';
import 'pages/login_page.dart';
import 'render/schema_renderer.dart';

class LowCodeApp extends StatelessWidget {
  const LowCodeApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '低代码移动端',
      theme: ThemeData(
        colorSchemeSeed: const Color(0xFF1976D2),
        useMaterial3: true,
        fontFamilyFallback: const ['Noto Sans SC', 'Microsoft YaHei', 'PingFang SC', 'Heiti SC', 'sans-serif', 'serif'],
        inputDecorationTheme: const InputDecorationTheme(
          isDense: true,
          contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        ),
      ),
      initialRoute: '/',
      onGenerateRoute: (settings) {
        if (settings.name == '/settings') {
          return MaterialPageRoute(builder: (_) => const SettingsPage());
        }
        final pageCode = settings.name?.replaceAll('/', '') ?? 'home';
        return MaterialPageRoute(
          builder: (_) => SchemaPage(pageCode: pageCode, params: settings.arguments as Map<String, dynamic>?),
        );
      },
    );
  }
}

class SchemaPage extends StatefulWidget {
  final String pageCode;
  final Map<String, dynamic>? params;

  const SchemaPage({super.key, required this.pageCode, this.params});

  @override
  State<SchemaPage> createState() => _SchemaPageState();
}

class _SchemaPageState extends State<SchemaPage> {
  PageSchema? _schema;
  String? _error;
  bool _loading = true;
  final _formData = <String, dynamic>{};
  final _scaffoldKey = GlobalKey<ScaffoldMessengerState>();

  @override
  void initState() {
    super.initState();
    if (widget.params != null) {
      _formData.addAll(widget.params!);
    }
    if (kIsWeb) {
      _loading = true;
      setupWebSchemaListener((schema, editMode, selectedId) {
        if (mounted) {
          setState(() {
            _schema = schema;
            _loading = false;
          });
          SchemaRenderer.editMode = editMode;
          SchemaRenderer.selectedId = selectedId;
        }
      });
    } else {
      _loadSchema();
    }
  }

  Future<void> _loadSchema() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final schema = await SchemaFetcher.instance.fetch(widget.pageCode);
      setState(() {
        _schema = schema;
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  void _onNavigate(String pageCode, {Map<String, dynamic>? params, String? mode}) {
    if (pageCode.isEmpty) return;
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => SchemaPage(pageCode: pageCode, params: params),
      ),
    );
  }

  void _onShowSnackbar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), duration: const Duration(seconds: 2)),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (_error != null) {
      return Scaffold(
        appBar: AppBar(title: const Text('错误')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error_outline, size: 48, color: Colors.red),
                const SizedBox(height: 16),
                Text(_error!, textAlign: TextAlign.center),
                const SizedBox(height: 24),
                ElevatedButton(
                  onPressed: _loadSchema,
                  child: const Text('重试'),
                ),
                const SizedBox(height: 8),
                OutlinedButton(
                  onPressed: () => Navigator.pushNamed(context, '/settings'),
                  child: const Text('服务器设置'),
                ),
              ],
            ),
          ),
        ),
      );
    }
    if (_schema == null) {
      return const Scaffold(body: Center(child: Text('页面不存在')));
    }

    final rc = RenderContext(
      formData: _formData,
      onNavigate: _onNavigate,
      onShowSnackbar: _onShowSnackbar,
      onRefresh: _loadSchema,
      onClosePage: () => Navigator.of(context).maybePop(),
      masterId: widget.params?['masterId']?.toString(),
    );

    return SchemaRenderer.render(_schema!.root, context, rc);
  }
}

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  late TextEditingController _urlController;

  @override
  void initState() {
    super.initState();
    _urlController = TextEditingController(text: AppConfig.instance.baseUrl);
  }

  @override
  void dispose() {
    _urlController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('服务器设置')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('服务器地址', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500)),
            const SizedBox(height: 8),
            TextField(
              controller: _urlController,
              decoration: const InputDecoration(
                hintText: 'http://192.168.x.x:7000',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: () async {
                  await AppConfig.instance.setBaseUrl(_urlController.text.trim());
                  SchemaFetcher.instance.clearCache();
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('已保存，请重新进入页面')),
                    );
                    Navigator.pop(context);
                  }
                },
                child: const Text('保存'),
              ),
            ),
            const SizedBox(height: 24),
            const Divider(),
            const SizedBox(height: 16),
            const Text('说明', style: TextStyle(fontSize: 14, color: Colors.grey)),
            const SizedBox(height: 8),
            const Text(
              '• 默认自动检测本机局域网 IP 并连接 7000 端口网关\n'
              '• 生产环境请改为 HTTPS 地址\n'
              '• 修改后需重新进入页面生效',
              style: TextStyle(fontSize: 13, color: Colors.grey),
            ),
            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton(
                style: OutlinedButton.styleFrom(foregroundColor: Colors.red),
                onPressed: () async {
                  await AppConfig.instance.logout();
                  SchemaFetcher.instance.clearCache();
                  if (!mounted) return;
                  Navigator.of(context).pushAndRemoveUntil(
                    MaterialPageRoute(builder: (_) => const LoginPage()),
                    (_) => false,
                  );
                },
                child: const Text('退出登录'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
