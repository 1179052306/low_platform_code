"""Verify DataGrid with css.height='100%' does not exceed splitter-panel boundary."""
from playwright.sync_api import sync_playwright


def main() -> None:
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1400, "height": 900})
        page.on("console", lambda msg: print(f"[CONSOLE] {msg.type}: {msg.text}"))

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

        # 2) Drag DataGrid into first splitter panel
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
        page.screenshot(path='test_screenshots/height_01_initial.png')

        # 3) Select the DataGrid node. Click on a non-toolbar area; toolbar lives at the top 40px.
        grid_node = page.locator('.canvas-node').filter(
            has=page.locator('.data-grid-wrapper')
        ).first
        gn_box = grid_node.bounding_box()
        assert gn_box
        page.mouse.click(gn_box['x'] + gn_box['width'] / 2, gn_box['y'] + gn_box['height'] / 2 + 10)
        page.wait_for_timeout(400)
        page.screenshot(path='test_screenshots/height_02_selected.png')

        # 4) Set css.height to "100" with unit "%" via the property panel
        # The 高度 row's input + select is the second prop-item in the 尺寸 section.
        # Use locating patterns that match the actual template markup.
        height_input = page.locator(
            '.prop-item:has(.prop-label:has-text("高度")) .css-unit-input'
        ).first
        height_unit_select = page.locator(
            '.prop-item:has(.prop-label:has-text("高度")) .css-unit-select'
        ).first

        height_unit_select.select_option('%')
        page.wait_for_timeout(200)
        height_input.fill('100')
        height_input.press('Tab')
        page.wait_for_timeout(800)
        page.screenshot(path='test_screenshots/height_03_set_100pct.png')

        # 5) Measure: split panel bottom vs DataGrid render bottoms
        result = page.evaluate('''() => {
            const panel = document.querySelector('.lc-el-splitter-panel');
            const gridNode = Array.from(document.querySelectorAll('.canvas-node'))
                .find(n => n.querySelector('.data-grid-wrapper'));
            if (!panel || !gridNode) return null;
            const dxRoot = gridNode.querySelector('.dx-widget');
            const wrapper = gridNode.querySelector('.data-grid-wrapper');
            const grid = gridNode.querySelector('.dx-datagrid');

            const getRect = (el) => {
                if (!el) return null;
                const r = el.getBoundingClientRect();
                return {
                    top: Math.round(r.top), bottom: Math.round(r.bottom),
                    left: Math.round(r.left), right: Math.round(r.right),
                    width: Math.round(r.width), height: Math.round(r.height),
                };
            };
            return {
                panel: getRect(panel),
                gridNode: getRect(gridNode),
                dxRoot: getRect(dxRoot),
                wrapper: getRect(wrapper),
                grid: getRect(grid),
            };
        }''')

        print('Measurement result:')
        for k, v in (result or {}).items():
            print(f'  {k}: {v}')

        assert result is not None
        panel = result['panel']
        grid_node = result['gridNode']
        dx_root = result['dxRoot']
        wrapper = result['wrapper']
        grid = result['grid']

        # 主断言：DataGrid canvas-node 不应超出 panel 边界
        assert grid_node['bottom'] <= panel['bottom'] + 2, (
            f"DataGrid canvas-node bottom ({grid_node['bottom']}) "
            f"overflows splitter-panel bottom ({panel['bottom']})"
        )
        # 次断言：DevExtreme 真实渲染容器 (dx-widget) 也不应超出 panel 边界
        if dx_root:
            assert dx_root['bottom'] <= panel['bottom'] + 2, (
                f"DataGrid dx-widget bottom ({dx_root['bottom']}) "
                f"overflows splitter-panel bottom ({panel['bottom']})"
            )
        # 兜底：data-grid-wrapper 也不应超出 panel 边界
        if wrapper:
            assert wrapper['bottom'] <= panel['bottom'] + 2, (
                f"DataGrid wrapper bottom ({wrapper['bottom']}) "
                f"overflows splitter-panel bottom ({panel['bottom']})"
            )

        browser.close()
        print('\nPASS: DataGrid stays within splitter-panel boundary.')


if __name__ == '__main__':
    main()
