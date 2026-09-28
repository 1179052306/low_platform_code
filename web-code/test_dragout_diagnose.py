"""Diagnose why DataGrid can't be dragged out of splitter-panel to canvas root."""
from playwright.sync_api import sync_playwright
import json


def main() -> None:
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1400, "height": 900})
        page.on("console", lambda msg: print(f"[CONSOLE] {msg.type}: {msg.text}") if "drag-debug" in msg.text else None)
        page.on("console", lambda msg: print(f"[CONSOLE] {msg.type}: {msg.text}") if msg.type == "warning" and "lowcode" not in msg.text.lower() else None)

        page.goto('http://localhost:5173')
        page.wait_for_load_state('networkidle')
        page.wait_for_timeout(800)

        # 1) Drag splitter to canvas
        splitter_item = page.locator('.material-item:has-text("分割面板")').first
        canvas = page.locator('.canvas-page').first
        s_box = splitter_item.bounding_box()
        c_box = canvas.bounding_box()
        assert s_box and c_box

        page.mouse.move(s_box['x'] + s_box['width'] / 2, s_box['y'] + s_box['height'] / 2)
        page.mouse.down()
        page.wait_for_timeout(200)
        page.mouse.move(c_box['x'] + 300, c_box['y'] + 200, steps=10)
        page.wait_for_timeout(200)
        page.mouse.up()
        page.wait_for_timeout(800)

        # 2) Drag DataGrid into first panel
        grid_item = page.locator('.material-item:has-text("数据表格")').first
        grid_item.scroll_into_view_if_needed()
        page.wait_for_timeout(200)
        panel = page.locator('.lc-el-splitter-panel').first
        g_box = grid_item.bounding_box()
        p_box = panel.bounding_box()
        assert g_box and p_box

        page.mouse.move(g_box['x'] + g_box['width'] / 2, g_box['y'] + g_box['height'] / 2)
        page.mouse.down()
        page.wait_for_timeout(200)
        page.mouse.move(p_box['x'] + p_box['width'] / 4, p_box['y'] + p_box['height'] / 4, steps=10)
        page.wait_for_timeout(200)
        page.mouse.up()
        page.wait_for_timeout(800)

        # 3) Click to select the DataGrid (inside the first panel)
        # 注意：不能用 .canvas-node:has(.data-grid-wrapper)，那会匹配到最外层的 splitter。
        # 必须从 .data-grid-wrapper 向上找最近的 .canvas-node 祖先，才是 DataGrid 自身节点。
        gn_box = page.evaluate('''() => {
            const w = document.querySelector('.data-grid-wrapper');
            if (!w) return null;
            const cn = w.closest('.canvas-node');
            if (!cn) return null;
            const r = cn.getBoundingClientRect();
            return { x: r.x, y: r.y, width: r.width, height: r.height, id: cn.id };
        }''')
        assert gn_box, 'DataGrid node not found'
        print(f"DataGrid node: {gn_box['id']} at ({gn_box['x']}, {gn_box['y']}) size {gn_box['width']}x{gn_box['height']}")
        click_x = gn_box['x'] + gn_box['width'] / 2
        click_y = gn_box['y'] + gn_box['height'] / 2

        # Inspect what element is at the click target
        what = page.evaluate(f'''() => {{
            const el = document.elementFromPoint({click_x}, {click_y});
            if (!el) return null;
            return {{
                tag: el.tagName,
                class: el.className?.toString?.()?.substring(0, 100),
                id: el.id,
                closestCanvasNodeId: el.closest('.canvas-node')?.id,
            }};
        }}''')
        print(f'Element at click ({click_x}, {click_y}):')
        print(' ', what)

        page.mouse.click(click_x, click_y)
        page.wait_for_timeout(300)

        # 4) Diagnose: what does elementFromPoint return at intended drop position?
        # Target: outside splitter-panel, near bottom-right of canvas
        target_x = c_box['x'] + c_box['width'] - 100  # 1070
        target_y = c_box['y'] + c_box['height'] - 100  # 783

        def describe(x, y):
            return page.evaluate(f'''() => {{
                const el = document.elementFromPoint({x}, {y});
                if (!el) return null;
                const cn = el.closest('.canvas-node');
                return {{
                    tag: el.tagName,
                    class: el.className?.substring(0, 100),
                    id: el.id,
                    canvasNodeId: cn?.id,
                    canvasNodeClass: cn?.className?.substring(0, 100),
                }};
            }}''')

        print('Element at target (1070, 783) BEFORE drag:')
        print(' ', describe(target_x, target_y))

        # 5) Try drag-out using custom mouse moves
        start_x = gn_box['x'] + gn_box['width'] / 2
        start_y = gn_box['y'] + gn_box['height'] / 2
        page.mouse.move(start_x, start_y)
        page.mouse.down()
        page.wait_for_timeout(200)

        # Capture state mid-drag (close to target but before mouseup)
        page.mouse.move(target_x - 50, target_y - 50, steps=10)
        page.wait_for_timeout(200)
        print('Element at target (1020, 733) DURING drag (before hide):')
        print(' ', describe(target_x - 50, target_y - 50))

        page.mouse.move(target_x, target_y, steps=10)
        page.wait_for_timeout(200)
        print('Element at target (1070, 783) JUST BEFORE mouseup:')
        print(' ', describe(target_x, target_y))

        page.mouse.up()
        page.wait_for_timeout(500)
        page.screenshot(path='test_screenshots/dragout_01_after.png')

        # 6) Inspect resulting tree
        result = page.evaluate('''() => {
            const nodes = document.querySelectorAll('.canvas-node');
            return Array.from(nodes).map(el => {
                const cs = window.getComputedStyle(el);
                return {
                    id: el.id,
                    type: el.getAttribute('data-type') || (el.querySelector('[data-material-type]')?.getAttribute('data-material-type')),
                    class: el.className?.substring(0, 80),
                    parentClass: el.parentElement?.className?.substring(0, 80),
                    parentTag: el.parentElement?.tagName,
                    rect: el.getBoundingClientRect().toJSON(),
                };
            });
        }''')
        print('Tree after drag-out:')
        for n in result:
            print(' ', n)

        browser.close()


if __name__ == '__main__':
    main()
