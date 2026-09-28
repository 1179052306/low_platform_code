"""After a container resize, does resizing a 100%-sized control 'jump'?

If the schema keeps a stale node.width/node.height while the DOM renders at the
percentage size, grabbing a resize handle snaps the control back to the stale size.
"""
from playwright.sync_api import sync_playwright

URL = 'http://localhost:5173'


def measure(page):
    return page.evaluate('''() => {
        const w = document.querySelector('.data-grid-wrapper');
        if (!w) return null;
        const node = w.closest('.canvas-node');
        const b = node.getBoundingClientRect();
        return { w: Math.round(b.width), h: Math.round(b.height),
                 left: Math.round(b.left), top: Math.round(b.top) };
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


def main() -> None:
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1400, "height": 900})
        page.goto(URL)
        page.wait_for_load_state('networkidle')
        page.wait_for_timeout(700)

        canvas = page.locator('.canvas-page').first
        c = canvas.bounding_box()
        drop_material(page, '分割面板', c['x'] + 350, c['y'] + 200)

        # select splitter (click empty panel 2) and make it bigger
        pb2 = page.locator('.lc-el-splitter-panel').nth(1).bounding_box()
        page.mouse.click(pb2['x'] + pb2['width'] / 2, pb2['y'] + pb2['height'] / 2)
        page.wait_for_timeout(300)
        for direction, dx, dy in (('e', 160, 0), ('s', 0, 120)):
            handle = page.locator(f'.canvas-node-selected .resize-handle.{direction}').first
            hb = handle.bounding_box()
            if not hb:
                print('SKIP: resize handle missing')
                browser.close()
                return
            page.mouse.move(hb['x'] + hb['width'] / 2, hb['y'] + hb['height'] / 2)
            page.mouse.down()
            page.mouse.move(hb['x'] + hb['width'] / 2 + dx, hb['y'] + hb['height'] / 2 + dy, steps=12)
            page.mouse.up()
            page.wait_for_timeout(600)
            page.mouse.click(pb2['x'] + pb2['width'] / 2, pb2['y'] + pb2['height'] / 2)
            page.wait_for_timeout(300)

        panel = page.locator('.lc-el-splitter-panel').first
        pb = panel.bounding_box()
        drop_material(page, '数据表格', pb['x'] + pb['width'] / 2, pb['y'] + pb['height'] / 2)

        m = measure(page)
        page.mouse.click(m['left'] + m['w'] / 2, m['top'] + m['h'] / 2)
        page.wait_for_timeout(400)
        set_css_size(page, '宽度', 100)
        set_css_size(page, '高度', 100)

        # widen the container so the rendered size diverges from the schema size
        grip = page.locator('.panel-handle').first
        gb = grip.bounding_box()
        page.mouse.move(gb['x'] + gb['width'] / 2, gb['y'] + gb['height'] / 2)
        page.mouse.down()
        page.mouse.move(gb['x'] + gb['width'] / 2 + 150, gb['y'] + gb['height'] / 2, steps=12)
        page.mouse.up()
        page.wait_for_timeout(700)

        grown = measure(page)
        print(f'rendered size after container grew: {grown}')

        # now grab the east resize handle and nudge it by +10px
        handle = page.locator('.canvas-node-selected .resize-handle.e').first
        hb = handle.bounding_box()
        if not hb:
            print('SKIP: east handle not visible (node may not be selected)')
            browser.close()
            return
        page.mouse.move(hb['x'] + hb['width'] / 2, hb['y'] + hb['height'] / 2)
        page.mouse.down()
        page.mouse.move(hb['x'] + hb['width'] / 2 + 10, hb['y'] + hb['height'] / 2, steps=6)
        page.mouse.up()
        page.wait_for_timeout(600)

        after = measure(page)
        print(f'size after nudging east handle by +10px: {after}')
        delta = after['w'] - grown['w']
        print(f'width delta: {delta:+d}  (expected ~+10 if no jump)')
        if abs(delta - 10) <= 4:
            print('PASS: resizing continues from the rendered size (no jump)')
        else:
            print(f'FAIL: control jumped by {delta:+d}px instead of +10px')

        browser.close()


if __name__ == '__main__':
    main()
