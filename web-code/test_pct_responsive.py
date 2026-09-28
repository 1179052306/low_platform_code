"""Verify a control sized with percentage width/height follows its container when
the container is resized.

Scenario: splitter (container) -> panel -> DataGrid with css width=100%, height=100%.
  * width  is exercised by dragging the panel divider (panel gets wider).
  * height is exercised by dragging the splitter's south resize handle (container taller).
"""
import sys
from playwright.sync_api import sync_playwright

URL = 'http://localhost:5173'


def measure(page):
    return page.evaluate('''() => {
        const panel = document.querySelector('.lc-el-splitter-panel');
        const w = document.querySelector('.data-grid-wrapper');
        if (!panel || !w) return null;
        const node = w.closest('.canvas-node');
        const grid = node.querySelector('.dx-datagrid');
        const r = (el) => {
            if (!el) return null;
            const b = el.getBoundingClientRect();
            return { w: Math.round(b.width), h: Math.round(b.height),
                     left: Math.round(b.left), top: Math.round(b.top) };
        };
        return { panel: r(panel), node: r(node), wrapper: r(w), grid: r(grid) };
    }''')


def drop_material(page, name, x, y):
    item = page.locator(f'.material-item:has-text("{name}")').first
    item.scroll_into_view_if_needed()
    page.wait_for_timeout(150)
    b = item.bounding_box()
    page.mouse.move(b['x'] + b['width'] / 2, b['y'] + b['height'] / 2)
    page.mouse.down()
    page.mouse.move(x, y, steps=10)
    page.mouse.up()
    page.wait_for_timeout(600)


def set_css_size(page, label, value, unit='%'):
    inp = page.locator(f'.prop-item:has(.prop-label:has-text("{label}")) .css-unit-input').first
    sel = page.locator(f'.prop-item:has(.prop-label:has-text("{label}")) .css-unit-select').first
    sel.select_option(unit)
    page.wait_for_timeout(150)
    inp.fill(str(value))
    inp.press('Tab')
    page.wait_for_timeout(700)


def select_splitter(page):
    """Click an empty spot inside panel 2 -> selects the splitter container."""
    panels = page.locator('.lc-el-splitter-panel')
    pb = panels.nth(1).bounding_box()
    page.mouse.click(pb['x'] + pb['width'] / 2, pb['y'] + pb['height'] / 2)
    page.wait_for_timeout(400)


def drag_resize_handle(page, direction, dx, dy):
    handle = page.locator(f'.canvas-node-selected .resize-handle.{direction}').first
    hb = handle.bounding_box()
    if not hb:
        return False
    page.mouse.move(hb['x'] + hb['width'] / 2, hb['y'] + hb['height'] / 2)
    page.mouse.down()
    page.mouse.move(hb['x'] + hb['width'] / 2 + dx, hb['y'] + hb['height'] / 2 + dy, steps=12)
    page.mouse.up()
    page.wait_for_timeout(700)
    return True


def drag_panel_divider(page, dx):
    grip = page.locator('.panel-handle').first
    gb = grip.bounding_box()
    if not gb:
        return False
    page.mouse.move(gb['x'] + gb['width'] / 2, gb['y'] + gb['height'] / 2)
    page.mouse.down()
    page.mouse.move(gb['x'] + gb['width'] / 2 + dx, gb['y'] + gb['height'] / 2, steps=12)
    page.mouse.up()
    page.wait_for_timeout(700)
    return True


def report(tag, before, after, axis, failures):
    key = 'w' if axis == 'width' else 'h'
    d_panel = after['panel'][key] - before['panel'][key]
    d_node = after['node'][key] - before['node'][key]
    d_grid = (after['grid'][key] - before['grid'][key]) if after['grid'] else None
    print(f'  {tag}: panel {d_panel:+d}  node {d_node:+d}  grid {d_grid:+d}')
    if d_panel < 15:
        print(f'    [WARN] container {axis} barely changed - resize may not have applied')
        return
    if d_node < d_panel * 0.5:
        failures.append(f'{tag}: node {axis} did not follow (node {d_node:+d} vs container {d_panel:+d})')
    if d_grid is not None and d_grid < d_panel * 0.5:
        failures.append(f'{tag}: grid {axis} did not follow (grid {d_grid:+d} vs container {d_panel:+d})')


