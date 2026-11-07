import 'dart:html' as html;

void postToParent(String msg) {
  try {
    html.window.parent?.postMessage(msg, '*');
  } catch (_) {}
}