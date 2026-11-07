import 'package:flutter/material.dart';
import '../app.dart';
import '../config/app_config.dart';
import '../net/api_client.dart';
import '../net/schema_fetcher.dart';

const _primary = Color(0xFF2F6Fed);
const _bgDark = Color(0xFF0F2540);
const _bgMid = Color(0xFF1E3A5F);
const _cardBg = Colors.white;
const _textMain = Color(0xFF2C3E50);
const _textSub = Color(0xFF7F8C8D);
const _fieldBg = Color(0xFFF5F7FA);
const _fieldBorder = Color(0xFFE4E7EB);

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _userCtrl = TextEditingController();
  final _pwdCtrl = TextEditingController();
  final _ipCtrl = TextEditingController();
  final _portCtrl = TextEditingController();

  bool _obscure = true;
  bool _loading = false;
  bool _showServerConfig = false;
  bool _rememberMe = false;
  String _connMode = 'gateway';
  String _urlPrefix = 'http://';

  @override
  void initState() {
    super.initState();
    final cfg = AppConfig.instance;
    _userCtrl.text = cfg.savedUsername;
    _pwdCtrl.text = cfg.savedPassword;
    _rememberMe = cfg.rememberMe;
    _connMode = cfg.connMode;
    _urlPrefix = cfg.urlPrefix;
    _ipCtrl.text = cfg.ip.isEmpty ? 'localhost' : cfg.ip;
    _portCtrl.text = cfg.port.isEmpty
        ? (_connMode == 'gateway' ? '7000' : '80')
        : cfg.port;
  }

  @override
  void dispose() {
    _userCtrl.dispose();
    _pwdCtrl.dispose();
    _ipCtrl.dispose();
    _portCtrl.dispose();
    super.dispose();
  }

  String _buildUrl() {
    final ip = _ipCtrl.text.trim().isEmpty ? 'localhost' : _ipCtrl.text.trim();
    final port = _portCtrl.text.trim().isEmpty
        ? (_connMode == 'gateway' ? '7000' : '80')
        : _portCtrl.text.trim();
    return '$_urlPrefix$ip:$port';
  }

  Future<void> _handleLogin() async {
    final username = _userCtrl.text.trim();
    final password = _pwdCtrl.text.trim();
    if (username.isEmpty || password.isEmpty) {
      _toast('请输入账号和密码');
      return;
    }
    final url = _buildUrl();
    setState(() => _loading = true);
    try {
      await AppConfig.instance.saveServerConfig(
        connMode: _connMode,
        urlPrefix: _urlPrefix,
        ip: _ipCtrl.text.trim().isEmpty ? 'localhost' : _ipCtrl.text.trim(),
        port: _portCtrl.text.trim().isEmpty
            ? (_connMode == 'gateway' ? '7000' : '80')
            : _portCtrl.text.trim(),
      );
      SchemaFetcher.instance.clearCache();
      final resp = await ApiClient.instance.login(username, password);
      final code = resp['code'];
      final data = resp['data'];
      String token = '';
      if (data is Map) {
        token = (data['token'] ?? data['access_token'] ?? '').toString();
      }
      if (code == 200 || code == 0 || code == '0' || code == '200' || token.isNotEmpty) {
        if (token.isEmpty) token = 'mock_${DateTime.now().millisecondsSinceEpoch}';
        await AppConfig.instance.setLogin(token: token, userName: username);
        await AppConfig.instance.saveRememberMe(_rememberMe,
            username: username, password: password);
        if (!mounted) return;
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (_) => const LowCodeApp()),
        );
      } else {
        _toast(resp['msg']?.toString() ?? '登录失败');
      }
    } catch (e) {
      _toast('登录失败：$e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _toast(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [_bgMid, _bgDark],
          ),
        ),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 24),
              child: _buildCard(),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildCard() {
    return Container(
      decoration: BoxDecoration(
        color: _cardBg,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.15),
            blurRadius: 20,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      padding: const EdgeInsets.all(28),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Center(
            child: Icon(Icons.warehouse_outlined, size: 48, color: _primary),
          ),
          const SizedBox(height: 12),
          const Center(
            child: Text(
              '欢迎登录',
              style: TextStyle(
                color: _textMain,
                fontSize: 22,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          const SizedBox(height: 4),
          Center(
            child: Text(
              '当前地址：${AppConfig.instance.baseUrl}',
              style: const TextStyle(color: _textSub, fontSize: 12),
            ),
          ),
          const SizedBox(height: 24),
          _buildField(controller: _userCtrl, hint: '账号', icon: Icons.person_outline),
          const SizedBox(height: 14),
          _buildField(
            controller: _pwdCtrl,
            hint: '密码',
            icon: Icons.lock_outline,
            obscure: _obscure,
            suffix: IconButton(
              icon: Icon(_obscure ? Icons.visibility_off : Icons.visibility,
                  color: _textSub, size: 20),
              onPressed: () => setState(() => _obscure = !_obscure),
            ),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              SizedBox(
                width: 20,
                height: 20,
                child: Checkbox(
                  value: _rememberMe,
                  onChanged: (v) => setState(() => _rememberMe = v ?? false),
                  activeColor: _primary,
                  checkColor: Colors.white,
                  side: const BorderSide(color: _fieldBorder, width: 1.5),
                ),
              ),
              const SizedBox(width: 8),
              const Text('记住密码', style: TextStyle(color: _textSub, fontSize: 14)),
            ],
          ),
          const SizedBox(height: 8),
          _buildServerConfig(),
          const SizedBox(height: 16),
          _buildLoginButton(),
          const SizedBox(height: 8),
          TextButton(
            onPressed: () => setState(() => _showServerConfig = !_showServerConfig),
            child: Text(
              _showServerConfig ? '收起服务器配置' : '配置服务器地址',
              style: const TextStyle(color: _primary, fontSize: 13),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildField({
    required TextEditingController controller,
    required String hint,
    required IconData icon,
    bool obscure = false,
    Widget? suffix,
  }) {
    return TextField(
      controller: controller,
      obscureText: obscure,
      style: const TextStyle(color: _textMain, fontSize: 14),
      decoration: InputDecoration(
        hintText: hint,
        hintStyle: const TextStyle(color: _textSub, fontSize: 14),
        prefixIcon: Icon(icon, color: _textSub, size: 20),
        suffixIcon: suffix,
        filled: true,
        fillColor: _fieldBg,
        contentPadding: const EdgeInsets.symmetric(vertical: 12),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: _fieldBorder, width: 1),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: _primary, width: 1.5),
        ),
      ),
    );
  }

  Widget _buildServerConfig() {
    return AnimatedSize(
      duration: const Duration(milliseconds: 250),
      curve: Curves.easeInOut,
      alignment: Alignment.topCenter,
      child: _showServerConfig
          ? Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Divider(height: 1, color: _fieldBorder),
                const SizedBox(height: 16),
                const Text('连接方式', style: TextStyle(color: _textSub, fontSize: 13)),
                const SizedBox(height: 6),
                _buildConnModeSelector(),
                const SizedBox(height: 14),
                const Text('前缀', style: TextStyle(color: _textSub, fontSize: 13)),
                const SizedBox(height: 6),
                _buildPrefixDropdown(),
                const SizedBox(height: 14),
                const Text('IP 地址', style: TextStyle(color: _textSub, fontSize: 13)),
                const SizedBox(height: 6),
                _buildField(
                  controller: _ipCtrl,
                  hint: 'localhost 或 192.168.x.x',
                  icon: Icons.dns,
                ),
                const SizedBox(height: 14),
                const Text('端口号', style: TextStyle(color: _textSub, fontSize: 13)),
                const SizedBox(height: 6),
                _buildField(
                  controller: _portCtrl,
                  hint: _connMode == 'gateway' ? '7000' : '80',
                  icon: Icons.numbers,
                ),
                const SizedBox(height: 12),
                Align(
                  alignment: Alignment.centerRight,
                  child: TextButton.icon(
                    onPressed: () async {
                      await AppConfig.instance.saveServerConfig(
                        connMode: _connMode,
                        urlPrefix: _urlPrefix,
                        ip: _ipCtrl.text.trim().isEmpty
                            ? 'localhost'
                            : _ipCtrl.text.trim(),
                        port: _portCtrl.text.trim().isEmpty
                            ? (_connMode == 'gateway' ? '7000' : '80')
                            : _portCtrl.text.trim(),
                      );
                      SchemaFetcher.instance.clearCache();
                      _toast('服务器配置已保存：${_buildUrl()}');
                    },
                    icon: const Icon(Icons.save, color: _primary, size: 18),
                    label: const Text('保存配置', style: TextStyle(color: _primary, fontSize: 13)),
                  ),
                ),
              ],
            )
          : const SizedBox.shrink(),
    );
  }

  Widget _buildConnModeSelector() {
    return Row(
      children: [
        Expanded(child: _buildModeRadio('gateway', 'Gateway 网关')),
        const SizedBox(width: 12),
        Expanded(child: _buildModeRadio('nginx', 'Nginx 代理')),
      ],
    );
  }

  Widget _buildModeRadio(String value, String label) {
    final selected = _connMode == value;
    return InkWell(
      borderRadius: BorderRadius.circular(8),
      onTap: () {
        setState(() {
          _connMode = value;
          if (_portCtrl.text.trim().isEmpty ||
              _portCtrl.text.trim() == '7000' ||
              _portCtrl.text.trim() == '80') {
            _portCtrl.text = value == 'gateway' ? '7000' : '80';
          }
        });
      },
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 12),
        decoration: BoxDecoration(
          color: selected ? _primary.withOpacity(0.08) : _fieldBg,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: selected ? _primary : _fieldBorder,
            width: selected ? 1.5 : 1,
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              selected ? Icons.radio_button_checked : Icons.radio_button_unchecked,
              color: selected ? _primary : _textSub,
              size: 16,
            ),
            const SizedBox(width: 6),
            Flexible(
              child: Text(
                label,
                style: TextStyle(
                  color: selected ? _primary : _textSub,
                  fontSize: 13,
                ),
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPrefixDropdown() {
    return Container(
      decoration: BoxDecoration(
        color: _fieldBg,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: _fieldBorder, width: 1),
      ),
      padding: const EdgeInsets.symmetric(horizontal: 12),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<String>(
          value: _urlPrefix,
          dropdownColor: Colors.white,
          style: const TextStyle(color: _textMain, fontSize: 14),
          icon: const Icon(Icons.arrow_drop_down, color: _textSub),
          isExpanded: true,
          items: const [
            DropdownMenuItem(value: 'http://', child: Text('http://')),
            DropdownMenuItem(value: 'https://', child: Text('https://')),
          ],
          onChanged: (v) {
            if (v != null) setState(() => _urlPrefix = v);
          },
        ),
      ),
    );
  }

  Widget _buildLoginButton() {
    return SizedBox(
      height: 46,
      child: Material(
        color: _primary,
        borderRadius: BorderRadius.circular(8),
        child: InkWell(
          borderRadius: BorderRadius.circular(8),
          onTap: _loading ? null : _handleLogin,
          child: Center(
            child: _loading
                ? const SizedBox(
                    width: 22,
                    height: 22,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                    ),
                  )
                  : const Text(
                      '登 录',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        letterSpacing: 8,
                      ),
                    ),
          ),
        ),
      ),
    );
  }
}