def main() -> None:
    failures = []
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1400, "height": 900})
        page.goto(URL)
        page.wait_for_load_state('networkidle')
        page.wait_for_timeout(700)

        canvas = page.locator('.canvas-page').first
        c = canvas.bounding_box()
        drop_material(page, '分割面板', c['x'] + 350, c['y'] + 200)

        # make the container taller first, so we have room to shrink/grow later
        select_splitter(page)
        if not drag_resize_handle(page, 's', 0, 150):
            print('SKIP: splitter south resize handle not found')
            browser.close()
            sys.exit(2)
        # make it wider too
        select_splitter(page)
        drag_resize_handle(page, 'e', 150, 0)

        panel = page.locator('.lc-el-splitter-panel').first
        pb = panel.bounding_box()
        drop_material(page, '数据表格', pb['x'] + pb['width'] / 2, pb['y'] + pb['height'] / 2)

        m0 = measure(page)
        assert m0
        page.mouse.click(m0['node']['left'] + m0['node']['w'] / 2,
                         m0['node']['top'] + m0['node']['h'] / 2)
        page.wait_for_timeout(400)
        set_css_size(page, '宽度', 100)
        set_css_size(page, '高度', 100)

        before = measure(page)
        print('before any resize:')
        for k in ('panel', 'node', 'wrapper', 'grid'):
            print(f'  {k:8s} {before[k]}')

        # --- width axis: drag the panel divider ---
        print('after widening panel 1 (divider +140px):')
        if not drag_panel_divider(page, 140):
            failures.append('panel divider handle not found')
        else:
            report('width', before, measure(page), 'width', failures)

        # --- height axis: make the splitter taller ---
        mid = measure(page)
        select_splitter(page)
        if not drag_resize_handle(page, 's', 0, 120):
            failures.append('splitter south resize handle not found (height pass)')
        else:
            print('after making splitter taller (+120px):')
            report('height', mid, measure(page), 'height', failures)

        final = measure(page)
        print('final:')
        for k in ('panel', 'node', 'wrapper', 'grid'):
            print(f'  {k:8s} {final[k]}')

        # --- phase 2: same again but with the DataGrid toolbar enabled ---
        # With a toolbar, css.height='100%' is converted to a pixel value,
        # so this path must be re-measured on every container resize.
        print('phase 2: enabling DataGrid toolbar')
        g = measure(page)
        page.mouse.click(g['node']['left'] + g['node']['w'] / 2,
                         g['node']['top'] + g['node']['h'] / 2)
        page.wait_for_timeout(400)
        sw = page.locator('.prop-item:has(.prop-label:has-text("显示工具栏")) .dx-switch').first
        if sw.count() == 0:
            print('  [WARN] toolbar switch not found - skipping phase 2')
        else:
            sw.click()
            page.wait_for_timeout(800)
            has_toolbar = page.evaluate(
                "() => !!document.querySelector('.data-grid-wrapper .grid-toolbar')")
            print(f'  toolbar rendered: {has_toolbar}')
            tb = measure(page)
            print(f'  with toolbar: node={tb["node"]} grid={tb["grid"]}')
            select_splitter(page)
            if drag_resize_handle(page, 's', 0, 120):
                print('  after making splitter taller (+120px):')
                report('height+toolbar', tb, measure(page), 'height', failures)
            else:
                failures.append('splitter south handle not found (toolbar pass)')

        browser.close()

    print()
    if failures:
        print('FAILED:')
        for f in failures:
            print(' -', f)
        sys.exit(1)
    print('PASS: percentage width/height follows the container size.')


if __name__ == '__main__':
    main()
